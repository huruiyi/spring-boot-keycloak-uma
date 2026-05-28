# Spring Boot + Vue 3 Keycloak UMA 按需权限 Demo

这个目录是当前推荐的完整示例项目，和根目录下早期 `v1_*`、`v2_*`、`v3_*` 等实验资源分开维护。

目标模型：

```text
普通登录 access token:
  只承载身份、基础 profile、少量身份角色

UMA RPT token:
  前端在进入页面或点击按钮前，按需向 Keycloak 申请当前操作需要的 resource + scope

Spring Boot API:
  只校验 RPT 中的 authorization.permissions
```

这样系统功能、按钮、接口越来越多时，不需要把所有权限都塞进普通 access token。

## 目录结构

```text
.
├─ docker-compose.yml       # Keycloak 26.1.0 容器启动
├─ startup/                 # 首次启动文档、realm JSON、启动/初始化脚本
├─ backend/                 # Spring Boot Resource Server
└─ frontend/                # Vue 3 + vue-router + keycloak-js
```

生成物不会纳入项目源码：

```text
backend/target/
frontend/node_modules/
frontend/dist/
*.log
```

## Keycloak 配置

启动 Keycloak：

在项目根目录执行：

```powershell
docker-compose up -d
```

导入或初始化 realm：

```powershell
.\startup\setup-uma.ps1
```

默认地址和账号：

```text
Keycloak: http://localhost:8080
Realm:    demo
Admin:    admin / admin
```

示例用户：

```text
admin   / admin123
manager / manager123
staff   / staff123
```

## 测试用户权限矩阵

当前三个用户满足本 demo 的测试场景：

```text
staff:
  测试普通用户，只能做基础订单操作

manager:
  测试部门负责人，可以审批订单、查看部分管理信息

admin:
  测试管理员，拥有 user + manager + admin 三组角色，覆盖全部示例权限
```

| 用户 | 密码 | Realm Role | 可申请的 UMA 权限 | 前端/接口表现 |
| --- | --- | --- | --- | --- |
| `staff` | `staff123` | `user` | `order#view`, `order#create`, `order#edit` | 可以查看订单、创建订单；不能审批订单；不能访问系统配置 |
| `manager` | `manager123` | `manager`, `user` | `order#view`, `order#create`, `order#edit`, `order#approve`, `order#export`, `user#view`, `system#view` | 可以查看/创建/审批订单；可以查看系统配置；不能保存系统配置 |
| `admin` | `admin123` | `admin`, `manager`, `user` | 全部示例权限：订单查看/创建/编辑/删除/审批/导出，用户查看/创建/编辑/删除/重置密码，系统查看/编辑 | 可以覆盖所有当前测试场景 |

说明：

```text
这里的 admin 不是只拥有 admin 单一角色，而是显式拥有 admin + manager + user。
原因是当前 UMA permission 通过 Role Policy 分组授权，admin 如果不同时拥有 manager/user，就不会自动获得 manager/user 对应的 permission。
```

## UMA 配置维护方式

`setup-uma.ps1` 不是唯一方式，它只是 demo 的一键初始化脚本。生产环境不建议每次新增按钮权限都手工跑全量脚本，推荐按场景分层维护。

### 1. 开发和本地演示

适合使用脚本：

```text
startup\setup-uma.ps1
```

用途：

```text
初始化 realm
初始化 resource / scope / policy / permission
本地重建 Keycloak 环境
CI 测试环境快速准备数据
```

脚本应该做成幂等逻辑：已存在则更新，不存在则创建。这样重复执行不会产生重复资源。

### 2. 正式环境发布

正式环境推荐把 Keycloak 配置当作版本化配置管理，而不是临时手工修改。

可选方式：

```text
Keycloak Admin REST API:
  由发布流水线调用，适合自研配置同步工具

kcadm.sh / kcadm.bat:
  Keycloak 官方命令行，适合运维脚本

Terraform Keycloak Provider:
  适合基础设施即代码，权限配置随环境一起审计

keycloak-config-cli:
  适合维护 JSON/YAML 配置并在发布时同步

Keycloak Admin Console:
  适合少量临时查看和排查，不适合作为长期唯一配置来源
```

生产建议：

```text
权限定义进入代码仓库
每次变更走评审
发布时由 CI/CD 同步到 Keycloak
同步工具必须幂等
禁止直接在生产控制台长期手工维护
```

### 3. 通过应用导入或同步

如果希望“新增系统功能时由应用自己注册权限”，可以在后端做一个权限同步模块。

推荐模式：

```text
应用启动或发布任务:
  读取本系统声明的权限清单
  对比 Keycloak 中 demo-api 的 Authorization 配置
  缺少则创建，多余则按策略忽略或标记废弃
  策略和权限映射由配置文件或后台管理流程维护
```

应用侧可以维护一个权限清单，例如：

```json
[
  {
    "resource": "order",
    "scopes": ["view", "create", "approve", "export"]
  },
  {
    "resource": "system",
    "scopes": ["view", "edit"]
  }
]
```

同步方向：

```text
业务应用 -> Keycloak:
  注册 resource 和 scope

权限管理后台 / 发布配置 -> Keycloak:
  维护 policy 和 permission
```

不建议让普通业务接口在用户请求时临时创建权限。权限变更属于管理动作，应该在发布、初始化、后台管理或运维任务中完成。

### 4. 应用导入需要的 Keycloak 能力

应用注册 UMA resource 可以使用 Keycloak Protection API。应用先用 resource server 的 service account 获取 PAT，再调用资源注册接口。

获取 service account token：

```http
POST http://localhost:8080/realms/demo/protocol/openid-connect/token
Content-Type: application/x-www-form-urlencoded

grant_type=client_credentials&
client_id=demo-api&
client_secret=<demo-api-secret>
```

创建 UMA resource：

```http
POST http://localhost:8080/realms/demo/authz/protection/resource_set
Authorization: Bearer <pat>
Content-Type: application/json

{
  "name": "invoice",
  "displayName": "Invoice",
  "scopes": [
    { "name": "view" },
    { "name": "approve" }
  ]
}
```

注意：

```text
Protection API 更适合注册 resource / scope
policy / permission 通常仍通过 Admin REST、kcadm、Terraform 或配置同步工具维护
```

原因是 policy / permission 代表组织授权规则，通常需要评审、审计和环境差异管理。

### 5. 推荐生产模型

```text
Realm Role:
  维护少量身份角色，例如 admin / manager / user

Client Role:
  只维护粗粒度应用角色，不承载大量按钮权限

UMA Resource + Scope:
  承载接口、按钮、业务操作权限

普通 access token:
  不放全量按钮权限，保持小而稳定

RPT:
  页面或按钮操作前按需申请，只包含本次操作需要的权限

权限配置来源:
  代码仓库中的权限清单 + CI/CD 同步 + Keycloak 审计
```

新增一个按钮权限时，推荐流程：

```text
1. 在前端按钮调用处声明需要的 permission，例如 invoice#approve
2. 在后端接口上校验同一个 permission
3. 在权限清单中新增 invoice resource 和 approve scope
4. 通过发布任务同步到 Keycloak
5. 通过管理后台或配置文件把 permission 绑定到对应 policy
```

## UMA 权限资源

```text
order:
  view
  create
  edit
  delete
  approve
  export

user:
  view
  create
  edit
  delete
  reset_pwd

system:
  view
  edit
```

策略示例：

```text
user:
  order:view
  order:create
  order:edit

manager:
  order:approve
  order:export
  user:view
  system:view

admin:
  order:delete
  user:create
  user:edit
  user:delete
  user:reset_pwd
  system:edit
```

实际用户权限是用户拥有的多个 Realm Role 的并集。当前测试用户中，`admin` 同时拥有 `admin`、`manager`、`user`，所以它拥有上面三组策略的并集。

## 启动后端

后端使用 Spring Boot 4.0.5、Java 21、Spring Security Resource Server。

在项目根目录执行：

```powershell
.\startup\run-backend.ps1
```

后端地址：

```text
http://localhost:9000
```

接口权限：

```text
GET  /api/orders              order#view
POST /api/orders              order#create
POST /api/orders/approve      order#approve
GET  /api/system/config       system#view
POST /api/system/config       system#edit
```

## 启动前端

前端使用 Vue 3、vue-router、Vite、keycloak-js。

```powershell
cd .\frontend
npm install

cd ..
.\startup\run-frontend.ps1
```

前端地址：

```text
http://localhost:5173
```

## 按需加载体现在哪里

前端核心方法：

```text
frontend/src/services/uma.ts
  getRpt(permissions: string[])
  apiFetch(url, permissions, init)
```

业务调用示例：

```ts
export async function listOrders() {
  return apiFetch<{ data: Order[] }>("/api/orders", ["order#view"]);
}

export async function approveOrder() {
  return apiFetch<{ approved: boolean }>("/api/orders/approve", ["order#approve"], {
    method: "POST"
  });
}
```

`listOrders` 只申请 `order#view`，`approveOrder` 只申请 `order#approve`。不同页面、按钮、操作需要什么权限，就只向 Keycloak 请求什么权限的 RPT。

如果换取 RPT 时返回：

```json
{
  "error": "access_denied",
  "error_description": "not_authorized"
}
```

先检查普通登录 access token 是否包含：

```json
{
  "realm_access": {
    "roles": ["manager", "user"]
  }
}
```

本项目的 UMA policy 使用 Role Policy。`demo-frontend` 的 access token 必须带有粗粒度 realm role，Keycloak 才能评估 `policy-user`、`policy-manager`、`policy-admin`。执行以下命令可补齐当前 realm 中已有 client 的 roles scope 和 realm role scope mapping：

```powershell
.\startup\setup-uma.ps1
```

后端校验位置：

```text
backend/src/main/java/com/example/uma/security/UmaPermissionService.java
backend/src/main/java/com/example/uma/api/OrderController.java
backend/src/main/java/com/example/uma/api/SystemController.java
```

示例：

```java
@PreAuthorize("@umaPermissionService.hasPermission(authentication.principal, 'order', 'approve')")
```

## 请求响应示例

获取普通登录 token：

```http
POST http://localhost:8080/realms/demo/protocol/openid-connect/token
Content-Type: application/x-www-form-urlencoded

grant_type=password&
client_id=demo-frontend&
username=manager&
password=manager123&
scope=openid profile email
```

按需申请订单查看 RPT：

```http
POST http://localhost:8080/realms/demo/protocol/openid-connect/token
Authorization: Bearer <access_token>
Content-Type: application/x-www-form-urlencoded

grant_type=urn:ietf:params:oauth:grant-type:uma-ticket&
audience=demo-api&
permission=order%23view
```

RPT 解码后关键内容：

```json
{
  "aud": "demo-api",
  "authorization": {
    "permissions": [
      {
        "rsname": "order",
        "scopes": ["view"]
      }
    ]
  }
}
```

调用后端：

```http
GET http://localhost:9000/api/orders
Authorization: Bearer <rpt>
```

响应：

```json
{
  "data": [
    {
      "id": 1001,
      "customer": "Acme Corp",
      "amount": 1299.00,
      "status": "PENDING"
    },
    {
      "id": 1002,
      "customer": "Globex",
      "amount": 2580.50,
      "status": "APPROVED"
    }
  ]
}
```

如果拿只包含 `order#view` 的 RPT 调审批接口：

```http
POST http://localhost:9000/api/orders/approve
Authorization: Bearer <rpt_only_has_order_view>
```

后端返回：

```http
HTTP/1.1 403 Forbidden
```

## 验证命令

```powershell
node -e "JSON.parse(require('fs').readFileSync('startup/demo-realm.json','utf8')); console.log('demo-realm.json valid')"

cd .\backend
$env:JAVA_HOME='<JDK 21 目录>'
$env:PATH="$env:JAVA_HOME\bin;$env:PATH"
mvn test

cd ..\frontend
npm install
npm run build
```

## 前端权限展示规则

当前前端已经根据 UMA 权限探测结果动态展示菜单和按钮。

| UI 元素 | 所需权限 | 有权限时 | 无权限时 |
| --- | --- | --- | --- |
| 订单菜单 | `order#view` | 显示订单菜单 | 不显示菜单 |
| 系统菜单 | `system#view` | 显示系统菜单 | 不显示菜单 |
| 订单刷新按钮 | `order#view` | 显示并允许刷新订单 | 不显示按钮，不发起订单列表请求 |
| 订单新建按钮 | `order#create` | 显示并允许创建订单 | 不显示按钮 |
| 订单审批按钮 | `order#approve` | 显示并允许审批订单 | 不显示按钮 |
| 系统保存按钮 | `system#edit` | 显示并允许保存配置 | 不显示按钮 |

实现位置：

```text
frontend/src/services/uma.ts
  getRpt(permissions)
  canRequestPermission(permission)
  loadPermissionMap(permissions)

frontend/src/App.vue
  根据 order#view / system#view 控制菜单

frontend/src/views/OrdersView.vue
  根据 order#view / order#create / order#approve 控制按钮和请求

frontend/src/views/SystemView.vue
  根据 system#view / system#edit 控制内容和保存按钮
```

前端隐藏菜单和按钮只是体验优化，不是安全边界。真正的权限边界仍然是后端接口校验 RPT 中的 `authorization.permissions`。

### 新建订单按钮调用链

`apiFetch("/api/orders", ["order#create"], ...)` 对应的是订单页的“新建”按钮。

调用链：

```text
frontend/src/views/OrdersView.vue
  新建按钮 @click="run(createOrder, '订单已创建')"

frontend/src/services/orders.ts
  createOrder()
    -> apiFetch("/api/orders", ["order#create"], { method: "POST", body })

frontend/src/services/uma.ts
  apiFetch()
    -> getRpt(["order#create"])
    -> Authorization: Bearer <RPT>
    -> fetch("http://localhost:9000/api/orders", { method: "POST" })

backend/src/main/java/com/example/uma/api/OrderController.java
  @PostMapping
  @PreAuthorize("@umaPermissionService.hasPermission(authentication.principal, 'order', 'create')")
```

也就是说：

```text
用户点击“新建”
  -> 前端先申请 order#create RPT
  -> 申请成功才调用 POST /api/orders
  -> 后端再次校验 RPT 中是否包含 order#create
```

## UMA permission 参数参考

本项目统一使用 Keycloak UMA 的 `resource#scope` 格式表达一次权限申请。

```text
order#create
```

含义是：

```text
resource = order
scope    = create
```

也就是说，`order#create` 不是前端自定义格式，而是 UMA token endpoint 支持的 permission 请求格式。HTTP 表单提交时，`#` 需要 URL 编码为 `%23`：

```text
permission=order%23create
```

### 单个权限

```http
POST http://localhost:8080/realms/demo/protocol/openid-connect/token
Authorization: Bearer <access_token>
Content-Type: application/x-www-form-urlencoded

grant_type=urn:ietf:params:oauth:grant-type:uma-ticket&
audience=demo-api&
permission=order%23create
```

### 多个权限

同一个请求可以提交多个 `permission` 参数：

```http
POST http://localhost:8080/realms/demo/protocol/openid-connect/token
Authorization: Bearer <access_token>
Content-Type: application/x-www-form-urlencoded

grant_type=urn:ietf:params:oauth:grant-type:uma-ticket&
audience=demo-api&
permission=order%23view&
permission=order%23approve&
permission=system%23view
```

RPT 解码后类似：

```json
{
  "authorization": {
    "permissions": [
      {
        "rsname": "order",
        "scopes": ["view", "approve"]
      },
      {
        "rsname": "system",
        "scopes": ["view"]
      }
    ]
  }
}
```

### 资源级请求

也可以只请求资源，不指定 scope：

```text
permission=order
```

这种方式表示请求 `order` 资源下可被授权的 scope。为了让前端、后端、Keycloak 配置更清晰，本项目不推荐在业务按钮中使用资源级请求。

### 资源加多个 scope

Keycloak UMA 也支持一个 resource 携带多个 scope：

```text
permission=order#view,approve
```

本项目前端更推荐重复传多个 `permission` 参数：

```text
permission=order#view
permission=order#approve
```

这样每个按钮或接口需要什么权限更直观，也方便缓存和排查。

### 当前 demo 使用的 permission

```text
order#view       查看订单
order#create     创建订单
order#edit       编辑订单
order#delete     删除订单
order#approve    审批订单
order#export     导出订单

user#view        查看用户
user#create      创建用户
user#edit        编辑用户
user#delete      删除用户
user#reset_pwd   重置用户密码

system#view      查看系统配置
system#edit      修改系统配置
```

## 测试与验证

以下内容原来放在 `startup/FIRST_STARTUP.md` 第 7 章，现在统一维护在 README 中。启动手册只保留启动过程。

### 获取普通登录 token

```powershell
$login = Invoke-RestMethod -Uri "http://localhost:8080/realms/demo/protocol/openid-connect/token" `
  -Method POST `
  -ContentType "application/x-www-form-urlencoded" `
  -Body @{
    grant_type = "password"
    client_id  = "demo-frontend"
    username   = "manager"
    password   = "manager123"
    scope      = "openid profile email"
  }

$accessToken = $login.access_token
```

普通 access token 不承载全量按钮权限。

### 按需申请订单查看 RPT

```powershell
$rptResp = Invoke-RestMethod -Uri "http://localhost:8080/realms/demo/protocol/openid-connect/token" `
  -Method POST `
  -ContentType "application/x-www-form-urlencoded" `
  -Headers @{ Authorization = "Bearer $accessToken" } `
  -Body @{
    grant_type = "urn:ietf:params:oauth:grant-type:uma-ticket"
    audience   = "demo-api"
    permission = "order#view"
  }

$rpt = $rptResp.access_token
```

这个 RPT 只包含当前请求需要的 `order#view` 权限。

### 使用 RPT 调后端接口

```powershell
Invoke-RestMethod -Uri "http://localhost:9000/api/orders" `
  -Headers @{ Authorization = "Bearer $rpt" }
```

预期响应：

```json
{
  "data": [
    {
      "amount": 1299.00,
      "customer": "Acme Corp",
      "id": 1001,
      "status": "PENDING"
    },
    {
      "amount": 2580.50,
      "customer": "Globex",
      "id": 1002,
      "status": "APPROVED"
    }
  ]
}
```

### 用错误 RPT 调审批接口

上面的 RPT 只有 `order#view`，不包含 `order#approve`。

```powershell
try {
  Invoke-RestMethod -Uri "http://localhost:9000/api/orders/approve" `
    -Method POST `
    -Headers @{ Authorization = "Bearer $rpt" }
} catch {
  $_.Exception.Response.StatusCode.value__
}
```

预期输出：

```text
403
```

这说明后端没有读取全量按钮权限，而是只校验 RPT 中本次按需申请到的权限。

## 常见问题

### 端口被占用

检查端口：

```powershell
Get-NetTCPConnection -LocalPort 8080,9000,5173 -ErrorAction SilentlyContinue |
  Select-Object LocalAddress,LocalPort,State,OwningProcess
```

停止指定进程：

```powershell
Stop-Process -Id <PID> -Force
```

### Keycloak realm 没有导入

如果第一次启动时 Keycloak 已经存在旧数据，`--import-realm` 可能不会覆盖已有 realm。

开发环境可以重建容器：

```powershell
docker compose down
docker compose up -d
```

如果使用了外部持久化卷，需要同时清理对应卷后再导入。

### setup-uma.ps1 认证失败

确认 Keycloak 管理员账号和密码：

```text
admin / admin
```

确认 Keycloak 已经启动：

```powershell
Invoke-WebRequest -Uri "http://localhost:8080" -UseBasicParsing
```

### UMA 换 RPT 返回 not_authorized

请求示例：

```http
POST http://localhost:8080/realms/demo/protocol/openid-connect/token
Authorization: Bearer <access_token>
Content-Type: application/x-www-form-urlencoded

grant_type=urn:ietf:params:oauth:grant-type:uma-ticket&
audience=demo-api&
permission=order%23view
```

如果响应：

```json
{
  "error": "access_denied",
  "error_description": "not_authorized"
}
```

优先检查普通登录 token 里是否有 `realm_access.roles`。

错误 token 通常类似：

```json
{
  "azp": "demo-frontend",
  "scope": "openid email profile",
  "preferred_username": "manager"
}
```

缺少：

```json
{
  "realm_access": {
    "roles": ["manager", "user"]
  }
}
```

原因：

```text
UMA Role Policy 需要根据请求方的 realm role 做策略评估。
如果 demo-frontend 的 access token 不携带 realm role，Keycloak 无法命中 policy-user / policy-manager。
```

处理方式：

```powershell
.\startup\setup-uma.ps1
```

该脚本会确保：

```text
demo-frontend 默认包含 roles client scope
demo-frontend 显式允许 admin / manager / user 三个 realm role scope mapping
```

然后重新登录获取新的 access token，再申请 RPT。

### 前端登录后跳转失败

确认 `startup/demo-realm.json` 中 `demo-frontend` 包含以下 redirect URI：

```text
http://localhost:5173/*
http://localhost:8081/*
```

当前前端默认地址是：

```text
http://localhost:5173
```

### 后端启动时 Java 版本不对

查看当前 Java：

```powershell
java -version
```

本项目要求 Java 21。修改：

```text
.\startup\run-backend.ps1
```

把 `JAVA_HOME` 改成你的 JDK 21 路径。

## 停止服务

停止前端和后端：

```powershell
Get-NetTCPConnection -LocalPort 9000,5173 -ErrorAction SilentlyContinue |
  Select-Object -ExpandProperty OwningProcess |
  Sort-Object -Unique |
  ForEach-Object { Stop-Process -Id $_ -Force }
```

停止 Keycloak：

```powershell
docker compose down
```
