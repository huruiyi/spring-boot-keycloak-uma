# Realm 维护说明

Realm 是 Keycloak 中身份、角色、客户端和授权配置的隔离边界。当前 demo 使用的 Realm 名称是 `demo`。

## 当前配置位置

| 配置 | 文件 | 说明 |
| --- | --- | --- |
| Realm 基础导入 | `startup/demo-realm.json` | 创建 `demo` Realm、基础 Realm Role、客户端、测试用户 |
| UMA 初始化 | `startup/setup-uma.ps1` | 在已有 Realm 中补齐 role scope mapping、resource、scope、policy、permission |
| Docker 启动 | `docker-compose.yml` | 通过 `--import-realm` 导入 `startup/demo-realm.json` |

## 新建 Realm

1. 复制 `startup/demo-realm.json`。
2. 修改顶层 `realm` 字段，例如从 `demo` 改成 `uat-demo`。
3. 修改客户端地址：
   - `demo-frontend.rootUrl`
   - `demo-frontend.redirectUris`
   - `demo-frontend.webOrigins`
4. 修改前端环境文件中的 `VITE_KEYCLOAK_REALM`。
5. 修改后端 `KEYCLOAK_ISSUER_URI`，格式为：

```text
http://localhost:8080/realms/<realm-name>
```

6. 运行初始化脚本时传入 Realm：

```powershell
.\startup\setup-uma.ps1 -Realm "uat-demo"
```

## Realm 基础配置维护清单

| 配置项 | 当前值 | 维护建议 |
| --- | --- | --- |
| `enabled` | `true` | 非特殊情况保持启用 |
| `sslRequired` | `external` | 本地 demo 可用；生产建议按部署模式明确 HTTPS 策略 |
| `registrationAllowed` | `false` | 当前系统不允许用户自助注册 |
| `loginWithEmailAllowed` | `true` | 允许邮箱登录 |
| `resetPasswordAllowed` | `true` | 允许重置密码 |

## 客户端维护

| Client | 类型 | 作用 | 关键配置 |
| --- | --- | --- | --- |
| `demo-frontend` | Public Client | 前端登录、获取普通 access token、按需申请 RPT | `standardFlowEnabled=true`, `directAccessGrantsEnabled=true`, `defaultClientScopes` 包含 `roles` |
| `demo-api` | Confidential Client / Resource Server | UMA resource server，承载 Authorization Services | `authorizationServicesEnabled=true`, `serviceAccountsEnabled=true` |

## 验证方式

```powershell
Invoke-WebRequest -Uri "http://localhost:8080/realms/demo/.well-known/openid-configuration" -UseBasicParsing
```

后端启动时也会通过 `issuer-uri` 读取 Realm 的 OIDC 配置。Realm 名称或地址错误时，后端资源服务器 JWT 校验会失败。
