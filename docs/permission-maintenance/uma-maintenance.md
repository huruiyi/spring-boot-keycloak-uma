# UMA 资源维护说明

UMA 资源维护包括 resource、scope、permission 三层。当前 demo 的 UMA 授权配置由 `startup/setup-uma.ps1` 创建。

## 当前资源模型

| Resource | URI | Scopes |
| --- | --- | --- |
| `order` | `/api/orders/*` | `view`, `create`, `edit`, `delete`, `approve`, `export` |
| `user` | `/api/users/*` | `view`, `create`, `edit`, `delete`, `reset_pwd` |
| `system` | `/api/system/*` | `view`, `edit` |

## 新增 Scope

示例：给订单新增 `cancel` 操作。

1. 在 `$scopes` 中加入：

```powershell
"cancel"
```

2. 在 `order` resource 的 `scopes` 中加入：

```powershell
@{ name = "cancel" }
```

3. 在 `$permissions` 中加入 scope permission：

```powershell
@{ name = "perm-order-cancel"; resource = "order"; scope = "cancel"; policy = "policy-manager" }
```

4. 后端新增权限常量和接口校验：

```java
public static final String ORDER_CANCEL_SCOPE = "cancel";
```

5. 前端调用时申请：

```typescript
apiFetch("/api/orders/cancel", ["order#cancel"], { method: "POST" })
```

## 新增 Resource

示例：新增报表资源 `report`。

1. 在 `$scopes` 中加入需要的动作，例如 `view`, `export` 已存在可复用。
2. 在 `$resources` 中新增：

```powershell
@{
    name = "report"
    uris = @("/api/reports/*")
    scopes = @(
        @{ name = "view" },
        @{ name = "export" }
    )
}
```

3. 在 `$permissions` 中新增：

```powershell
@{ name = "perm-report-view"; resource = "report"; scope = "view"; policy = "policy-manager" },
@{ name = "perm-report-export"; resource = "report"; scope = "export"; policy = "policy-admin" }
```

4. 同步后端、前端和权限模型文档。

## Permission 请求格式

前端统一使用：

```text
resource#scope
```

示例：

```text
order#view
system#edit
```

HTTP 表单中 `#` 会编码为 `%23`。Keycloak token endpoint 接收到的是 `permission=order%23view`。

## 维护检查表

| 检查项 | 说明 |
| --- | --- |
| `$scopes` 是否包含新 scope | Keycloak 中 scope 必须先存在 |
| `$resources` 是否把 scope 挂到 resource | resource 不包含 scope 时无法授权该 resource/scope |
| `$permissions` 是否把 resource/scope 绑定到 policy | 没有 permission 就无法申请到 RPT |
| 后端是否校验新权限 | 前端隐藏按钮不是安全边界 |
| 前端是否按需申请精确 permission | 避免申请过大权限 |
| `PERMISSION_MODEL.md` 是否同步 | 方便查阅和审计 |
