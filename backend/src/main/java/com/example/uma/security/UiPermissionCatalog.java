package com.example.uma.security;

import com.example.uma.api.dto.UiPermissionDto;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.stream.StreamSupport;

@Component
public class UiPermissionCatalog {

  private final ObjectMapper objectMapper;
  private final Path modelFile;

  public UiPermissionCatalog(
      @Value("${app.permission-model-file:../permission-data/permission-model.json}") String modelFile
  ) {
    this.objectMapper = new ObjectMapper();
    this.modelFile = Path.of(modelFile);
  }

  public List<UiPermissionDto> listEnabled() {
    if (!Files.exists(modelFile)) {
      return List.of();
    }

    try {
      JsonNode uiPermissions = objectMapper.readTree(modelFile.toFile()).path("uiPermissions");
      if (!uiPermissions.isArray()) {
        return List.of();
      }

      return StreamSupport.stream(uiPermissions.spliterator(), false)
          .map(this::toDto)
          .filter(UiPermissionDto::enabled)
          .sorted(Comparator.comparingInt(UiPermissionDto::sort).thenComparing(UiPermissionDto::code))
          .toList();
    } catch (IOException e) {
      throw new IllegalStateException("读取 UI 权限模型失败：" + modelFile, e);
    }
  }

  private UiPermissionDto toDto(JsonNode node) {
    return new UiPermissionDto(
        node.path("code").asText(),
        node.path("name").asText(),
        node.path("type").asText(),
        node.path("page").asText(),
        node.path("permission").asText(),
        node.path("sort").asInt(),
        node.path("enabled").asBoolean(true)
    );
  }
}
