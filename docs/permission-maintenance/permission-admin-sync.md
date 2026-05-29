# Permission Admin 同步 Keycloak

`permission-admin` 保存的是本地 JSON 权限模型，默认路径：

```text
permission-admin/data/permission-model.json
```

这个文件是运行数据，不提交到 git。修改后台页面并保存后，执行同步脚本把 JSON 中的配置推送到 Keycloak：

```powershell
.\startup\sync-permission-model.ps1
```

常用参数：

```powershell
.\startup\sync-permission-model.ps1 `
  -KeycloakUrl "http://localhost:8080" `
  -Realm "demo" `
  -AdminUser "admin" `
  -AdminPassword "admin" `
  -ResourceServerClientId "demo-api" `
  -ModelFile "permission-admin/data/permission-model.json"
```

同步范围：

- Realm Role：创建或更新 `realmRoles`。
- Client：补齐 `defaultClientScopes` 和 `realmRoleScopeMappings`。
- User：创建或更新 `users`，设置密码并补齐 Realm Role。
- UMA Scope：根据所有 resource 的 `scopes` 创建或更新。
- UMA Resource：创建或更新 `resources`。
- Role Policy：创建或更新 `policies`，当前仅支持 `type=role`。
- Scope Permission：创建或更新 `permissions`。

使用流程：

1. 启动 Keycloak。
2. 启动 `permission-admin`。
3. 在后台维护角色、用户、资源、策略、权限并保存。
4. 执行 `.\startup\sync-permission-model.ps1`。
5. 重新登录前端用户，获取新的 access token，再申请 RPT 验证权限。

注意事项：

- 脚本是 upsert 行为：存在则更新，不存在则创建。
- 脚本不会删除 Keycloak 中多余的 role、user、resource、policy 或 permission。
- 如果新增了 Realm Role，并希望前端登录 token 带出该角色，需要在后台 Client 页面把它加入 `demo-frontend` 的 `realmRoleScopeMappings` 后再同步。
- 如果修改了用户角色，前端需要重新登录，旧 token 不会自动变化。
