<#
  Prepara a maquina: JDK 17 e Maven locais em .tools/ e o arquivo .env.
  Nao instala nada no sistema (funciona sem permissao de administrador).
#>
$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
$tools = Join-Path $root '.tools'

Write-Host "==> Procurando um JDK 17" -ForegroundColor Cyan

# 1) JDK 17 do sistema
$systemJdk = Get-Command javac -ErrorAction SilentlyContinue
if ($systemJdk) {
    $version = (& $systemJdk.Source -version 2>&1) -join ' '
    if ($version -match '^javac 17\.') {
        Write-Host "    JDK 17 encontrado no sistema: $version" -ForegroundColor Green
        Set-Item -Path 'env:JAVA_HOME' -Value (Split-Path -Parent (Split-Path -Parent $systemJdk.Source))
    }
}

# 2) JDK 17 baixado em .tools/
if (-not $env:JAVA_HOME -or -not (Test-Path (Join-Path $env:JAVA_HOME 'bin\javac.exe'))) {
    $local = Get-ChildItem $tools -Directory -Filter 'jdk-17*' -ErrorAction SilentlyContinue |
             Select-Object -First 1
    if ($local) {
        Write-Host "    usando JDK local: $($local.Name)" -ForegroundColor Green
        Set-Item -Path 'env:JAVA_HOME' -Value $local.FullName
    } else {
        Write-Host "    baixando Temurin 17..." -ForegroundColor Yellow
        New-Item -ItemType Directory -Force -Path $tools | Out-Null
        $zip = Join-Path $tools 'jdk17.zip'
        Invoke-WebRequest -UseBasicParsing `
            -Uri 'https://api.adoptium.net/v3/binary/latest/17/ga/windows/x64/jdk/hotspot/normal/eclipse' `
            -OutFile $zip
        Expand-Archive -Path $zip -DestinationPath $tools -Force
        Remove-Item $zip -Force
        $local = Get-ChildItem $tools -Directory -Filter 'jdk-17*' | Select-Object -First 1
        Set-Item -Path 'env:JAVA_HOME' -Value $local.FullName
        Write-Host "    JDK instalado em $($local.FullName)" -ForegroundColor Green
    }
}

$env:PATH = "$env:JAVA_HOME\bin;$env:PATH"
& "$env:JAVA_HOME\bin\java.exe" -version 2>&1 | ForEach-Object { Write-Host "    $_" }

Write-Host "==> Arquivo .env" -ForegroundColor Cyan
$envFile = Join-Path $root '.env'
if (Test-Path $envFile) {
    Write-Host "    .env ja existe (mantido como esta)" -ForegroundColor Green
} else {
    Copy-Item (Join-Path $root '.env.example') $envFile
    Write-Host "    .env criado a partir do .env.example" -ForegroundColor Green
}

Write-Host ""
Write-Host "Setup concluido. Proximo passo:  .\scripts\dev.ps1" -ForegroundColor Cyan
