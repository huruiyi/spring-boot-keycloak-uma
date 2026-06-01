package com.example.uma.security;

import com.example.uma.api.OrderRecord;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

@Service
public class OrderDataPermissionService {

  private static final BigDecimal APPROVE_AMOUNT_LIMIT = new BigDecimal("2000.00");

  public boolean canView(Jwt jwt, OrderRecord order) {
    UserAccessContext user = UserAccessContext.from(jwt);
    if (isPrivileged(user)) {
      return sameTenantOrTenantNotProvided(user, order);
    }
    if (!sameTenantOrTenantNotProvided(user, order)) {
      return false;
    }
    return user.username().equals(order.owner())
        || (!user.department().isBlank() && user.department().equals(order.department()));
  }

  public boolean canApprove(Jwt jwt, OrderRecord order) {
    UserAccessContext user = UserAccessContext.from(jwt);
    if (isPrivileged(user)) {
      return sameTenantOrTenantNotProvided(user, order);
    }
    return canView(jwt, order) && order.amount().compareTo(APPROVE_AMOUNT_LIMIT) <= 0;
  }

  private boolean isPrivileged(UserAccessContext user) {
    return user.hasRole("admin") || user.hasRole("manager");
  }

  private boolean sameTenantOrTenantNotProvided(UserAccessContext user, OrderRecord order) {
    return user.tenant().isBlank() || user.tenant().equals(order.tenant());
  }
}
