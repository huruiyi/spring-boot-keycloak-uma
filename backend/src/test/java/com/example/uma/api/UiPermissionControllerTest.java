package com.example.uma.api;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = "app.permission-model-file=../permission-data/permission-model.json")
class UiPermissionControllerTest {

  @Autowired
  private MockMvc mockMvc;

  @Test
  void listUiPermissionsRequiresAuthentication() throws Exception {
    mockMvc.perform(get("/api/ui-permissions"))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void listUiPermissionsReturnsEnabledModelItems() throws Exception {
    mockMvc.perform(get("/api/ui-permissions").with(jwt()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data[0].code").value("menu.orders"))
        .andExpect(jsonPath("$.data[0].name").value("订单管理菜单"))
        .andExpect(jsonPath("$.data[0].permission").value("order#view"));
  }
}
