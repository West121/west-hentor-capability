param(
    [string]$PublicBaseUrl = "http://127.0.0.1"
)

$ErrorActionPreference = "Stop"

$backend = Invoke-WebRequest -UseBasicParsing -Uri "http://127.0.0.1:9901/v3/api-docs" -TimeoutSec 15
$frontend = Invoke-WebRequest -UseBasicParsing -Uri "$PublicBaseUrl/login" -TimeoutSec 15

if ($backend.StatusCode -ne 200) {
    throw "Backend verification failed: HTTP $($backend.StatusCode)"
}
if ($frontend.StatusCode -ne 200 -or $frontend.Content -notmatch '<div id="root">') {
    throw "Frontend verification failed."
}

Write-Host "Backend API docs: HTTP $($backend.StatusCode)"
Write-Host "Frontend login SPA: HTTP $($frontend.StatusCode)"
Write-Host "Deployment verification passed."
