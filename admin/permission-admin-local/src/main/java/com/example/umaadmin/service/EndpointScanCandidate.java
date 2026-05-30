package com.example.umaadmin.service;

public record EndpointScanCandidate(String name, String method, String path, String permission, boolean exists) {
}
