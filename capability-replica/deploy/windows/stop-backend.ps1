param(
    [string]$DeployRoot = "D:\apps\capability"
)

$ErrorActionPreference = "Stop"
$pidPath = Join-Path $DeployRoot "run\backend.pid"

if (-not (Test-Path $pidPath)) {
    Write-Host "Backend PID file does not exist."
    exit 0
}

$backendPid = Get-Content $pidPath
$process = Get-Process -Id $backendPid -ErrorAction SilentlyContinue
if ($process) {
    Stop-Process -Id $backendPid
    $process.WaitForExit(30000)
}

Remove-Item $pidPath -Force
Write-Host "Capability backend stopped."
