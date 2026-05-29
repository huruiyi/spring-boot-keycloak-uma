package com.example.umaadmin.data;

import com.example.umaadmin.model.ClientModel;
import com.example.umaadmin.model.PermissionModel;
import com.example.umaadmin.model.PermissionRuleModel;
import com.example.umaadmin.model.PolicyModel;
import com.example.umaadmin.model.RealmRoleModel;
import com.example.umaadmin.model.SystemEndpointModel;
import com.example.umaadmin.model.UmaResourceModel;
import com.example.umaadmin.model.UserModel;

import java.util.List;

public final class PermissionModelFactory {

  private PermissionModelFactory() {
  }

  public static PermissionModel defaultModel() {
    PermissionModel model = new PermissionModel();
    model.setRealmRoles(List.of(
        new RealmRoleModel("user", "普通用户"),
        new RealmRoleModel("manager", "管理人员"),
        new RealmRoleModel("admin", "管理员")
    ));
    model.setUsers(List.of(
        new UserModel("staff", "staff@example.com", "staff123", List.of("user")),
        new UserModel("manager", "manager@example.com", "manager123", List.of("manager", "user")),
        new UserModel("admin", "admin@example.com", "admin123", List.of("admin", "manager", "user"))
    ));
    model.setClients(List.of(
        new ClientModel("demo-frontend", "public", "前端登录和按需申请 RPT", List.of("profile", "email", "roles"), List.of("admin", "manager", "user")),
        new ClientModel("demo-api", "confidential-resource-server", "UMA Resource Server", List.of("profile", "email", "roles"), List.of())
    ));
    model.setResources(List.of(
        new UmaResourceModel("order", List.of("/api/orders/*"), List.of("view", "create", "edit", "delete", "approve", "export")),
        new UmaResourceModel("user", List.of("/api/users/*"), List.of("view", "create", "edit", "delete", "reset_pwd")),
        new UmaResourceModel("system", List.of("/api/system/*"), List.of("view", "edit"))
    ));
    model.setPolicies(List.of(
        new PolicyModel("policy-user", "role", "user", "普通用户策略"),
        new PolicyModel("policy-manager", "role", "manager", "管理人员策略"),
        new PolicyModel("policy-admin", "role", "admin", "管理员策略")
    ));
    model.setPermissions(List.of(
        new PermissionRuleModel("perm-order-view", "order", "view", "policy-user"),
        new PermissionRuleModel("perm-order-create", "order", "create", "policy-user"),
        new PermissionRuleModel("perm-order-edit", "order", "edit", "policy-user"),
        new PermissionRuleModel("perm-order-approve", "order", "approve", "policy-manager"),
        new PermissionRuleModel("perm-order-export", "order", "export", "policy-manager"),
        new PermissionRuleModel("perm-order-delete", "order", "delete", "policy-admin"),
        new PermissionRuleModel("perm-user-view", "user", "view", "policy-manager"),
        new PermissionRuleModel("perm-user-create", "user", "create", "policy-admin"),
        new PermissionRuleModel("perm-user-edit", "user", "edit", "policy-admin"),
        new PermissionRuleModel("perm-user-delete", "user", "delete", "policy-admin"),
        new PermissionRuleModel("perm-user-reset-pwd", "user", "reset_pwd", "policy-admin"),
        new PermissionRuleModel("perm-system-view", "system", "view", "policy-manager"),
        new PermissionRuleModel("perm-system-edit", "system", "edit", "policy-admin")
    ));
    model.setEndpoints(List.of(
        new SystemEndpointModel("订单列表", "GET", "/api/orders", "order#view"),
        new SystemEndpointModel("新建订单", "POST", "/api/orders", "order#create"),
        new SystemEndpointModel("审批订单", "POST", "/api/orders/approve", "order#approve"),
        new SystemEndpointModel("查看系统配置", "GET", "/api/system/config", "system#view"),
        new SystemEndpointModel("保存系统配置", "POST", "/api/system/config", "system#edit")
    ));
    return model;
  }
}
