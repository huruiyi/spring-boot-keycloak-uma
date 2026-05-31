package com.example.umaadmin.model;

import java.util.ArrayList;
import java.util.List;

public class PermissionModel {

  private List<RealmRoleModel> realmRoles = new ArrayList<>();
  private List<UserModel> users = new ArrayList<>();
  private List<ClientModel> clients = new ArrayList<>();
  private List<UmaResourceModel> resources = new ArrayList<>();
  private List<PolicyModel> policies = new ArrayList<>();
  private List<PermissionRuleModel> permissions = new ArrayList<>();
  private List<SystemEndpointModel> endpoints = new ArrayList<>();

  public List<RealmRoleModel> getRealmRoles() {
    return realmRoles;
  }

  public void setRealmRoles(List<RealmRoleModel> realmRoles) {
    this.realmRoles = mutableList(realmRoles);
  }

  public List<UserModel> getUsers() {
    return users;
  }

  public void setUsers(List<UserModel> users) {
    this.users = mutableList(users);
  }

  public List<ClientModel> getClients() {
    return clients;
  }

  public void setClients(List<ClientModel> clients) {
    this.clients = mutableList(clients);
  }

  public List<UmaResourceModel> getResources() {
    return resources;
  }

  public void setResources(List<UmaResourceModel> resources) {
    this.resources = mutableList(resources);
  }

  public List<PolicyModel> getPolicies() {
    return policies;
  }

  public void setPolicies(List<PolicyModel> policies) {
    this.policies = mutableList(policies);
  }

  public List<PermissionRuleModel> getPermissions() {
    return permissions;
  }

  public void setPermissions(List<PermissionRuleModel> permissions) {
    this.permissions = mutableList(permissions);
  }

  public List<SystemEndpointModel> getEndpoints() {
    return endpoints;
  }

  public void setEndpoints(List<SystemEndpointModel> endpoints) {
    this.endpoints = mutableList(endpoints);
  }

  private static <T> List<T> mutableList(List<T> values) {
    return values == null ? new ArrayList<>() : new ArrayList<>(values);
  }
}
