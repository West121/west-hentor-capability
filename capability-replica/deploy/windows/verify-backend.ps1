param(
    [string]$BackendBaseUrl = "http://127.0.0.1:9901"
)

$ErrorActionPreference = "Stop"

$response = Invoke-WebRequest -UseBasicParsing -Uri "$BackendBaseUrl/v3/api-docs" -TimeoutSec 15
if ($response.StatusCode -ne 200) {
    throw "Backend verification failed: HTTP $($response.StatusCode)"
}

Write-Host "Capability backend is healthy: HTTP $($response.StatusCode)"
