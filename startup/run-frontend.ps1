$ErrorActionPreference = 'Stop'

$ProjectRoot = Resolve-Path (Join-Path $PSScriptRoot '..')
$FrontendDir = Join-Path $ProjectRoot 'frontend'

Set-Location $FrontendDir
npm run dev -- --port 5173
