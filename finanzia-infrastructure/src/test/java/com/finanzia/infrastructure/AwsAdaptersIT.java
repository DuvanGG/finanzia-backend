package com.finanzia.infrastructure;

import com.finanzia.application.model.AuditEventType;
import com.finanzia.application.model.AuditOutcome;
import com.finanzia.application.model.AuditRecord;
import com.finanzia.domain.common.Email;
import com.finanzia.domain.common.IpAddress;
import com.finanzia.domain.common.UserId;
import com.finanzia.infrastructure.audit.DynamoAuditLog;
import com.finanzia.infrastructure.mail.SesEmailSender;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.localstack.LocalStackContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.ScanResponse;
import software.amazon.awssdk.services.ses.SesClient;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * Integración de auditoría (DynamoDB) y correo (SES) contra LocalStack.
 */
@Testcontainers
class AwsAdaptersIT {

    private static final String TABLE = "finanzia_audit_test";

    @Container
    static final LocalStackContainer LOCALSTACK =
            new LocalStackContainer(DockerImageName.parse("localstack/localstack:3.5.0"))
                    .withServices(LocalStackContainer.Service.DYNAMODB, LocalStackContainer.Service.SES)
                    .withStartupTimeout(Duration.ofMinutes(3));

    private static DynamoDbClient dynamo;
    private static SesClient ses;

    @BeforeAll
    static void clients() {
        AwsBasicCredentials credentials =
                AwsBasicCredentials.create(LOCALSTACK.getAccessKey(), LOCALSTACK.getSecretKey());
        dynamo = DynamoDbClient.builder()
                .endpointOverride(LOCALSTACK.getEndpointOverride(LocalStackContainer.Service.DYNAMODB))
                .region(Region.of(LOCALSTACK.getRegion()))
                .credentialsProvider(StaticCredentialsProvider.create(credentials))
                .build();
        ses = SesClient.builder()
                .endpointOverride(LOCALSTACK.getEndpointOverride(LocalStackContainer.Service.SES))
                .region(Region.of(LOCALSTACK.getRegion()))
                .credentialsProvider(StaticCredentialsProvider.create(credentials))
                .build();
    }

    @Test
    void auditWritesAppendOnlyRecordsWithoutSecrets() {
        DynamoAuditLog audit = new DynamoAuditLog(dynamo, TABLE);
        UserId user = UserId.random();

        audit.record(new AuditRecord(Instant.now(), AuditEventType.LOGIN_FAILED, AuditOutcome.FAILURE,
                user, IpAddress.of("10.1.1.1"), "device-x", "corr-1",
                Map.of("reason", "bad-password"), "login fallido"));
        audit.record(new AuditRecord(Instant.now(), AuditEventType.LOGIN_SUCCESS, AuditOutcome.SUCCESS,
                user, IpAddress.of("10.1.1.1"), "device-x", "corr-2",
                Map.of(), "login ok"));

        ScanResponse response = dynamo.scan(b -> b.tableName(TABLE));
        assertThat(response.items()).hasSize(2);

        Map<String, AttributeValue> item = response.items().stream()
                .filter(i -> i.get("type").s().equals("LOGIN_FAILED"))
                .findFirst().orElseThrow();
        assertThat(item.get("outcome").s()).isEqualTo("FAILURE");
        assertThat(item.get("userId").s()).isEqualTo(user.toString());
        assertThat(item.get("details").m()).containsKey("reason").doesNotContainKey("password");
        assertThat(item.keySet()).doesNotContain("password", "token", "hash");
    }

    @Test
    void sesSendsEmailThroughLocalStack() {
        ses.verifyEmailIdentity(b -> b.emailAddress("no-reply@finanzia.test"));
        ses.verifyEmailIdentity(b -> b.emailAddress("dest@finanzia.test"));
        SesEmailSender sender = new SesEmailSender(ses, "no-reply@finanzia.test");

        assertThatCode(() -> sender.send(Email.of("dest@finanzia.test"), "Verifica tu cuenta", "Codigo: 123456"))
                .doesNotThrowAnyException();
    }
}