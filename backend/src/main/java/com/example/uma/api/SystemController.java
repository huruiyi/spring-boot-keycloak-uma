package com.example.uma.api;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/system/config")
public class SystemController {

  @GetMapping
  @PreAuthorize("@umaPermissionService.hasPermission(authentication.principal, 'system', 'view')")
  public Map<String, Object> viewConfig() {
    return Map.of("maintenanceMode", false, "region", "local");
  }

  @PostMapping
  @PreAuthorize("@umaPermissionService.hasPermission(authentication.principal, 'system', 'edit')")
  public Map<String, Object> editConfig() {
    return Map.of("updated", true);
  }
}
