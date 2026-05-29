package com.example.umaadmin.web;

import jakarta.validation.constraints.NotBlank;

public class EndpointForm {

  @NotBlank
  private String name;
  @NotBlank
  private String method;
  @NotBlank
  private String path;
  @NotBlank
  private String permission;

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  public String getMethod() {
    return method;
  }

  public void setMethod(String method) {
    this.method = method;
  }

  public String getPath() {
    return path;
  }

  public void setPath(String path) {
    this.path = path;
  }

  public String getPermission() {
    return permission;
  }

  public void setPermission(String permission) {
    this.permission = permission;
  }
}
