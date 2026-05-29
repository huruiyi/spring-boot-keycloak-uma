package com.example.uma.security;

import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class UmaPermissionServiceTest {

  private final UmaPermissionService service = new UmaPermissionService();

  @Test
  void hasPermissionReturnsTrueWhenRptContainsResourceAndScope() {
    Jwt jwt = jwtWithPermissions(List.of(
        Map.of("rsname", "order", "scopes", List.of("view", "create"))
    ));

    assertThat(service.hasPermission(jwt, "order", "view")).isTrue();
    assertThat(service.hasPermission(jwt, "order", "approve")).isFalse();
  }

  @Test
  void permissionsReturnsEmptyListWhenAuthorizationClaimIsMissing() {
    Jwt jwt = Jwt.withTokenValue("token")
        .header("alg", "none")
        .issuedAt(Instant.now())
        .expiresAt(Instant.now().plusSeconds(60))
        .build();

    assertThat(service.permissions(jwt)).isEmpty();
  }

  @Test
  void permissionsSupportsKeycloakResourceNameVariants() {
    Jwt jwt = jwtWithPermissions(List.of(
        Map.of("resource_name", "system", "scopes", List.of("view")),
        Map.of("name", "order", "scopes", List.of("approve"))
    ));

    assertThat(service.permissions(jwt))
        .extracting(UmaPermission::resource)
        .containsExactly("system", "order");
  }

  private Jwt jwtWithPermissions(List<Map<String, Object>> permissions) {
    return Jwt.withTokenValue("token")
        .header("alg", "none")
        .issuedAt(Instant.now())
        .expiresAt(Instant.now().plusSeconds(60))
        .claim("authorization", Map.of("permissions", permissions))
        .build();
  }
}
