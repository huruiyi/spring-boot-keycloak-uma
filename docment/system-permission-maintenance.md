# 系统权限变更说明

系统权限是业务系统中的接口、菜单、按钮、操作和 UMA permission 的对应关系。每次新增或调整系统功能，都应该同步维护 Keycloak、后端、前端和文档。

## 当前后端权限点

| 功能 | 接口 | 后端权限 |
| --- | --- | --- |
| 订单列表 | `GET /api/orders` | `order#view` |
| 新建订单 | `POST /api/orders` | `order#create` |
| 审批订单 | `POST /api/orders/approve` | `order#approve` |
| 查看系统配置 | `GET /api/system/config` | `system#view` |
| 保存系统配置 | `POST /api/system/config` | `system#edit` |

## 新增系统权限的完整流程

示例：新增订单取消功能 `order#cancel`。

### 1. 定义权限

先明确四件事：

| 项 | 示例 |
| --- | --- |
| Resource | `order` |
| Scope | `cancel` |
| Permission | `perm-order-cancel` |
| 授权策略 | `policy-manager` |

### 2. 更新 Keycloak UMA 初始化脚本

修改 `startup/setup-uma.ps1`：

- `$scopes` 加入 `cancel`。
- `order` resource 加入 `cancel` scope。
- `$permissions` 加入 `perm-order-cancel`。

### 3. 更新后端权限常量

修改 `backend/src/main/java/com/example/uma/security/UmaPermissions.java`：

```java
public static final String ORDER_CANCEL_SCOPE = "cancel";
public static final String HAS_ORDER_CANCEL =
    "@umaPermissionService.hasPermission(authentication.principal, T(com.example.uma.security.UmaPermissions).ORDER_RESOURCE, T(com.example.uma.security.UmaPermissions).ORDER_CANCEL_SCOPE)";
```

### 4. 更新后端接口

在 Controller 上使用：

```java
@PreAuthorize(UmaPermissions.HAS_ORDER_CANCEL)
```

后端必须校验 RPT 中的 `authorization.permissions`。不能只依赖前端按钮隐藏。

### 5. 更新前端权限申请

在 service 中按需申请精确权限：

```typescript
apiFetch("/api/orders/cancel", ["order#cancel"], { method: "POST" })
```

如果是菜单或按钮展示，还要更新页面的 `loadPermissionMap` 权限清单。

### 6. 更新测试

| 测试 | 建议 |
| --- | --- |
| 后端安全测试 | 有权限返回成功，无权限返回 403 |
| 前端服务测试 | 确认调用时申请 `order#cancel` |
| 冒烟测试 | 只在关键链路需要时加入 |

### 7. 更新文档

同步更新：

- `PERMISSION_MODEL.md`
- `docment/uma-maintenance.md`
- 本文档中的接口权限表

## 调整已有权限归属

示例：把 `system#view` 从 `manager` 调整为 `user`。

1. 修改 `startup/setup-uma.ps1` 中对应 permission 的 `policy`：

```powershell
@{ name = "perm-system-view"; resource = "system"; scope = "view"; policy = "policy-user" }
```

2. 重新执行初始化脚本。
3. 重新登录用户，获取新的 access token。
4. 重新申请 RPT。
5. 验证 staff 是否可以访问 `GET /api/system/config`。

注意：当前 `New-IfMissing` 只负责创建缺失项，不负责更新已存在 permission。如果要支持“已存在则更新”，需要扩展脚本为 upsert 逻辑，或者在 Keycloak Admin Console 中手工调整。

## 删除系统权限

删除权限前先确认：

- 后端接口是否已经删除或不再使用该 permission。
- 前端是否不再申请该 permission。
- Keycloak 中 permission、resource scope 是否仍被其他功能复用。
- 文档和测试是否同步删除。

生产环境不建议直接删除，优先先下线前端入口和后端接口，再清理 Keycloak 授权配置。
