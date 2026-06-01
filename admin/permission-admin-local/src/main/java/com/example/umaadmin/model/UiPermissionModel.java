package com.example.umaadmin.model;

public record UiPermissionModel(
    String code,
    String name,
    String type,
    String page,
    String permission,
    int sort,
    boolean enabled
) {
}
