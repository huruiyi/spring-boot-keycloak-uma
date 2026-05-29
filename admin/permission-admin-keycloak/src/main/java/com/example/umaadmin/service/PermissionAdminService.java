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

import java.util.ArrayList;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class PermissionAdminService {

  private static final Map<String, String> DEFAULT_METHODS = Map.of(
      "view", "GET",
      "create", "POST",
      "edit", "PUT",
      "delete", "DELETE",
      "approve", "POST",
      "export", "GET",
      "reset_pwd", "POST"
  );

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
    saveResource(resource.name(), resource);
  }

  public void saveResource(String originalName, UmaResourceModel resource) {
    PermissionModel model = repository.get();
    String normalizedOriginalName = originalName == null || originalName.isBlank() ? resource.name() : originalName;
    model.getResources().removeIf(item -> item.name().equals(normalizedOriginalName) || item.name().equals(resource.name()));
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

  public int generateDefaultEndpoints() {
    PermissionModel model = repository.get();
    model.setEndpoints(new ArrayList<>(model.getEndpoints()));
    Map<String, UmaResourceModel> resourcesByName = model.getResources().stream()
        .collect(Collectors.toMap(UmaResourceModel::name, Function.identity(), (left, right) -> left));

    int generated = 0;
    for (DefaultEndpointCandidate candidate : defaultEndpointCandidates(model, resourcesByName).values()) {
      if (hasEndpointForPermission(model, candidate.permission())) {
        continue;
      }
      if (hasEndpointForMethodAndPath(model, candidate.method(), candidate.path())) {
        continue;
      }

      model.getEndpoints().add(new SystemEndpointModel(
          candidate.name(),
          candidate.method(),
          candidate.path(),
          candidate.permission()
      ));
      generated++;
    }

    if (generated > 0) {
      repository.save(model);
    }
    return generated;
  }

  private Map<String, DefaultEndpointCandidate> defaultEndpointCandidates(PermissionModel model, Map<String, UmaResourceModel> resourcesByName) {
    Map<String, DefaultEndpointCandidate> candidates = new java.util.LinkedHashMap<>();
    for (PermissionRuleModel permission : model.getPermissions()) {
      if (permission.resource().isBlank() || permission.scope().isBlank()) {
        continue;
      }
      UmaResourceModel resource = resourcesByName.get(permission.resource());
      if (resource == null || resource.uris().isEmpty()) {
        continue;
      }
      putCandidate(candidates, permission.resource(), permission.scope(), resource.uris().getFirst());
    }
    for (UmaResourceModel resource : model.getResources()) {
      if (resource.uris().isEmpty()) {
        continue;
      }
      for (String scope : resource.scopes()) {
        putCandidate(candidates, resource.name(), scope, resource.uris().getFirst());
      }
    }
    return candidates;
  }

  private void putCandidate(Map<String, DefaultEndpointCandidate> candidates, String resource, String scope, String uri) {
    String permission = resource + "#" + scope;
    candidates.putIfAbsent(permission, new DefaultEndpointCandidate(
        resource + " " + scope,
        defaultMethod(scope),
        defaultPath(uri, scope),
        permission
    ));
  }

  private boolean hasEndpointForPermission(PermissionModel model, String permission) {
    return model.getEndpoints().stream()
        .map(SystemEndpointModel::permission)
        .filter(Objects::nonNull)
        .anyMatch(permission::equals);
  }

  private boolean hasEndpointForMethodAndPath(PermissionModel model, String method, String path) {
    return model.getEndpoints().stream()
        .anyMatch(endpoint -> endpoint.method().equals(method) && endpoint.path().equals(path));
  }

  private String defaultMethod(String scope) {
    return DEFAULT_METHODS.getOrDefault(scope.toLowerCase(Locale.ROOT), "GET");
  }

  private String defaultPath(String uri, String scope) {
    String basePath = uri.endsWith("/*") ? uri.substring(0, uri.length() - 2) : uri;
    if (isResourceLevelScope(scope)) {
      return basePath;
    }
    return basePath + "/" + scope.toLowerCase(Locale.ROOT).replace('_', '-');
  }

  private boolean isResourceLevelScope(String scope) {
    String normalizedScope = scope.toLowerCase(Locale.ROOT);
    return "view".equals(normalizedScope)
        || "create".equals(normalizedScope)
        || "edit".equals(normalizedScope)
        || "delete".equals(normalizedScope);
  }

  private record DefaultEndpointCandidate(String name, String method, String path, String permission) {
  }
}
