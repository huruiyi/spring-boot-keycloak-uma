package com.example.umaadmin.web;

import jakarta.validation.constraints.NotBlank;

public class ResourceForm {

  private String originalName;
  @NotBlank
  private String name;
  private String uris;
  private String scopes;

  public String getOriginalName() {
    return originalName;
  }

  public void setOriginalName(String originalName) {
    this.originalName = originalName;
  }

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  public String getUris() {
    return uris;
  }

  public void setUris(String uris) {
    this.uris = uris;
  }

  public String getScopes() {
    return scopes;
  }

  public void setScopes(String scopes) {
    this.scopes = scopes;
  }
}
