package com.example.uma.api.dto;

public record UiPermissionDto(
    String code,
    String name,
    String type,
    String page,
    String permission,
    int sort,
    boolean enabled
) {
}
