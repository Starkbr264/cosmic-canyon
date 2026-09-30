<#
  Sobe o ambiente de desenvolvimento: Postgres (Docker) + API + frontend.
  Use .\scripts\dev.ps1 banco|api|frontend|tudo
#>
param(
    [ValidateSet('tudo', 'banco', 'api', 'frontend')]
    [string]$Alvo = 'tudo'
)

$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot

. (Join-Path $PSScriptRoot 'env.ps1')
$tools = Join-Path $root '.tools'

# JDK para o Maven: usa o do sistema se for 17+, senao o de .tools
if (-not $env:JAVA_HOME) {
    $local = Get-ChildItem $tools -Directory -Filter 'jdk-17*' -ErrorAction SilentlyContinue |
             Select-Object -First 1
    if ($local) { $env:JAVA_HOME = $local.FullName }
}
$apiPort = if ($env:API_PORT) { $env:API_PORT } else { '8080' }

if ($Alvo -in @('tudo', 'banco')) {
    Write-Host "==> Docker Compose (postgres)" -ForegroundColor Cyan
    Push-Location $root
    try { docker compose up -d postgres } finally { Pop-Location }
}

if ($Alvo -in @('tudo', 'api')) {
    Write-Host "==> API Spring Boot em http://localhost:$apiPort" -ForegroundColor Cyan
    Write-Host "    Swagger UI: http://localhost:$apiPort/swagger-ui.html" -ForegroundColor DarkGray

    $mvnw = if ($env:OS -eq 'Windows_NT') { 'mvnw.cmd' } else { 'mvnw' }
    Start-Process powershell -ArgumentList @(
        '-NoExit', '-Command',
        "& { `$env:JAVA_HOME='$env:JAVA_HOME'; & '$root\backend\$mvnw' -f '$root\backend\pom.xml' spring-boot:run }"
    ) -WorkingDirectory (Join-Path $root 'backend')
}

if ($Alvo -in @('tudo', 'frontend')) {
    Write-Host "==> Frontend Next.js em http://localhost:3000" -ForegroundColor Cyan
    $frontend = Join-Path $root 'frontend'
    if (-not (Test-Path (Join-Path $frontend 'node_modules'))) {
        Write-Host "    instalando dependencias (npm install)..." -ForegroundColor Yellow
        Push-Location $frontend
        try { npm install } finally { Pop-Location }
    }
    $env:NEXT_PUBLIC_API_URL = "http://localhost:$apiPort"
    Push-Location $frontend
    try { npm run dev } finally { Pop-Location }
}

Write-Host ""
Write-Host "Ambiente no ar. Ctrl+C encerra cada processo." -ForegroundColor Green
