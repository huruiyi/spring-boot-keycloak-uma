package com.example.umaadmin.model;

import java.util.List;

public record UmaResourceModel(String name, List<String> uris, List<String> scopes) {
}
