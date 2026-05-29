package com.example.umaadmin.web;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public class UserForm {

  @NotBlank
  private String username;
  @Email
  private String email;
  @NotBlank
  private String password;
  private String realmRoles;

  public String getUsername() {
    return username;
  }

  public void setUsername(String username) {
    this.username = username;
  }

  public String getEmail() {
    return email;
  }

  public void setEmail(String email) {
    this.email = email;
  }

  public String getPassword() {
    return password;
  }

  public void setPassword(String password) {
    this.password = password;
  }

  public String getRealmRoles() {
    return realmRoles;
  }

  public void setRealmRoles(String realmRoles) {
    this.realmRoles = realmRoles;
  }
}
