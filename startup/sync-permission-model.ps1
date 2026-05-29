param(
    [string]$KeycloakUrl = "http://localhost:8080",
    [string]$Realm = "demo",
    [string]$AdminUser = "admin",
    [string]$AdminPassword = "admin",
    [string]$ResourceServerClientId = "demo-api",
    [string]$ModelFile = "admin/permission-admin-local/permission-data/permission-model.json"
)

$ErrorActionPreference = "Stop"

$ProjectRoot = Resolve-Path (Join-Path $PSScriptRoot "..")
$ResolvedModelFile = if ([System.IO.Path]::IsPathRooted($ModelFile)) {
    $ModelFile
} else {
    Join-Path $ProjectRoot $ModelFile
}

if (-not (Test-Path $ResolvedModelFile)) {
    throw "Permission model file not found: $ResolvedModelFile"
}

function ConvertTo-Array {
    param([object]$Value)

    if ($null -eq $Value) {
        return ,@()
    }

    return ,@($Value)
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

    $json = $Body | ConvertTo-Json -Depth 30
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
        $json = "[" + ($Body[0] | ConvertTo-Json -Depth 30) + "]"
    } else {
        $json = $Body | ConvertTo-Json -Depth 30
    }

    return Invoke-RestMethod -Method $Method -Uri $Url -Headers $headers -ContentType "application/json" -Body $json
}

function Get-AdminToken {
    return (Invoke-RestMethod -Method POST `
        -Uri "$KeycloakUrl/realms/master/protocol/openid-connect/token" `
        -ContentType "application/x-www-form-urlencoded" `
        -Body @{
            grant_type = "password"
            client_id = "admin-cli"
            username = $AdminUser
            password = $AdminPassword
        }).access_token
}

function Get-ClientUuid {
    param(
        [string]$ClientId,
        [string]$Token
    )

    $clients = Invoke-KeycloakJson -Method GET -Url "$KeycloakUrl/admin/realms/$Realm/clients?clientId=$ClientId" -Token $Token
    if (-not $clients -or $clients.Count -eq 0) {
        throw "Client not found: $ClientId"
    }

    return $clients[0].id
}

function Sync-RealmRole {
    param(
        [object]$Role,
        [string]$Token
    )

    $roleUrl = "$KeycloakUrl/admin/realms/$Realm/roles/$($Role.name)"
    try {
        $existing = Invoke-KeycloakJson -Method GET -Url $roleUrl -Token $Token
        Write-Host "  Updating realm role: $($Role.name)"
        Invoke-KeycloakJson -Method PUT -Url $roleUrl -Token $Token -Body @{
            name = $Role.name
            description = $Role.description
        } | Out-Null
        return $existing
    } catch {
        Write-Host "  Creating realm role: $($Role.name)"
        Invoke-KeycloakJson -Method POST -Url "$KeycloakUrl/admin/realms/$Realm/roles" -Token $Token -Body @{
            name = $Role.name
            description = $Role.description
        } | Out-Null
        return Invoke-KeycloakJson -Method GET -Url $roleUrl -Token $Token
    }
}

function Add-DefaultClientScopeIfMissing {
    param(
        [string]$ClientId,
        [string]$ScopeName,
        [string]$Token
    )

    $clientUuid = Get-ClientUuid -ClientId $ClientId -Token $Token
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
        [string]$ClientId,
        [string[]]$RoleNames,
        [string]$Token
    )

    $clientUuid = Get-ClientUuid -ClientId $ClientId -Token $Token
    $mappedRoles = Invoke-KeycloakJson -Method GET -Url "$KeycloakUrl/admin/realms/$Realm/clients/$clientUuid/scope-mappings/realm" -Token $Token
    $missingRoles = @()
    foreach ($roleName in $RoleNames) {
        $exists = $mappedRoles | Where-Object { $_.name -eq $roleName } | Select-Object -First 1
        if ($exists) {
            Write-Host "  realm role scope mapping exists: $ClientId -> $roleName"
            continue
        }

        $missingRoles += Invoke-KeycloakJson -Method GET -Url "$KeycloakUrl/admin/realms/$Realm/roles/$roleName" -Token $Token
    }

    if ($missingRoles.Count -eq 0) {
        return
    }

    $names = ($missingRoles | ForEach-Object { $_.name }) -join ", "
    Write-Host "  Adding realm role scope mappings: $ClientId -> $names"
    Invoke-KeycloakJsonArray -Method POST -Url "$KeycloakUrl/admin/realms/$Realm/clients/$clientUuid/scope-mappings/realm" -Token $Token -Body $missingRoles | Out-Null
}

function Sync-User {
    param(
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
        } | Out-Null
    } else {
        Write-Host "  Creating user: $($User.username)"
        Invoke-KeycloakJson -Method POST -Url "$KeycloakUrl/admin/realms/$Realm/users" -Token $Token -Body @{
            username = $User.username
            email = $User.email
            enabled = $true
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

    $mappedRoles = Invoke-KeycloakJson -Method GET -Url "$KeycloakUrl/admin/realms/$Realm/users/$userId/role-mappings/realm" -Token $Token
    $missingRoles = @()
    foreach ($roleName in (ConvertTo-Array $User.realmRoles)) {
        $exists = $mappedRoles | Where-Object { $_.name -eq $roleName } | Select-Object -First 1
        if ($exists) {
            Write-Host "  user realm role exists: $($User.username) -> $roleName"
            continue
        }

        $missingRoles += Invoke-KeycloakJson -Method GET -Url "$KeycloakUrl/admin/realms/$Realm/roles/$roleName" -Token $Token
    }

    if ($missingRoles.Count -gt 0) {
        $names = ($missingRoles | ForEach-Object { $_.name }) -join ", "
        Write-Host "  Adding user realm roles: $($User.username) -> $names"
        Invoke-KeycloakJsonArray -Method POST -Url "$KeycloakUrl/admin/realms/$Realm/users/$userId/role-mappings/realm" -Token $Token -Body $missingRoles | Out-Null
    }
}

function Sync-NamedItem {
    param(
        [string]$Kind,
        [string]$ListUrl,
        [string]$CreateUrl,
        [string]$UpdateUrlPrefix,
        [string]$Token,
        [object]$Body
    )

    $items = Invoke-KeycloakJson -Method GET -Url $ListUrl -Token $Token
    $existing = $items | Where-Object { $_.name -eq $Body.name } | Select-Object -First 1
    if ($existing) {
        $existingId = if ($existing.id) {
            $existing.id
        } else {
            $existing.PSObject.Properties["_id"].Value
        }
        if (-not $existingId) {
            throw "Cannot resolve Keycloak id for $Kind`: $($Body.name)"
        }

        if ($Kind -eq "resource") {
            $Body["_id"] = $existingId
        }

        Write-Host "  Updating $Kind`: $($Body.name)"
        Invoke-KeycloakJson -Method PUT -Url "$UpdateUrlPrefix/$existingId" -Token $Token -Body $Body | Out-Null
        return
    }

    Write-Host "  Creating $Kind`: $($Body.name)"
    Invoke-KeycloakJson -Method POST -Url $CreateUrl -Token $Token -Body $Body | Out-Null
}

$model = Get-Content -Path $ResolvedModelFile -Raw -Encoding UTF8 | ConvertFrom-Json

Write-Host "[1/8] Get admin token"
$adminToken = Get-AdminToken

Write-Host "[2/8] Sync realm roles"
foreach ($role in (ConvertTo-Array $model.realmRoles)) {
    Sync-RealmRole -Role $role -Token $adminToken | Out-Null
}

Write-Host "[3/8] Sync client scopes and role scope mappings"
foreach ($client in (ConvertTo-Array $model.clients)) {
    foreach ($scopeName in (ConvertTo-Array $client.defaultClientScopes)) {
        Add-DefaultClientScopeIfMissing -ClientId $client.clientId -ScopeName $scopeName -Token $adminToken
    }

    $roleScopeMappings = ConvertTo-Array $client.realmRoleScopeMappings
    if ($roleScopeMappings.Count -gt 0) {
        Add-RealmRoleScopeMappingIfMissing -ClientId $client.clientId -RoleNames $roleScopeMappings -Token $adminToken
    }
}

Write-Host "[4/8] Sync users"
foreach ($user in (ConvertTo-Array $model.users)) {
    Sync-User -User $user -Token $adminToken
}

Write-Host "[5/8] Find resource server client: $ResourceServerClientId"
$resourceServerUuid = Get-ClientUuid -ClientId $ResourceServerClientId -Token $adminToken
$authzBase = "$KeycloakUrl/admin/realms/$Realm/clients/$resourceServerUuid/authz/resource-server"

Write-Host "[6/8] Sync scopes and resources"
$scopeNames = New-Object System.Collections.Generic.HashSet[string]
foreach ($resource in (ConvertTo-Array $model.resources)) {
    foreach ($scopeName in (ConvertTo-Array $resource.scopes)) {
        [void]$scopeNames.Add($scopeName)
    }
}

foreach ($scopeName in $scopeNames) {
    Sync-NamedItem `
        -Kind "scope" `
        -ListUrl "$authzBase/scope" `
        -CreateUrl "$authzBase/scope" `
        -UpdateUrlPrefix "$authzBase/scope" `
        -Token $adminToken `
        -Body @{ name = $scopeName }
}

foreach ($resource in (ConvertTo-Array $model.resources)) {
    $scopeRefs = @()
    foreach ($scopeName in (ConvertTo-Array $resource.scopes)) {
        $scopeRefs += @{ name = $scopeName }
    }

    Sync-NamedItem `
        -Kind "resource" `
        -ListUrl "$authzBase/resource" `
        -CreateUrl "$authzBase/resource" `
        -UpdateUrlPrefix "$authzBase/resource" `
        -Token $adminToken `
        -Body @{
            name = $resource.name
            uris = ConvertTo-Array $resource.uris
            scopes = $scopeRefs
        }
}

Write-Host "[7/8] Sync role policies"
foreach ($policy in (ConvertTo-Array $model.policies)) {
    if ($policy.type -ne "role") {
        throw "Unsupported policy type for $($policy.name): $($policy.type)"
    }

    Sync-NamedItem `
        -Kind "role policy" `
        -ListUrl "$authzBase/policy" `
        -CreateUrl "$authzBase/policy/role" `
        -UpdateUrlPrefix "$authzBase/policy/role" `
        -Token $adminToken `
        -Body @{
            name = $policy.name
            type = "role"
            logic = "POSITIVE"
            decisionStrategy = "UNANIMOUS"
            roles = @(@{ id = $policy.realmRole; required = $true })
            description = $policy.description
        }
}

Write-Host "[8/8] Sync scope permissions"
foreach ($permission in (ConvertTo-Array $model.permissions)) {
    Sync-NamedItem `
        -Kind "scope permission" `
        -ListUrl "$authzBase/permission" `
        -CreateUrl "$authzBase/permission/scope" `
        -UpdateUrlPrefix "$authzBase/permission/scope" `
        -Token $adminToken `
        -Body @{
            name = $permission.name
            type = "scope"
            logic = "POSITIVE"
            decisionStrategy = "UNANIMOUS"
            resources = @($permission.resource)
            scopes = @($permission.scope)
            policies = @($permission.policy)
        }
}

Write-Host "Permission model sync completed: $ResolvedModelFile"
