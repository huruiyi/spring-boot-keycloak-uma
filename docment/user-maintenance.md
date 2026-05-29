# 用户维护说明

用户维护包括创建用户、设置密码、分配 Realm Role，以及确认普通 access token 中能带出角色。

## 当前测试用户

| 用户 | 密码 | Realm Role | 用途 |
| --- | --- | --- | --- |
| `staff` | `staff123` | `user` | 普通用户，只能执行基础订单操作 |
| `manager` | `manager123` | `manager`, `user` | 管理人员，可审批订单和查看系统配置 |
| `admin` | `admin123` | `admin`, `manager`, `user` | 管理员，覆盖当前 demo 全部权限 |

## 新建用户

本地 demo 可以直接在 `startup/demo-realm.json` 的 `users` 数组中新增用户：

```json
{
  "username": "auditor",
  "enabled": true,
  "email": "auditor@example.com",
  "firstName": "Permission",
  "lastName": "Auditor",
  "emailVerified": true,
  "credentials": [
    {
      "type": "password",
      "value": "auditor123",
      "temporary": false
    }
  ],
  "realmRoles": [
    "manager"
  ]
}
```

如果 Realm 已经存在，`--import-realm` 不一定覆盖已有用户。此时优先使用 Keycloak Admin Console 或 Admin REST API 创建用户。

## 给已有用户补 Realm Role

当前脚本提供了 `Add-UserRealmRoleIfMissing` 辅助函数。要让脚本自动补角色，可在 `startup/setup-uma.ps1` 中增加调用：

```powershell
Add-UserRealmRoleIfMissing -KeycloakUrl $KeycloakUrl -Realm $Realm -Username "auditor" -RoleNames @("manager") -Token $adminToken
```

## 用户角色分配原则

| 目标 | 推荐做法 |
| --- | --- |
| 只允许基础业务操作 | 分配 `user` |
| 允许管理类读取和审批 | 分配 `manager`，必要时同时分配 `user` |
| 允许管理配置、删除、用户维护 | 分配 `admin`，当前 demo 中同时分配 `manager` 和 `user` |

当前 UMA Role Policy 不做角色继承。`admin` 如果只拥有 `admin`，不会自动获得 `manager` 和 `user` 对应 permission，所以当前 demo 显式给 `admin` 用户分配三种 Realm Role。

## 验证用户 token 是否携带 Realm Role

登录后解码普通 access token，确认存在：

```json
{
  "realm_access": {
    "roles": ["manager", "user"]
  }
}
```

如果没有 `realm_access.roles`，检查：

- `demo-frontend.defaultClientScopes` 是否包含 `roles`。
- `demo-frontend` 是否存在 Realm Role scope mapping。
- 是否重新登录获取了新的 access token。
