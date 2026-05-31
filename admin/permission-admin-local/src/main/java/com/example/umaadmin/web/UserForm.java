package com.example.umaadmin.web;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

import java.util.ArrayList;
import java.util.List;

public class UserForm {

  @NotBlank
  private String username;
  @Email
  private String email;
  private String password;
  private List<String> realmRoles = new ArrayList<>();

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

  public List<String> getRealmRoles() {
    return realmRoles;
  }

  public void setRealmRoles(List<String> realmRoles) {
    this.realmRoles = realmRoles == null ? new ArrayList<>() : realmRoles;
  }
}
