package com.example.uma.api.dto;

import com.example.uma.security.UmaPermission;

import java.util.List;

public record PermissionsResponse(List<UmaPermission> permissions) {
}
