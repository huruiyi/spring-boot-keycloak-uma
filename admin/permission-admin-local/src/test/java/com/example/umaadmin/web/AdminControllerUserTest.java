package com.example.umaadmin.web;

import com.example.umaadmin.data.PermissionModelRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = "app.data-file=target/test-data/users-controller-permission-model.json")
class AdminControllerUserTest {

  @Autowired
  private MockMvc mockMvc;

  @Autowired
  private PermissionModelRepository repository;

  @Test
  void editUserKeepsExistingPasswordWhenPasswordIsBlank() throws Exception {
    String originalPassword = repository.get().getUsers().stream()
        .filter(user -> user.username().equals("staff"))
        .findFirst()
        .orElseThrow()
        .password();

    mockMvc.perform(post("/users")
            .with(csrf())
            .with(user("admin").roles("ADMIN", "USER_ADMIN"))
            .param("username", "staff")
            .param("email", "staff-updated@example.com")
            .param("password", "")
            .param("realmRoles", "manager"))
        .andExpect(status().is3xxRedirection())
        .andExpect(redirectedUrl("/users"));

    assertThat(repository.get().getUsers())
        .filteredOn(user -> user.username().equals("staff"))
        .singleElement()
        .satisfies(user -> {
          assertThat(user.email()).isEqualTo("staff-updated@example.com");
          assertThat(user.password()).isEqualTo(originalPassword);
          assertThat(user.realmRoles()).containsExactly("manager");
        });
  }
}
