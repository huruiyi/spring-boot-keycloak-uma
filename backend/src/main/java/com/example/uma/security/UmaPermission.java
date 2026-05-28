package com.example.uma.security;

import java.util.Collection;
import java.util.Map;

public record UmaPermission(String resource, Collection<String> scopes) {

  public static UmaPermission from(Map<String, Object> value) {
    String resource = firstString(value, "rsname", "resource_name", "name");
    Object scopesValue = value.get("scopes");
    Collection<String> scopes = scopesValue instanceof Collection<?> collection
        ? collection.stream().map(String::valueOf).toList()
        : java.util.List.of();
    return new UmaPermission(resource, scopes);
  }

  private static String firstString(Map<String, Object> value, String... keys) {
    for (String key : keys) {
      Object item = value.get(key);
      if (item != null) {
        return String.valueOf(item);
      }
    }
    return "";
  }
}
