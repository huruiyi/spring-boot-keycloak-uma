package com.example.umaadmin.config;

import org.junit.jupiter.api.Test;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;

import static org.assertj.core.api.Assertions.assertThat;

class SecurityConfigTest {

  @Test
  void userDetailsServiceCreatesAdminAndScopedAdminUsers() {
    UserDetailsService service = new SecurityConfig().userDetailsService(
        "admin",
        "admin",
        "viewer",
        "viewer",
        "user-admin",
        "user-admin",
        "policy-admin",
        "policy-admin",
        "sync-admin",
        "sync-admin",
        PasswordEncoderFactories.createDelegatingPasswordEncoder()
    );

    assertThat(authorities(service.loadUserByUsername("admin")))
        .contains("ROLE_ADMIN", "ROLE_USER_ADMIN", "ROLE_POLICY_ADMIN", "ROLE_SYNC_ADMIN");
    assertThat(authorities(service.loadUserByUsername("viewer"))).containsExactly("ROLE_READ_ONLY");
    assertThat(authorities(service.loadUserByUsername("policy-admin"))).containsExactly("ROLE_POLICY_ADMIN");
  }

  private java.util.List<String> authorities(UserDetails user) {
    return user.getAuthorities().stream().map(Object::toString).toList();
  }
}
