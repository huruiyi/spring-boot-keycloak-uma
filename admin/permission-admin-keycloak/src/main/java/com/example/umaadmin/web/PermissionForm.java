package com.example.umaadmin.web;

import jakarta.validation.constraints.NotBlank;

public class PermissionForm {

  @NotBlank
  private String name;
  @NotBlank
  private String resource;
  @NotBlank
  private String scope;
  @NotBlank
  private String policy;

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  public String getResource() {
    return resource;
  }

  public void setResource(String resource) {
    this.resource = resource;
  }

  public String getScope() {
    return scope;
  }

  public void setScope(String scope) {
    this.scope = scope;
  }

  public String getPolicy() {
    return policy;
  }

  public void setPolicy(String policy) {
    this.policy = policy;
  }
}
