# Permission Admin Keycloak

This module is a Keycloak-backed variant of `permission-admin`.

It uses the same Spring Boot + Thymeleaf management UI, but it does not persist a local JSON file. Reads and writes go directly through the Keycloak Admin REST API.

## Start

```powershell
.\startup\run-permission-admin-keycloak.ps1
```

Default URL:

```text
http://localhost:9200
```

Default local admin login:

```text
admin / admin
```

## Configuration

```text
PERMISSION_ADMIN_KEYCLOAK_PORT=9200
PERMISSION_ADMIN_KEYCLOAK_USERNAME=admin
PERMISSION_ADMIN_KEYCLOAK_PASSWORD=admin
PERMISSION_ADMIN_KEYCLOAK_LOG_FILE=logs/permission-admin-keycloak.log

KEYCLOAK_URL=http://localhost:8080
KEYCLOAK_REALM=demo
KEYCLOAK_ADMIN_USER=admin
KEYCLOAK_ADMIN_PASSWORD=admin
KEYCLOAK_RESOURCE_SERVER_CLIENT_ID=demo-api
KEYCLOAK_MANAGED_CLIENTS=demo-frontend,demo-api
KEYCLOAK_ENDPOINT_ATTRIBUTE=permissionAdmin.endpoints
```

## Persistence

The following items are stored directly in Keycloak:

- Realm roles
- Users and realm role mappings
- Managed client default scopes and realm role scope mappings
- UMA scopes
- UMA resources
- Role policies
- Scope permissions

Endpoint mappings are not a native Keycloak UMA concept. This module stores them as a JSON string in the resource server client attribute named by `KEYCLOAK_ENDPOINT_ATTRIBUTE`.
