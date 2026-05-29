package com.example.umaadmin.web;

import jakarta.validation.constraints.NotBlank;

public class PolicyForm {

  @NotBlank
  private String name;
  @NotBlank
  private String type = "role";
  @NotBlank
  private String realmRole;
  private String description;

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  public String getType() {
    return type;
  }

  public void setType(String type) {
    this.type = type;
  }

  public String getRealmRole() {
    return realmRole;
  }

  public void setRealmRole(String realmRole) {
    this.realmRole = realmRole;
  }

  public String getDescription() {
    return description;
  }

  public void setDescription(String description) {
    this.description = description;
  }
}
