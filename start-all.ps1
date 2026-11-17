# Starts the whole Louage Tracker stack locally, in dependency order.
# Usage:  powershell -ExecutionPolicy Bypass -File .\start-all.ps1
#         powershell -ExecutionPolicy Bypass -File .\start-all.ps1 -SkipBuild

param(
    [switch]$SkipBuild
)

$ErrorActionPreference = "Stop"
$root = $PSScriptRoot

if (-not $env:APP_JWT_SECRET) {
    $env:APP_JWT_SECRET = "louage-tracker-dev-secret-key-32-chars-min"
}

function Test-Redis {
    $client = [System.Net.Sockets.TcpClient]::new()
    try {
        $client.Connect("127.0.0.1", 6379)
        return $true
    } catch {
        return $false
    } finally {
        $client.Dispose()
    }
}

if (-not (Test-Redis)) {
    Write-Host "Redis is not reachable on 127.0.0.1:6379, starting it with docker compose..."
    & docker compose -f (Join-Path $root "docker-compose.yml") up -d redis
    if ($LASTEXITCODE -ne 0) {
        throw "Could not start Redis. Start Docker Desktop or run 'docker compose up -d redis' manually."
    }
    $deadline = (Get-Date).AddSeconds(60)
    while (-not (Test-Redis)) {
        if ((Get-Date) -gt $deadline) {
            throw "Redis did not become reachable on 127.0.0.1:6379"
        }
        Start-Sleep -Seconds 2
    }
}
Write-Host "Redis is reachable on 127.0.0.1:6379"

$modules = @(
    @{ Name = "eureka-server";   Port = 8761 },
    @{ Name = "auth-service";    Port = 8084 },
    @{ Name = "louage-service";  Port = 8081 },
    @{ Name = "station-service"; Port = 8082 },
    @{ Name = "trip-service";    Port = 8083 },
    @{ Name = "api-gateway";     Port = 8080 }
)

function Wait-Port {
    param(
        [string]$Name,
        [int]$Port,
        [int]$TimeoutSeconds = 90
    )

    $deadline = (Get-Date).AddSeconds($TimeoutSeconds)
    while ((Get-Date) -lt $deadline) {
        $client = [System.Net.Sockets.TcpClient]::new()
        try {
            $client.Connect("127.0.0.1", $Port)
            Write-Host "$Name is listening on port $Port"
            return
        } catch {
            Start-Sleep -Seconds 2
        } finally {
            $client.Dispose()
        }
    }
    throw "$Name did not start on port $Port within $TimeoutSeconds seconds"
}

if (-not $SkipBuild) {
    Write-Host "Building all modules..."
    & mvn -B -ntp -DskipTests install
    if ($LASTEXITCODE -ne 0) {
        throw "Maven build failed"
    }
}

foreach ($module in $modules) {
    $name = $module.Name
    $dir = Join-Path $root $name
    $jar = Join-Path $dir "target\$name-1.0.0.jar"

    if (-not (Test-Path $jar)) {
        throw "Missing $jar. Run without -SkipBuild first."
    }

    Write-Host "Starting $name on port $($module.Port)..."
    Start-Process -FilePath "java" `
        -ArgumentList @("-jar", $jar) `
        -WorkingDirectory $dir | Out-Null

    Wait-Port -Name $name -Port $module.Port
}

Write-Host ""
Write-Host "All services are up:"
Write-Host "  Redis       redis://localhost:6379"
Write-Host "  Eureka      http://localhost:8761"
Write-Host "  Auth        http://localhost:8084/swagger-ui.html"
Write-Host "  Gateway     http://localhost:8080"
Write-Host "  Louage      http://localhost:8081/swagger-ui.html"
Write-Host "  Station     http://localhost:8082/swagger-ui.html"
Write-Host "  Trip        http://localhost:8083/swagger-ui.html"
