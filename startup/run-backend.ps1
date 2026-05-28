$ErrorActionPreference = 'Stop'

$ProjectRoot = Resolve-Path (Join-Path $PSScriptRoot '..')
$BackendDir = Join-Path $ProjectRoot 'backend'

$env:JAVA_HOME = 'D:\Soft\Jdk\jdk-21'
$env:PATH = "$env:JAVA_HOME\bin;$env:PATH"

Set-Location $BackendDir
mvn spring-boot:run
