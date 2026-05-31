package com.example.umaadmin.audit;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

@Service
public class AuditLogService {

  private final ObjectMapper objectMapper;
  private final Path auditFile;

  public AuditLogService(@Value("${app.audit.file}") String auditFile) {
    this.objectMapper = new ObjectMapper();
    this.auditFile = Path.of(auditFile);
  }

  public void record(String operator, String sourceIp, String action, String target, String before, String after) {
    Map<String, Object> event = new LinkedHashMap<>();
    event.put("time", OffsetDateTime.now().toString());
    event.put("operator", operator);
    event.put("sourceIp", sourceIp);
    event.put("action", action);
    event.put("target", target);
    event.put("before", parseJson(before));
    event.put("after", parseJson(after));
    try {
      Path parent = auditFile.toAbsolutePath().getParent();
      if (parent != null) {
        Files.createDirectories(parent);
      }
      Files.writeString(
          auditFile,
          objectMapper.writeValueAsString(event) + System.lineSeparator(),
          StandardCharsets.UTF_8,
          StandardOpenOption.CREATE,
          StandardOpenOption.APPEND
      );
    } catch (IOException e) {
      throw new IllegalStateException("Failed to write audit log", e);
    }
  }

  private Object parseJson(String json) {
    if (json == null || json.isBlank()) {
      return null;
    }
    try {
      return objectMapper.readTree(json);
    } catch (IOException e) {
      return json;
    }
  }
}
