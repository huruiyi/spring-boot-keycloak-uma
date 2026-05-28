# 首次启动说明

本文档记录本项目的首次启动过程，适用于 Windows PowerShell。以下命令默认从项目根目录执行。

项目组成：

```text
Keycloak 26.1          http://localhost:8080
Spring Boot Backend    http://localhost:9000
Vue 3 Frontend         http://localhost:5173
```

## 1. 前置环境检查

打开 PowerShell，执行：

```powershell
docker --version
docker compose version
java -version
mvn -version
node -v
npm -v
```

本项目后端启动脚本会设置 `JAVA_HOME`。如果你的 JDK 21 目录不同，需要修改：

```text
.\startup\run-backend.ps1
```

确认项目目录：

```powershell
cd <项目根目录>
dir
```

应能看到：

```text
docker-compose.yml
startup\demo-realm.json
startup\setup-uma.ps1
backend
frontend
```

## 2. 启动 Keycloak

进入项目根目录：

```powershell
cd <项目根目录>
```

启动 Keycloak：

```powershell
docker compose up -d
```

查看容器状态：

```powershell
docker ps --filter "name=keycloak-uma-demo"
```

查看 Keycloak 日志：

```powershell
docker logs -f keycloak-uma-demo
```

看到类似内容表示启动完成：

```text
Keycloak started
Listening on: http://0.0.0.0:8080
```

浏览器打开：

```text
http://localhost:8080
```

管理账号：

```text
admin / admin
```

## 3. 初始化 UMA 配置

`docker-compose.yml` 会通过 `startup/demo-realm.json` 导入基础 realm、client、用户和角色。

UMA 的 resource、scope、policy、permission 由脚本初始化：

```powershell
cd <项目根目录>
.\startup\setup-uma.ps1
```

默认参数：

```text
KeycloakUrl:   http://localhost:8080
Realm:         demo
AdminUser:     admin
AdminPassword: admin
ClientId:      demo-api
```

如果要显式传参：

```powershell
.\startup\setup-uma.ps1 `
  -KeycloakUrl "http://localhost:8080" `
  -Realm "demo" `
  -AdminUser "admin" `
  -AdminPassword "admin" `
  -ClientId "demo-api"
```

执行成功后，脚本会创建或确认以下 UMA 配置：

```text
scopes:
  view
  create
  edit
  delete
  approve
  export
  reset_pwd

resources:
  order
  user
  system

policies:
  policy-user
  policy-manager
  policy-admin

permissions:
  order-view
  order-create
  order-edit
  order-delete
  order-approve
  order-export
  user-view
  user-create
  user-edit
  user-delete
  user-reset-pwd
  system-view
  system-edit
```

## 4. 安装并构建前端依赖

首次启动前端需要安装依赖：

```powershell
cd .\frontend
npm install
```

可选：先做一次生产构建验证：

```powershell
npm run build
```

成功时会看到类似：

```text
✓ built
```

## 5. 启动后端

新开一个 PowerShell 窗口：

```powershell
cd <项目根目录>
.\startup\run-backend.ps1
```

脚本内容会设置 Java 21：

```powershell
$env:JAVA_HOME = '<JDK 21 目录>'
$env:PATH = "$env:JAVA_HOME\bin;$env:PATH"
mvn spring-boot:run
```

看到类似内容表示后端启动成功：

```text
Tomcat started on port 9000
Started UmaBackendApplication
```

验证后端端口：

```powershell
Get-NetTCPConnection -LocalPort 9000
```

未带 token 访问业务接口应返回 `401`：

```powershell
try {
  Invoke-WebRequest -Uri "http://localhost:9000/api/orders" -UseBasicParsing
} catch {
  $_.Exception.Response.StatusCode.value__
}
```

预期输出：

```text
401
```

## 6. 启动前端

再新开一个 PowerShell 窗口：

```powershell
cd <项目根目录>
.\startup\run-frontend.ps1
```

看到类似内容表示前端启动成功：

```text
VITE ready
Local: http://localhost:5173/
```

浏览器打开：

```text
http://localhost:5173
```

登录示例用户：

```text
admin   / admin123
manager / manager123
staff   / staff123
```

首次启动到这里结束。权限矩阵、UMA 验证、permission 参数说明、常见问题和停止服务命令统一维护在项目根目录的 `README.md`。
