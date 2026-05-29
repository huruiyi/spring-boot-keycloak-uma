param(
    [string]$KeycloakUrl = "http://localhost:8080",
    [string]$BackendUrl = "http://localhost:9000",
    [string]$FrontendUrl = "http://localhost:5173",
    [string]$Realm = "demo",
    [string]$ClientId = "demo-frontend",
    [string]$Audience = "demo-api",
    [string]$Username = "manager",
    [string]$Password = "manager123",
    [switch]$StartServices
)

$ErrorActionPreference = 'Stop'

$ProjectRoot = Resolve-Path (Join-Path $PSScriptRoot '..')

function Test-HttpEndpoint {
    param(
        [string]$Name,
        [string]$Url,
        [int[]]$ExpectedStatus = @(200)
    )

    try {
        $response = Invoke-WebRequest -Uri $Url -UseBasicParsing -TimeoutSec 10 -MaximumRedirection 0 -SkipHttpErrorCheck
    } catch {
        throw "$Name is not reachable at $Url. $($_.Exception.Message)"
    }

    if ($ExpectedStatus -notcontains [int]$response.StatusCode) {
        throw "$Name returned HTTP $($response.StatusCode) at $Url. Expected one of: $($ExpectedStatus -join ', ')"
    }

    Write-Host "[OK] $Name $Url -> HTTP $($response.StatusCode)"
}

function Test-IsReachable {
    param([string]$Url)

    try {
        Invoke-WebRequest -Uri $Url -UseBasicParsing -TimeoutSec 3 -MaximumRedirection 0 -SkipHttpErrorCheck | Out-Null
        return $true
    } catch {
        return $false
    }
}

function Wait-HttpEndpoint {
    param(
        [string]$Name,
        [string]$Url,
        [int[]]$ExpectedStatus = @(200),
        [int]$TimeoutSec = 90
    )

    $deadline = (Get-Date).AddSeconds($TimeoutSec)
    while ((Get-Date) -lt $deadline) {
        try {
            $response = Invoke-WebRequest -Uri $Url -UseBasicParsing -TimeoutSec 5 -MaximumRedirection 0 -SkipHttpErrorCheck
            if ($ExpectedStatus -contains [int]$response.StatusCode) {
                Write-Host "[OK] $Name is ready"
                return
            }
        } catch {
        }

        Start-Sleep -Seconds 2
    }

    throw "$Name did not become ready at $Url within $TimeoutSec seconds."
}

function Start-UmaDemoServices {
    Write-Host "Starting Keycloak with docker compose"
    Push-Location $ProjectRoot
    try {
        docker compose up -d | Out-Host
    } finally {
        Pop-Location
    }

    if (-not (Test-IsReachable "$BackendUrl/actuator/health")) {
        Write-Host "Starting backend"
        Start-Process powershell `
            -WindowStyle Hidden `
            -WorkingDirectory $ProjectRoot `
            -ArgumentList @("-NoProfile", "-ExecutionPolicy", "Bypass", "-File", ".\startup\run-backend.ps1") | Out-Null
    }

    if (-not (Test-IsReachable $FrontendUrl)) {
        Write-Host "Starting frontend"
        Start-Process powershell `
            -WindowStyle Hidden `
            -WorkingDirectory $ProjectRoot `
            -ArgumentList @("-NoProfile", "-ExecutionPolicy", "Bypass", "-File", ".\startup\run-frontend.ps1") | Out-Null
    }

    Wait-HttpEndpoint -Name "Keycloak" -Url $KeycloakUrl -TimeoutSec 120
    Wait-HttpEndpoint -Name "Backend health" -Url "$BackendUrl/actuator/health" -TimeoutSec 120
    Wait-HttpEndpoint -Name "Frontend" -Url $FrontendUrl -TimeoutSec 120
}

Write-Host "Running UMA demo smoke test"

if ($StartServices) {
    Start-UmaDemoServices
}

Test-HttpEndpoint -Name "Keycloak" -Url $KeycloakUrl
Test-HttpEndpoint -Name "Backend health" -Url "$BackendUrl/actuator/health"
Test-HttpEndpoint -Name "Frontend" -Url $FrontendUrl

$login = Invoke-RestMethod `
    -Method POST `
    -Uri "$KeycloakUrl/realms/$Realm/protocol/openid-connect/token" `
    -ContentType "application/x-www-form-urlencoded" `
    -Body @{
        grant_type = "password"
        client_id = $ClientId
        username = $Username
        password = $Password
    }

$rpt = Invoke-RestMethod `
    -Method POST `
    -Uri "$KeycloakUrl/realms/$Realm/protocol/openid-connect/token" `
    -ContentType "application/x-www-form-urlencoded" `
    -Headers @{ Authorization = "Bearer $($login.access_token)" } `
    -Body @{
        grant_type = "urn:ietf:params:oauth:grant-type:uma-ticket"
        audience = $Audience
        permission = "order#view"
    }

$ordersTraceId = "smoke-$([guid]::NewGuid())"
$orders = Invoke-RestMethod `
    -Method GET `
    -Uri "$BackendUrl/api/orders" `
    -Headers @{
        Authorization = "Bearer $($rpt.access_token)"
        "X-Trace-Id" = $ordersTraceId
    }

if (-not $orders.data -or $orders.data.Count -lt 1) {
    throw "Backend happy path returned no orders."
}

Write-Host "[OK] UMA happy path returned $($orders.data.Count) orders"

$traceId = "smoke-$([guid]::NewGuid())"
$unauthorized = Invoke-WebRequest `
    -Uri "$BackendUrl/api/orders" `
    -Headers @{ "X-Trace-Id" = $traceId } `
    -UseBasicParsing `
    -TimeoutSec 10 `
    -SkipHttpErrorCheck

if ([int]$unauthorized.StatusCode -ne 401) {
    throw "Backend security check returned HTTP $($unauthorized.StatusCode). Expected 401 without a bearer token."
}

$returnedTraceId = $unauthorized.Headers["X-Trace-Id"]
if ($returnedTraceId -ne $traceId) {
    throw "Backend trace id check failed. Expected $traceId, got $returnedTraceId"
}

Write-Host "[OK] Backend security and trace id check -> HTTP 401, X-Trace-Id returned"
Write-Host "Smoke test passed"
