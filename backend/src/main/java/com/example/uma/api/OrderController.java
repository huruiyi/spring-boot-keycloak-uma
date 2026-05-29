package com.example.uma.api;

import com.example.uma.api.dto.ApproveOrderResponse;
import com.example.uma.api.dto.CreateOrderRequest;
import com.example.uma.api.dto.CreateOrderResponse;
import com.example.uma.api.dto.OrderDto;
import com.example.uma.api.dto.OrderListResponse;
import com.example.uma.api.dto.PermissionsResponse;
import com.example.uma.security.UmaPermissionService;
import com.example.uma.security.UmaPermissions;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;

@RestController
@RequestMapping("/api/orders")
public class OrderController {

  private final UmaPermissionService umaPermissionService;

  public OrderController(UmaPermissionService umaPermissionService) {
    this.umaPermissionService = umaPermissionService;
  }

  @GetMapping
  @PreAuthorize(UmaPermissions.HAS_ORDER_VIEW)
  public OrderListResponse listOrders() {
    return new OrderListResponse(
        List.of(
            new OrderDto(1001, "Acme Corp", new BigDecimal("1299.00"), "PENDING"),
            new OrderDto(1002, "Globex", new BigDecimal("2580.50"), "APPROVED")
        )
    );
  }

  @PostMapping
  @PreAuthorize(UmaPermissions.HAS_ORDER_CREATE)
  public CreateOrderResponse createOrder(@Valid @RequestBody CreateOrderRequest body) {
    return new CreateOrderResponse(true, body);
  }

  @PostMapping("/approve")
  @PreAuthorize(UmaPermissions.HAS_ORDER_APPROVE)
  public ApproveOrderResponse approveOrder() {
    return new ApproveOrderResponse(true);
  }

  @GetMapping("/permissions")
  public PermissionsResponse permissions(@AuthenticationPrincipal Jwt jwt) {
    return new PermissionsResponse(umaPermissionService.permissions(jwt));
  }
}
