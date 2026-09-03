$ErrorActionPreference = "Stop"

. "$PSScriptRoot\scripts\LocalDev.ps1"

Write-LocalInfo "Preparando ambiente local..."
Initialize-Java
Initialize-Docker
Import-LocalEnvironment
Test-BackendPortFree
Start-Postgres
Wait-PostgresHealthy
Test-PostgresConnection

Write-LocalInfo "Iniciando backend em primeiro plano. Use Ctrl+C para encerrar."
Set-Location $script:BackendDir
& ".\mvnw.cmd" spring-boot:run
exit $LASTEXITCODE
