package com.example.uma.security;

import org.springframework.security.oauth2.jwt.Jwt;

import java.util.Collection;
import java.util.List;
import java.util.Map;

public record UserAccessContext(String username, List<String> roles, String department, String tenant) {

  public static UserAccessContext from(Jwt jwt) {
    return new UserAccessContext(
        claim(jwt, "preferred_username", jwt.getSubject()),
        roles(jwt),
        claim(jwt, "department", ""),
        claim(jwt, "tenant", claim(jwt, "tenant_id", ""))
    );
  }

  public boolean hasRole(String role) {
    return roles.contains(role);
  }

  private static String claim(Jwt jwt, String name, String fallback) {
    Object value = jwt.getClaims().get(name);
    return value == null ? fallback : String.valueOf(value);
  }

  @SuppressWarnings("unchecked")
  private static List<String> roles(Jwt jwt) {
    Object realmAccess = jwt.getClaims().get("realm_access");
    if (!(realmAccess instanceof Map<?, ?> access)) {
      return List.of();
    }
    Object roles = access.get("roles");
    if (!(roles instanceof Collection<?> values)) {
      return List.of();
    }
    return values.stream().map(String::valueOf).toList();
  }
}
