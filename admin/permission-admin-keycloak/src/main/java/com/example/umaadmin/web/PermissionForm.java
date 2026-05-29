package com.example.umaadmin.web;

import jakarta.validation.constraints.NotBlank;

import java.util.ArrayList;
import java.util.List;

public class PermissionForm {

  @NotBlank
  private String name;
  @NotBlank
  private String resource;
  @NotBlank
  private String scope;
  private List<String> policies = new ArrayList<>();

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

  public List<String> getPolicies() {
    return policies;
  }

  public void setPolicies(List<String> policies) {
    this.policies = policies == null ? new ArrayList<>() : policies;
  }
}
