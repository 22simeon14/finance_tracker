# Verify MVP migrations on a clean temporary PostgreSQL container.
# Happy path only: apply 001 + 002, then run db/verify_happy_path.sql

$ErrorActionPreference = "Stop"

$ContainerName = if ($env:CONTAINER_NAME) { $env:CONTAINER_NAME } else { "finance_tracker_verify_pg" }
$PostgresImage = if ($env:POSTGRES_IMAGE) { $env:POSTGRES_IMAGE } else { "postgres:16" }
$PostgresUser = if ($env:POSTGRES_USER) { $env:POSTGRES_USER } else { "postgres" }
$PostgresPassword = if ($env:POSTGRES_PASSWORD) { $env:POSTGRES_PASSWORD } else { "postgres" }
$PostgresDb = if ($env:POSTGRES_DB) { $env:POSTGRES_DB } else { "finance_tracker_verify" }
$KeepContainer = if ($env:KEEP_CONTAINER) { $env:KEEP_CONTAINER } else { "0" }

$RepoRoot = Split-Path -Parent $PSScriptRoot
$Migration001 = Join-Path $RepoRoot "db/migrations/001_create_mvp_schema.sql"
$Migration002 = Join-Path $RepoRoot "db/migrations/002_seed_categories.sql"
$VerifySql = Join-Path $RepoRoot "db/verify_happy_path.sql"

function Invoke-Docker {
    param(
        [string[]]$Arguments,
        [switch]$AllowFailure
    )

    & docker @Arguments
    if (-not $AllowFailure -and $LASTEXITCODE -ne 0) {
        throw "Docker command failed: docker $($Arguments -join ' ')"
    }
}

function Remove-VerifyContainer {
    $previousPreference = $ErrorActionPreference
    $ErrorActionPreference = "SilentlyContinue"
    try {
        & docker rm -f $ContainerName *> $null
    } finally {
        $ErrorActionPreference = $previousPreference
    }
}

function Wait-ForPostgres {
    Write-Host "Waiting for PostgreSQL to become ready..."
    $ready = $false
    $previousPreference = $ErrorActionPreference
    $ErrorActionPreference = "SilentlyContinue"
    try {
        for ($i = 1; $i -le 60; $i++) {
            & docker exec $ContainerName psql -U $PostgresUser -d $PostgresDb -c "SELECT 1" *> $null
            if ($LASTEXITCODE -eq 0) {
                $ready = $true
                break
            }
            Start-Sleep -Seconds 1
        }
    } finally {
        $ErrorActionPreference = $previousPreference
    }

    if (-not $ready) {
        throw "PostgreSQL did not become ready in time."
    }
}

function Invoke-PsqlFile {
    param(
        [string]$FilePath
    )

    $fileName = Split-Path $FilePath -Leaf
    $containerPath = "/tmp/$fileName"

    Invoke-Docker -Arguments @("cp", $FilePath, "${ContainerName}:${containerPath}")
    Invoke-Docker -Arguments @(
        "exec", $ContainerName,
        "psql", "-v", "ON_ERROR_STOP=1",
        "-U", $PostgresUser,
        "-d", $PostgresDb,
        "-f", $containerPath
    )
}

try {
    Write-Host "Starting clean PostgreSQL ($PostgresImage)..."
    Remove-VerifyContainer
    Invoke-Docker -Arguments @(
        "run", "-d",
        "--name", $ContainerName,
        "-e", "POSTGRES_USER=$PostgresUser",
        "-e", "POSTGRES_PASSWORD=$PostgresPassword",
        "-e", "POSTGRES_DB=$PostgresDb",
        $PostgresImage
    ) | Out-Null

    Wait-ForPostgres

    Write-Host "Applying 001_create_mvp_schema.sql..."
    Invoke-PsqlFile -FilePath $Migration001

    Write-Host "Applying 002_seed_categories.sql..."
    Invoke-PsqlFile -FilePath $Migration002

    Write-Host "Running happy-path verification..."
    Invoke-PsqlFile -FilePath $VerifySql

    Write-Host ""
    Write-Host "Verification passed."
    Write-Host "Tables:"
    Invoke-Docker -Arguments @(
        "exec", $ContainerName,
        "psql", "-U", $PostgresUser, "-d", $PostgresDb, "-c",
        "SELECT table_name FROM information_schema.tables WHERE table_schema = 'public' ORDER BY table_name;"
    )

    if ($KeepContainer -eq "1") {
        Write-Host ""
        Write-Host "Container kept running: $ContainerName"
        Write-Host "Connect with:"
        Write-Host "  docker exec -it $ContainerName psql -U $PostgresUser -d $PostgresDb"
    }
}
finally {
    if ($KeepContainer -ne "1") {
        Remove-VerifyContainer
    }
}
