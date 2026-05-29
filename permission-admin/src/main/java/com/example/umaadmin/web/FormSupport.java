package com.example.umaadmin.web;

import java.util.Arrays;
import java.util.List;

final class FormSupport {

  private FormSupport() {
  }

  static List<String> splitCsv(String value) {
    if (value == null || value.isBlank()) {
      return List.of();
    }
    return Arrays.stream(value.split(","))
        .map(String::trim)
        .filter(item -> !item.isBlank())
        .distinct()
        .toList();
  }
}
