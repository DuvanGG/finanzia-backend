package com.finanzia.infrastructure.audit;

import com.finanzia.application.model.AuditRecord;
import com.finanzia.application.port.out.AuditLog;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.CreateTableRequest;
import software.amazon.awssdk.services.dynamodb.model.ResourceInUseException;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Auditoría de seguridad append-only en DynamoDB. Solo PutItem; nunca se
 * actualizan ni eliminan registros. Contenido sin secretos (sin passwords,
 * tokens ni hashes), por imposición del puerto y los casos de uso.
 */
public class DynamoAuditLog implements AuditLog {

    private static final Logger log = LoggerFactory.getLogger(DynamoAuditLog.class);

    private final DynamoDbClient client;
    private final String table;

    public DynamoAuditLog(DynamoDbClient client, String table) {
        this.client = client;
        this.table = table;
        ensureTable();
    }

    @Override
    public void record(AuditRecord record) {
        try {
            Map<String, AttributeValue> item = new LinkedHashMap<>();
            item.put("id", AttributeValue.builder().s(UUID.randomUUID().toString()).build());
            item.put("occurredAt", AttributeValue.builder().s(record.occurredAt().toString()).build());
            item.put("type", AttributeValue.builder().s(record.type().name()).build());
            item.put("outcome", AttributeValue.builder().s(record.outcome().name()).build());
            if (record.userId() != null) {
                item.put("userId", AttributeValue.builder().s(record.userId().toString()).build());
            }
            if (record.ip() != null) {
                item.put("ip", AttributeValue.builder().s(record.ip().value()).build());
            }
            if (record.device() != null) {
                item.put("device", AttributeValue.builder().s(record.device()).build());
            }
            if (record.correlationId() != null) {
                item.put("correlationId", AttributeValue.builder().s(record.correlationId()).build());
            }
            item.put("description", AttributeValue.builder().s(String.valueOf(record.description())).build());
            Map<String, AttributeValue> detailsMap = new LinkedHashMap<>();
            record.details().forEach((k, v) -> detailsMap.put(k, AttributeValue.builder().s(String.valueOf(v)).build()));
            item.put("details", AttributeValue.builder().m(detailsMap).build());

            client.putItem(builder -> builder.tableName(table).item(item));
        } catch (RuntimeException e) {
            log.error("Failed to write audit record {}: {}", record.type(), e.getMessage());
        }
    }

    private void ensureTable() {
        try {
            client.describeTable(b -> b.tableName(table));
            return;
        } catch (software.amazon.awssdk.services.dynamodb.model.ResourceNotFoundException e) {
            // la tabla no existe: la creamos abajo
        }
        try {
            client.createTable(CreateTableRequest.builder()
                    .tableName(table)
                    .attributeDefinitions(
                            a -> a.attributeName("id").attributeType("S"))
                    .keySchema(k -> k.attributeName("id").keyType("HASH"))
                    .billingMode("PAY_PER_REQUEST")
                    .build());
            log.info("Created DynamoDB audit table '{}'", table);
        } catch (ResourceInUseException e) {
            // carrera benigna con otro nodo
        }
    }
}