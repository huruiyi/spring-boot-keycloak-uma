package com.example.uma.api;

import com.example.uma.api.dto.ApproveOrderResponse;
import com.example.uma.api.dto.CreateOrderRequest;
import com.example.uma.api.dto.CreateOrderResponse;
import com.example.uma.api.dto.OrderDto;
import com.example.uma.api.dto.OrderListResponse;
import com.example.uma.api.dto.PermissionsResponse;
import com.example.uma.security.OrderDataPermissionService;
import com.example.uma.security.UmaPermissionService;
import com.example.uma.security.UmaPermissions;
import jakarta.validation.Valid;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;

@RestController
@RequestMapping("/api/orders")
public class OrderController {

  private static final List<OrderRecord> ORDERS = List.of(
      new OrderRecord(1001, "Acme Corp", new BigDecimal("1299.00"), "PENDING", "user", "sales", "default"),
      new OrderRecord(1002, "Globex", new BigDecimal("2580.50"), "APPROVED", "manager", "finance", "default")
  );

  private final UmaPermissionService umaPermissionService;
  private final OrderDataPermissionService orderDataPermissionService;

  public OrderController(UmaPermissionService umaPermissionService, OrderDataPermissionService orderDataPermissionService) {
    this.umaPermissionService = umaPermissionService;
    this.orderDataPermissionService = orderDataPermissionService;
  }

  @GetMapping
  @PreAuthorize(UmaPermissions.HAS_ORDER_VIEW)
  public OrderListResponse listOrders(@AuthenticationPrincipal Jwt jwt) {
    List<OrderDto> visibleOrders = ORDERS.stream()
        .filter(order -> orderDataPermissionService.canView(jwt, order))
        .map(OrderRecord::toDto)
        .toList();
    return new OrderListResponse(visibleOrders);
  }

  @PostMapping
  @PreAuthorize(UmaPermissions.HAS_ORDER_CREATE)
  public CreateOrderResponse createOrder(@Valid @RequestBody CreateOrderRequest body) {
    return new CreateOrderResponse(true, body);
  }

  @PostMapping("/approve")
  @PreAuthorize(UmaPermissions.HAS_ORDER_APPROVE)
  public ApproveOrderResponse approveOrder(@AuthenticationPrincipal Jwt jwt) {
    OrderRecord order = ORDERS.getFirst();
    if (!orderDataPermissionService.canApprove(jwt, order)) {
      throw new AccessDeniedException("当前用户不满足订单数据审批条件");
    }
    return new ApproveOrderResponse(true);
  }

  @GetMapping("/permissions")
  public PermissionsResponse permissions(@AuthenticationPrincipal Jwt jwt) {
    return new PermissionsResponse(umaPermissionService.permissions(jwt));
  }
}
