package com.example.uma.security;

public final class UmaPermissions {

  public static final String ORDER_RESOURCE = "order";
  public static final String ORDER_VIEW_SCOPE = "view";
  public static final String ORDER_CREATE_SCOPE = "create";
  public static final String ORDER_APPROVE_SCOPE = "approve";

  public static final String SYSTEM_RESOURCE = "system";
  public static final String SYSTEM_VIEW_SCOPE = "view";
  public static final String SYSTEM_EDIT_SCOPE = "edit";

  public static final String HAS_ORDER_VIEW =
      "@umaPermissionService.hasPermission(authentication.principal, T(com.example.uma.security.UmaPermissions).ORDER_RESOURCE, T(com.example.uma.security.UmaPermissions).ORDER_VIEW_SCOPE)";
  public static final String HAS_ORDER_CREATE =
      "@umaPermissionService.hasPermission(authentication.principal, T(com.example.uma.security.UmaPermissions).ORDER_RESOURCE, T(com.example.uma.security.UmaPermissions).ORDER_CREATE_SCOPE)";
  public static final String HAS_ORDER_APPROVE =
      "@umaPermissionService.hasPermission(authentication.principal, T(com.example.uma.security.UmaPermissions).ORDER_RESOURCE, T(com.example.uma.security.UmaPermissions).ORDER_APPROVE_SCOPE)";
  public static final String HAS_SYSTEM_VIEW =
      "@umaPermissionService.hasPermission(authentication.principal, T(com.example.uma.security.UmaPermissions).SYSTEM_RESOURCE, T(com.example.uma.security.UmaPermissions).SYSTEM_VIEW_SCOPE)";
  public static final String HAS_SYSTEM_EDIT =
      "@umaPermissionService.hasPermission(authentication.principal, T(com.example.uma.security.UmaPermissions).SYSTEM_RESOURCE, T(com.example.uma.security.UmaPermissions).SYSTEM_EDIT_SCOPE)";

  private UmaPermissions() {
  }
}
