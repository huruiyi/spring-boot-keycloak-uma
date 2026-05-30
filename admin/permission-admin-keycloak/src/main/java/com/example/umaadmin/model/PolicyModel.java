package com.example.umaadmin.model;

public record PolicyModel(String name, String type, String realmRole, String target, String description) {

  public PolicyModel(String name, String type, String realmRole, String description) {
    this(name, type, realmRole, realmRole, description);
  }

  public String subject() {
    return target == null || target.isBlank() ? realmRole : target;
  }
}
