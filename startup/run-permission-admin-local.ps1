$ErrorActionPreference = 'Stop'

$ProjectRoot = Resolve-Path (Join-Path $PSScriptRoot '..')
$AdminDir = Join-Path $ProjectRoot 'admin\permission-admin-local'

Set-Location $AdminDir
mvn spring-boot:run
