package com.example.uma.security;

import com.example.uma.api.dto.UiPermissionDto;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Component
public class UiPermissionCatalog {

  private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {
  };
  private static final TypeReference<List<UiPermissionDto>> UI_PERMISSION_LIST_TYPE = new TypeReference<>() {
  };

  private final ObjectMapper objectMapper;
  private final HttpClient httpClient;
  private final Clock clock;
  private final String keycloakUrl;
  private final String realm;
  private final String adminUser;
  private final String adminPassword;
  private final String resourceServerClientId;
  private final String uiPermissionAttribute;
  private final Duration cacheTtl;
  private List<UiPermissionDto> cached = List.of();
  private Instant cachedAt = Instant.EPOCH;

  @Autowired
  public UiPermissionCatalog(
      @Value("${app.keycloak.url:http://localhost:8080}") String keycloakUrl,
      @Value("${app.keycloak.realm:demo}") String realm,
      @Value("${app.keycloak.admin-user:admin}") String adminUser,
      @Value("${app.keycloak.admin-password:admin}") String adminPassword,
      @Value("${app.keycloak.resource-server-client-id:demo-api}") String resourceServerClientId,
      @Value("${app.keycloak.ui-permission-attribute:uma.ui-permissions}") String uiPermissionAttribute,
      @Value("${app.keycloak.ui-permission-cache-ttl:PT30S}") Duration cacheTtl
  ) {
    this(new ObjectMapper(), HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build(), Clock.systemUTC(),
        keycloakUrl, realm, adminUser, adminPassword, resourceServerClientId, uiPermissionAttribute, cacheTtl);
  }

  UiPermissionCatalog(
      ObjectMapper objectMapper,
      HttpClient httpClient,
      Clock clock,
      String keycloakUrl,
      String realm,
      String adminUser,
      String adminPassword,
      String resourceServerClientId,
      String uiPermissionAttribute,
      Duration cacheTtl
  ) {
    this.objectMapper = objectMapper;
    this.httpClient = httpClient;
    this.clock = clock;
    this.keycloakUrl = trimRight(keycloakUrl, "/");
    this.realm = realm;
    this.adminUser = adminUser;
    this.adminPassword = adminPassword;
    this.resourceServerClientId = resourceServerClientId;
    this.uiPermissionAttribute = uiPermissionAttribute;
    this.cacheTtl = cacheTtl;
  }

  public synchronized List<UiPermissionDto> listEnabled() {
    Instant now = clock.instant();
    if (!cached.isEmpty() && cachedAt.plus(cacheTtl).isAfter(now)) {
      return cached;
    }

    cached = loadFromKeycloak().stream()
        .filter(UiPermissionDto::enabled)
        .sorted(Comparator.comparingInt(UiPermissionDto::sort).thenComparing(UiPermissionDto::code))
        .toList();
    cachedAt = now;
    return cached;
  }

  private List<UiPermissionDto> loadFromKeycloak() {
    String token = adminToken();
    String clientUuid = clientUuid(token);
    Map<String, Object> client = getMap(adminBase() + "/clients/" + clientUuid, token);
    Map<String, Object> attributes = objectMap(client.get("attributes"));
    String json = attributeValue(attributes.get(uiPermissionAttribute));
    if (json.isBlank()) {
      return List.of();
    }

    try {
      return objectMapper.readValue(json, UI_PERMISSION_LIST_TYPE);
    } catch (JsonProcessingException e) {
      throw new IllegalStateException("读取 Keycloak UI 权限目录失败: " + uiPermissionAttribute, e);
    }
  }

  private String adminToken() {
    String body = "grant_type=password&client_id=admin-cli&username=" + encode(adminUser)
        + "&password=" + encode(adminPassword);
    HttpRequest request = HttpRequest.newBuilder()
        .uri(URI.create(keycloakUrl + "/realms/master/protocol/openid-connect/token"))
        .header("Content-Type", "application/x-www-form-urlencoded")
        .POST(HttpRequest.BodyPublishers.ofString(body))
        .build();
    Map<String, Object> response = send(request);
    return text(response, "access_token");
  }

  private String clientUuid(String token) {
    List<Map<String, Object>> clients = getList(adminBase() + "/clients?clientId=" + encode(resourceServerClientId), token);
    if (clients.isEmpty()) {
      throw new IllegalStateException("Keycloak client not found: " + resourceServerClientId);
    }
    return text(clients.getFirst(), "id");
  }

  private List<Map<String, Object>> getList(String url, String token) {
    HttpRequest request = HttpRequest.newBuilder()
        .uri(URI.create(url))
        .header("Authorization", "Bearer " + token)
        .GET()
        .build();
    try {
      return objectMapper.readValue(sendRaw(request), new TypeReference<>() {
      });
    } catch (JsonProcessingException e) {
      throw new IllegalStateException("解析 Keycloak 响应失败: " + url, e);
    }
  }

  private Map<String, Object> getMap(String url, String token) {
    HttpRequest request = HttpRequest.newBuilder()
        .uri(URI.create(url))
        .header("Authorization", "Bearer " + token)
        .GET()
        .build();
    return send(request);
  }

  private Map<String, Object> send(HttpRequest request) {
    try {
      String body = sendRaw(request);
      if (body.isBlank()) {
        return Map.of();
      }
      return objectMapper.readValue(body, MAP_TYPE);
    } catch (JsonProcessingException e) {
      throw new IllegalStateException("解析 Keycloak 响应失败: " + request.uri(), e);
    }
  }

  private String sendRaw(HttpRequest request) {
    try {
      HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
      if (response.statusCode() >= 400) {
        throw new IllegalStateException("Keycloak 请求失败: status=" + response.statusCode()
            + ", method=" + request.method()
            + ", uri=" + request.uri()
            + ", body=" + response.body());
      }
      return response.body() == null ? "" : response.body();
    } catch (IOException e) {
      throw new IllegalStateException("Keycloak 请求失败: method=" + request.method() + ", uri=" + request.uri(), e);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException("Keycloak 请求中断: method=" + request.method() + ", uri=" + request.uri(), e);
    }
  }

  private String adminBase() {
    return keycloakUrl + "/admin/realms/" + encode(realm);
  }

  private Map<String, Object> objectMap(Object value) {
    if (!(value instanceof Map<?, ?> map)) {
      return Map.of();
    }
    Map<String, Object> result = new LinkedHashMap<>();
    map.forEach((key, item) -> result.put(Objects.toString(key), item));
    return result;
  }

  private String attributeValue(Object value) {
    if (value instanceof List<?> list && !list.isEmpty()) {
      return Objects.toString(list.getFirst(), "");
    }
    return Objects.toString(value, "");
  }

  private String text(Map<String, Object> map, String key) {
    return Objects.toString(map.get(key), "");
  }

  private String trimRight(String value, String suffix) {
    String result = value;
    while (result.endsWith(suffix)) {
      result = result.substring(0, result.length() - suffix.length());
    }
    return result;
  }

  private String encode(String value) {
    return URLEncoder.encode(value, StandardCharsets.UTF_8);
  }
}
