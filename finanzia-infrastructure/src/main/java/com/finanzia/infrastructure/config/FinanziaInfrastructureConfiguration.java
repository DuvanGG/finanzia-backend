package com.finanzia.infrastructure.config;

import com.finanzia.application.config.AuthSettings;
import com.finanzia.application.port.out.AuditLog;
import com.finanzia.application.port.out.Clock;
import com.finanzia.application.port.out.EmailCodeStore;
import com.finanzia.application.port.out.EmailSender;
import com.finanzia.application.port.out.LoginAttemptStore;
import com.finanzia.application.port.out.PasswordHasher;
import com.finanzia.application.port.out.RefreshTokenStore;
import com.finanzia.application.port.out.TokenProvider;
import com.finanzia.application.port.out.UserRepository;
import com.finanzia.application.service.ChangePasswordService;
import com.finanzia.application.service.DisableAccountService;
import com.finanzia.application.service.LoginService;
import com.finanzia.application.service.RefreshSessionService;
import com.finanzia.application.service.RegisterUserService;
import com.finanzia.application.service.ResetPasswordService;
import com.finanzia.application.service.SessionManagementService;
import com.finanzia.application.service.VerifyEmailService;
import com.finanzia.infrastructure.audit.DynamoAuditLog;
import com.finanzia.infrastructure.mail.SesEmailSender;
import com.finanzia.infrastructure.redis.RedisLoginAttemptStore;
import com.finanzia.infrastructure.time.SystemClock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.ses.SesClient;

import java.net.URI;

/**
 * Ensamblado de dependencias de la capa de infraestructura: hashing, reloj,
 * clientes AWS (LocalStack), y los casos de uso de la capa de aplicación.
 */
@Configuration
@EnableConfigurationProperties(AuthProperties.class)
public class FinanziaInfrastructureConfiguration {

    private static final Logger log = LoggerFactory.getLogger(FinanziaInfrastructureConfiguration.class);

    // ----- Seguridad: hashing BCrypt cost 12 -----

    @Bean
    public BCryptPasswordEncoder bcryptPasswordEncoder() {
        return new BCryptPasswordEncoder(12);
    }

    // ----- Reloj -----

    @Bean
    public Clock systemClock() {
        return new SystemClock();
    }

    // ----- Configuración de dominio -----

    @Bean
    public AuthSettings authSettings(AuthProperties properties) {
        return properties.toSettings();
    }

    // ----- Clientes AWS apuntando a LocalStack -----

    @Bean
    public DynamoDbClient dynamoDbClient(@Value("${aws.dynamodb.endpoint}") String endpoint,
                                         @Value("${aws.dynamodb.access-key}") String accessKey,
                                         @Value("${aws.dynamodb.secret-key}") String secretKey,
                                         @Value("${aws.region}") String region) {
        return DynamoDbClient.builder()
                .endpointOverride(URI.create(endpoint))
                .region(Region.of(region))
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(accessKey, secretKey)))
                .build();
    }

    @Bean
    public SesClient sesClient(@Value("${aws.ses.endpoint}") String endpoint,
                               @Value("${aws.ses.access-key}") String accessKey,
                               @Value("${aws.ses.secret-key}") String secretKey,
                               @Value("${aws.region}") String region) {
        return SesClient.builder()
                .endpointOverride(URI.create(endpoint))
                .region(Region.of(region))
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(accessKey, secretKey)))
                .build();
    }

    @Bean
    public AuditLog auditLog(DynamoDbClient dynamoDbClient,
                             @Value("${aws.dynamodb.audit-table:finanzia-audit}") String table) {
        return new DynamoAuditLog(dynamoDbClient, table);
    }

    @Bean
    public EmailSender emailSender(SesClient sesClient,
                                   @Value("${finanzia.mail.from:no-reply@finanzia.dev}") String from,
                                   @Value("${finanzia.mail.verify-sender:false}") boolean verifySender) {
        if (verifySender) {
            try {
                sesClient.verifyEmailIdentity(builder -> builder.emailAddress(from));
                log.info("SES sender '{}' verificado (modo desarrollo)", from);
            } catch (RuntimeException e) {
                log.warn("No se pudo verificar el remitente SES '{}': {}", from, e.getMessage());
            }
        }
        return new SesEmailSender(sesClient, from);
    }

    // ----- Casos de uso de aplicación (ensamblados en infraestructura) -----

    @Bean
    public RegisterUserService registerUserService(UserRepository users, PasswordHasher hasher,
                                                   EmailCodeStore codes, EmailSender sender,
                                                   LoginAttemptStore attempts, AuditLog audit,
                                                   AuthSettings settings, Clock clock) {
        return new RegisterUserService(users, hasher, codes, sender, attempts, audit, settings, clock);
    }

    @Bean
    public VerifyEmailService verifyEmailService(UserRepository users, EmailCodeStore codes,
                                                 EmailSender sender, LoginAttemptStore attempts,
                                                 AuditLog audit, AuthSettings settings, Clock clock) {
        return new VerifyEmailService(users, codes, sender, attempts, audit, settings, clock);
    }

    @Bean
    public LoginService loginService(UserRepository users, PasswordHasher hasher, TokenProvider tokens,
                                     RefreshTokenStore sessions, LoginAttemptStore attempts,
                                     AuditLog audit, AuthSettings settings, Clock clock) {
        return new LoginService(users, hasher, tokens, sessions, attempts, audit, settings, clock);
    }

    @Bean
    public RefreshSessionService refreshSessionService(RefreshTokenStore sessions, UserRepository users,
                                                      TokenProvider tokens, AuditLog audit,
                                                      AuthSettings settings, Clock clock) {
        return new RefreshSessionService(sessions, users, tokens, audit, settings, clock);
    }

    @Bean
    public SessionManagementService sessionManagementService(RefreshTokenStore sessions, AuditLog audit, Clock clock) {
        return new SessionManagementService(sessions, audit, clock);
    }

    @Bean
    public ChangePasswordService changePasswordService(UserRepository users, PasswordHasher hasher,
                                                       RefreshTokenStore sessions, AuditLog audit, Clock clock) {
        return new ChangePasswordService(users, hasher, sessions, audit, clock);
    }

    @Bean
    public ResetPasswordService resetPasswordService(UserRepository users, PasswordHasher hasher,
                                                     EmailCodeStore codes, EmailSender sender,
                                                     LoginAttemptStore attempts, RefreshTokenStore sessions,
                                                     AuditLog audit, AuthSettings settings, Clock clock) {
        return new ResetPasswordService(users, hasher, codes, sender, attempts, sessions, audit, settings, clock);
    }

    @Bean
    public DisableAccountService disableAccountService(UserRepository users, RefreshTokenStore sessions,
                                                       AuditLog audit, Clock clock) {
        return new DisableAccountService(users, sessions, audit, clock);
    }
}