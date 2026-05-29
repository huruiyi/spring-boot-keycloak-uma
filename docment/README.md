# 权限维护文档索引

本目录按维护维度拆分 Keycloak UMA 权限相关说明。系统权限、用户、策略、Realm 和 UMA 资源都会持续变化时，优先更新对应维度文档，再同步脚本和验证结果。

## 文档清单

| 维度 | 文档 | 适用场景 |
| --- | --- | --- |
| Realm | [realm-maintenance.md](./realm-maintenance.md) | 新建 Realm、维护 Realm 基础配置、维护客户端入口 |
| 用户 | [user-maintenance.md](./user-maintenance.md) | 新建用户、修改密码、分配 Realm Role |
| 策略 | [policy-maintenance.md](./policy-maintenance.md) | 新增业务角色、调整 Role Policy、调整授权策略 |
| UMA | [uma-maintenance.md](./uma-maintenance.md) | 新增 resource/scope/permission，维护 UMA 授权资源 |
| 系统权限 | [system-permission-maintenance.md](./system-permission-maintenance.md) | 业务接口、前端按钮、后端校验和 Keycloak 权限同步变更 |

## 推荐变更顺序

1. 先定义业务权限清单：资源、动作、使用入口、可授权角色。
2. 更新 Keycloak 配置脚本：`startup/setup-uma.ps1`。
3. 更新 realm 初始导入配置：`startup/demo-realm.json`，仅放基础 Realm、Client、用户、Realm Role。
4. 更新后端权限常量和接口校验：`backend/src/main/java/com/example/uma/security/UmaPermissions.java`。
5. 更新前端权限申请和展示控制。
6. 更新 [PERMISSION_MODEL.md](../../PERMISSION_MODEL.md)。
7. 执行验证：后端测试、前端测试、构建、冒烟测试。

## 当前 demo 的边界

- 当前 demo 使用 Realm Role，不使用 Client Role。
- `demo-frontend` 是 public client，负责用户登录和按需申请 RPT。
- `demo-api` 是 resource server client，开启 Authorization Services。
- 后端业务接口只信任 RPT 中的 `authorization.permissions`，不直接按普通 access token 中的角色放行业务接口。
