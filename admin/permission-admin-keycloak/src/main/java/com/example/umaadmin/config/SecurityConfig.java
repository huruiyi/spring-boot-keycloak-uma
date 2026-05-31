package com.example.umaadmin.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;

import java.util.ArrayList;
import java.util.List;

@Configuration
public class SecurityConfig {

  @Bean
  SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
    http
        .authorizeHttpRequests(auth -> auth
            .requestMatchers("/assets/**", "/favicon.ico", "/actuator/health").permitAll()
            .requestMatchers(HttpMethod.POST, "/roles/**", "/users/**").hasAnyRole("ADMIN", "USER_ADMIN")
            .requestMatchers(HttpMethod.POST, "/resources/**", "/policies/**", "/permissions/**", "/endpoints/**").hasAnyRole("ADMIN", "POLICY_ADMIN")
            .requestMatchers(HttpMethod.POST, "/analysis/import-scanned-endpoints", "/model-sync/import").hasAnyRole("ADMIN", "SYNC_ADMIN")
            .requestMatchers(HttpMethod.POST, "/model-sync/preview").authenticated()
            .anyRequest().authenticated()
        )
        .formLogin(login -> login.loginPage("/login").permitAll())
        .logout(logout -> logout.logoutSuccessUrl("/login?logout"));
    return http.build();
  }

  @Bean
  UserDetailsService userDetailsService(
      @Value("${app.security.username}") String username,
      @Value("${app.security.password}") String password,
      @Value("${app.security.readonly-username}") String readonlyUsername,
      @Value("${app.security.readonly-password}") String readonlyPassword,
      @Value("${app.security.user-admin-username}") String userAdminUsername,
      @Value("${app.security.user-admin-password}") String userAdminPassword,
      @Value("${app.security.policy-admin-username}") String policyAdminUsername,
      @Value("${app.security.policy-admin-password}") String policyAdminPassword,
      @Value("${app.security.sync-admin-username}") String syncAdminUsername,
      @Value("${app.security.sync-admin-password}") String syncAdminPassword,
      PasswordEncoder passwordEncoder
  ) {
    List<UserDetails> users = new ArrayList<>();
    users.add(user(username, password, passwordEncoder, "ADMIN", "USER_ADMIN", "POLICY_ADMIN", "SYNC_ADMIN"));
    users.add(user(readonlyUsername, readonlyPassword, passwordEncoder, "READ_ONLY"));
    users.add(user(userAdminUsername, userAdminPassword, passwordEncoder, "USER_ADMIN"));
    users.add(user(policyAdminUsername, policyAdminPassword, passwordEncoder, "POLICY_ADMIN"));
    users.add(user(syncAdminUsername, syncAdminPassword, passwordEncoder, "SYNC_ADMIN"));
    return new InMemoryUserDetailsManager(users);
  }

  @Bean
  PasswordEncoder passwordEncoder() {
    return PasswordEncoderFactories.createDelegatingPasswordEncoder();
  }

  private UserDetails user(String username, String password, PasswordEncoder passwordEncoder, String... roles) {
    return User.withUsername(username)
        .password(passwordEncoder.encode(password))
        .roles(roles)
        .build();
  }
}
