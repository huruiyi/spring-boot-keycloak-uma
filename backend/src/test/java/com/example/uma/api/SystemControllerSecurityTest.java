package com.example.uma.api;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Map;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class SystemControllerSecurityTest {

  @Autowired
  private MockMvc mockMvc;

  @Test
  void viewConfigAllowsRptWithSystemViewPermission() throws Exception {
    mockMvc.perform(get("/api/system/config").with(jwt().jwt(jwt -> jwt.claim("authorization", authorization("system", "view")))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.region").value("local"));
  }

  @Test
  void editConfigRejectsRptWithoutSystemEditScope() throws Exception {
    mockMvc.perform(post("/api/system/config").with(jwt().jwt(jwt -> jwt.claim("authorization", authorization("system", "view")))))
        .andExpect(status().isForbidden());
  }

  private Map<String, Object> authorization(String resource, String scope) {
    return Map.of("permissions", List.of(Map.of("rsname", resource, "scopes", List.of(scope))));
  }
}
