package com.example.umaadmin;

import com.example.umaadmin.data.PermissionModelRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@TestPropertySource(properties = "app.data-file=target/test-data/permission-model.json")
class PermissionAdminApplicationTest {

  @Autowired
  private PermissionModelRepository repository;

  @Test
  void contextLoadsWithDefaultPermissionModel() {
    assertThat(repository.get().getUsers()).isNotEmpty();
    assertThat(repository.get().getResources()).extracting("name").contains("order", "system");
  }
}
