# Derruba o ambiente e (opcionalmente) apaga os volumes do Postgres.
#   .\scripts\stop.ps1          -> para os containers
#   .\scripts\stop.ps1 -Volume  -> apaga tambem o banco (migrations rodam de novo)

param([switch]$Volume)

$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot

Push-Location $root
try {
    if ($Volume) {
        Write-Host "==> Removendo containers e volumes (o banco local sera apagado)" -ForegroundColor Yellow
        docker compose --profile tools down -v
    } else {
        Write-Host "==> Parando containers (volume preservado)" -ForegroundColor Cyan
        docker compose --profile tools down
    }
} finally {
    Pop-Location
}

Write-Host "Pronto." -ForegroundColor Green
