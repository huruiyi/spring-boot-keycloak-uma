package com.example.umaadmin.service;

import com.example.umaadmin.data.PermissionModelRepository;
import com.example.umaadmin.model.PermissionModel;
import com.example.umaadmin.model.PermissionRuleModel;
import com.example.umaadmin.model.PolicyModel;
import com.example.umaadmin.model.RealmRoleModel;
import com.example.umaadmin.model.SystemEndpointModel;
import com.example.umaadmin.model.UmaResourceModel;
import com.example.umaadmin.model.UserModel;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.io.IOException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
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
  private final ObjectMapper objectMapper;

  public PermissionAdminService(PermissionModelRepository repository) {
    this.repository = repository;
    this.objectMapper = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);
  }

  public PermissionModel model() {
    return repository.get();
  }

  public String modelJson() {
    try {
      return objectMapper.writeValueAsString(repository.get());
    } catch (JsonProcessingException e) {
      throw new IllegalStateException("Failed to serialize permission model", e);
    }
  }

  public PermissionModel parseModel(String json) {
    try {
      return objectMapper.readValue(json, PermissionModel.class);
    } catch (JsonProcessingException e) {
      throw new IllegalArgumentException("Invalid permission model JSON", e);
    }
  }

  public void replaceModel(PermissionModel model) {
    repository.save(model);
  }

  public List<ModelChange> diff(PermissionModel target) {
    PermissionModel current = repository.get();
    List<ModelChange> changes = new ArrayList<>();
    compare(changes, "Realm Role", current.getRealmRoles(), target.getRealmRoles(), RealmRoleModel::name);
    compare(changes, "用户", current.getUsers(), target.getUsers(), UserModel::username);
    compare(changes, "UMA Resource", current.getResources(), target.getResources(), UmaResourceModel::name);
    compare(changes, "Policy", current.getPolicies(), target.getPolicies(), PolicyModel::name);
    compare(changes, "Permission", current.getPermissions(), target.getPermissions(), PermissionRuleModel::name);
    compare(changes, "Endpoint", current.getEndpoints(), target.getEndpoints(), endpoint -> endpoint.method() + " " + endpoint.path());
    return changes.stream()
        .sorted(Comparator.comparing(ModelChange::section).thenComparing(ModelChange::name).thenComparing(ModelChange::action))
        .toList();
  }

  private <T> void compare(List<ModelChange> changes, String section, List<T> current, List<T> target, Function<T, String> keyFunction) {
    Map<String, T> currentByKey = byKey(current, keyFunction);
    Map<String, T> targetByKey = byKey(target, keyFunction);
    for (Map.Entry<String, T> entry : targetByKey.entrySet()) {
      T previous = currentByKey.get(entry.getKey());
      if (previous == null) {
        changes.add(new ModelChange(section, entry.getKey(), "新增"));
      } else if (!objectMapper.valueToTree(previous).equals(objectMapper.valueToTree(entry.getValue()))) {
        changes.add(new ModelChange(section, entry.getKey(), "修改"));
      }
    }
    for (String key : currentByKey.keySet()) {
      if (!targetByKey.containsKey(key)) {
        changes.add(new ModelChange(section, key, "删除"));
      }
    }
  }

  private <T> Map<String, T> byKey(List<T> values, Function<T, String> keyFunction) {
    return values.stream()
        .collect(Collectors.toMap(keyFunction, Function.identity(), (left, right) -> left, LinkedHashMap::new));
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

  public List<EndpointScanCandidate> scanBackendEndpoints() {
    PermissionModel model = repository.get();
    Set<String> existing = model.getEndpoints().stream()
        .map(endpoint -> endpoint.method() + " " + endpoint.path())
        .collect(Collectors.toCollection(LinkedHashSet::new));
    Map<String, String> permissions = backendPermissionExpressions();
    List<EndpointScanCandidate> candidates = new ArrayList<>();
    Path apiDir = Path.of("..", "..", "backend", "src", "main", "java", "com", "example", "uma", "api").normalize();
    if (!Files.isDirectory(apiDir)) {
      return List.of();
    }
    try (var stream = Files.walk(apiDir)) {
      for (Path file : stream.filter(path -> path.toString().endsWith("Controller.java")).toList()) {
        candidates.addAll(scanController(file, permissions, existing));
      }
    } catch (IOException e) {
      throw new IllegalStateException("Failed to scan backend endpoints", e);
    }
    return candidates;
  }

  public int importScannedEndpoints() {
    PermissionModel model = repository.get();
    int imported = 0;
    for (EndpointScanCandidate candidate : scanBackendEndpoints()) {
      if (candidate.exists() || candidate.permission().isBlank()) {
        continue;
      }
      model.getEndpoints().add(new SystemEndpointModel(candidate.name(), candidate.method(), candidate.path(), candidate.permission()));
      imported++;
    }
    if (imported > 0) {
      repository.save(model);
    }
    return imported;
  }

  public List<UsageIssue> usageIssues() {
    PermissionModel model = repository.get();
    Set<String> permissions = model.getPermissions().stream()
        .map(permission -> permission.resource() + "#" + permission.scope())
        .collect(Collectors.toCollection(LinkedHashSet::new));
    Set<String> endpointPermissions = model.getEndpoints().stream()
        .map(SystemEndpointModel::permission)
        .filter(permission -> permission != null && !permission.isBlank())
        .collect(Collectors.toCollection(LinkedHashSet::new));
    List<UsageIssue> issues = new ArrayList<>();
    for (String permission : permissions) {
      if (!endpointPermissions.contains(permission)) {
        issues.add(new UsageIssue("未绑定接口", permission, "Permission 没有被系统接口权限引用"));
      }
    }
    for (SystemEndpointModel endpoint : model.getEndpoints()) {
      if (!permissions.contains(endpoint.permission())) {
        issues.add(new UsageIssue("无效接口权限", endpoint.method() + " " + endpoint.path(), endpoint.permission()));
      }
    }
    Set<String> scanned = scanBackendEndpoints().stream()
        .map(candidate -> candidate.method() + " " + candidate.path())
        .collect(Collectors.toSet());
    for (SystemEndpointModel endpoint : model.getEndpoints()) {
      if (!scanned.contains(endpoint.method() + " " + endpoint.path())) {
        issues.add(new UsageIssue("未在后端扫描到", endpoint.method() + " " + endpoint.path(), endpoint.permission()));
      }
    }
    return issues;
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

  private List<EndpointScanCandidate> scanController(Path file, Map<String, String> permissions, Set<String> existing) {
    try {
      String source = Files.readString(file, StandardCharsets.UTF_8);
      String basePath = firstMatch(source, "@RequestMapping\\(\"([^\"]*)\"\\)", "");
      Pattern methodPattern = Pattern.compile("@(Get|Post|Put|Delete|Patch)Mapping(?:\\(\"([^\"]*)\"\\))?\\s*(?:@PreAuthorize\\(UmaPermissions\\.([A-Z0-9_]+)\\)\\s*)?public\\s+[^\\n]+?\\s+(\\w+)\\(");
      Matcher matcher = methodPattern.matcher(source);
      List<EndpointScanCandidate> candidates = new ArrayList<>();
      while (matcher.find()) {
        String method = methodName(matcher.group(1));
        String path = normalizePath(basePath, matcher.group(2));
        String permission = permissionFromConstant(matcher.group(3), permissions);
        candidates.add(new EndpointScanCandidate(matcher.group(4), method, path, permission, existing.contains(method + " " + path)));
      }
      return candidates;
    } catch (IOException e) {
      throw new IllegalStateException("Failed to scan controller: " + file, e);
    }
  }

  private Map<String, String> backendPermissionExpressions() {
    Path file = Path.of("..", "..", "backend", "src", "main", "java", "com", "example", "uma", "security", "UmaPermissions.java").normalize();
    if (!Files.exists(file)) {
      return Map.of();
    }
    try {
      String source = Files.readString(file, StandardCharsets.UTF_8);
      Map<String, String> constants = new LinkedHashMap<>();
      Matcher constantMatcher = Pattern.compile("public static final String ([A-Z0-9_]+) = \"([^\"]+)\";").matcher(source);
      while (constantMatcher.find()) {
        constants.put(constantMatcher.group(1), constantMatcher.group(2));
      }
      Map<String, String> result = new LinkedHashMap<>();
      Matcher expressionMatcher = Pattern.compile("public static final String (HAS_[A-Z0-9_]+) =[\\s\\S]*?UmaPermissions\\)\\.([A-Z0-9_]+), T\\(com\\.example\\.uma\\.security\\.UmaPermissions\\)\\.([A-Z0-9_]+)\\)").matcher(source);
      while (expressionMatcher.find()) {
        String resource = constants.getOrDefault(expressionMatcher.group(2), "");
        String scope = constants.getOrDefault(expressionMatcher.group(3), "");
        if (!resource.isBlank() && !scope.isBlank()) {
          result.put(expressionMatcher.group(1), resource + "#" + scope);
        }
      }
      return result;
    } catch (IOException e) {
      throw new IllegalStateException("Failed to scan backend permissions", e);
    }
  }

  private String permissionFromConstant(String constant, Map<String, String> permissions) {
    if (constant == null || constant.isBlank()) {
      return "";
    }
    String mapped = permissions.get(constant);
    if (mapped != null) {
      return mapped;
    }
    if (constant.startsWith("HAS_")) {
      String[] parts = constant.substring(4).toLowerCase(Locale.ROOT).split("_", 2);
      if (parts.length == 2) {
        return parts[0] + "#" + parts[1];
      }
    }
    return "";
  }

  private String firstMatch(String source, String regex, String fallback) {
    Matcher matcher = Pattern.compile(regex).matcher(source);
    return matcher.find() ? matcher.group(1) : fallback;
  }

  private String methodName(String mappingPrefix) {
    return switch (mappingPrefix) {
      case "Post" -> "POST";
      case "Put" -> "PUT";
      case "Delete" -> "DELETE";
      case "Patch" -> "PATCH";
      default -> "GET";
    };
  }

  private String normalizePath(String basePath, String childPath) {
    String normalizedBase = basePath == null ? "" : basePath;
    String normalizedChild = childPath == null ? "" : childPath;
    String path = (normalizedBase + "/" + normalizedChild).replaceAll("/{2,}", "/");
    if (path.length() > 1 && path.endsWith("/")) {
      path = path.substring(0, path.length() - 1);
    }
    return path.isBlank() ? "/" : path;
  }

  private record DefaultEndpointCandidate(String name, String method, String path, String permission) {
  }
}
