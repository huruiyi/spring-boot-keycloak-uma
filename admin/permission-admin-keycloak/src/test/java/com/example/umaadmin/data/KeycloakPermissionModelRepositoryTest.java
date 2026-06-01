package com.example.umaadmin.data;

import com.example.umaadmin.model.PermissionModel;
import com.example.umaadmin.model.PermissionRuleModel;
import com.example.umaadmin.model.PolicyModel;
import com.example.umaadmin.model.UmaResourceModel;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class KeycloakPermissionModelRepositoryTest {

  private final ObjectMapper objectMapper = new ObjectMapper();
  private MockKeycloak keycloak;

  @AfterEach
  void stopKeycloak() {
    if (keycloak != null) {
      keycloak.stop();
    }
  }

  @Test
  void loadsPoliciesAndPermissionsFromKeycloakResponseShapes() throws IOException {
    keycloak = new MockKeycloak();
    KeycloakPermissionModelRepository repository = repository(keycloak.url());

    PermissionModel model = repository.get();

    assertThat(model.getPolicies())
        .extracting(PolicyModel::name, PolicyModel::type, PolicyModel::subject)
        .containsExactly(
            org.assertj.core.groups.Tuple.tuple("policy-manager", "role", "manager"),
            org.assertj.core.groups.Tuple.tuple("policy-auditor-user", "user", "auditor"),
            org.assertj.core.groups.Tuple.tuple("policy-demo-client", "client", "demo-frontend")
        );
    assertThat(model.getPermissions())
        .singleElement()
        .satisfies(permission -> {
          assertThat(permission.name()).isEqualTo("perm-order-export");
          assertThat(permission.resource()).isEqualTo("order");
          assertThat(permission.scope()).isEqualTo("export");
          assertThat(permission.policies()).containsExactly("policy-manager", "policy-auditor-user");
          assertThat(permission.decisionStrategy()).isEqualTo("UNANIMOUS");
        });
  }

  @Test
  void savesScopePermissionWithMultiplePoliciesAndDecisionStrategy() throws IOException {
    keycloak = new MockKeycloak();
    KeycloakPermissionModelRepository repository = repository(keycloak.url());
    PermissionModel model = repository.get();
    model.setPermissions(List.of(
        model.getPermissions().getFirst(),
        new PermissionRuleModel(
            "perm-order-approve",
            "order",
            "approve",
            List.of("policy-manager", "policy-demo-client"),
            "AFFIRMATIVE"
        )
    ));

    repository.save(model);

    Map<String, Object> created = keycloak.createdScopePermission("perm-order-approve");
    assertThat(created).containsEntry("name", "perm-order-approve");
    assertThat(created).containsEntry("type", "scope");
    assertThat(created).containsEntry("decisionStrategy", "AFFIRMATIVE");
    assertThat(created.get("resources")).isEqualTo(List.of("order"));
    assertThat(created.get("scopes")).isEqualTo(List.of("approve"));
    assertThat(created.get("policies")).isEqualTo(List.of("policy-manager", "policy-demo-client"));
  }

  private KeycloakPermissionModelRepository repository(String keycloakUrl) {
    return new KeycloakPermissionModelRepository(
        keycloakUrl,
        "demo",
        "admin",
        "admin",
        "backend-api",
        "demo-frontend",
        "uma.endpoints"
    );
  }

  private class MockKeycloak {

    private final HttpServer server;
    private final List<Map<String, Object>> createdScopePermissions = new ArrayList<>();

    private MockKeycloak() throws IOException {
      server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
      server.createContext("/", this::handle);
      server.start();
    }

    private String url() {
      return "http://localhost:" + server.getAddress().getPort();
    }

    private void stop() {
      server.stop(0);
    }

    private Map<String, Object> createdScopePermission(String name) {
      return createdScopePermissions.stream()
          .filter(item -> name.equals(item.get("name")))
          .findFirst()
          .orElseThrow();
    }

    private void handle(HttpExchange exchange) throws IOException {
      String method = exchange.getRequestMethod();
      String path = exchange.getRequestURI().getPath();
      String query = exchange.getRequestURI().getRawQuery();
      String route = query == null ? path : path + "?" + query;

      if ("POST".equals(method) && "/realms/master/protocol/openid-connect/token".equals(path)) {
        respond(exchange, 200, Map.of("access_token", "test-token"));
        return;
      }
      if ("POST".equals(method) && path.endsWith("/authz/resource-server/permission/scope")) {
        Map<String, Object> body = objectMapper.readValue(exchange.getRequestBody(), new TypeReference<>() {
        });
        createdScopePermissions.add(body);
        respond(exchange, 201, Map.of());
        return;
      }
      if (!"GET".equals(method) && !"PUT".equals(method)) {
        respondNoContent(exchange);
        return;
      }
      if ("PUT".equals(method)) {
        respondNoContent(exchange);
        return;
      }

      Object response = responseFor(route);
      if (response == null) {
        respond(exchange, 404, Map.of("error", "not found", "route", route));
        return;
      }
      respond(exchange, 200, response);
    }

    private Object responseFor(String route) {
      return switch (route) {
        case "/admin/realms/demo/roles" -> List.of(
            mapOf("id", "role-manager-id", "name", "manager", "description", "经理"),
            mapOf("id", "role-admin-id", "name", "admin", "description", "管理员"),
            mapOf("id", "default-role-id", "name", "default-roles-demo")
        );
        case "/admin/realms/demo/roles/manager" -> mapOf("id", "role-manager-id", "name", "manager");
        case "/admin/realms/demo/roles/admin" -> mapOf("id", "role-admin-id", "name", "admin");
        case "/admin/realms/demo/clients?clientId=demo-frontend" -> List.of(mapOf(
            "id", "frontend-uuid",
            "clientId", "demo-frontend",
            "name", "演示前端",
            "publicClient", true
        ));
        case "/admin/realms/demo/clients?clientId=backend-api" -> List.of(mapOf(
            "id", "rs-uuid",
            "clientId", "backend-api",
            "name", "资源服务",
            "authorizationServicesEnabled", true,
            "attributes", Map.of("uma.endpoints", "[]")
        ));
        case "/admin/realms/demo/clients" -> List.of(
            mapOf("id", "frontend-uuid", "clientId", "demo-frontend"),
            mapOf("id", "rs-uuid", "clientId", "backend-api")
        );
        case "/admin/realms/demo/clients/frontend-uuid/default-client-scopes" -> List.of();
        case "/admin/realms/demo/clients/frontend-uuid/scope-mappings/realm" -> List.of();
        case "/admin/realms/demo/clients/rs-uuid" -> mapOf(
            "id", "rs-uuid",
            "clientId", "backend-api",
            "attributes", new LinkedHashMap<>(Map.of("uma.endpoints", "[]"))
        );
        case "/admin/realms/demo/users" -> List.of(mapOf(
            "id", "user-auditor-id",
            "username", "auditor",
            "email", "auditor@example.com"
        ));
        case "/admin/realms/demo/users?username=auditor&exact=true" -> List.of(mapOf(
            "id", "user-auditor-id",
            "username", "auditor",
            "email", "auditor@example.com"
        ));
        case "/admin/realms/demo/users/user-auditor-id/role-mappings/realm" -> List.of(
            mapOf("id", "role-manager-id", "name", "manager")
        );
        case "/admin/realms/demo/clients/rs-uuid/authz/resource-server/resource" -> List.of(mapOf(
            "_id", "resource-order-id",
            "name", "order",
            "uris", List.of("/api/orders/*"),
            "scopes", List.of(mapOf("id", "scope-export-id", "name", "export"), mapOf("id", "scope-approve-id", "name", "approve"))
        ));
        case "/admin/realms/demo/clients/rs-uuid/authz/resource-server/scope" -> List.of(
            mapOf("id", "scope-export-id", "name", "export"),
            mapOf("id", "scope-approve-id", "name", "approve")
        );
        case "/admin/realms/demo/clients/rs-uuid/authz/resource-server/policy" -> List.of(
            mapOf("id", "policy-manager-id", "name", "policy-manager", "type", "role"),
            mapOf("id", "policy-auditor-user-id", "name", "policy-auditor-user", "type", "user"),
            mapOf("_id", "policy-demo-client-id", "name", "policy-demo-client", "type", "client")
        );
        case "/admin/realms/demo/clients/rs-uuid/authz/resource-server/policy/policy-manager-id" -> mapOf(
            "id", "policy-manager-id",
            "name", "policy-manager",
            "type", "role",
            "description", "经理策略",
            "config", Map.of("roles", "[{\"id\":\"role-manager-id\",\"required\":true}]")
        );
        case "/admin/realms/demo/clients/rs-uuid/authz/resource-server/policy/policy-auditor-user-id" -> mapOf(
            "id", "policy-auditor-user-id",
            "name", "policy-auditor-user",
            "type", "user",
            "description", "审计员策略",
            "config", Map.of("users", "[\"user-auditor-id\"]")
        );
        case "/admin/realms/demo/clients/rs-uuid/authz/resource-server/policy/policy-demo-client-id" -> mapOf(
            "_id", "policy-demo-client-id",
            "name", "policy-demo-client",
            "type", "client",
            "description", "前端客户端策略",
            "config", Map.of("clients", "[\"frontend-uuid\"]")
        );
        case "/admin/realms/demo/clients/rs-uuid/authz/resource-server/permission" -> List.of(mapOf(
            "id", "perm-order-export-id",
            "name", "perm-order-export",
            "type", "scope"
        ));
        case "/admin/realms/demo/clients/rs-uuid/authz/resource-server/policy/perm-order-export-id" -> mapOf(
            "id", "perm-order-export-id",
            "name", "perm-order-export",
            "type", "scope",
            "decisionStrategy", "UNANIMOUS",
            "config", Map.of(
                "resources", "[\"resource-order-id\"]",
                "scopes", "[\"scope-export-id\"]",
                "applyPolicies", "[\"policy-manager-id\",\"policy-auditor-user-id\"]"
            )
        );
        case "/admin/realms/demo/clients/rs-uuid/authz/resource-server/policy/perm-order-export-id/resources" -> List.of();
        case "/admin/realms/demo/clients/rs-uuid/authz/resource-server/policy/perm-order-export-id/scopes" -> List.of();
        case "/admin/realms/demo/clients/rs-uuid/authz/resource-server/permission/perm-order-export-id/associatedPolicies" -> List.of();
        case "/admin/realms/demo/clients/rs-uuid/authz/resource-server/permission/scope" -> List.of(mapOf(
            "id", "perm-order-export-id",
            "name", "perm-order-export",
            "type", "scope"
        ));
        case "/admin/realms/demo/client-scopes" -> List.of();
        default -> null;
      };
    }

    private void respond(HttpExchange exchange, int status, Object body) throws IOException {
      byte[] bytes = objectMapper.writeValueAsString(body).getBytes(StandardCharsets.UTF_8);
      exchange.getResponseHeaders().add("Content-Type", "application/json");
      exchange.sendResponseHeaders(status, bytes.length);
      exchange.getResponseBody().write(bytes);
      exchange.close();
    }

    private void respondNoContent(HttpExchange exchange) throws IOException {
      exchange.sendResponseHeaders(204, -1);
      exchange.close();
    }

    private Map<String, Object> mapOf(Object... values) {
      Map<String, Object> map = new LinkedHashMap<>();
      for (int i = 0; i < values.length; i += 2) {
        map.put(values[i].toString(), values[i + 1]);
      }
      return map;
    }
  }
}
