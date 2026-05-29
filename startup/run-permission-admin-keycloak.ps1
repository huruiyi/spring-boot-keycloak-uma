$ErrorActionPreference = 'Stop'

$ProjectRoot = Resolve-Path (Join-Path $PSScriptRoot '..')
$AdminDir = Join-Path $ProjectRoot 'admin\permission-admin-keycloak'

Set-Location $AdminDir
mvn spring-boot:run
