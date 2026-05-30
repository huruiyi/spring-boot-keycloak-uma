param(
    [string]$KeycloakUrl = "http://localhost:8080",
    [string]$Realm = "demo",
    [string]$AdminUser = "admin",
    [string]$AdminPassword = "admin",
    [string]$ClientId = "demo-api"
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
    return Invoke-RestMethod -Method $Method -Uri $Url -Headers $headers -ContentType "application/json" -Body $json
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

    return Invoke-RestMethod -Method $Method -Uri $Url -Headers $headers -ContentType "application/json" -Body $json
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

Write-Host "[1/7] Get admin token"
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

Write-Host "[2/7] Ensure role claims are included in frontend token"
Add-DefaultClientScopeIfMissing -KeycloakUrl $KeycloakUrl -Realm $Realm -ClientId "demo-frontend" -ScopeName "roles" -Token $adminToken
Add-RealmRoleScopeMappingIfMissing -KeycloakUrl $KeycloakUrl -Realm $Realm -ClientId "demo-frontend" -RoleNames @("admin", "manager", "user") -Token $adminToken
Add-UserRealmRoleIfMissing -KeycloakUrl $KeycloakUrl -Realm $Realm -Username "admin" -RoleNames @("admin", "manager", "user") -Token $adminToken
Add-UserRealmRoleIfMissing -KeycloakUrl $KeycloakUrl -Realm $Realm -Username "manager" -RoleNames @("manager", "user") -Token $adminToken
Add-UserRealmRoleIfMissing -KeycloakUrl $KeycloakUrl -Realm $Realm -Username "staff" -RoleNames @("user") -Token $adminToken

Write-Host "[3/7] Find resource server client: $ClientId"
$clients = Invoke-KeycloakJson -Method GET -Url "$KeycloakUrl/admin/realms/$Realm/clients?clientId=$ClientId" -Token $adminToken
if (-not $clients -or $clients.Count -eq 0) {
    throw "Client not found: $ClientId"
}
$clientUuid = $clients[0].id
$authzBase = "$KeycloakUrl/admin/realms/$Realm/clients/$clientUuid/authz/resource-server"

Write-Host "[4/7] Create scopes"
$scopes = @("view", "create", "edit", "delete", "approve", "export", "reset_pwd")
foreach ($scope in $scopes) {
    New-IfMissing -Kind "scope" -ListUrl "$authzBase/scope" -CreateUrl "$authzBase/scope" -Token $adminToken -Body @{ name = $scope } | Out-Null
}

Write-Host "[5/7] Create resources"
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
foreach ($resource in $resources) {
    New-IfMissing -Kind "resource" -ListUrl "$authzBase/resource" -CreateUrl "$authzBase/resource" -Token $adminToken -Body $resource | Out-Null
}

Write-Host "[6/7] Create role policies"
$policies = @(
    @{ name = "policy-user"; role = "user" },
    @{ name = "policy-manager"; role = "manager" },
    @{ name = "policy-admin"; role = "admin" }
)
foreach ($policy in $policies) {
    New-IfMissing `
        -Kind "role policy" `
        -ListUrl "$authzBase/policy" `
        -CreateUrl "$authzBase/policy/role" `
        -Token $adminToken `
        -Body @{
            name = $policy.name
            type = "role"
            logic = "POSITIVE"
            decisionStrategy = "UNANIMOUS"
            roles = @(@{ id = $policy.role; required = $true })
        } | Out-Null
}

Write-Host "[7/7] Create scope permissions"
$permissions = @(
    @{ name = "perm-order-view"; resource = "order"; scope = "view"; policies = @("policy-user"); decisionStrategy = "AFFIRMATIVE" },
    @{ name = "perm-order-create"; resource = "order"; scope = "create"; policies = @("policy-user"); decisionStrategy = "AFFIRMATIVE" },
    @{ name = "perm-order-edit"; resource = "order"; scope = "edit"; policies = @("policy-user"); decisionStrategy = "AFFIRMATIVE" },
    @{ name = "perm-order-approve"; resource = "order"; scope = "approve"; policies = @("policy-manager"); decisionStrategy = "AFFIRMATIVE" },
    @{ name = "perm-order-export"; resource = "order"; scope = "export"; policies = @("policy-manager"); decisionStrategy = "AFFIRMATIVE" },
    @{ name = "perm-order-delete"; resource = "order"; scope = "delete"; policies = @("policy-admin"); decisionStrategy = "AFFIRMATIVE" },
    @{ name = "perm-user-view"; resource = "user"; scope = "view"; policies = @("policy-manager"); decisionStrategy = "AFFIRMATIVE" },
    @{ name = "perm-user-create"; resource = "user"; scope = "create"; policies = @("policy-admin"); decisionStrategy = "AFFIRMATIVE" },
    @{ name = "perm-user-edit"; resource = "user"; scope = "edit"; policies = @("policy-admin"); decisionStrategy = "AFFIRMATIVE" },
    @{ name = "perm-user-delete"; resource = "user"; scope = "delete"; policies = @("policy-admin"); decisionStrategy = "AFFIRMATIVE" },
    @{ name = "perm-user-reset-pwd"; resource = "user"; scope = "reset_pwd"; policies = @("policy-admin"); decisionStrategy = "AFFIRMATIVE" },
    @{ name = "perm-system-view"; resource = "system"; scope = "view"; policies = @("policy-manager"); decisionStrategy = "AFFIRMATIVE" },
    @{ name = "perm-system-edit"; resource = "system"; scope = "edit"; policies = @("policy-admin"); decisionStrategy = "AFFIRMATIVE" }
)
foreach ($permission in $permissions) {
    New-IfMissing `
        -Kind "scope permission" `
        -ListUrl "$authzBase/permission" `
        -CreateUrl "$authzBase/permission/scope" `
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

Write-Host "UMA setup completed."
