## Context

FinanzIA es un monolito hexagonal greenfield (Java 21, Spring Boot 4.1, WebFlux) con módulos `domain`, `application`, `api`, `infrastructure` y `bootstrap` — todos vacíos salvo el bootstrap. Ya hay Postgres y LocalStack (S3, DynamoDB) en `docker-compose.yml`, Flyway desactivado, y ningún cliente Redis. El frontend es una SPA Angular que consume la API REST. Ver proposal.md para la motivación; los espec `auth/*` definen el comportamiento contratado.

Restricciones que condicionan el diseño:
- Payload-friendly: dominios y aplicación no pueden depender de Spring Security.
- Multi-tenant: el aislamiento por `userId` es un requisito de seguridad de primer orden.
- SPA: tokens por header `Authorization: Bearer`, sin cookies, sin sesiones servidor.
- Auth propia en Postgres; Redis solo para estado efímero (aún no existe en el compose).

## Goals / Non-Goals

**Goals:**
- Auth autocontenido con puertos limpios: el dominio no conoce Spring, JPA ni JWT.
- Sesiones/refresh tokens con Postgres como fuente de verdad; Redis para rate limiting/lockout y, opcionalmente, denylist de access tokens.
- Spring Security solo como adaptador (config en `infrastructure`, filtros/controladores en `api`).
- Flujos completos: registro + verificación, login + lockout, rotación de refresh + detección de reuso, revocación, recovery, RBAC y aislamiento.

**Non-Goals:**
- No IdP externo ni OAuth2/OIDC (se diseñará luego como otro adaptador del mismo `TokenProvider`/`UserRepository`).
- No MFA/TOTP en esta iteración (los puertos dejan hueco para `totpVerifier`).
- No modelo de organización/tenant ni recursos compartidos entre usuarios (el aislamiento es ownership por `userId`).
- No secret handling en production (env/secrets manager es responsabilidad externa).
- No UI de administración de usuarios; solo endpoints y reglas.

## Decisions

### D1. Límites de módulo y ubicación de puertos
- `finanzia-domain`: entidades y VOs sin dependencias. `User` (aggregate root) y `Session` (aggregate separado referenciado por `userId`). No define repositorios.
- `finanzia-application`: casos de uso (services) y **puertos** (interfaces outbound). Los repositorios viven aquí —clean architecture clásica— para que `domain` quede solo con entidades, VOs, eventos y reglas.
- `finanzia-infrastructure`: adaptadores JPA/BCrypt/JJWT/Redis/DynamoDB/SES + beans de Spring Security (`JwtEncoder`, `JwtDecoder`, `PasswordEncoder`, `ReactiveAuthenticationManager`).
- `finanzia-api`: `SecurityWebFilterChain`, filtro JWT, `@RestController` de `/auth/*`, DTOs, `@PreAuthorize`.

*Alternativa*: repositorios como interfaces en `domain` (visión DDD). Se descartó para conservar el dominio 100% libre de persistencia y porque no hay aggregates complejos que la justifiquen.

### D2. Tipos de token y algoritmo
- **Access token** = JWS firmado con **HS256** (HMAC, secret ≥ 32 bytes desde env), claims: `iss` (finanzia), `aud` (finanzia-api), `sub` (userId), `jti` (uuid), `iat`, `exp` (15 min), y snapshot de `roles` para autorización barata. Header con `kid` para rotación de clave. Tolerancia de reloj ±30s.
- **Refresh token** = valor opaco aleatorio de 256 bits; **nunca se persiste en claro**, solo su SHA-256.
- Se usa JJWT 0.12.x (encode/decode/verify) detrás del puerto `TokenProvider`/`TokenDecoder`.

*Alternativa*: asimétrico (RS256/ES256) se descartó al ser un solo servicio confiable; el puerto `TokenProvider` permite cambiar de curva sin tocar dominio. Roles en token (no lookup por request) por latencia; el costo es la frescura — cubierto por `auth/authorization` (re-auth tras cambio de rol) y por el TTL de 15 min.

### D3. Rotación de refresh, family y detección de reuso
Tabla `sessions` (Postgres) como ledger de sesiones:

```
sessions
  id            uuid PK
  user_id       uuid FK -> users.id        (index)
  family_id     uuid                       (index) -- familia de rotacion
  token_hash    char(64) UNIQUE            -- sha256 del refresh actual
  device        text
  ip            text
  created_at    timestamptz
  expires_at    timestamptz
  rotated_to    uuid null                  -- fila sucesora
  revoked_at    timestamptz null           -- revocacion explicita
  consumed_at   timestamptz null           -- token usado para rotar
```

Rotación: `UPDATE sessions SET rotated_to = ?, consumed_at = now() WHERE token_hash = ? AND revoked_at IS NULL AND expires_at > now()` — si afecta 0 filas (token ya usado, revocado o expirado) → **camino de reuso/revocación**.
Reuso: marcar `revoked_at = now()` para **todas** las filas de la `family_id`, registrar `RefreshTokenReused` en auditoría.

**Ventana de gracia** para concurrencia (Requirement "Concurrent refresh"): si el token presentado tiene `consumed_at` dentro de la ventana (config ~30 s), se resuelve el `rotated_to` sucesor y se le devuelve el par actual del sucesor (sin emitir rotación extra ni revocar). La familia solo se revoca para presentaciones fuera de ventana.

### D4. Límite de sesiones activas
La creación de una sesión (login) cuenta las filas activas (`revoked_at IS NULL AND expires_at > now()`) de `user_id`; sobre 10 activas se revoca la más antigua (`ORDER BY created_at`).

### D5. Hashing de contraseña
Puerto `PasswordHasher` con BCrypt **cost 12** (Spring Security crypto). El VO `Password` valida política (≥12 chars, no repetición) dentro del dominio; el hashing ocurre en `infrastructure`.

### D6. Brute-force / lockout en Redis
Puerto `LoginAttemptStore`: contadores atómicos por `user_id` y por IP con TTL (INCR+EXPIRE). Lockout progresivo (5 fallos → 5 min, 10 → 15, 15 → 60) evaluado en `application`. Si Redis cae → **fail-open con alerta** (ver Riesgos).

### D7. Auditoría en DynamoDB
Puerto `AuditLog`: los casos de uso emiten eventos de dominio (`LoginFailed`, `AccountLocked`, `RefreshTokenReused`, `SessionRevoked`, `CredentialsChanged`, `UserRegistered`, `EmailVerified`) y un publicador los persiste en DynamoDB (tabla `finanzia-audit` en LocalStack), siempre dentro del contrato de `auth/security-audit` (cero secretos).

### D8. Spring Security WebFlux como adaptador
- En `infrastructure`: beans `JwtEncoder`, `JwtDecoder`, `PasswordEncoder`, y un `ReactiveAuthenticationManager` que valida el Bearer y produce un principal portador de `userId` + roles (snapshot del token).
- En `api`: `SecurityWebFilterChain` con allowlist de rutas públicas (`POST /auth/register`, `/auth/verify-email`, `/auth/login`, `/auth/refresh`, `/auth/forgot-password`, `/auth/reset-password`, `/actuator/health`), todo lo demás deny-by-default; `@EnableReactiveMethodSecurity` para `@PreAuthorize("hasRole('ADMIN')")`.
- Bearer → sin CSRF; CORS allowlist del origen Angular + headers de seguridad (HSTS, X-Content-Type-Options, X-Frame-Options, CSP).

### D9. Códigos de verificación/reset
Tabla `email_codes` (purpose = VERIFY | RESET), TTL 24 h (verify) / 15 min (reset), single-use, almacenados hasheados. Envío por `EmailSender` (SES en LocalStack).

### D10. Modelo de datos (migración Flyway V1__auth.sql)

```
users
  id            uuid PK
  email         varchar UNIQUE (normalizado, lowercase)
  password_hash varchar
  role          varchar (USER | ADMIN)
  email_verified_at timestamptz null
  status        varchar (ACTIVE | DISABLED)
  created_at / updated_at timestamptz

sessions      (ver D3)
email_codes
  id uuid PK; user_id FK; code_hash char(64); purpose varchar;
  expires_at timestamptz; consumed_at timestamptz null  (UNIQUE user_id+mix?)
```

## Risks / Trade-offs

- **Reuso vs concurrencia** → mitigado con ventana de gracia (D3); residual: un atacante que replique dentro de la ventana logra una rotación antes de ser detectado → impacto acotado por refresh opaco (no reutilizable fuera de la ventana) y auditoría del reuso fuera de ventana.
- **Staleness del rol en el token** → mitigado con TTL corto y re-auth tras cambio de rol (spec `authorization`); el límite es ≤15 min.
- **Redis caído** → fail-open degrada la protección brute-force; se compensa con alerta y contadores en Postgres como fallback opcional (decisión de spillover futura).
- **Clave HS256 única** → rotación con `kid`; migración futura a firma asimétrica es aislada tras `TokenProvider`.
- **Relojes** → tolerancia ±30s en decode + NTP.
- **Crecimiento de auditoría** → retención/expiración como operación externa (fuera de specs).
- **Renombrado de paras en rotación** → la ventana de gracia exige IDs de familia estables; no se reutilizan UUIDs.

## Migration Plan

1. Agregar servicio `redis` a `docker-compose.yml` (contraparte de la cache del dashboard ya planificada).
2. Habilitar Flyway en `application.yml`; `V1__auth.sql` crea `users`, `sessions`, `email_codes`.
3. Configuración por env: `JWT_SECRET` (≥32 bytes), TTLs de access/refresh/códigos, política de lockout, allowlist CORS de Angular.
4. Se despliega como feature flag desactivado hasta que exista un primer usuario consumiendo la API; sin cambios de esquema posteriores destructivos en el mismo cambio.
5. Rollback: revertir el commit de la migración; las tablas son nuevas (no afectan features existentes; no las hay hoy).

## Open Questions

- **Bootstrap del primer ADMIN** (seed por env en una migración/command vs endpoint de setup): se asume seed por env en `infrastructure`; resolvible en tasks sin tocar specs.
- **Expiración/purga de sesiones revocadas y códigos** (job vs query-lazy deletion): se asume borrado perezoso al listar/generar; no cambia el comportamiento observable contratado en specs.