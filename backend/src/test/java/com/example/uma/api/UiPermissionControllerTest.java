package com.example.uma.api;

import com.example.uma.api.dto.UiPermissionDto;
import com.example.uma.security.UiPermissionCatalog;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class UiPermissionControllerTest {

  @Autowired
  private MockMvc mockMvc;

  @MockitoBean
  private UiPermissionCatalog catalog;

  @Test
  void listUiPermissionsRequiresAuthentication() throws Exception {
    mockMvc.perform(get("/api/ui-permissions"))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void listUiPermissionsReturnsEnabledModelItems() throws Exception {
    when(catalog.listEnabled()).thenReturn(List.of(new UiPermissionDto(
        "menu.orders",
        "订单管理菜单",
        "menu",
        "orders",
        "order#view",
        10,
        true
    )));

    mockMvc.perform(get("/api/ui-permissions").with(jwt()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data[0].code").value("menu.orders"))
        .andExpect(jsonPath("$.data[0].name").value("订单管理菜单"))
        .andExpect(jsonPath("$.data[0].permission").value("order#view"));
  }
}
