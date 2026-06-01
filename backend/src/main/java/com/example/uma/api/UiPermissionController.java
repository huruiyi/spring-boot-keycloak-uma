package com.example.uma.api;

import com.example.uma.api.dto.UiPermissionListResponse;
import com.example.uma.security.UiPermissionCatalog;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/ui-permissions")
public class UiPermissionController {

  private final UiPermissionCatalog catalog;

  public UiPermissionController(UiPermissionCatalog catalog) {
    this.catalog = catalog;
  }

  @GetMapping
  public UiPermissionListResponse list() {
    return new UiPermissionListResponse(catalog.listEnabled());
  }
}
