param(
    [string]$KeycloakUrl = "http://localhost:8080",
    [string]$Realm = "demo",
    [string]$AdminUser = "admin",
    [string]$AdminPassword = "admin",
    [string]$ClientId = "demo-api",
    [string]$UiPermissionAttribute = "uma.ui-permissions",
    [string]$EndpointAttribute = "uma.endpoints"
)

$ErrorActionPreference = "Stop"

function ConvertTo-Array {
    param([object]$Value)

    if ($null -eq $Value) {
        return ,@()
    }

    return ,@($Value)
}

function Get-PermissionPolicies {
    param([object]$Permission)

    $values = ConvertTo-Array $Permission.policies
    if ($values.Count -eq 0 -and $Permission.policy) {
        $values = @($Permission.policy)
    }
    return ,$values
}

function Get-PermissionDecisionStrategy {
    param([object]$Permission)

    if ($Permission.decisionStrategy -eq "UNANIMOUS") {
        return "UNANIMOUS"
    }
    return "AFFIRMATIVE"
}

function Get-PolicyTarget {
    param([object]$Policy)

    if ($Policy.target) {
        return $Policy.target
    }
    return $Policy.role
}

function Invoke-KeycloakJson {
    param(
        [string]$Method,
        [string]$Url,
        [string]$Token,
        [object]$Body = $null
    )

    $headers = @{ Authorization = "Bearer $Token" }
    if ($null -eq $Body) {
        return Invoke-RestMethod -Method $Method -Uri $Url -Headers $headers
    }

    $json = $Body | ConvertTo-Json -Depth 20
    return Invoke-RestMethod -Method $Method -Uri $Url -Headers $headers -ContentType "application/json; charset=utf-8" -Body ([System.Text.Encoding]::UTF8.GetBytes($json))
}

function Invoke-KeycloakJsonArray {
    param(
        [string]$Method,
        [string]$Url,
        [string]$Token,
        [object[]]$Body
    )

    $headers = @{ Authorization = "Bearer $Token" }
    if ($Body.Count -eq 1) {
        $json = "[" + ($Body[0] | ConvertTo-Json -Depth 20) + "]"
    } else {
        $json = $Body | ConvertTo-Json -Depth 20
    }

    return Invoke-RestMethod -Method $Method -Uri $Url -Headers $headers -ContentType "application/json; charset=utf-8" -Body ([System.Text.Encoding]::UTF8.GetBytes($json))
}

function New-IfMissing {
    param(
        [string]$Kind,
        [string]$ListUrl,
        [string]$CreateUrl,
        [string]$Token,
        [object]$Body
    )

    $items = Invoke-KeycloakJson -Method GET -Url $ListUrl -Token $Token
    $name = $Body.name
    $exists = $items | Where-Object { $_.name -eq $name } | Select-Object -First 1
    if ($exists) {
        Write-Host "  $Kind exists: $name"
        return $exists
    }

    Write-Host "  Creating $Kind`: $name"
    return Invoke-KeycloakJson -Method POST -Url $CreateUrl -Token $Token -Body $Body
}

function Set-ClientJsonAttribute {
    param(
        [string]$KeycloakUrl,
        [string]$Realm,
        [string]$ClientId,
        [string]$AttributeName,
        [object]$Value,
        [string]$Token
    )

    $clients = Invoke-KeycloakJson -Method GET -Url "$KeycloakUrl/admin/realms/$Realm/clients?clientId=$ClientId" -Token $Token
    if (-not $clients -or $clients.Count -eq 0) {
        throw "Client not found: $ClientId"
    }

    $clientUuid = $clients[0].id
    $client = Invoke-KeycloakJson -Method GET -Url "$KeycloakUrl/admin/realms/$Realm/clients/$clientUuid" -Token $Token
    if ($null -eq $client.attributes) {
        $client | Add-Member -MemberType NoteProperty -Name attributes -Value @{} -Force
    }

    $json = $Value | ConvertTo-Json -Depth 30 -Compress
    $client.attributes | Add-Member -MemberType NoteProperty -Name $AttributeName -Value $json -Force
    Write-Host "  Publishing client attribute: $ClientId -> $AttributeName"
    Invoke-KeycloakJson -Method PUT -Url "$KeycloakUrl/admin/realms/$Realm/clients/$clientUuid" -Token $Token -Body $client | Out-Null
}

function Get-RealmRoleId {
    param(
        [string]$KeycloakUrl,
        [string]$Realm,
        [string]$RoleName,
        [string]$Token
    )

    $role = Invoke-KeycloakJson -Method GET -Url "$KeycloakUrl/admin/realms/$Realm/roles/$RoleName" -Token $Token
    if (-not $role.id) {
        throw "Realm role id not found: $RoleName"
    }
    return $role.id
}

function Get-KeycloakItemId {
    param([object]$Item)

    if ($Item.id) {
        return $Item.id
    }
    return $Item.PSObject.Properties["_id"].Value
}

function Upsert-RealmRole {
    param(
        [string]$KeycloakUrl,
        [string]$Realm,
        [object]$Role,
        [string]$Token
    )

    $roleUrl = "$KeycloakUrl/admin/realms/$Realm/roles/$($Role.name)"
    $body = @{
        name = $Role.name
        description = $Role.description
    }

    try {
        Invoke-KeycloakJson -Method GET -Url $roleUrl -Token $Token | Out-Null
        Write-Host "  Updating realm role: $($Role.name)"
        Invoke-KeycloakJson -Method PUT -Url $roleUrl -Token $Token -Body $body | Out-Null
    } catch {
        Write-Host "  Creating realm role: $($Role.name)"
        Invoke-KeycloakJson -Method POST -Url "$KeycloakUrl/admin/realms/$Realm/roles" -Token $Token -Body $body | Out-Null
    }
}

function Add-DefaultClientScopeIfMissing {
    param(
        [string]$KeycloakUrl,
        [string]$Realm,
        [string]$ClientId,
        [string]$ScopeName,
        [string]$Token
    )

    $clients = Invoke-KeycloakJson -Method GET -Url "$KeycloakUrl/admin/realms/$Realm/clients?clientId=$ClientId" -Token $Token
    if (-not $clients -or $clients.Count -eq 0) {
        throw "Client not found: $ClientId"
    }

    $clientUuid = $clients[0].id
    $defaultScopes = Invoke-KeycloakJson -Method GET -Url "$KeycloakUrl/admin/realms/$Realm/clients/$clientUuid/default-client-scopes" -Token $Token
    $exists = $defaultScopes | Where-Object { $_.name -eq $ScopeName } | Select-Object -First 1
    if ($exists) {
        Write-Host "  default client scope exists: $ClientId -> $ScopeName"
        return
    }

    $clientScopes = Invoke-KeycloakJson -Method GET -Url "$KeycloakUrl/admin/realms/$Realm/client-scopes" -Token $Token
    $scope = $clientScopes | Where-Object { $_.name -eq $ScopeName } | Select-Object -First 1
    if (-not $scope) {
        throw "Client scope not found: $ScopeName"
    }

    Write-Host "  Adding default client scope: $ClientId -> $ScopeName"
    Invoke-KeycloakJson -Method PUT -Url "$KeycloakUrl/admin/realms/$Realm/clients/$clientUuid/default-client-scopes/$($scope.id)" -Token $Token | Out-Null
}

function Add-RealmRoleScopeMappingIfMissing {
    param(
        [string]$KeycloakUrl,
        [string]$Realm,
        [string]$ClientId,
        [string[]]$RoleNames,
        [string]$Token
    )

    $clients = Invoke-KeycloakJson -Method GET -Url "$KeycloakUrl/admin/realms/$Realm/clients?clientId=$ClientId" -Token $Token
    if (-not $clients -or $clients.Count -eq 0) {
        throw "Client not found: $ClientId"
    }

    $clientUuid = $clients[0].id
    $mappedRoles = Invoke-KeycloakJson -Method GET -Url "$KeycloakUrl/admin/realms/$Realm/clients/$clientUuid/scope-mappings/realm" -Token $Token
    $missingRoles = @()
    foreach ($roleName in $RoleNames) {
        $exists = $mappedRoles | Where-Object { $_.name -eq $roleName } | Select-Object -First 1
        if ($exists) {
            Write-Host "  realm role scope mapping exists: $ClientId -> $roleName"
            continue
        }

        $role = Invoke-KeycloakJson -Method GET -Url "$KeycloakUrl/admin/realms/$Realm/roles/$roleName" -Token $Token
        $missingRoles += $role
    }

    if ($missingRoles.Count -eq 0) {
        return
    }

    $names = ($missingRoles | ForEach-Object { $_.name }) -join ", "
    Write-Host "  Adding realm role scope mappings: $ClientId -> $names"
    Invoke-KeycloakJsonArray -Method POST -Url "$KeycloakUrl/admin/realms/$Realm/clients/$clientUuid/scope-mappings/realm" -Token $Token -Body $missingRoles | Out-Null
}

function Add-UserRealmRoleIfMissing {
    param(
        [string]$KeycloakUrl,
        [string]$Realm,
        [string]$Username,
        [string[]]$RoleNames,
        [string]$Token
    )

    $users = Invoke-KeycloakJson -Method GET -Url "$KeycloakUrl/admin/realms/$Realm/users?username=$Username&exact=true" -Token $Token
    if (-not $users -or $users.Count -eq 0) {
        throw "User not found: $Username"
    }

    $userId = $users[0].id
    $mappedRoles = Invoke-KeycloakJson -Method GET -Url "$KeycloakUrl/admin/realms/$Realm/users/$userId/role-mappings/realm" -Token $Token
    $missingRoles = @()
    foreach ($roleName in $RoleNames) {
        $exists = $mappedRoles | Where-Object { $_.name -eq $roleName } | Select-Object -First 1
        if ($exists) {
            Write-Host "  user realm role exists: $Username -> $roleName"
            continue
        }

        $role = Invoke-KeycloakJson -Method GET -Url "$KeycloakUrl/admin/realms/$Realm/roles/$roleName" -Token $Token
        $missingRoles += $role
    }

    if ($missingRoles.Count -eq 0) {
        return
    }

    $names = ($missingRoles | ForEach-Object { $_.name }) -join ", "
    Write-Host "  Adding user realm roles: $Username -> $names"
    Invoke-KeycloakJsonArray -Method POST -Url "$KeycloakUrl/admin/realms/$Realm/users/$userId/role-mappings/realm" -Token $Token -Body $missingRoles | Out-Null
}

function Upsert-User {
    param(
        [string]$KeycloakUrl,
        [string]$Realm,
        [object]$User,
        [string]$Token
    )

    $users = Invoke-KeycloakJson -Method GET -Url "$KeycloakUrl/admin/realms/$Realm/users?username=$($User.username)&exact=true" -Token $Token
    if ($users -and $users.Count -gt 0) {
        $userId = $users[0].id
        Write-Host "  Updating user: $($User.username)"
        Invoke-KeycloakJson -Method PUT -Url "$KeycloakUrl/admin/realms/$Realm/users/$userId" -Token $Token -Body @{
            username = $User.username
            email = $User.email
            enabled = $true
            emailVerified = $true
        } | Out-Null
    } else {
        Write-Host "  Creating user: $($User.username)"
        Invoke-KeycloakJson -Method POST -Url "$KeycloakUrl/admin/realms/$Realm/users" -Token $Token -Body @{
            username = $User.username
            email = $User.email
            enabled = $true
            emailVerified = $true
        } | Out-Null
        $users = Invoke-KeycloakJson -Method GET -Url "$KeycloakUrl/admin/realms/$Realm/users?username=$($User.username)&exact=true" -Token $Token
        $userId = $users[0].id
    }

    if ($User.password) {
        Invoke-KeycloakJson -Method PUT -Url "$KeycloakUrl/admin/realms/$Realm/users/$userId/reset-password" -Token $Token -Body @{
            type = "password"
            value = $User.password
            temporary = $false
        } | Out-Null
    }
}

$realmRoles = @(
    @{ name = "admin"; description = "系统管理员" },
    @{ name = "manager"; description = "业务管理员" },
    @{ name = "user"; description = "普通用户" },
    @{ name = "auditor"; description = "审计人员" }
)
$userRoleMappings = @(
    @{ username = "admin"; email = "admin@example.com"; password = "admin123"; roles = @("admin", "manager", "user") },
    @{ username = "manager"; email = "manager@example.com"; password = "manager123"; roles = @("manager", "user") },
    @{ username = "staff"; email = "staff@example.com"; password = "staff123"; roles = @("user") },
    @{ username = "auditor"; email = "auditor@example.com"; password = "auditor123"; roles = @("auditor", "user") }
)
$scopes = @("view", "create", "edit", "delete", "approve", "export", "reset_pwd")
$resources = @(
    @{
        name = "order"
        uris = @("/api/orders/*")
        scopes = @(
            @{ name = "view" },
            @{ name = "create" },
            @{ name = "edit" },
            @{ name = "delete" },
            @{ name = "approve" },
            @{ name = "export" }
        )
    },
    @{
        name = "user"
        uris = @("/api/users/*")
        scopes = @(
            @{ name = "view" },
            @{ name = "create" },
            @{ name = "edit" },
            @{ name = "delete" },
            @{ name = "reset_pwd" }
        )
    },
    @{
        name = "system"
        uris = @("/api/system/*")
        scopes = @(
            @{ name = "view" },
            @{ name = "edit" }
        )
    }
)
$policies = @(
    @{ name = "policy-user"; type = "role"; role = "user"; description = "普通用户策略" },
    @{ name = "policy-manager"; type = "role"; role = "manager"; description = "业务管理员策略" },
    @{ name = "policy-admin"; type = "role"; role = "admin"; description = "系统管理员策略" },
    @{ name = "policy-auditor"; type = "role"; role = "auditor"; description = "审计人员策略" }
)
$permissions = @(
    @{ name = "perm-order-view"; resource = "order"; scope = "view"; policies = @("policy-user"); decisionStrategy = "AFFIRMATIVE" },
    @{ name = "perm-order-create"; resource = "order"; scope = "create"; policies = @("policy-user"); decisionStrategy = "AFFIRMATIVE" },
    @{ name = "perm-order-edit"; resource = "order"; scope = "edit"; policies = @("policy-user"); decisionStrategy = "AFFIRMATIVE" },
    @{ name = "perm-order-approve"; resource = "order"; scope = "approve"; policies = @("policy-manager"); decisionStrategy = "AFFIRMATIVE" },
    @{ name = "perm-order-export"; resource = "order"; scope = "export"; policies = @("policy-manager", "policy-auditor"); decisionStrategy = "AFFIRMATIVE" },
    @{ name = "perm-order-delete"; resource = "order"; scope = "delete"; policies = @("policy-admin"); decisionStrategy = "AFFIRMATIVE" },
    @{ name = "perm-user-view"; resource = "user"; scope = "view"; policies = @("policy-manager"); decisionStrategy = "AFFIRMATIVE" },
    @{ name = "perm-user-create"; resource = "user"; scope = "create"; policies = @("policy-admin"); decisionStrategy = "AFFIRMATIVE" },
    @{ name = "perm-user-edit"; resource = "user"; scope = "edit"; policies = @("policy-admin"); decisionStrategy = "AFFIRMATIVE" },
    @{ name = "perm-user-delete"; resource = "user"; scope = "delete"; policies = @("policy-admin"); decisionStrategy = "AFFIRMATIVE" },
    @{ name = "perm-user-reset-pwd"; resource = "user"; scope = "reset_pwd"; policies = @("policy-admin"); decisionStrategy = "AFFIRMATIVE" },
    @{ name = "perm-system-view"; resource = "system"; scope = "view"; policies = @("policy-manager"); decisionStrategy = "AFFIRMATIVE" },
    @{ name = "perm-system-edit"; resource = "system"; scope = "edit"; policies = @("policy-admin"); decisionStrategy = "AFFIRMATIVE" }
)
$endpoints = @(
    @{ name = "订单列表"; method = "GET"; path = "/api/orders"; permission = "order#view" },
    @{ name = "新建订单"; method = "POST"; path = "/api/orders"; permission = "order#create" },
    @{ name = "编辑订单"; method = "PUT"; path = "/api/orders"; permission = "order#edit" },
    @{ name = "审批订单"; method = "POST"; path = "/api/orders/approve"; permission = "order#approve" },
    @{ name = "导出订单"; method = "GET"; path = "/api/orders/export"; permission = "order#export" },
    @{ name = "删除订单"; method = "DELETE"; path = "/api/orders"; permission = "order#delete" },
    @{ name = "用户列表"; method = "GET"; path = "/api/users"; permission = "user#view" },
    @{ name = "新建用户"; method = "POST"; path = "/api/users"; permission = "user#create" },
    @{ name = "编辑用户"; method = "PUT"; path = "/api/users"; permission = "user#edit" },
    @{ name = "删除用户"; method = "DELETE"; path = "/api/users"; permission = "user#delete" },
    @{ name = "重置用户密码"; method = "POST"; path = "/api/users/reset-pwd"; permission = "user#reset_pwd" },
    @{ name = "查看系统配置"; method = "GET"; path = "/api/system/config"; permission = "system#view" },
    @{ name = "保存系统配置"; method = "POST"; path = "/api/system/config"; permission = "system#edit" }
)
$uiPermissions = @(
    @{ code = "menu.orders"; name = "订单管理菜单"; type = "menu"; page = "orders"; permission = "order#view"; sort = 10; enabled = $true },
    @{ code = "button.orders.refresh"; name = "刷新订单按钮"; type = "button"; page = "orders"; permission = "order#view"; sort = 20; enabled = $true },
    @{ code = "button.orders.create"; name = "新建订单按钮"; type = "button"; page = "orders"; permission = "order#create"; sort = 30; enabled = $true },
    @{ code = "button.orders.approve"; name = "审批订单按钮"; type = "button"; page = "orders"; permission = "order#approve"; sort = 40; enabled = $true },
    @{ code = "menu.system"; name = "系统配置菜单"; type = "menu"; page = "system"; permission = "system#view"; sort = 50; enabled = $true },
    @{ code = "button.system.save"; name = "保存系统配置按钮"; type = "button"; page = "system"; permission = "system#edit"; sort = 60; enabled = $true }
)

Write-Host "[1/8] Get admin token"
$adminTokenResponse = Invoke-RestMethod -Method POST `
    -Uri "$KeycloakUrl/realms/master/protocol/openid-connect/token" `
    -ContentType "application/x-www-form-urlencoded" `
    -Body @{
        grant_type = "password"
        client_id = "admin-cli"
        username = $AdminUser
        password = $AdminPassword
    }
$adminToken = $adminTokenResponse.access_token

Write-Host "[2/8] Ensure role claims are included in frontend token"
foreach ($role in $realmRoles) {
    Upsert-RealmRole -KeycloakUrl $KeycloakUrl -Realm $Realm -Role $role -Token $adminToken
}
$realmRoleNames = $realmRoles | ForEach-Object { $_.name }
Add-DefaultClientScopeIfMissing -KeycloakUrl $KeycloakUrl -Realm $Realm -ClientId "demo-frontend" -ScopeName "roles" -Token $adminToken
Add-RealmRoleScopeMappingIfMissing -KeycloakUrl $KeycloakUrl -Realm $Realm -ClientId "demo-frontend" -RoleNames $realmRoleNames -Token $adminToken
foreach ($mapping in $userRoleMappings) {
    Upsert-User -KeycloakUrl $KeycloakUrl -Realm $Realm -User $mapping -Token $adminToken
    Add-UserRealmRoleIfMissing -KeycloakUrl $KeycloakUrl -Realm $Realm -Username $mapping.username -RoleNames $mapping.roles -Token $adminToken
}

Write-Host "[3/8] Find resource server client: $ClientId"
$clients = Invoke-KeycloakJson -Method GET -Url "$KeycloakUrl/admin/realms/$Realm/clients?clientId=$ClientId" -Token $adminToken
if (-not $clients -or $clients.Count -eq 0) {
    throw "Client not found: $ClientId"
}
$clientUuid = $clients[0].id
$authzBase = "$KeycloakUrl/admin/realms/$Realm/clients/$clientUuid/authz/resource-server"

Write-Host "[4/8] Create scopes"
foreach ($scope in $scopes) {
    New-IfMissing -Kind "scope" -ListUrl "$authzBase/scope" -CreateUrl "$authzBase/scope" -Token $adminToken -Body @{ name = $scope } | Out-Null
}

Write-Host "[5/8] Create resources"
foreach ($resource in $resources) {
    New-IfMissing -Kind "resource" -ListUrl "$authzBase/resource" -CreateUrl "$authzBase/resource" -Token $adminToken -Body $resource | Out-Null
}

Write-Host "[6/8] Create role policies"
foreach ($policy in $policies) {
    $roleName = Get-PolicyTarget $policy
    $roleId = Get-RealmRoleId -KeycloakUrl $KeycloakUrl -Realm $Realm -RoleName $roleName -Token $adminToken
    $policyBody = @{
        name = $policy.name
        type = "role"
        logic = "POSITIVE"
        decisionStrategy = "UNANIMOUS"
        roles = @(@{ id = $roleId; required = $true })
        description = $policy.description
    }
    $existingPolicies = Invoke-KeycloakJson -Method GET -Url "$authzBase/policy" -Token $adminToken
    $existingPolicy = $existingPolicies | Where-Object { $_.name -eq $policy.name } | Select-Object -First 1
    if ($existingPolicy) {
        $policyId = Get-KeycloakItemId $existingPolicy
        $policyBody.Remove("roles")
        $policyBody.id = $policyId
        $policyBody.config = @{
            roles = "[" + (@{ id = $roleId; required = $true } | ConvertTo-Json -Depth 10 -Compress) + "]"
        }
        Write-Host "  Updating role policy: $($policy.name)"
        Invoke-KeycloakJson -Method PUT -Url "$authzBase/policy/$policyId" -Token $adminToken -Body $policyBody | Out-Null
    } else {
        Write-Host "  Creating role policy: $($policy.name)"
        Invoke-KeycloakJson -Method POST -Url "$authzBase/policy/role" -Token $adminToken -Body $policyBody | Out-Null
    }
}

Write-Host "[7/8] Create scope permissions"
foreach ($permission in $permissions) {
    $existingPermissions = Invoke-KeycloakJson -Method GET -Url "$authzBase/permission" -Token $adminToken
    $existingPermission = $existingPermissions | Where-Object { $_.name -eq $permission.name } | Select-Object -First 1
    if ($existingPermission) {
        $permissionId = Get-KeycloakItemId $existingPermission
        Write-Host "  Replacing scope permission: $($permission.name)"
        Invoke-KeycloakJson -Method DELETE -Url "$authzBase/permission/$permissionId" -Token $adminToken | Out-Null
    } else {
        Write-Host "  Creating scope permission: $($permission.name)"
    }

    Invoke-KeycloakJson `
        -Method POST `
        -Url "$authzBase/permission/scope" `
        -Token $adminToken `
        -Body @{
            name = $permission.name
            type = "scope"
            logic = "POSITIVE"
            decisionStrategy = Get-PermissionDecisionStrategy $permission
            resources = @($permission.resource)
            scopes = @($permission.scope)
            policies = Get-PermissionPolicies $permission
        } | Out-Null
}

Write-Host "[8/8] Publish endpoint and UI permission catalogs"
Set-ClientJsonAttribute -KeycloakUrl $KeycloakUrl -Realm $Realm -ClientId $ClientId -AttributeName $EndpointAttribute -Value $endpoints -Token $adminToken
Set-ClientJsonAttribute -KeycloakUrl $KeycloakUrl -Realm $Realm -ClientId $ClientId -AttributeName $UiPermissionAttribute -Value $uiPermissions -Token $adminToken

Write-Host "UMA setup completed."
