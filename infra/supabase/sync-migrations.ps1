# Copia as migrations do Flyway para o formato da Supabase CLI.
#   .\infra\supabase\sync-migrations.ps1
#
# A CLI do Supabase usa o mesmo padrão de versionamento (V<n>__<descricao>.sql),
# entao na pratica e uma copia. O script so cria a pasta e avisa sobre a
# divergencia de historico, que precisa de atencao manual.

$ErrorActionPreference = 'Stop'

$root = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)
$source = Join-Path $root 'backend\src\main\resources\db\migration'
$target = Join-Path $root 'supabase\migrations'

if (-not (Test-Path $source)) {
    Write-Error "Pasta de migrations nao encontrada: $source"
}

New-Item -ItemType Directory -Force -Path $target | Out-Null
Copy-Item (Join-Path $source '*.sql') $target -Force

$migrations = Get-ChildItem $target -Filter '*.sql' | Sort-Object Name
Write-Host "Migrations copiadas para $target :" -ForegroundColor Green
$migrations | ForEach-Object { Write-Host "  $($_.Name)" }

if ($migrations.Count -eq 0) {
    Write-Host "Nenhuma migration encontrada." -ForegroundColor Yellow
}

Write-Host ""
Write-Host "Se o projeto Supabase ja tinha um historico, confira" -ForegroundColor Yellow
Write-Host "flyway_schema_history antes de misturar os dois gerenciadores." -ForegroundColor Yellow
