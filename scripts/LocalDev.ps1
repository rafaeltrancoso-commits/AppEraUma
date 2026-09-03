$ErrorActionPreference = "Stop"

$script:ProjectRoot = Split-Path -Parent $PSScriptRoot
$script:BackendDir = Join-Path $script:ProjectRoot "backend"
$script:EnvPath = Join-Path $script:ProjectRoot ".env"
$script:JdkPath = "C:\Program Files\Java\jdk-22"
$script:DockerBinPath = "C:\Program Files\Docker\Docker\resources\bin"
$script:DockerDesktopExe = "C:\Program Files\Docker\Docker\Docker Desktop.exe"
$script:PostgresContainer = "erauma-postgres"
$script:BackendPort = 8080
$script:PostgresHostPort = 5433

function Write-LocalInfo {
    param([string] $Message)
    Write-Host "[EraUma] $Message"
}

function Fail-LocalDev {
    param([string] $Message)
    Write-Host ""
    Write-Host "[EraUma] ERRO: $Message"
    exit 1
}

function Add-PathEntryForProcess {
    param([string] $Path)
    if (-not (Test-Path $Path)) {
        Fail-LocalDev "Caminho nao encontrado: $Path"
    }
    $entries = $env:Path -split ';'
    if ($entries -notcontains $Path) {
        $env:Path = "$Path;$env:Path"
    }
}

function Initialize-Java {
    if (-not (Test-Path (Join-Path $script:JdkPath "bin\java.exe"))) {
        Fail-LocalDev "JDK 22 nao encontrado em $script:JdkPath. Instale JDK 21+ ou ajuste o script."
    }

    $env:JAVA_HOME = $script:JdkPath
    Add-PathEntryForProcess (Join-Path $script:JdkPath "bin")

    $previousErrorActionPreference = $ErrorActionPreference
    $ErrorActionPreference = "Continue"
    try {
        $versionOutput = (& java -version 2>&1 | Out-String)
    }
    finally {
        $ErrorActionPreference = $previousErrorActionPreference
    }
    if ($versionOutput -notmatch 'version "(\d+)') {
        Fail-LocalDev "Nao foi possivel identificar a versao do Java."
    }

    $major = [int] $Matches[1]
    if ($major -lt 21) {
        Fail-LocalDev "Java incompativel: versao $major detectada. O backend exige Java 21 ou superior."
    }

    Write-LocalInfo "Java $major configurado para esta sessao."
}

function Initialize-Docker {
    if (-not (Test-Path (Join-Path $script:DockerBinPath "docker.exe"))) {
        Fail-LocalDev "Docker CLI nao encontrado em $script:DockerBinPath."
    }

    Add-PathEntryForProcess $script:DockerBinPath

    if (-not (Test-DockerReady)) {
        if (-not (Test-Path $script:DockerDesktopExe)) {
            Fail-LocalDev "Docker Desktop nao esta respondendo e o executavel nao foi encontrado."
        }

        Write-LocalInfo "Docker Desktop nao esta respondendo. Iniciando e aguardando..."
        Start-Process -FilePath $script:DockerDesktopExe -WindowStyle Hidden | Out-Null

        $deadline = (Get-Date).AddSeconds(120)
        while ((Get-Date) -lt $deadline) {
            Start-Sleep -Seconds 3
            if (Test-DockerReady) {
                break
            }
        }
    }

    if (-not (Test-DockerReady)) {
        Fail-LocalDev "Docker Desktop nao iniciou dentro do tempo limite."
    }

    Write-LocalInfo "Docker Client e Server respondendo."
}

function Test-DockerReady {
    try {
        & docker info *> $null
        return ($LASTEXITCODE -eq 0)
    }
    catch {
        return $false
    }
}

function Read-DotEnvFile {
    param([string] $Path)

    if (-not (Test-Path $Path)) {
        Fail-LocalDev ".env nao encontrado. Crie a partir de .env.example: Copy-Item .env.example .env"
    }

    $values = @{}
    foreach ($line in Get-Content $Path) {
        $trimmed = $line.Trim()
        if ([string]::IsNullOrWhiteSpace($trimmed) -or $trimmed.StartsWith("#") -or -not $trimmed.Contains("=")) {
            continue
        }

        $parts = $trimmed -split "=", 2
        $name = $parts[0].Trim()
        $value = $parts[1].Trim()
        if ((($value.StartsWith('"') -and $value.EndsWith('"')) -or ($value.StartsWith("'") -and $value.EndsWith("'"))) -and $value.Length -ge 2) {
            $value = $value.Substring(1, $value.Length - 2)
        }

        if (-not [string]::IsNullOrWhiteSpace($name)) {
            $values[$name] = $value
        }
    }

    return $values
}

function Import-LocalEnvironment {
    param(
        [switch] $ConfigureSpringDatasource = $true
    )

    $values = Read-DotEnvFile $script:EnvPath

    foreach ($name in $values.Keys) {
        [Environment]::SetEnvironmentVariable($name, $values[$name], "Process")
    }

    if ($ConfigureSpringDatasource) {
        $dbName = Get-RequiredValue $values "POSTGRES_DB"
        $dbUser = Get-RequiredValue $values "POSTGRES_USER"
        $dbPassword = Get-RequiredValue $values "POSTGRES_PASSWORD"

        $env:SPRING_PROFILES_ACTIVE = "local"
        $env:SPRING_DATASOURCE_URL = "jdbc:postgresql://localhost:$script:PostgresHostPort/$dbName"
        $env:SPRING_DATASOURCE_USERNAME = $dbUser
        $env:SPRING_DATASOURCE_PASSWORD = $dbPassword
    }
    else {
        [Environment]::SetEnvironmentVariable("SPRING_PROFILES_ACTIVE", $null, "Process")
        [Environment]::SetEnvironmentVariable("SPRING_DATASOURCE_URL", $null, "Process")
        [Environment]::SetEnvironmentVariable("SPRING_DATASOURCE_USERNAME", $null, "Process")
        [Environment]::SetEnvironmentVariable("SPRING_DATASOURCE_PASSWORD", $null, "Process")
    }

    if ([string]::IsNullOrWhiteSpace($env:JWT_SECRET)) {
        $bytes = New-Object byte[] 48
        $rng = [System.Security.Cryptography.RandomNumberGenerator]::Create()
        try {
            $rng.GetBytes($bytes)
        }
        finally {
            $rng.Dispose()
        }
        $env:JWT_SECRET = [Convert]::ToBase64String($bytes)
        Write-LocalInfo "JWT_SECRET criado apenas em memoria para esta sessao local."
    }

    Write-LocalInfo ".env carregado sem exibir segredos."
}

function Get-RequiredValue {
    param(
        [hashtable] $Values,
        [string] $Name
    )

    if (-not $Values.ContainsKey($Name) -or [string]::IsNullOrWhiteSpace($Values[$Name])) {
        Fail-LocalDev "Variavel obrigatoria ausente no .env: $Name"
    }

    return $Values[$Name]
}

function Start-Postgres {
    Push-Location $script:ProjectRoot
    try {
        Write-LocalInfo "Iniciando PostgreSQL via Docker Compose..."
        & docker compose up -d
        if ($LASTEXITCODE -ne 0) {
            Fail-LocalDev "Falha ao executar docker compose up -d."
        }
    }
    finally {
        Pop-Location
    }
}

function Wait-PostgresHealthy {
    $deadline = (Get-Date).AddSeconds(90)
    while ((Get-Date) -lt $deadline) {
        $status = (& docker inspect --format="{{if .State.Health}}{{.State.Health.Status}}{{else}}{{.State.Status}}{{end}}" $script:PostgresContainer 2>$null) -join ""
        if ($status -eq "healthy") {
            Write-LocalInfo "PostgreSQL healthy."
            return
        }
        Start-Sleep -Seconds 2
    }

    Fail-LocalDev "PostgreSQL nao ficou healthy no tempo limite."
}

function Test-PostgresConnection {
    $command = 'PGPASSWORD="$POSTGRES_PASSWORD" psql -h host.docker.internal -p 5433 -U "$POSTGRES_USER" -d "$POSTGRES_DB" -tAc "select 1" >/dev/null'
    & docker exec $script:PostgresContainer sh -c $command
    if ($LASTEXITCODE -ne 0) {
        Fail-LocalDev "Nao foi possivel conectar ao PostgreSQL em localhost:5433. Verifique senha do .env e volume local existente."
    }

    Write-LocalInfo "Conexao validada em localhost:5433."
}

function Test-BackendPortFree {
    $connection = Get-NetTCPConnection -LocalPort $script:BackendPort -State Listen -ErrorAction SilentlyContinue
    if ($connection) {
        Fail-LocalDev "A porta 8080 ja esta em uso. Encerre o backend atual antes de iniciar outro."
    }
}

function Wait-BackendHealth {
    $deadline = (Get-Date).AddSeconds(120)
    while ((Get-Date) -lt $deadline) {
        try {
            $health = Invoke-RestMethod "http://localhost:$script:BackendPort/actuator/health" -TimeoutSec 2
            if ($health.status -eq "UP") {
                Write-LocalInfo "Backend UP em http://localhost:$script:BackendPort/actuator/health."
                return
            }
        }
        catch {
            Start-Sleep -Seconds 2
        }
    }

    Fail-LocalDev "Backend nao respondeu UP em /actuator/health no tempo limite."
}
