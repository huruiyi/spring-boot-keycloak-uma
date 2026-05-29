package com.example.umaadmin.data;

import com.example.umaadmin.model.ClientModel;
import com.example.umaadmin.model.PermissionModel;
import com.example.umaadmin.model.PermissionRuleModel;
import com.example.umaadmin.model.PolicyModel;
import com.example.umaadmin.model.RealmRoleModel;
import com.example.umaadmin.model.SystemEndpointModel;
import com.example.umaadmin.model.UmaResourceModel;
import com.example.umaadmin.model.UserModel;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Repository;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Repository
public class KeycloakPermissionModelRepository implements PermissionModelRepository {

  private static final TypeReference<List<Map<String, Object>>> LIST_OF_MAPS = new TypeReference<>() {
  };

  private final ObjectMapper objectMapper;
  private final HttpClient httpClient;
  private final String keycloakUrl;
  private final String realm;
  private final String adminUser;
  private final String adminPassword;
  private final String resourceServerClientId;
  private final List<String> managedClients;
  private final String endpointAttribute;
  private PermissionModel lastModel;

  public KeycloakPermissionModelRepository(
      @Value("${app.keycloak.url}") String keycloakUrl,
      @Value("${app.keycloak.realm}") String realm,
      @Value("${app.keycloak.admin-user}") String adminUser,
      @Value("${app.keycloak.admin-password}") String adminPassword,
      @Value("${app.keycloak.resource-server-client-id}") String resourceServerClientId,
      @Value("${app.keycloak.managed-clients}") String managedClients,
      @Value("${app.keycloak.endpoint-attribute}") String endpointAttribute
  ) {
    this.objectMapper = new ObjectMapper();
    this.httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    this.keycloakUrl = trimRight(keycloakUrl, "/");
    this.realm = realm;
    this.adminUser = adminUser;
    this.adminPassword = adminPassword;
    this.resourceServerClientId = resourceServerClientId;
    this.managedClients = splitCsv(managedClients);
    this.endpointAttribute = endpointAttribute;
  }

  @Override
  public synchronized PermissionModel get() {
    PermissionModel model = loadFromKeycloak();
    lastModel = copy(model);
    return copy(model);
  }

  @Override
  public synchronized void save(PermissionModel model) {
    PermissionModel previous = lastModel == null ? loadFromKeycloak() : copy(lastModel);
    String token = adminToken();
    syncRealmRoles(previous, model, token);
    syncClients(model, token);
    syncUsers(previous, model, token);

    String clientUuid = clientUuid(resourceServerClientId, token);
    String authzBase = adminBase() + "/clients/" + clientUuid + "/authz/resource-server";
    syncScopes(model, authzBase, token);
    syncResources(previous, model, authzBase, token);
    syncPolicies(previous, model, authzBase, token);
    syncPermissions(previous, model, authzBase, token);
    syncEndpoints(model, clientUuid, token);
    lastModel = loadFromKeycloak();
  }

  @Override
  public synchronized void saveRole(RealmRoleModel role) {
    String token = adminToken();
    upsertRealmRole(role, token);
    lastModel = loadFromKeycloak();
  }

  @Override
  public synchronized void deleteRole(String name) {
    String token = adminToken();
    delete(adminBase() + "/roles/" + encodePath(name), token);
    lastModel = loadFromKeycloak();
  }

  @Override
  public synchronized void saveUser(UserModel user) {
    String token = adminToken();
    upsertUser(user, token);
    lastModel = loadFromKeycloak();
  }

  @Override
  public synchronized void deleteUser(String username) {
    String token = adminToken();
    String userId = userId(username, token);
    if (userId != null) {
      delete(adminBase() + "/users/" + userId, token);
    }
    lastModel = loadFromKeycloak();
  }

  private PermissionModel loadFromKeycloak() {
    String token = adminToken();
    PermissionModel model = new PermissionModel();
    model.setRealmRoles(new ArrayList<>(loadRealmRoles(token)));
    model.setClients(new ArrayList<>(loadClients(token)));
    model.setUsers(new ArrayList<>(loadUsers(token)));

    String clientUuid = clientUuid(resourceServerClientId, token);
    String authzBase = adminBase() + "/clients/" + clientUuid + "/authz/resource-server";
    model.setResources(new ArrayList<>(loadResources(authzBase, token)));
    model.setPolicies(new ArrayList<>(loadPolicies(authzBase, token)));
    model.setPermissions(new ArrayList<>(loadPermissions(authzBase, token)));
    model.setEndpoints(new ArrayList<>(loadEndpoints(clientUuid, token)));
    return model;
  }

  private List<RealmRoleModel> loadRealmRoles(String token) {
    return getList(adminBase() + "/roles", token).stream()
        .filter(role -> !isBuiltInRealmRole(text(role, "name")))
        .map(role -> new RealmRoleModel(text(role, "name"), text(role, "description")))
        .toList();
  }

  private List<ClientModel> loadClients(String token) {
    List<ClientModel> clients = new ArrayList<>();
    for (String clientId : managedClients) {
      Map<String, Object> client = clientByClientId(clientId, token);
      String uuid = text(client, "id");
      List<String> defaultScopes = getList(adminBase() + "/clients/" + uuid + "/default-client-scopes", token)
          .stream()
          .map(scope -> text(scope, "name"))
          .filter(name -> !name.isBlank())
          .toList();
      List<String> roleMappings = getList(adminBase() + "/clients/" + uuid + "/scope-mappings/realm", token)
          .stream()
          .map(role -> text(role, "name"))
          .filter(name -> !name.isBlank())
          .toList();
      clients.add(new ClientModel(
          clientId,
          Boolean.TRUE.equals(client.get("publicClient")) ? "public" : clientType(client),
          text(client, "name"),
          defaultScopes,
          roleMappings
      ));
    }
    return clients;
  }

  private List<UserModel> loadUsers(String token) {
    return getList(adminBase() + "/users", token).stream()
        .filter(user -> !text(user, "username").startsWith("service-account-"))
        .map(user -> {
          String id = text(user, "id");
          List<String> roles = getList(adminBase() + "/users/" + id + "/role-mappings/realm", token)
              .stream()
              .map(role -> text(role, "name"))
              .filter(name -> !isBuiltInRealmRole(name))
              .toList();
          return new UserModel(text(user, "username"), text(user, "email"), "", roles);
        })
        .toList();
  }

  private List<UmaResourceModel> loadResources(String authzBase, String token) {
    return getList(authzBase + "/resource", token).stream()
        .map(resource -> new UmaResourceModel(
            text(resource, "name"),
            stringList(resource.get("uris")),
            objectNameList(resource.get("scopes"))
        ))
        .toList();
  }

  private List<PolicyModel> loadPolicies(String authzBase, String token) {
    Map<String, String> roleIdToName = realmRoleIdToName(token);
    return getList(authzBase + "/policy", token).stream()
        .filter(policy -> "role".equals(text(policy, "type")))
        .map(policy -> new PolicyModel(
            text(policy, "name"),
            "role",
            roleFromPolicy(policy, roleIdToName),
            text(policy, "description")
        ))
        .toList();
  }

  private List<PermissionRuleModel> loadPermissions(String authzBase, String token) {
    Map<String, String> resourceIdToName = idToName(getList(authzBase + "/resource", token));
    Map<String, String> scopeIdToName = idToName(getList(authzBase + "/scope", token));
    Map<String, String> policyIdToName = idToName(getList(authzBase + "/policy", token));
    return getList(authzBase + "/permission").stream()
        .filter(permission -> "scope".equals(text(permission, "type")))
        .map(permission -> {
          String permissionId = keycloakId(permission);
          Map<String, Object> detail = getMap(authzBase + "/policy/" + permissionId, token);
          Map<String, String> config = config(detail);
          List<String> resources = firstNonEmpty(
              configuredValues(detail, config, "resources"),
              objectNameList(getList(authzBase + "/policy/" + permissionId + "/resources", token))
          );
          List<String> scopes = firstNonEmpty(
              configuredValues(detail, config, "scopes"),
              objectNameList(getList(authzBase + "/policy/" + permissionId + "/scopes", token))
          );
          List<String> policies = firstNonEmpty(
              configuredValues(detail, config, "policies"),
              configuredValues(detail, config, "applyPolicies")
          );
          policies = firstNonEmpty(
              policies,
              objectNameList(getList(authzBase + "/permission/" + permissionId + "/associatedPolicies", token))
          );
          return new PermissionRuleModel(
              text(detail, "name"),
              resolveFirst(resources, resourceIdToName),
              resolveFirst(scopes, scopeIdToName),
              resolveAll(policies, policyIdToName)
          );
        })
        .toList();
  }

  private List<Map<String, Object>> getList(String url) {
    return getList(url, adminToken());
  }

  private List<SystemEndpointModel> loadEndpoints(String clientUuid, String token) {
    Map<String, Object> client = getMap(adminBase() + "/clients/" + clientUuid, token);
    Map<String, Object> attributes = objectMap(client.get("attributes"));
    String json = Objects.toString(attributes.get(endpointAttribute), "");
    if (json.isBlank()) {
      return List.of();
    }
    try {
      return objectMapper.readValue(json, new TypeReference<>() {
      });
    } catch (JsonProcessingException e) {
      throw new IllegalStateException("Failed to parse endpoint attribute from Keycloak client: " + endpointAttribute, e);
    }
  }

  private void syncRealmRoles(PermissionModel previous, PermissionModel model, String token) {
    Set<String> currentNames = names(model.getRealmRoles().stream().map(RealmRoleModel::name).toList());
    for (RealmRoleModel role : model.getRealmRoles()) {
      upsertRealmRole(role, token);
    }
    for (RealmRoleModel role : previous.getRealmRoles()) {
      if (!currentNames.contains(role.name())) {
        delete(adminBase() + "/roles/" + encodePath(role.name()), token);
      }
    }
  }

  private void upsertRealmRole(RealmRoleModel role, String token) {
    String roleUrl = adminBase() + "/roles/" + encodePath(role.name());
    Map<String, Object> body = Map.of("name", role.name(), "description", safe(role.description()));
    if (exists(roleUrl, token)) {
      put(roleUrl, token, body);
    } else {
      post(adminBase() + "/roles", token, body);
    }
  }

  private void syncClients(PermissionModel model, String token) {
    for (ClientModel client : model.getClients()) {
      String uuid = clientUuid(client.clientId(), token);
      List<String> defaultScopes = getList(adminBase() + "/clients/" + uuid + "/default-client-scopes", token)
          .stream()
          .map(scope -> text(scope, "name"))
          .toList();
      for (String scopeName : client.defaultClientScopes()) {
        if (!defaultScopes.contains(scopeName)) {
          Map<String, Object> scope = clientScope(scopeName, token);
          put(adminBase() + "/clients/" + uuid + "/default-client-scopes/" + text(scope, "id"), token, null);
        }
      }
      addRealmRoleScopeMappings(uuid, client.realmRoleScopeMappings(), token);
    }
  }

  private void syncUsers(PermissionModel previous, PermissionModel model, String token) {
    Set<String> currentNames = names(model.getUsers().stream().map(UserModel::username).toList());
    for (UserModel user : model.getUsers()) {
      upsertUser(user, token);
    }
    for (UserModel user : previous.getUsers()) {
      if (!currentNames.contains(user.username())) {
        String userId = userId(user.username(), token);
        if (userId != null) {
          delete(adminBase() + "/users/" + userId, token);
        }
      }
    }
  }

  private void upsertUser(UserModel user, String token) {
    String userId = userId(user.username(), token);
    if (userId == null) {
      post(adminBase() + "/users", token, userPayload(user));
      userId = userId(user.username(), token);
      if (userId == null) {
        throw new IllegalStateException("Keycloak user was not found after create: " + user.username());
      }
    } else {
      put(adminBase() + "/users/" + userId, token, userPayload(user));
    }
    if (!safe(user.password()).isBlank()) {
      put(adminBase() + "/users/" + userId + "/reset-password", token, mapOf(
          "type", "password",
          "value", user.password(),
          "temporary", false
      ));
    }
    replaceUserRealmRoles(userId, user.realmRoles(), token);
  }

  private Map<String, Object> userPayload(UserModel user) {
    Map<String, Object> payload = mapOf(
        "username", user.username(),
        "enabled", true
    );
    if (!safe(user.email()).isBlank()) {
      payload.put("email", user.email());
    }
    return payload;
  }

  private void syncScopes(PermissionModel model, String authzBase, String token) {
    Set<String> scopeNames = model.getResources().stream()
        .flatMap(resource -> resource.scopes().stream())
        .collect(Collectors.toCollection(LinkedHashSet::new));
    for (String scopeName : scopeNames) {
      upsertNamed(authzBase + "/scope", authzBase + "/scope", token, Map.of("name", scopeName), false);
    }
  }

  private void syncResources(PermissionModel previous, PermissionModel model, String authzBase, String token) {
    Set<String> currentNames = names(model.getResources().stream().map(UmaResourceModel::name).toList());
    for (UmaResourceModel resource : model.getResources()) {
      List<Map<String, Object>> scopes = resource.scopes().stream()
          .map(scope -> Map.<String, Object>of("name", scope))
          .toList();
      upsertNamed(authzBase + "/resource", authzBase + "/resource", token, mapOf(
          "name", resource.name(),
          "uris", resource.uris(),
          "scopes", scopes
      ), true);
    }
    deleteMissing(previous.getResources().stream().map(UmaResourceModel::name).toList(), currentNames, authzBase + "/resource", token);
  }

  private void syncPolicies(PermissionModel previous, PermissionModel model, String authzBase, String token) {
    Set<String> currentNames = names(model.getPolicies().stream().map(PolicyModel::name).toList());
    Map<String, PolicyModel> previousByName = previous.getPolicies().stream()
        .collect(Collectors.toMap(PolicyModel::name, item -> item, (left, right) -> left, LinkedHashMap::new));
    for (PolicyModel policy : model.getPolicies()) {
      if (!"role".equals(policy.type())) {
        throw new IllegalArgumentException("Unsupported policy type: " + policy.type());
      }
      PolicyModel previousPolicy = previousByName.get(policy.name());
      if (previousPolicy != null && samePolicy(previousPolicy, policy)) {
        continue;
      }
      upsertRolePolicy(policy, authzBase, token);
    }
    deleteMissing(previous.getPolicies().stream().map(PolicyModel::name).toList(), currentNames, authzBase + "/policy", token);
  }

  private void upsertRolePolicy(PolicyModel policy, String authzBase, String token) {
    String roleId = text(getMap(adminBase() + "/roles/" + encodePath(policy.realmRole()), token), "id");
    Map<String, Object> existing = findByName(authzBase + "/policy", policy.name(), token);
    if (existing == null) {
      post(authzBase + "/policy/role", token, mapOf(
          "name", policy.name(),
          "type", "role",
          "logic", "POSITIVE",
          "decisionStrategy", "UNANIMOUS",
          "roles", List.of(Map.of("id", roleId, "required", true)),
          "description", safe(policy.description())
      ));
      return;
    }

    try {
      put(authzBase + "/policy/" + keycloakId(existing), token, mapOf(
          "id", keycloakId(existing),
          "name", policy.name(),
          "type", "role",
          "logic", "POSITIVE",
          "decisionStrategy", "UNANIMOUS",
          "config", Map.of("roles", objectMapper.writeValueAsString(List.of(Map.of("id", roleId, "required", true)))),
          "description", safe(policy.description())
      ));
    } catch (JsonProcessingException e) {
      throw new IllegalStateException("Failed to serialize role policy config: " + policy.name(), e);
    }
  }

  private void syncPermissions(PermissionModel previous, PermissionModel model, String authzBase, String token) {
    Set<String> currentNames = names(model.getPermissions().stream().map(PermissionRuleModel::name).toList());
    Map<String, PermissionRuleModel> previousByName = previous.getPermissions().stream()
        .collect(Collectors.toMap(PermissionRuleModel::name, item -> item, (left, right) -> left, LinkedHashMap::new));
    for (PermissionRuleModel permission : model.getPermissions()) {
      PermissionRuleModel previousPermission = previousByName.get(permission.name());
      if (previousPermission != null && samePermission(previousPermission, permission)) {
        continue;
      }
      upsertScopePermission(permission, authzBase, token);
    }
    deleteMissing(previous.getPermissions().stream().map(PermissionRuleModel::name).toList(), currentNames, authzBase + "/permission", token);
  }

  private void upsertScopePermission(PermissionRuleModel permission, String authzBase, String token) {
    Map<String, Object> existing = findByName(authzBase + "/permission", permission.name(), token);
    if (existing != null) {
      delete(authzBase + "/permission/" + keycloakId(existing), token);
    }
    post(authzBase + "/permission/scope", token, mapOf(
        "name", permission.name(),
        "type", "scope",
        "logic", "POSITIVE",
        "decisionStrategy", "UNANIMOUS",
        "resources", List.of(permission.resource()),
        "scopes", List.of(permission.scope()),
        "policies", permission.policies()
    ));
  }

  private boolean samePolicy(PolicyModel left, PolicyModel right) {
    return Objects.equals(left.name(), right.name())
        && Objects.equals(left.type(), right.type())
        && Objects.equals(left.realmRole(), right.realmRole())
        && Objects.equals(safe(left.description()), safe(right.description()));
  }

  private boolean samePermission(PermissionRuleModel left, PermissionRuleModel right) {
    return Objects.equals(left.name(), right.name())
        && Objects.equals(left.resource(), right.resource())
        && Objects.equals(left.scope(), right.scope())
        && Objects.equals(left.policies(), right.policies());
  }

  private void syncEndpoints(PermissionModel model, String clientUuid, String token) {
    Map<String, Object> client = getMap(adminBase() + "/clients/" + clientUuid, token);
    Map<String, Object> attributes = objectMap(client.get("attributes"));
    try {
      attributes.put(endpointAttribute, objectMapper.writeValueAsString(model.getEndpoints()));
    } catch (JsonProcessingException e) {
      throw new IllegalStateException("Failed to serialize endpoint mappings", e);
    }
    client.put("attributes", attributes);
    put(adminBase() + "/clients/" + clientUuid, token, client);
  }

  private void replaceUserRealmRoles(String userId, List<String> roleNames, String token) {
    List<Map<String, Object>> mapped = getList(adminBase() + "/users/" + userId + "/role-mappings/realm", token);
    List<Map<String, Object>> managedMapped = mapped.stream()
        .filter(role -> !isBuiltInRealmRole(text(role, "name")))
        .toList();
    if (!managedMapped.isEmpty()) {
      request("DELETE", adminBase() + "/users/" + userId + "/role-mappings/realm", token, managedMapped);
    }
    List<Map<String, Object>> desired = new ArrayList<>();
    for (String roleName : roleNames) {
      desired.add(getMap(adminBase() + "/roles/" + encodePath(roleName), token));
    }
    if (!desired.isEmpty()) {
      request("POST", adminBase() + "/users/" + userId + "/role-mappings/realm", token, desired);
    }
  }

  private void addRealmRoleScopeMappings(String clientUuid, List<String> roleNames, String token) {
    List<String> mappedNames = getList(adminBase() + "/clients/" + clientUuid + "/scope-mappings/realm", token)
        .stream()
        .map(role -> text(role, "name"))
        .toList();
    List<Map<String, Object>> missing = new ArrayList<>();
    for (String roleName : roleNames) {
      if (!mappedNames.contains(roleName)) {
        missing.add(getMap(adminBase() + "/roles/" + encodePath(roleName), token));
      }
    }
    if (!missing.isEmpty()) {
      request("POST", adminBase() + "/clients/" + clientUuid + "/scope-mappings/realm", token, missing);
    }
  }

  private void upsertNamed(String listUrl, String createUrl, String token, Map<String, Object> body, boolean includeUnderscoreId) {
    String name = Objects.toString(body.get("name"), "");
    Map<String, Object> existing = findByName(listUrl, name, token);
    if (existing == null) {
      post(createUrl, token, body);
      return;
    }
    String id = keycloakId(existing);
    Map<String, Object> updateBody = new LinkedHashMap<>(body);
    if (includeUnderscoreId) {
      updateBody.put("_id", id);
    }
    put(listUrl + "/" + id, token, updateBody);
  }

  private void deleteMissing(List<String> previousNames, Set<String> currentNames, String listUrl, String token) {
    for (String previousName : previousNames) {
      if (!currentNames.contains(previousName)) {
        Map<String, Object> existing = findByName(listUrl, previousName, token);
        if (existing != null) {
          delete(listUrl + "/" + keycloakId(existing), token);
        }
      }
    }
  }

  private Map<String, Object> findByName(String listUrl, String name, String token) {
    return getList(listUrl, token).stream()
        .filter(item -> name.equals(text(item, "name")))
        .findFirst()
        .orElse(null);
  }

  private Map<String, Object> clientByClientId(String clientId, String token) {
    List<Map<String, Object>> clients = getList(adminBase() + "/clients?clientId=" + encodeQuery(clientId), token);
    if (clients.isEmpty()) {
      throw new IllegalStateException("Keycloak client not found: " + clientId);
    }
    return clients.getFirst();
  }

  private String clientUuid(String clientId, String token) {
    return text(clientByClientId(clientId, token), "id");
  }

  private Map<String, Object> clientScope(String scopeName, String token) {
    return getList(adminBase() + "/client-scopes", token).stream()
        .filter(scope -> scopeName.equals(text(scope, "name")))
        .findFirst()
        .orElseThrow(() -> new IllegalStateException("Client scope not found: " + scopeName));
  }

  private String userId(String username, String token) {
    String url = adminBase() + "/users?username=" + encodeQuery(username) + "&exact=true";
    List<Map<String, Object>> users = getList(url, token);
    return users.isEmpty() ? null : text(users.getFirst(), "id");
  }

  private String adminToken() {
    String body = "grant_type=password&client_id=admin-cli&username=" + encodeQuery(adminUser)
        + "&password=" + encodeQuery(adminPassword);
    HttpRequest request = HttpRequest.newBuilder()
        .uri(URI.create(keycloakUrl + "/realms/master/protocol/openid-connect/token"))
        .header("Content-Type", "application/x-www-form-urlencoded")
        .POST(HttpRequest.BodyPublishers.ofString(body))
        .build();
    Map<String, Object> response = send(request, new TypeReference<>() {
    });
    return text(response, "access_token");
  }

  private List<Map<String, Object>> getList(String url, String token) {
    return request("GET", url, token, null, LIST_OF_MAPS);
  }

  private Map<String, Object> getMap(String url, String token) {
    return request("GET", url, token, null, new TypeReference<>() {
    });
  }

  private void post(String url, String token, Object body) {
    request("POST", url, token, body);
  }

  private void put(String url, String token, Object body) {
    request("PUT", url, token, body);
  }

  private void delete(String url, String token) {
    request("DELETE", url, token, null);
  }

  private boolean exists(String url, String token) {
    try {
      getMap(url, token);
      return true;
    } catch (IllegalStateException e) {
      if (e.getMessage().contains("status=404")) {
        return false;
      }
      throw e;
    }
  }

  private void request(String method, String url, String token, Object body) {
    request(method, url, token, body, new TypeReference<Map<String, Object>>() {
    });
  }

  private <T> T request(String method, String url, String token, Object body, TypeReference<T> type) {
    HttpRequest.Builder builder = HttpRequest.newBuilder()
        .uri(URI.create(url))
        .header("Authorization", "Bearer " + token);
    if (body == null) {
      builder.method(method, HttpRequest.BodyPublishers.noBody());
    } else {
      builder.header("Content-Type", "application/json");
      try {
        builder.method(method, HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(body)));
      } catch (JsonProcessingException e) {
        throw new IllegalStateException("Failed to serialize Keycloak request body", e);
      }
    }
    HttpRequest request = builder.build();
    return send(request, type);
  }

  private <T> T send(HttpRequest request, TypeReference<T> type) {
    try {
      HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
      if (response.statusCode() >= 400) {
        throw new IllegalStateException("Keycloak request failed: status=" + response.statusCode()
            + ", method=" + request.method()
            + ", uri=" + request.uri()
            + ", body=" + response.body());
      }
      if (response.body() == null || response.body().isBlank()) {
        return objectMapper.readValue("{}", type);
      }
      return objectMapper.readValue(response.body(), type);
    } catch (IOException e) {
      throw new IllegalStateException("Keycloak request failed: method=" + request.method() + ", uri=" + request.uri(), e);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException("Keycloak request interrupted: method=" + request.method() + ", uri=" + request.uri(), e);
    }
  }

  private String adminBase() {
    return keycloakUrl + "/admin/realms/" + encodePath(realm);
  }

  private Map<String, String> realmRoleIdToName(String token) {
    Map<String, String> roles = new LinkedHashMap<>();
    for (Map<String, Object> role : getList(adminBase() + "/roles", token)) {
      roles.put(text(role, "id"), text(role, "name"));
    }
    return roles;
  }

  private String roleFromPolicy(Map<String, Object> policy, Map<String, String> roleIdToName) {
    Object roles = policy.get("roles");
    List<String> roleNames = objectNameList(roles);
    if (!roleNames.isEmpty()) {
      return roleNames.getFirst();
    }
    Map<String, String> config = config(policy);
    String configuredRoles = config.get("roles");
    if (configuredRoles == null || configuredRoles.isBlank()) {
      return "";
    }
    try {
      JsonNode node = objectMapper.readTree(configuredRoles);
      if (node.isArray() && !node.isEmpty()) {
        String roleId = node.get(0).path("id").asText();
        return roleIdToName.getOrDefault(roleId, roleId);
      }
    } catch (JsonProcessingException ignored) {
      return configuredRoles;
    }
    return "";
  }

  private List<String> configuredValues(Map<String, Object> item, Map<String, String> config, String field) {
    List<String> directValues = objectNameList(item.get(field));
    if (!directValues.isEmpty()) {
      return directValues;
    }
    String configured = config.get(field);
    if (configured == null || configured.isBlank()) {
      return List.of();
    }
    try {
      JsonNode node = objectMapper.readTree(configured);
      if (node.isArray() && !node.isEmpty()) {
        List<String> values = new ArrayList<>();
        for (JsonNode value : node) {
          String text = value.isObject() ? value.path("name").asText(value.path("id").asText()) : value.asText();
          if (!text.isBlank()) {
            values.add(text);
          }
        }
        return values;
      }
    } catch (JsonProcessingException ignored) {
      return List.of(configured);
    }
    return List.of();
  }

  private Map<String, String> idToName(List<Map<String, Object>> items) {
    Map<String, String> result = new LinkedHashMap<>();
    for (Map<String, Object> item : items) {
      String name = text(item, "name");
      if (!name.isBlank()) {
        result.put(keycloakId(item), name);
        result.put(name, name);
      }
    }
    return result;
  }

  private String resolveFirst(List<String> values, Map<String, String> idToName) {
    List<String> resolved = resolveAll(values, idToName);
    return resolved.isEmpty() ? "" : resolved.getFirst();
  }

  private List<String> resolveAll(List<String> values, Map<String, String> idToName) {
    return values.stream()
        .map(value -> idToName.getOrDefault(value, value))
        .filter(value -> !value.isBlank())
        .distinct()
        .toList();
  }

  private List<String> firstNonEmpty(List<String> left, List<String> right) {
    return left.isEmpty() ? right : left;
  }

  private Map<String, String> config(Map<String, Object> item) {
    Object config = item.get("config");
    if (!(config instanceof Map<?, ?> map)) {
      return Map.of();
    }
    Map<String, String> result = new LinkedHashMap<>();
    map.forEach((key, value) -> result.put(Objects.toString(key), Objects.toString(value, "")));
    return result;
  }

  private String keycloakId(Map<String, Object> item) {
    String id = text(item, "id");
    if (!id.isBlank()) {
      return id;
    }
    return Objects.toString(item.get("_id"), "");
  }

  private boolean isBuiltInRealmRole(String name) {
    return name == null
        || name.isBlank()
        || name.startsWith("default-roles-")
        || "offline_access".equals(name)
        || "uma_authorization".equals(name);
  }

  private List<String> objectNameList(Object value) {
    if (value == null) {
      return List.of();
    }
    if (value instanceof List<?> list) {
      return list.stream()
          .map(item -> item instanceof Map<?, ?> map ? namedObjectValue(map) : Objects.toString(item, ""))
          .filter(item -> !item.isBlank())
          .toList();
    }
    return List.of(Objects.toString(value)).stream().filter(item -> !item.isBlank()).toList();
  }

  private String namedObjectValue(Map<?, ?> map) {
    String name = Objects.toString(map.get("name"), "");
    if (!name.isBlank()) {
      return name;
    }
    return Objects.toString(map.get("id"), "");
  }

  private List<String> stringList(Object value) {
    if (value instanceof List<?> list) {
      return list.stream().map(Objects::toString).toList();
    }
    if (value == null) {
      return List.of();
    }
    return List.of(Objects.toString(value));
  }

  @SuppressWarnings("unchecked")
  private Map<String, Object> objectMap(Object value) {
    if (value instanceof Map<?, ?> map) {
      Map<String, Object> result = new LinkedHashMap<>();
      map.forEach((key, item) -> result.put(Objects.toString(key), item));
      return result;
    }
    return new LinkedHashMap<>();
  }

  private List<String> splitCsv(String value) {
    if (value == null || value.isBlank()) {
      return List.of();
    }
    return List.of(value.split(",")).stream().map(String::trim).filter(item -> !item.isBlank()).toList();
  }

  private Set<String> names(List<String> names) {
    return new LinkedHashSet<>(names);
  }

  private Map<String, Object> mapOf(Object... values) {
    Map<String, Object> map = new LinkedHashMap<>();
    for (int i = 0; i < values.length; i += 2) {
      map.put(Objects.toString(values[i]), values[i + 1]);
    }
    return map;
  }

  private String text(Map<String, Object> map, String key) {
    return Objects.toString(map.get(key), "");
  }

  private String safe(String value) {
    return value == null ? "" : value;
  }

  private String clientType(Map<String, Object> client) {
    return Boolean.TRUE.equals(client.get("authorizationServicesEnabled")) ? "confidential-resource-server" : "confidential";
  }

  private String trimRight(String value, String suffix) {
    String result = value;
    while (result.endsWith(suffix)) {
      result = result.substring(0, result.length() - suffix.length());
    }
    return result;
  }

  private String encodeQuery(String value) {
    return URLEncoder.encode(value, StandardCharsets.UTF_8);
  }

  private String encodePath(String value) {
    return UriComponentsBuilder.newInstance().pathSegment(value).build().toUriString().substring(1);
  }

  private PermissionModel copy(PermissionModel model) {
    return objectMapper.convertValue(model, PermissionModel.class);
  }
}
