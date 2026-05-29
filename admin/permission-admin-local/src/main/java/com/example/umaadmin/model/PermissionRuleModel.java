package com.example.umaadmin.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.ArrayList;
import java.util.List;

public class PermissionRuleModel {

  private final String name;
  private final String resource;
  private final String scope;
  private final List<String> policies;

  public PermissionRuleModel(String name, String resource, String scope, String policy) {
    this(name, resource, scope, policy, null);
  }

  public PermissionRuleModel(String name, String resource, String scope, List<String> policies) {
    this(name, resource, scope, null, policies);
  }

  @JsonCreator
  public PermissionRuleModel(
      @JsonProperty("name") String name,
      @JsonProperty("resource") String resource,
      @JsonProperty("scope") String scope,
      @JsonProperty("policy") String policy,
      @JsonProperty("policies") List<String> policies
  ) {
    this.name = name;
    this.resource = resource;
    this.scope = scope;
    this.policies = normalizePolicies(policy, policies);
  }

  public String name() {
    return name;
  }

  public String resource() {
    return resource;
  }

  public String scope() {
    return scope;
  }

  @JsonIgnore
  public String policy() {
    return policies.isEmpty() ? "" : policies.getFirst();
  }

  public List<String> policies() {
    return policies;
  }

  public String getName() {
    return name;
  }

  public String getResource() {
    return resource;
  }

  public String getScope() {
    return scope;
  }

  public List<String> getPolicies() {
    return policies;
  }

  private static List<String> normalizePolicies(String policy, List<String> policies) {
    List<String> values = new ArrayList<>();
    if (policies != null) {
      values.addAll(policies.stream().filter(item -> item != null && !item.isBlank()).toList());
    }
    if (values.isEmpty() && policy != null && !policy.isBlank()) {
      values.add(policy);
    }
    return List.copyOf(values);
  }
}
