package com.example.umaadmin.model;

import java.util.List;

public record UserModel(String username, String email, String password, List<String> realmRoles) {
}
