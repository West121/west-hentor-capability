param(
    [string]$DeployRoot = "D:\apps\capability"
)

$ErrorActionPreference = "Stop"

$jarPath = Join-Path $DeployRoot "backend\capability-replica.jar"
$configPath = Join-Path $DeployRoot "config\application-prod.yml"
$envPath = Join-Path $DeployRoot "config\backend.env.ps1"
$logDir = Join-Path $DeployRoot "logs"
$runDir = Join-Path $DeployRoot "run"
$pidPath = Join-Path $runDir "backend.pid"

if (-not (Test-Path $jarPath)) {
    throw "Backend jar not found: $jarPath"
}
if (-not (Test-Path $configPath)) {
    throw "Backend config not found: $configPath"
}
if (-not (Test-Path $envPath)) {
    throw "Copy backend.env.example.ps1 to backend.env.ps1 and configure SQL Server first."
}

. $envPath

foreach ($requiredName in @("SPRING_DATASOURCE_URL", "SPRING_DATASOURCE_USERNAME", "SPRING_DATASOURCE_PASSWORD")) {
    if ([string]::IsNullOrWhiteSpace([Environment]::GetEnvironmentVariable($requiredName))) {
        throw "Missing required environment variable: $requiredName"
    }
}

New-Item -ItemType Directory -Force -Path $logDir, $runDir | Out-Null

if (Test-Path $pidPath) {
    $oldPid = Get-Content $pidPath -ErrorAction SilentlyContinue
    if ($oldPid -and (Get-Process -Id $oldPid -ErrorAction SilentlyContinue)) {
        throw "Backend is already running with PID $oldPid"
    }
    Remove-Item $pidPath -Force
}

$configUri = ($configPath -replace '\\', '/')
$javaArgs = @(
    "-Xms512m",
    "-Xmx3584m",
    "-Dfile.encoding=UTF-8",
    "-jar",
    $jarPath,
    "--spring.config.additional-location=file:$configUri"
)

$process = Start-Process `
    -FilePath "java.exe" `
    -ArgumentList $javaArgs `
    -WorkingDirectory (Split-Path $jarPath) `
    -RedirectStandardOutput (Join-Path $logDir "backend.out.log") `
    -RedirectStandardError (Join-Path $logDir "backend.err.log") `
    -PassThru

Set-Content -Path $pidPath -Value $process.Id -Encoding ascii
Write-Host "Capability backend started. PID=$($process.Id), port=$($env:SERVER_PORT)"
