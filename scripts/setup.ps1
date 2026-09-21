# Main Responsibility: One-shot local clone setup (env, Compose, health wait, frontend deps).
# Run from the repository root:  .\scripts\setup.ps1

$ErrorActionPreference = "Stop"

$RepoRoot = Split-Path -Parent $PSScriptRoot
Set-Location $RepoRoot

if (-not (Test-Path (Join-Path $RepoRoot "docker-compose.yml"))) {
    throw "docker-compose.yml not found. Run this script from the repository root (or via .\scripts\setup.ps1)."
}

$EnvFile = Join-Path $RepoRoot ".env"
$EnvExample = Join-Path $RepoRoot ".env.example"

# Create a local .env only when missing so existing secrets are never overwritten.
if (-not (Test-Path $EnvFile)) {
    if (-not (Test-Path $EnvExample)) {
        throw ".env.example is missing; cannot create .env."
    }
    Copy-Item $EnvExample $EnvFile
    Write-Host "Created .env from .env.example"
}
else {
    Write-Host ".env already exists - leaving it unchanged"
}

# Warn when Groq is unset; do not invent a key (processing fails until the user fills it).
$groqKey = $null
Get-Content $EnvFile | ForEach-Object {
    if ($_ -match '^\s*GROQ_API_KEY\s*=\s*(.*)$') {
        $groqKey = $Matches[1].Trim().Trim('"').Trim("'")
    }
}
if ([string]::IsNullOrWhiteSpace($groqKey)) {
    Write-Host ""
    Write-Host "WARNING: GROQ_API_KEY is empty in .env." -ForegroundColor Yellow
    Write-Host "  Receipt LLM parsing will fail until you set a free key from https://console.groq.com" -ForegroundColor Yellow
    Write-Host "  Compose will still start; upload processing may land in PROCESSING_FAILED." -ForegroundColor Yellow
    Write-Host ""
}

Write-Host "Starting Docker Compose (postgres + ocr + backend)..."
docker compose up --build -d
if ($LASTEXITCODE -ne 0) {
    throw "docker compose up --build -d failed."
}

# First OCR image build can take several minutes; poll backend health until ready.
$healthUrl = "http://localhost:8080/health"
$maxAttempts = 60
$delaySeconds = 5
Write-Host "Waiting for $healthUrl (up to ~$($maxAttempts * $delaySeconds / 60) minutes)..."
$ready = $false
$previousPreference = $ErrorActionPreference
$ErrorActionPreference = "SilentlyContinue"
try {
    for ($i = 1; $i -le $maxAttempts; $i++) {
        try {
            $response = Invoke-WebRequest -Uri $healthUrl -UseBasicParsing -TimeoutSec 5
            if ($response.StatusCode -eq 200) {
                $ready = $true
                break
            }
        }
        catch {
            # Backend / OCR still starting.
        }
        Write-Host "  attempt $i/$maxAttempts - not ready yet"
        Start-Sleep -Seconds $delaySeconds
    }
}
finally {
    $ErrorActionPreference = $previousPreference
}

if (-not $ready) {
    throw "Backend health check did not succeed in time. Check: docker compose logs"
}

Write-Host "Backend health OK."
Write-Host ""

$FrontendDir = Join-Path $RepoRoot "frontend"
Write-Host "Installing frontend dependencies (npm install)..."
Push-Location $FrontendDir
try {
    npm install
    if ($LASTEXITCODE -ne 0) {
        throw "npm install failed in frontend/."
    }
}
finally {
    Pop-Location
}

Write-Host ""
Write-Host "Setup finished."
Write-Host ""
Write-Host "Next steps:"
Write-Host "  1. Set GROQ_API_KEY in .env if you have not already (https://console.groq.com), then:"
Write-Host "       docker compose up -d --force-recreate backend"
Write-Host "  2. Start the frontend:"
Write-Host "       cd frontend"
Write-Host "       npm run dev"
Write-Host "  3. Open http://localhost:5173"
Write-Host ""
Write-Host "Existing Postgres volumes / migration 003: see db/README.md"
Write-Host "Backend health: http://localhost:8080/health"
