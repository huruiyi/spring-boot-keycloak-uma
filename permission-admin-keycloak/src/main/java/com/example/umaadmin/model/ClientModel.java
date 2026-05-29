package com.example.umaadmin.model;

import java.util.List;

public record ClientModel(
    String clientId,
    String type,
    String description,
    List<String> defaultClientScopes,
    List<String> realmRoleScopeMappings
) {
}
