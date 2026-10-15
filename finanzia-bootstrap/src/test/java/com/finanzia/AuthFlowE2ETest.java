package com.finanzia;

import com.finanzia.domain.common.Email;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.localstack.LocalStackContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.time.Duration;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Test end-to-end sobre infraestructura real (Testcontainers: Postgres, Redis,
 * LocalStack): registro → verificación → login → refresh (rotación) →
 * logout-all, reglas de autorización (401/403/404), roles ADMIN/USER y flujos
 * de contraseña.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
class AuthFlowE2ETest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>(DockerImageName.parse("postgres:16-alpine"));

    @Container
    static final GenericContainer<?> REDIS =
            new GenericContainer<>(DockerImageName.parse("redis:7-alpine")).withExposedPorts(6379);

    @Container
    static final LocalStackContainer LOCALSTACK =
            new LocalStackContainer(DockerImageName.parse("localstack/localstack:3.5.0"))
                    .withServices(LocalStackContainer.Service.DYNAMODB, LocalStackContainer.Service.SES)
                    .withStartupTimeout(Duration.ofMinutes(3));

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.data.redis.host", REDIS::getHost);
        registry.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));
        registry.add("aws.region", () -> "us-east-1");
        registry.add("aws.dynamodb.endpoint", () -> LOCALSTACK.getEndpointOverride(
                LocalStackContainer.Service.DYNAMODB).toString());
        registry.add("aws.dynamodb.access-key", LOCALSTACK::getAccessKey);
        registry.add("aws.dynamodb.secret-key", LOCALSTACK::getSecretKey);
        registry.add("aws.dynamodb.audit-table", () -> "finanzia-audit-e2e");
        registry.add("aws.ses.endpoint", () -> LOCALSTACK.getEndpointOverride(
                LocalStackContainer.Service.SES).toString());
        registry.add("aws.ses.access-key", LOCALSTACK::getAccessKey);
        registry.add("aws.ses.secret-key", LOCALSTACK::getSecretKey);
        registry.add("finanzia.admin.email", () -> "admin@finanzia.test");
        registry.add("finanzia.admin.password", () -> "AdminSeed123!");
    }

    @TestConfiguration
    static class EmailConfig {
        @Bean
        @Primary
        RecordingEmailSender recordingEmailSender() {
            return new RecordingEmailSender();
        }
    }

    @Autowired
    RecordingEmailSender emails;
    @LocalServerPort
    int port;
    WebTestClient web;

    @BeforeEach
    void client() {
        web = WebTestClient.bindToServer().baseUrl("http://localhost:" + port).build();
    }

    private static final String ALICE = "alice@example.com";
    private static final String CAROL = "carol@example.com";
    private static final String OWNER = "owner@example.com";
    private static final String VICTIM = "victim@example.com";
    private static final String PASSWORD = "StrongPass123!";

    @Test
    void fullFlowRegisterVerifyLoginRefreshLogoutAll() {
        register(ALICE);
        String verifyCode = emails.lastCodeFor(Email.of(ALICE), "Verifica");

        web.post().uri("/auth/verify-email")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of("code", verifyCode))
                .exchange()
                .expectStatus().isNoContent();

        Tokens login = login(ALICE, PASSWORD);

        web.get().uri("/auth/sessions").header(HttpHeaders.AUTHORIZATION, "Bearer " + login.accessToken())
                .exchange()
                .expectStatus().isOk()
                .expectBody().jsonPath("$.length()").isEqualTo(1);

        // Reuso del propio token recién emitido (cabeza activa, ventana de gracia)
        refresh(login.refreshToken());
        // Token original ya consumido: dentro de la ventana, sigue la cadena y rota la cabeza
        Tokens pair3 = refresh(login.refreshToken());

        web.post().uri("/auth/logout-all")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + pair3.accessToken())
                .exchange()
                .expectStatus().isNoContent();

        web.get().uri("/auth/sessions").header(HttpHeaders.AUTHORIZATION, "Bearer " + pair3.accessToken())
                .exchange()
                .expectStatus().isOk()
                .expectBody().jsonPath("$.length()").isEqualTo(0);

        // Tras logout-all la familia queda revocada también: refresh → 401
        web.post().uri("/auth/refresh")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of("refreshToken", pair3.refreshToken()))
                .exchange()
                .expectStatus().isUnauthorized();
    }

    @Test
    void securityRulesAndIdor() {
        register(OWNER);
        verify(emails.lastCodeFor(Email.of(OWNER), "Verifica"));
        Tokens alice = login(OWNER, PASSWORD);

        register(VICTIM);
        verify(emails.lastCodeFor(Email.of(VICTIM), "Verifica"));
        Tokens bob = login(VICTIM, PASSWORD);

        String sessionsBody = web.get().uri("/auth/sessions")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + bob.accessToken())
                .exchange().expectStatus().isOk()
                .returnResult(String.class).getResponseBody().blockFirst();
        String bobSessionId = JsonPath.read(sessionsBody, "$[0].id");

        // IDOR: acceder a la sesión de Bob con el token de Alice responde 404 (no 403)
        web.delete().uri("/auth/sessions/{id}", bobSessionId)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + alice.accessToken())
                .exchange()
                .expectStatus().isNotFound();

        web.delete().uri("/auth/sessions/{id}", UUID.randomUUID())
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + alice.accessToken())
                .exchange()
                .expectStatus().isNotFound();

        // 401 sin token
        web.get().uri("/auth/sessions").exchange().expectStatus().isUnauthorized();

        // USER no puede usar endpoints de ADMIN
        web.post().uri("/admin/users/{id}/disable", alice.userId())
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + alice.accessToken())
                .exchange()
                .expectStatus().isForbidden();

        // ADMIN (seed) puede deshabilitar, y el usuario ya no puede loguearse
        Tokens admin = login("admin@finanzia.test", "AdminSeed123!");
        web.post().uri("/admin/users/{id}/disable", alice.userId())
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + admin.accessToken())
                .exchange()
                .expectStatus().isNoContent();

        web.post().uri("/auth/login").contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of("email", OWNER, "password", PASSWORD))
                .exchange()
                .expectStatus().isUnauthorized();
    }

    @Test
    void passwordChangeAndRecovery() {
        register(CAROL);
        verify(emails.lastCodeFor(Email.of(CAROL), "Verifica"));
        Tokens carol = login(CAROL, PASSWORD);

        // Contraseña actual incorrecta → 401
        web.post().uri("/auth/change-password")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + carol.accessToken())
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of("currentPassword", "WrongPass123!", "newPassword", "NewStrong456!"))
                .exchange()
                .expectStatus().isUnauthorized();

        web.post().uri("/auth/change-password")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + carol.accessToken())
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of("currentPassword", PASSWORD, "newPassword", "NewStrong456!"))
                .exchange()
                .expectStatus().isNoContent();

        web.post().uri("/auth/login").contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of("email", CAROL, "password", PASSWORD))
                .exchange()
                .expectStatus().isUnauthorized();

        Tokens relogin = login(CAROL, "NewStrong456!");
        assertThat(relogin.userId()).isEqualTo(carol.userId());

        // Recuperación: forgot → código por email → reset → login con la nueva
        web.post().uri("/auth/forgot-password")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of("email", CAROL))
                .exchange()
                .expectStatus().isAccepted();

        String resetCode = emails.lastCodeFor(Email.of(CAROL), "Recupera");
        web.post().uri("/auth/reset-password")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of("code", resetCode, "newPassword", "Recovered789!"))
                .exchange()
                .expectStatus().isNoContent();

        login(CAROL, "Recovered789!");
    }

    private void register(String email) {
        web.post().uri("/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of("email", email, "password", PASSWORD))
                .exchange()
                .expectStatus().isAccepted()
                .expectBody().jsonPath("$.message").isNotEmpty();
    }

    private void verify(String code) {
        web.post().uri("/auth/verify-email")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of("code", code))
                .exchange()
                .expectStatus().isNoContent();
    }

    private Tokens login(String email, String password) {
        String body = web.post().uri("/auth/login").contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of("email", email, "password", password))
                .exchange()
                .expectStatus().isOk()
                .returnResult(String.class).getResponseBody().blockFirst();
        return new Tokens(JsonPath.read(body, "$.accessToken"), JsonPath.read(body, "$.refreshToken"),
                JsonPath.read(body, "$.userId"));
    }

    private Tokens refresh(String refreshToken) {
        String body = web.post().uri("/auth/refresh").contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of("refreshToken", refreshToken))
                .exchange()
                .expectStatus().isOk()
                .returnResult(String.class).getResponseBody().blockFirst();
        return new Tokens(JsonPath.read(body, "$.accessToken"), JsonPath.read(body, "$.refreshToken"), null);
    }

    private record Tokens(String accessToken, String refreshToken, String userId) {
    }
}