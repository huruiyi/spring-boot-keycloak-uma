package com.example.umaadmin.service;

import com.example.umaadmin.data.PermissionModelRepository;
import com.example.umaadmin.model.PermissionModel;
import com.example.umaadmin.model.PermissionRuleModel;
import com.example.umaadmin.model.PolicyModel;
import com.example.umaadmin.model.RealmRoleModel;
import com.example.umaadmin.model.SystemEndpointModel;
import com.example.umaadmin.model.UmaResourceModel;
import com.example.umaadmin.model.UserModel;
import org.springframework.stereotype.Service;

@Service
public class PermissionAdminService {

  private final PermissionModelRepository repository;

  public PermissionAdminService(PermissionModelRepository repository) {
    this.repository = repository;
  }

  public PermissionModel model() {
    return repository.get();
  }

  public void addRole(RealmRoleModel role) {
    repository.saveRole(role);
  }

  public void deleteRole(String name) {
    repository.deleteRole(name);
  }

  public void addUser(UserModel user) {
    repository.saveUser(user);
  }

  public void deleteUser(String username) {
    repository.deleteUser(username);
  }

  public void addResource(UmaResourceModel resource) {
    PermissionModel model = repository.get();
    model.getResources().removeIf(item -> item.name().equals(resource.name()));
    model.getResources().add(resource);
    repository.save(model);
  }

  public void deleteResource(String name) {
    PermissionModel model = repository.get();
    model.getResources().removeIf(item -> item.name().equals(name));
    repository.save(model);
  }

  public void addPolicy(PolicyModel policy) {
    PermissionModel model = repository.get();
    model.getPolicies().removeIf(item -> item.name().equals(policy.name()));
    model.getPolicies().add(policy);
    repository.save(model);
  }

  public void deletePolicy(String name) {
    PermissionModel model = repository.get();
    model.getPolicies().removeIf(item -> item.name().equals(name));
    repository.save(model);
  }

  public void addPermission(PermissionRuleModel permission) {
    PermissionModel model = repository.get();
    model.getPermissions().removeIf(item -> item.name().equals(permission.name()));
    model.getPermissions().add(permission);
    repository.save(model);
  }

  public void deletePermission(String name) {
    PermissionModel model = repository.get();
    model.getPermissions().removeIf(item -> item.name().equals(name));
    repository.save(model);
  }

  public void addEndpoint(SystemEndpointModel endpoint) {
    PermissionModel model = repository.get();
    model.getEndpoints().removeIf(item -> item.method().equals(endpoint.method()) && item.path().equals(endpoint.path()));
    model.getEndpoints().add(endpoint);
    repository.save(model);
  }

  public void deleteEndpoint(String method, String path) {
    PermissionModel model = repository.get();
    model.getEndpoints().removeIf(item -> item.method().equals(method) && item.path().equals(path));
    repository.save(model);
  }
}
