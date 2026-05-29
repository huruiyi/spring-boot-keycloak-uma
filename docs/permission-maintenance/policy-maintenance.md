# 策略维护说明

策略决定哪些身份条件可以获得 UMA permission。当前 demo 使用 Keycloak Role Policy，通过 Realm Role 授权。

## 当前策略

| Policy | Role | 说明 |
| --- | --- | --- |
| `policy-user` | `user` | 普通业务用户 |
| `policy-manager` | `manager` | 管理人员 |
| `policy-admin` | `admin` | 管理员 |

这些策略在 `startup/setup-uma.ps1` 的 `$policies` 数组中维护。

## 新增业务角色和策略

示例：新增审计员 `auditor`，只能查看订单和用户。

1. 在 `startup/demo-realm.json` 的 `roles.realm` 中新增 Realm Role：

```json
{
  "name": "auditor",
  "description": "Auditor role"
}
```

2. 在 `startup/setup-uma.ps1` 中让 `demo-frontend` token 能带出该角色：

```powershell
Add-RealmRoleScopeMappingIfMissing -KeycloakUrl $KeycloakUrl -Realm $Realm -ClientId "demo-frontend" -RoleNames @("admin", "manager", "user", "auditor") -Token $adminToken
```

3. 在 `$policies` 中新增策略：

```powershell
@{ name = "policy-auditor"; role = "auditor" }
```

4. 在 `$permissions` 中把目标 permission 指向新策略：

```powershell
@{ name = "perm-order-view"; resource = "order"; scope = "view"; policy = "policy-auditor" }
```

如果一个 permission 需要多个策略，当前脚本需要扩展 `policies = @($permission.policy)` 的数据结构，例如允许传入 `policies = @("policy-manager", "policy-auditor")`。

## 策略命名规范

| 类型 | 命名 |
| --- | --- |
| Role Policy | `policy-<role>` |
| 订单权限 | `perm-order-<scope>` |
| 用户权限 | `perm-user-<scope>` |
| 系统权限 | `perm-system-<scope>` |

## 何时新增策略

| 场景 | 是否新增策略 |
| --- | --- |
| 只是把已有权限给已有角色 | 不需要，调整 permission 的 policy 映射 |
| 新增一类组织身份，例如审计员、客服主管 | 需要新增 Realm Role 和 Role Policy |
| 需要按部门、属性、时间判断 | 当前 Role Policy 不够，应评估 JS Policy、Group Policy 或外部授权服务 |

## 验证策略

1. 给用户分配目标 Realm Role。
2. 重新登录获取普通 access token。
3. 用 UMA token endpoint 申请目标 permission。
4. 解码 RPT，确认 `authorization.permissions` 包含目标 resource/scope。
