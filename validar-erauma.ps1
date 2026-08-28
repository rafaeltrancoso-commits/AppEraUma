# ============================================================
# EraUma - Ambiente Completo de Validacao Local
# ============================================================

$ErrorActionPreference = "Stop"

# ------------------------------------------------------------
# CONFIGURACAO BASE
# ------------------------------------------------------------

$ROOT      = "D:\Developer RR Sistemas\AppEraUma"
$BACKEND   = Join-Path $ROOT "backend"
$MOBILE    = Join-Path $ROOT "mobile"
$ENV_LOCAL = Join-Path $ROOT ".env.local"

$DOCKER = "C:\Program Files\Docker\Docker\resources\bin\docker.exe"
$JAVA   = "C:\Program Files\Java\jdk-22"

$BACKEND_PORT = 8080

$LOG_DIR = Join-Path $ROOT "logs"
$BACKEND_LOG = Join-Path $LOG_DIR "backend-validacao.log"

Write-Host ""
Write-Host "============================================================"
Write-Host "          ERAUMA - AMBIENTE DE VALIDACAO"
Write-Host "============================================================"
Write-Host ""

# ============================================================
# FUNCOES
# ============================================================

function Fail {
    param(
        [string]$Message
    )

    Write-Host ""
    Write-Host "============================================================"
    Write-Host "ERRO"
    Write-Host "============================================================"
    Write-Host $Message
    Write-Host ""

    exit 1
}

function Load-DotEnv {
    param(
        [string]$Path
    )

    if (-not (Test-Path $Path)) {
        Fail ".env.local nao encontrado em: $Path"
    }

    Write-Host "[OK] Carregando configuracoes de .env.local"

    foreach ($line in Get-Content $Path) {

        $trimmed = $line.Trim()

        if ([string]::IsNullOrWhiteSpace($trimmed)) {
            continue
        }

        if ($trimmed.StartsWith("#")) {
            continue
        }

        if (-not $trimmed.Contains("=")) {
            continue
        }

        $parts = $trimmed -split "=", 2

        $name  = $parts[0].Trim()
        $value = $parts[1].Trim()

        if (
            ($value.StartsWith('"') -and $value.EndsWith('"')) -or
            ($value.StartsWith("'") -and $value.EndsWith("'"))
        ) {
            $value = $value.Substring(1, $value.Length - 2)
        }

        if (-not [string]::IsNullOrWhiteSpace($name)) {
            [Environment]::SetEnvironmentVariable(
                $name,
                $value,
                "Process"
            )
        }
    }
}

function Require-Env {
    param(
        [string]$Name
    )

    $value = [Environment]::GetEnvironmentVariable(
        $Name,
        "Process"
    )

    if ([string]::IsNullOrWhiteSpace($value)) {
        Fail "Variavel obrigatoria nao configurada: $Name"
    }
}

function Test-PortFree {
    param(
        [int]$Port
    )

    $connection = Get-NetTCPConnection `
        -LocalPort $Port `
        -State Listen `
        -ErrorAction SilentlyContinue

    return (-not $connection)
}

function Find-FreePort {
    param(
        [int]$StartPort = 8081,
        [int]$EndPort = 8090
    )

    for ($port = $StartPort; $port -le $EndPort; $port++) {

        if (Test-PortFree $port) {
            return $port
        }
    }

    return $null
}

# ============================================================
# 1. VALIDAR DIRETORIOS
# ============================================================

Write-Host "[1/11] Validando estrutura do projeto..."

if (-not (Test-Path $ROOT)) {
    Fail "Diretorio raiz nao encontrado: $ROOT"
}

if (-not (Test-Path $BACKEND)) {
    Fail "Backend nao encontrado: $BACKEND"
}

if (-not (Test-Path $MOBILE)) {
    Fail "Mobile nao encontrado: $MOBILE"
}

if (-not (Test-Path (Join-Path $BACKEND "mvnw.cmd"))) {
    Fail "mvnw.cmd nao encontrado no backend."
}

if (-not (Test-Path (Join-Path $MOBILE "package.json"))) {
    Fail "package.json nao encontrado no mobile."
}

if (-not (Test-Path $LOG_DIR)) {
    New-Item `
        -ItemType Directory `
        -Path $LOG_DIR `
        -Force | Out-Null
}

Write-Host "[OK] Estrutura encontrada."

# ============================================================
# 2. CARREGAR .env.local
# ============================================================

Write-Host ""
Write-Host "[2/11] Carregando ambiente local..."

Load-DotEnv $ENV_LOCAL

# Banco
Require-Env "POSTGRES_DB"
Require-Env "POSTGRES_USER"
Require-Env "POSTGRES_PASSWORD"
Require-Env "POSTGRES_PORT"

# JWT
Require-Env "JWT_SECRET"

# Provider de historia
if ([string]::IsNullOrWhiteSpace($env:APP_STORY_GENERATOR)) {
    $env:APP_STORY_GENERATOR = "mock"
}

# Se estiver usando OpenAI, a chave passa a ser obrigatoria
if ($env:APP_STORY_GENERATOR -eq "openai") {

    Require-Env "OPENAI_API_KEY"
    Require-Env "OPENAI_MODEL"

    if ($env:OPENAI_API_KEY.Length -lt 30) {
        Fail "OPENAI_API_KEY parece invalida ou ainda contem um placeholder."
    }
}

# Configuracoes default seguras
if ([string]::IsNullOrWhiteSpace($env:APP_STORY_AI_FALLBACK_ENABLED)) {
    $env:APP_STORY_AI_FALLBACK_ENABLED = "true"
}

if ([string]::IsNullOrWhiteSpace($env:APP_STORY_IMAGE_GENERATION_ENABLED)) {
    $env:APP_STORY_IMAGE_GENERATION_ENABLED = "false"
}

if (
    $env:APP_STORY_IMAGE_GENERATION_ENABLED -eq "true" -and
    $env:APP_STORY_GENERATOR -eq "openai"
) {

    Require-Env "OPENAI_IMAGE_MODEL"
}

if ([string]::IsNullOrWhiteSpace($env:APP_STORAGE_ROOT)) {
    $env:APP_STORAGE_ROOT = "storage"
}

$env:SPRING_PROFILES_ACTIVE = "local"

Write-Host "[OK] Ambiente carregado."
Write-Host ""
Write-Host "Provider texto : $env:APP_STORY_GENERATOR"
Write-Host "Modelo texto   : $env:OPENAI_MODEL"
Write-Host "Imagem IA      : $env:APP_STORY_IMAGE_GENERATION_ENABLED"
Write-Host "Modelo imagem  : $env:OPENAI_IMAGE_MODEL"
Write-Host "Storage        : $env:APP_STORAGE_ROOT"

if (-not [string]::IsNullOrWhiteSpace($env:OPENAI_API_KEY)) {
    Write-Host "OpenAI Key     : configurada ($($env:OPENAI_API_KEY.Length) caracteres)"
}
else {
    Write-Host "OpenAI Key     : nao configurada"
}

# ============================================================
# 3. JAVA 22
# ============================================================

Write-Host ""
Write-Host "[3/11] Configurando Java..."

if (-not (Test-Path "$JAVA\bin\java.exe")) {
    Fail "JDK 22 nao encontrado em: $JAVA"
}

$env:JAVA_HOME = $JAVA
$env:Path = "$JAVA\bin;$env:Path"

Write-Host "[OK] JAVA_HOME=$env:JAVA_HOME"

& "$JAVA\bin\java.exe" -version

# ============================================================
# 4. DOCKER
# ============================================================

Write-Host ""
Write-Host "[4/11] Verificando Docker..."

if (-not (Test-Path $DOCKER)) {
    Fail "Docker nao encontrado em: $DOCKER"
}

try {

    & $DOCKER info *> $null

    if ($LASTEXITCODE -ne 0) {
        throw "Docker nao respondeu."
    }

}
catch {

    Fail "Docker Desktop nao esta iniciado. Abra o Docker Desktop e execute novamente."
}

Write-Host "[OK] Docker ativo."

# ============================================================
# 5. POSTGRESQL
# ============================================================

Write-Host ""
Write-Host "[5/11] Subindo PostgreSQL..."

Set-Location $ROOT

& $DOCKER compose --env-file .env up -d postgres

if ($LASTEXITCODE -ne 0) {
    Fail "Falha ao iniciar PostgreSQL."
}

Write-Host ""
& $DOCKER compose ps

Write-Host ""
Write-Host "Aguardando PostgreSQL ficar healthy..."

$postgresHealthy = $false

for ($i = 1; $i -le 30; $i++) {

    $status = & $DOCKER inspect `
        --format="{{.State.Health.Status}}" `
        erauma-postgres `
        2>$null

    if ($status -eq "healthy") {
        $postgresHealthy = $true
        break
    }

    Write-Host "PostgreSQL: $status - tentativa $i/30"

    Start-Sleep -Seconds 2
}

if (-not $postgresHealthy) {
    Fail "PostgreSQL nao ficou healthy."
}

Write-Host "[OK] PostgreSQL healthy."

# ============================================================
# 6. LIBERAR PORTA 8080
# ============================================================

Write-Host ""
Write-Host "[6/11] Verificando porta $BACKEND_PORT..."

$connections = Get-NetTCPConnection `
    -LocalPort $BACKEND_PORT `
    -State Listen `
    -ErrorAction SilentlyContinue

if ($connections) {

    $processIds = $connections |
        Select-Object -ExpandProperty OwningProcess -Unique

    foreach ($processId in $processIds) {

        $process = Get-Process `
            -Id $processId `
            -ErrorAction SilentlyContinue

        if (-not $process) {
            continue
        }

        Write-Host ""
        Write-Host "Porta $BACKEND_PORT ocupada:"
        Write-Host "PID      : $processId"
        Write-Host "Processo : $($process.ProcessName)"

        if ($process.ProcessName -eq "java") {

            Write-Host "Encerrando backend Java anterior..."

            try {

                Stop-Process `
                    -Id $processId `
                    -Force `
                    -ErrorAction Stop

            }
            catch {

                Write-Host "Stop-Process falhou. Tentando taskkill..."

                taskkill /PID $processId /F /T | Out-Null
            }

            Start-Sleep -Seconds 2

        }
        else {

            Fail "Porta $BACKEND_PORT ocupada por processo nao-Java: $($process.ProcessName)"
        }
    }
}

if (-not (Test-PortFree $BACKEND_PORT)) {
    Fail "Porta $BACKEND_PORT continua ocupada."
}

Write-Host "[OK] Porta $BACKEND_PORT livre."

# ============================================================
# 7. TESTES BACKEND
# ============================================================

Write-Host ""
Write-Host "[7/11] Executando testes backend..."

Set-Location $BACKEND

& ".\mvnw.cmd" test

if ($LASTEXITCODE -ne 0) {

    Write-Host ""
    Write-Host "============================================================"
    Write-Host "BACKEND COM ERRO - NAO SERA INICIADO"
    Write-Host "============================================================"
    Write-Host ""

    exit 1
}

Write-Host "[OK] Testes backend passaram."

# ============================================================
# 8. DESCOBRIR IP LOCAL
# ============================================================

Write-Host ""
Write-Host "[8/11] Detectando IP da rede..."

$IP = Get-NetIPAddress `
    -AddressFamily IPv4 `
    -InterfaceAlias "Wi-Fi" `
    -ErrorAction SilentlyContinue |
    Where-Object {
        $_.IPAddress -notlike "127.*" -and
        $_.IPAddress -notlike "169.254.*"
    } |
    Select-Object `
        -First 1 `
        -ExpandProperty IPAddress

if (-not $IP) {

    $IP = Get-NetIPAddress `
        -AddressFamily IPv4 `
        -ErrorAction SilentlyContinue |
        Where-Object {

            (
                $_.IPAddress -like "192.168.*" -or
                $_.IPAddress -like "10.*"
            ) -and

            $_.IPAddress -notlike "169.254.*"

        } |
        Select-Object `
            -First 1 `
            -ExpandProperty IPAddress
}

if (-not $IP) {
    Fail "Nao foi possivel determinar o IP local."
}

Write-Host "[OK] IP encontrado: $IP"

# ============================================================
# 9. INICIAR BACKEND
# ============================================================

Write-Host ""
Write-Host "[9/11] Iniciando backend..."

if (Test-Path $BACKEND_LOG) {
    Remove-Item $BACKEND_LOG -Force
}

#
# IMPORTANTE:
# Start-Process herda o ambiente atual.
# Portanto JWT_SECRET, OPENAI_API_KEY, providers etc.
# ja estao disponiveis na nova janela.
#
# Nao colocamos os segredos dentro da string do comando.
#

$backendCommand = @"
`$env:JAVA_HOME = '$JAVA'
`$env:Path = '$JAVA\bin;' + `$env:Path
`$env:SPRING_PROFILES_ACTIVE = 'local'

Set-Location '$BACKEND'

Write-Host ''
Write-Host '============================================================'
Write-Host 'ERAUMA BACKEND'
Write-Host '============================================================'
Write-Host ''

Write-Host ('Story provider : ' + `$env:APP_STORY_GENERATOR)
Write-Host ('Text model     : ' + `$env:OPENAI_MODEL)
Write-Host ('Image enabled  : ' + `$env:APP_STORY_IMAGE_GENERATION_ENABLED)
Write-Host ('Image model    : ' + `$env:OPENAI_IMAGE_MODEL)
Write-Host ('Storage        : ' + `$env:APP_STORAGE_ROOT)
Write-Host ('OpenAI Key     : ' + (-not [string]::IsNullOrWhiteSpace(`$env:OPENAI_API_KEY)))
Write-Host ''

.\mvnw.cmd spring-boot:run 2>&1 |
    Tee-Object -FilePath '$BACKEND_LOG'
"@

Start-Process `
    powershell.exe `
    -ArgumentList @(
        "-NoExit",
        "-ExecutionPolicy",
        "Bypass",
        "-Command",
        $backendCommand
    )

# ============================================================
# 10. HEALTH CHECK
# ============================================================

Write-Host ""
Write-Host "[10/11] Aguardando backend..."

$healthOK = $false

for ($i = 1; $i -le 60; $i++) {

    try {

        $health = Invoke-RestMethod `
            "http://localhost:${BACKEND_PORT}/actuator/health" `
            -TimeoutSec 2

        if ($health.status -eq "UP") {

            $healthOK = $true
            break
        }

    }
    catch {
        # Spring ainda iniciando
    }

    Write-Host "Aguardando backend... tentativa $i/60"

    Start-Sleep -Seconds 2
}

if (-not $healthOK) {

    Write-Host ""
    Write-Host "============================================================"
    Write-Host "BACKEND NAO FICOU UP"
    Write-Host "============================================================"
    Write-Host ""

    Write-Host "Log:"
    Write-Host $BACKEND_LOG

    if (Test-Path $BACKEND_LOG) {

        Write-Host ""
        Write-Host "Ultimas linhas:"
        Write-Host ""

        Get-Content `
            $BACKEND_LOG `
            -Tail 40
    }

    exit 1
}

Write-Host "[OK] Backend UP."

# ============================================================
# 11. MOBILE / WEB
# ============================================================

Write-Host ""
Write-Host "[11/11] Validando Mobile/Web..."

Set-Location $MOBILE

$env:EXPO_PUBLIC_API_URL = "http://${IP}:${BACKEND_PORT}/api"

Write-Host ""
Write-Host "API do Expo:"
Write-Host $env:EXPO_PUBLIC_API_URL

Write-Host ""
Write-Host "Executando TypeScript..."

npm run typecheck

if ($LASTEXITCODE -ne 0) {
    Fail "Erro no TypeScript."
}

Write-Host ""
Write-Host "Executando ESLint..."

npm run lint

if ($LASTEXITCODE -ne 0) {
    Fail "Erro no ESLint."
}

Write-Host "[OK] Mobile validado."

# ============================================================
# 12. ENCONTRAR PORTA LIVRE PARA EXPO
# ============================================================

$EXPO_PORT = Find-FreePort `
    -StartPort 8081 `
    -EndPort 8090

if (-not $EXPO_PORT) {
    Fail "Nao existe porta livre entre 8081 e 8090 para o Expo."
}

Write-Host ""
Write-Host "[OK] Porta Expo selecionada: $EXPO_PORT"

# ============================================================
# 13. INICIAR EXPO WEB
# ============================================================

Write-Host ""
Write-Host "Iniciando Expo Web..."

$expoCommand = @"
Set-Location '$MOBILE'

`$env:EXPO_PUBLIC_API_URL = 'http://${IP}:${BACKEND_PORT}/api'

Write-Host ''
Write-Host '============================================================'
Write-Host 'ERAUMA EXPO WEB'
Write-Host '============================================================'
Write-Host ''
Write-Host 'API: http://${IP}:${BACKEND_PORT}/api'
Write-Host 'Web: http://${IP}:${EXPO_PORT}'
Write-Host ''

npx expo start --web --clear --port $EXPO_PORT
"@

Start-Process `
    powershell.exe `
    -ArgumentList @(
        "-NoExit",
        "-ExecutionPolicy",
        "Bypass",
        "-Command",
        $expoCommand
    )

# ============================================================
# FINAL
# ============================================================

Write-Host ""
Write-Host "============================================================"
Write-Host "             ERAUMA PRONTO PARA VALIDACAO"
Write-Host "============================================================"
Write-Host ""

Write-Host "BACKEND LOCAL"
Write-Host "http://localhost:${BACKEND_PORT}"
Write-Host ""

Write-Host "HEALTH LOCAL"
Write-Host "http://localhost:${BACKEND_PORT}/actuator/health"
Write-Host ""

Write-Host "BACKEND NA REDE"
Write-Host "http://${IP}:${BACKEND_PORT}"
Write-Host ""

Write-Host "HEALTH NA REDE"
Write-Host "http://${IP}:${BACKEND_PORT}/actuator/health"
Write-Host ""

Write-Host "API"
Write-Host "http://${IP}:${BACKEND_PORT}/api"
Write-Host ""

Write-Host "EXPO WEB"
Write-Host "http://${IP}:${EXPO_PORT}"
Write-Host ""

Write-Host "LOG BACKEND"
Write-Host $BACKEND_LOG
Write-Host ""

Write-Host "IA"
Write-Host "Story provider : $env:APP_STORY_GENERATOR"
Write-Host "Text model     : $env:OPENAI_MODEL"
Write-Host "Image enabled  : $env:APP_STORY_IMAGE_GENERATION_ENABLED"
Write-Host "Image model    : $env:OPENAI_IMAGE_MODEL"
Write-Host ""

Write-Host "OpenAI Key configurada:"
Write-Host (-not [string]::IsNullOrWhiteSpace($env:OPENAI_API_KEY))
Write-Host ""

Write-Host "============================================================"
Write-Host "VALIDACOES SUGERIDAS"
Write-Host "============================================================"
Write-Host ""
Write-Host "1. Login"
Write-Host "2. Cadastro / Minhas criancas"
Write-Host "3. Momentos"
Write-Host "4. Upload de foto > 1 MB"
Write-Host "5. Calendario"
Write-Host "6. Linha do tempo"
Write-Host "7. Historia TEXT_ONLY"
Write-Host "8. Historia ILLUSTRATED"
Write-Host "9. OpenAI texto"
Write-Host "10. GPT Image"
Write-Host "11. Biblioteca"
Write-Host "12. Narracao"
Write-Host ""

Write-Host "Ambiente iniciado com sucesso."
Write-Host ""