<#
  Carrega o .env na sessao do PowerShell.
  Use com ponto:  . .\scripts\env.ps1
  Sem isso, o Spring Boot nao enxerga as variaveis que so o Docker Compose le.
#>
param([string]$Path = "$PSScriptRoot\..\.env")

if (-not (Test-Path $Path)) {
    Write-Host "[env] .env nao encontrado em $Path - usando padroes do compose" -ForegroundColor Yellow
    return
}

Get-Content $Path | ForEach-Object {
    $line = $_.Trim()
    if (-not $line -or $line.StartsWith('#')) { return }

    $parts = $line -split '=', 2
    if ($parts.Count -ne 2) { return }

    $name = $parts[0].Trim()
    $value = $parts[1].Trim().Trim('"').Trim("'")
    Set-Item -Path "env:$name" -Value $value
}

Write-Host "[env] variaveis carregadas de $Path" -ForegroundColor Green
