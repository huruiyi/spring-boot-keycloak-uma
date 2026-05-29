# Keycloak UMA 权限模型速查

本文档整理当前 demo 中用户、Realm Role、Client Scope、UMA Resource、Scope、Policy、Permission 的对应关系。

配置来源：

- `startup/demo-realm.json`
- `startup/setup-uma.ps1`

如果需要新增用户、Realm、策略、UMA 资源或系统权限，先参考 [docs/permission-maintenance/README.md](./docs/permission-maintenance/README.md) 中按维度拆分的维护说明。

## 用户角色总览

| 用户 | 密码 | Realm Role | Client Role | `demo-frontend` 默认 Client Scope | 可评估策略 |
| --- | --- | --- | --- | --- | --- |
| `staff` | `staff123` | `user` | 无 | `profile`, `email`, `roles` | `policy-user` |
| `manager` | `manager123` | `manager`, `user` | 无 | `profile`, `email`, `roles` | `policy-user`, `policy-manager` |
| `admin` | `admin123` | `admin`, `manager`, `user` | 无 | `profile`, `email`, `roles` | `policy-user`, `policy-manager`, `policy-admin` |

当前 demo 没有配置 Client Role。权限判断依赖 Realm Role，通过 Keycloak UMA Role Policy 映射到 resource/scope permission。

## Realm Role 到 UMA 权限

| Realm Role | Policy | 可获得 UMA 权限 |
| --- | --- | --- |
| `user` | `policy-user` | `order#view`, `order#create`, `order#edit` |
| `manager` | `policy-manager` | `order#approve`, `order#export`, `user#view`, `system#view` |
| `admin` | `policy-admin` | `order#delete`, `user#create`, `user#edit`, `user#delete`, `user#reset_pwd`, `system#edit` |

用户最终权限是多个 Realm Role 的并集。例如 `admin` 同时拥有 `admin`、`manager`、`user`，所以它拥有三组策略对应权限的并集。

## 用户最终 UMA 权限

| 用户 | 最终可申请 UMA 权限 |
| --- | --- |
| `staff` | `order#view`, `order#create`, `order#edit` |
| `manager` | `order#view`, `order#create`, `order#edit`, `order#approve`, `order#export`, `user#view`, `system#view` |
| `admin` | `order#view`, `order#create`, `order#edit`, `order#approve`, `order#export`, `order#delete`, `user#view`, `user#create`, `user#edit`, `user#delete`, `user#reset_pwd`, `system#view`, `system#edit` |

## Resource / Scope / Permission 明细

| Resource | URI | Scope | Permission | Policy |
| --- | --- | --- | --- | --- |
| `order` | `/api/orders/*` | `view` | `perm-order-view` | `policy-user` |
| `order` | `/api/orders/*` | `create` | `perm-order-create` | `policy-user` |
| `order` | `/api/orders/*` | `edit` | `perm-order-edit` | `policy-user` |
| `order` | `/api/orders/*` | `approve` | `perm-order-approve` | `policy-manager` |
| `order` | `/api/orders/*` | `export` | `perm-order-export` | `policy-manager` |
| `order` | `/api/orders/*` | `delete` | `perm-order-delete` | `policy-admin` |
| `user` | `/api/users/*` | `view` | `perm-user-view` | `policy-manager` |
| `user` | `/api/users/*` | `create` | `perm-user-create` | `policy-admin` |
| `user` | `/api/users/*` | `edit` | `perm-user-edit` | `policy-admin` |
| `user` | `/api/users/*` | `delete` | `perm-user-delete` | `policy-admin` |
| `user` | `/api/users/*` | `reset_pwd` | `perm-user-reset-pwd` | `policy-admin` |
| `system` | `/api/system/*` | `view` | `perm-system-view` | `policy-manager` |
| `system` | `/api/system/*` | `edit` | `perm-system-edit` | `policy-admin` |

## 当前后端接口实际校验

| 接口 | 后端要求权限 | `staff` | `manager` | `admin` |
| --- | --- | --- | --- | --- |
| `GET /api/orders` | `order#view` | 可访问 | 可访问 | 可访问 |
| `POST /api/orders` | `order#create` | 可访问 | 可访问 | 可访问 |
| `POST /api/orders/approve` | `order#approve` | 不可访问 | 可访问 | 可访问 |
| `GET /api/system/config` | `system#view` | 不可访问 | 可访问 | 可访问 |
| `POST /api/system/config` | `system#edit` | 不可访问 | 不可访问 | 可访问 |

## Token / Scope 关系

| 概念 | 当前配置 | 作用 |
| --- | --- | --- |
| 普通 access token | `demo-frontend` 登录后获取 | 承载用户身份和 Realm Role |
| Client Scope `roles` | `demo-frontend` 默认包含 | 让 access token 带上 `realm_access.roles` |
| UMA RPT | 前端按需申请 | 承载 `authorization.permissions` |
| 后端鉴权依据 | RPT 中的 `authorization.permissions` | 不直接用普通 access token 的角色放行业务接口 |

## 关键链路

```text
用户登录 demo-frontend
  -> 普通 access token 携带 realm_access.roles
  -> 前端按操作申请 UMA RPT，例如 order#view
  -> Keycloak 根据 Realm Role 命中 Role Policy
  -> RPT 中写入 authorization.permissions
  -> 前端用 RPT 调 Spring Boot API
  -> 后端只校验 RPT 中是否包含接口要求的 resource/scope
```
