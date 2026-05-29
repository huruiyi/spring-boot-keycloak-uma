package com.example.uma.api;

import com.example.uma.api.dto.SystemConfigResponse;
import com.example.uma.api.dto.UpdateSystemConfigResponse;
import com.example.uma.security.UmaPermissions;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/system/config")
public class SystemController {

  @GetMapping
  @PreAuthorize(UmaPermissions.HAS_SYSTEM_VIEW)
  public SystemConfigResponse viewConfig() {
    return new SystemConfigResponse(false, "local");
  }

  @PostMapping
  @PreAuthorize(UmaPermissions.HAS_SYSTEM_EDIT)
  public UpdateSystemConfigResponse editConfig() {
    return new UpdateSystemConfigResponse(true);
  }
}
