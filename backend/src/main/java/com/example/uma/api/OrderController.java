package com.example.uma.api;

import com.example.uma.security.UmaPermissionService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/orders")
public class OrderController {

  private final UmaPermissionService umaPermissionService;

  public OrderController(UmaPermissionService umaPermissionService) {
    this.umaPermissionService = umaPermissionService;
  }

  @GetMapping
  @PreAuthorize("@umaPermissionService.hasPermission(authentication.principal, 'order', 'view')")
  public Map<String, Object> listOrders() {
    return Map.of(
        "data", List.of(
            Map.of("id", 1001, "customer", "Acme Corp", "amount", new BigDecimal("1299.00"), "status", "PENDING"),
            Map.of("id", 1002, "customer", "Globex", "amount", new BigDecimal("2580.50"), "status", "APPROVED")
        )
    );
  }

  @PostMapping
  @PreAuthorize("@umaPermissionService.hasPermission(authentication.principal, 'order', 'create')")
  public Map<String, Object> createOrder(@RequestBody Map<String, Object> body) {
    return Map.of("created", true, "order", body);
  }

  @PostMapping("/approve")
  @PreAuthorize("@umaPermissionService.hasPermission(authentication.principal, 'order', 'approve')")
  public Map<String, Object> approveOrder() {
    return Map.of("approved", true);
  }

  @GetMapping("/permissions")
  public Map<String, Object> permissions(@AuthenticationPrincipal Jwt jwt) {
    return Map.of("permissions", umaPermissionService.permissions(jwt));
  }
}
