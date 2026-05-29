package com.example.uma.api;

import com.example.uma.config.RequestTraceFilter;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Map;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class OrderControllerSecurityTest {

  @Autowired
  private MockMvc mockMvc;

  @Test
  void listOrdersRequiresAuthentication() throws Exception {
    mockMvc.perform(get("/api/orders"))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void listOrdersAllowsRptWithOrderViewPermission() throws Exception {
    mockMvc.perform(get("/api/orders").with(jwt().jwt(jwt -> jwt.claim("authorization", authorization("order", "view")))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data[0].id").value(1001));
  }

  @Test
  void requestTraceIdIsReturnedWhenProvided() throws Exception {
    mockMvc.perform(get("/api/orders")
            .header(RequestTraceFilter.TRACE_ID_HEADER, "test-trace-id")
            .with(jwt().jwt(jwt -> jwt.claim("authorization", authorization("order", "view")))))
        .andExpect(status().isOk())
        .andExpect(header().string(RequestTraceFilter.TRACE_ID_HEADER, "test-trace-id"));
  }

  @Test
  void approveOrderRejectsRptWithoutApproveScope() throws Exception {
    mockMvc.perform(post("/api/orders/approve").with(jwt().jwt(jwt -> jwt.claim("authorization", authorization("order", "view")))))
        .andExpect(status().isForbidden());
  }

  @Test
  void createOrderValidatesRequestBody() throws Exception {
    mockMvc.perform(post("/api/orders")
            .with(jwt().jwt(jwt -> jwt.claim("authorization", authorization("order", "create"))))
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {
                  "customer": "",
                  "amount": 0,
                  "status": "PENDING"
                }
                """))
        .andExpect(status().isBadRequest());
  }

  @Test
  void createOrderAllowsRptWithCreateScope() throws Exception {
    mockMvc.perform(post("/api/orders")
            .with(jwt().jwt(jwt -> jwt.claim("authorization", authorization("order", "create"))))
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {
                  "customer": "New Customer",
                  "amount": 668.8,
                  "status": "PENDING"
                }
                """))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.created").value(true))
        .andExpect(jsonPath("$.order.customer").value("New Customer"));
  }

  private Map<String, Object> authorization(String resource, String scope) {
    return Map.of("permissions", List.of(Map.of("rsname", resource, "scopes", List.of(scope))));
  }
}
