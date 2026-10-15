## 1. Infraestructura y dependencias

- [x] 1.1 Añadir dependencias por módulo en `build.gradle` (Spring Security WebFlux + validation en `finanzia-api`; `spring-security-crypto`, JJWT 0.12.x, `commons-lang3` en `finanzia-infrastructure`) y verificar `./gradlew build` compila todos los módulos
- [x] 1.2 Añadir servicio `redis` a `docker-compose.yml` y verificar `docker compose up -d redis` queda healthy
- [x] 1.3 Habilitar Flyway y crear `V1__auth.sql` (tablas `users`, `sessions`, `email_codes` con índices por `user_id`, `family_id`, `token_hash` UNIQUE) y verificar que el arranque con `ddl-auto: validate` valida sin errores contra Postgres de compose

## 2. Dominio (`finanzia-domain`)

- [x] 2.1 Implementar VO `Email` (trim, lowercase, IDN/punycode) y verificar tests unitarios de normalización y duplicados
- [x] 2.2 Implementar VOs `UserId`, `SessionId`, `TokenFamilyId`, `JwtId`, `Role`, `TokenTtl`, `IpAddress` y verificar tests de igualdad por valor y validación
- [x] 2.3 Implementar VOs `Password` (política ≥12 chars, sin repetición) y `PasswordHash` y verificar tests de la política
- [x] 2.4 Implementar entidad `User` (email verificado, status ACTIVE/DISABLED, rol, `changePassword`) y verificar tests de reglas (no loguea sin email verificado, no loguea deshabilitado)
- [x] 2.5 Implementar entidad `Session` (creación, rotación, revocación simple y de familia, expiración) y verificar tests de reuso y límite de ventana
- [x] 2.6 Implementar eventos de dominio (`UserRegistered`, `EmailVerified`, `LoginFailed`, `AccountLocked`, `RefreshTokenReused`, `SessionRevoked`, `CredentialsChanged`) y verificar `./gradlew :finanzia-domain:test`

## 3. Aplicación (`finanzia-application`: puertos + casos de uso)

- [x] 3.1 Definir puertos `UserRepository`, `RefreshTokenStore`, `PasswordHasher`, `TokenProvider`, `TokenDecoder`, `Clock`, `EmailSender`, `LoginAttemptStore`, `AuditLog` y verificar que `finanzia-application` compila sin dependencias de Spring Security/Spring Framework
- [x] 3.2 Implementar `RegisterUser` (normalización, política de password, cuenta no verificada, email de verificación, respuesta genérica anti-enumeración) y verificar tests unitarios de `auth/user-registration`
- [x] 3.3 Implementar `VerifyEmail` (código single-use, expiración, reenvío) y verificar tests unitarios
- [x] 3.4 Implementar `Login` (verificación de email, verificación de hash incluso para emails desconocidos, respuesta genérica, lockout progresivo por cuenta e IP, reset de contador, emisión de par de tokens + sesión) y verificar tests de `auth/user-login`
- [x] 3.5 Implementar `RefreshSession` (rotación con marcar consumido, ventana de gracia de ~30 s para concurrencia, detección de reuso → revocación de toda la familia + evento de auditoría) y verificar tests de `auth/token-session`
- [x] 3.6 Implementar gestión de sesiones: listar, revocar una, `logout-all`, y límite de 10 sesiones activas (revocar la más antigua) y verificar tests unitarios
- [x] 3.7 Implementar `ChangePassword` (revoca otras sesiones, conserva la actual) y `ResetPassword` (código single-use corto, revoca todas las sesiones, política de password) y verificar tests de `auth/password-recovery`
- [x] 3.8 Implementar deshabilitación de cuenta (revoca todas las sesiones y bloquea login) y verificar tests
- [x] 3.9 Implementar publicación de eventos a `AuditLog` desde cada caso de uso y verificar test de contenido sin secretos (sin passwords, tokens ni hashes)

## 4. Colas de infraestructura (`finanzia-infrastructure`: adaptadores)

- [x] 4.1 Implementar `BCryptPasswordHasher` con cost 12 y verificar tests de hash/verify y rechazo de hash inválido
- [x] 4.2 Implementar `JpaUserRepository` + `UserJpaEntity` + mapeo con MapStruct y verificar tests de integración con Testcontainers Postgres
- [x] 4.3 Implementar `JpaSessionStore` + `SessionJpaEntity` con consultas atómicas (rotación por `token_hash`, revocación por `family_id`, listado por `user_id`) y verificar tests de integración
- [x] 4.4 Implementar `JwtTokenProvider` (HS256, claims iss/aud/sub/jti/iat/exp/roles, `kid`, tolerancia de reloj ±30 s, rechazo de expirados) y verificar tests de encode/decode/rechazo
- [x] 4.5 Implementar `RedisLoginAttemptStore` (contadores atómicos con TTL por cuenta e IP) y verificar tests con Testcontainers Redis
- [x] 4.6 Implementar `DynamoAuditLog` (append-only, sin update ni delete) y verificar tests con Testcontainers LocalStack
- [x] 4.7 Implementar `EmailSender` vía SES en LocalStack y verificar envío en test de integración
- [x] 4.8 Implementar `SystemClock` y verificar que los casos de uso reciben la hora inyectada en tests

## 5. API (`finanzia-api`: Spring Security + endpoints)

- [x] 5.1 Implementar `SecurityWebFilterChain` con allowlist de rutas públicas (`/auth/register`, `/auth/verify-email`, `/auth/login`, `/auth/refresh`, `/auth/forgot-password`, `/auth/reset-password`, `/actuator/health`) y deny-by-default, y verificar 401 sin token con `WebTestClient`
- [x] 5.2 Implementar filtro JWT que resuelve Bearer → principal (`userId` + roles del token) y verificar que tokens expirados/malformados se rechazan
- [x] 5.3 Implementar `POST /auth/register` y `POST /auth/verify-email` con DTOs de validación y verificar contrato de `auth/user-registration` con `WebTestClient`
- [x] 5.4 Implementar `POST /auth/login` con respuestas genéricas de error y lockout, y verificar contrato de `auth/user-login`
- [x] 5.5 Implementar `POST /auth/refresh` (rotación + ventana de gracia + reuso) y verificar contrato de `auth/token-session`
- [x] 5.6 Implementar `POST /auth/logout` y `POST /auth/logout-all` y verificar revocación de sesión/familia
- [x] 5.7 Implementar `GET /auth/sessions` y `DELETE /auth/sessions/{id}` y verificar listado y revocación individual
- [x] 5.8 Implementar `POST /auth/change-password`, `POST /auth/forgot-password` y `POST /auth/reset-password` y verificar contrato de `auth/password-recovery`
- [x] 5.9 Implementar `@EnableReactiveMethodSecurity` con `@PreAuthorize("hasRole('ADMIN')")` y un recurso protegido de ejemplo; verificar con `WebTestClient` 403 para USER, 200 para ADMIN, y 404 (no 403) al acceder a recurso de otro `userId`

## 6. Configuración, bootstrap y verificación final

- [x] 6.1 Configurar `application.yml`: `JWT_SECRET` por env (≥32 bytes), TTLs (access 15 min, refresh 7 días, código verify 24 h, reset 15 min), política de lockout, ventana de gracia, y CORS allowlist del origen Angular + headers de seguridad (HSTS, X-Content-Type-Options, X-Frame-Options, CSP)
- [x] 6.2 Seed del primer ADMIN por env en `finanzia-infrastructure` y verificar que arranca con un admin activo y contraseña hasheada
- [x] 6.3 Test de integración end-to-end con `WebTestClient` + Testcontainers (Postgres + Redis + LocalStack) que cubre el flujo completo register → verify → login → refresh (rotación) → reuso detectado → logout-all, y limpia sesiones revocadas de forma perezosa al listar/generar
- [x] 6.4 Verificar que `./gradlew test` pasa completo, la app arranca contra compose, y `openspec validate --all` reporta el change validado