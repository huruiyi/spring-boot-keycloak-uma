package com.example.umaadmin.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.ArrayList;
import java.util.List;

public class PermissionRuleModel {

  public static final String DEFAULT_DECISION_STRATEGY = "AFFIRMATIVE";

  private final String name;
  private final String resource;
  private final String scope;
  private final List<String> policies;
  private final String decisionStrategy;

  public PermissionRuleModel(String name, String resource, String scope, String policy) {
    this(name, resource, scope, policy, null, null);
  }

  public PermissionRuleModel(String name, String resource, String scope, List<String> policies) {
    this(name, resource, scope, policies, null);
  }

  public PermissionRuleModel(String name, String resource, String scope, List<String> policies, String decisionStrategy) {
    this(name, resource, scope, null, policies, decisionStrategy);
  }

  @JsonCreator
  public PermissionRuleModel(
      @JsonProperty("name") String name,
      @JsonProperty("resource") String resource,
      @JsonProperty("scope") String scope,
      @JsonProperty("policy") String policy,
      @JsonProperty("policies") List<String> policies,
      @JsonProperty("decisionStrategy") String decisionStrategy
  ) {
    this.name = name;
    this.resource = resource;
    this.scope = scope;
    this.policies = normalizePolicies(policy, policies);
    this.decisionStrategy = normalizeDecisionStrategy(decisionStrategy);
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

  public String decisionStrategy() {
    return decisionStrategy;
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

  public String getDecisionStrategy() {
    return decisionStrategy;
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

  private static String normalizeDecisionStrategy(String decisionStrategy) {
    if ("UNANIMOUS".equals(decisionStrategy)) {
      return "UNANIMOUS";
    }
    return DEFAULT_DECISION_STRATEGY;
  }
}
