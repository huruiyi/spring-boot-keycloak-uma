package com.example.umaadmin.audit;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class AuditLogServiceTest {

  @TempDir
  private Path tempDir;

  @Test
  void recordWritesJsonLineWithBeforeAndAfterSnapshots() throws Exception {
    Path auditFile = tempDir.resolve("audit.log");
    AuditLogService service = new AuditLogService(auditFile.toString());

    service.record("admin", "127.0.0.1", "POST /policies", "name=policy-admin", "{\"value\":1}", "{\"value\":2}");

    String line = Files.readString(auditFile).trim();
    JsonNode event = new ObjectMapper().readTree(line);
    assertThat(event.path("operator").asText()).isEqualTo("admin");
    assertThat(event.path("sourceIp").asText()).isEqualTo("127.0.0.1");
    assertThat(event.path("action").asText()).isEqualTo("POST /policies");
    assertThat(event.path("target").asText()).isEqualTo("name=policy-admin");
    assertThat(event.path("before").path("value").asInt()).isEqualTo(1);
    assertThat(event.path("after").path("value").asInt()).isEqualTo(2);
  }
}
