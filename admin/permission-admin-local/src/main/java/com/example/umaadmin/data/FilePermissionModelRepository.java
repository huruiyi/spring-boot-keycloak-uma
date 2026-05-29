package com.example.umaadmin.data;

import com.example.umaadmin.model.PermissionModel;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Repository;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

@Repository
public class FilePermissionModelRepository implements PermissionModelRepository {

  private static final Logger log = LoggerFactory.getLogger(FilePermissionModelRepository.class);

  private final ObjectMapper objectMapper;
  private final Path dataFile;
  private PermissionModel cachedModel;

  public FilePermissionModelRepository(@Value("${app.data-file}") String dataFile) {
    this.objectMapper = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);
    this.dataFile = Path.of(dataFile);
  }

  @Override
  public synchronized PermissionModel get() {
    if (cachedModel != null) {
      return cachedModel;
    }
    if (!Files.exists(dataFile)) {
      cachedModel = PermissionModelFactory.defaultModel();
      save(cachedModel);
      return cachedModel;
    }
    try {
      cachedModel = objectMapper.readValue(dataFile.toFile(), PermissionModel.class);
      return cachedModel;
    } catch (IOException e) {
      log.error("Failed to read permission model file: {}", dataFile.toAbsolutePath(), e);
      throw new IllegalStateException("Failed to read permission model file: " + dataFile, e);
    }
  }

  @Override
  public synchronized void save(PermissionModel model) {
    try {
      Path parent = dataFile.getParent();
      if (parent != null) {
        Files.createDirectories(parent);
      }
      objectMapper.writeValue(dataFile.toFile(), model);
      cachedModel = model;
    } catch (IOException e) {
      log.error("Failed to save permission model file: {}", dataFile.toAbsolutePath(), e);
      throw new IllegalStateException("Failed to save permission model file: " + dataFile, e);
    }
  }
}
