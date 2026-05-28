package com.example.uma.security;

import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.List;
import java.util.Map;

@Service
public class UmaPermissionService {

  public boolean hasPermission(Jwt jwt, String resource, String scope) {
    return permissions(jwt).stream()
        .anyMatch(permission ->
            resource.equals(permission.resource()) && permission.scopes().contains(scope)
        );
  }

  @SuppressWarnings("unchecked")
  public List<UmaPermission> permissions(Jwt jwt) {
    Object authorizationValue = jwt.getClaims().get("authorization");
    if (!(authorizationValue instanceof Map<?, ?> authorization)) {
      return List.of();
    }

    Object permissionsValue = authorization.get("permissions");
    if (!(permissionsValue instanceof Collection<?> permissions)) {
      return List.of();
    }

    return permissions.stream()
        .filter(Map.class::isInstance)
        .map(value -> UmaPermission.from((Map<String, Object>) value))
        .toList();
  }
}
