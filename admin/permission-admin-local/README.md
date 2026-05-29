# permission-admin-local

本模块是本地 JSON 数据版本的权限管理后台，用于编辑 `permission-model.json`。它不直接写入 Keycloak，适合先整理权限模型，再通过同步脚本导入 Keycloak。

## 启动

```powershell
.\startup\run-permission-admin-local.ps1
```

默认地址：

- 后台地址：http://localhost:9100
- 用户名：admin
- 密码：admin

## 配置

常用环境变量：

- `PERMISSION_ADMIN_LOCAL_PORT`：服务端口，默认 `9100`
- `PERMISSION_ADMIN_LOCAL_DATA_FILE`：权限模型文件路径，默认 `permission-data/permission-model.json`
- `PERMISSION_ADMIN_LOCAL_USERNAME`：登录用户名，默认 `admin`
- `PERMISSION_ADMIN_LOCAL_PASSWORD`：登录密码，默认 `admin`
- `PERMISSION_ADMIN_LOCAL_LOG_FILE`：日志文件，默认 `logs/permission-admin-local.log`

## 同步到 Keycloak

本模块只保存本地数据。需要写入 Keycloak 时，执行根目录同步脚本：

```powershell
.\startup\sync-permission-model.ps1
```

Keycloak 直连版本位于 `admin/permission-admin-keycloak`，它通过 Keycloak Admin API 直接保存到 Keycloak。
