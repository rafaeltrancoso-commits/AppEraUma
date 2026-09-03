$ErrorActionPreference = "Stop"

. "$PSScriptRoot\scripts\LocalDev.ps1"

Write-LocalInfo "Preparando ambiente de testes local..."
Initialize-Java
Initialize-Docker
Import-LocalEnvironment -ConfigureSpringDatasource:$false
Start-Postgres
Wait-PostgresHealthy
Test-PostgresConnection

Write-LocalInfo "Executando backend\\mvnw.cmd clean test..."
Set-Location $script:BackendDir
& ".\mvnw.cmd" clean test
exit $LASTEXITCODE
