# Smoke test do fluxo de compra, executado contra uma API ja no ar.
#
#   .\scripts\smoke.ps1                       # usa $env:TRADEWIND_API ou http://localhost:8081
#   .\scripts\smoke.ps1 -Api http://host:8080
#
# Sai com codigo 1 se qualquer verificacao falhar, o que faz o CI barrar o
# deploy. Nao precisa de Docker nem de banco proprio: fala so com a API.
# Nao insere dados de cartao, chave PIX ou conta - o pagamento e simulado.

[CmdletBinding()]
param(
    [string]$Api = $(if ($env:TRADEWIND_API) { $env:TRADEWIND_API } else { 'http://localhost:8081' })
)

$ErrorActionPreference = 'Stop'
$falhas = 0

function Write-Titulo($texto) { Write-Output ''; Write-Output "=== $texto ===" }

function Get-Api($rota) {
    Invoke-RestMethod "$Api$rota" -TimeoutSec 20
}

function Invoke-Post($rota, $corpo) {
    $json = $corpo | ConvertTo-Json -Depth 6
    try {
        Invoke-RestMethod -Method Post -Uri "$Api$rota" -ContentType 'application/json' -Body $json -TimeoutSec 20
    } catch {
        # Sem o corpo, um 409 nao diz nada. A API responde ProblemDetail (RFC 9457).
        $detalhe = ''
        try {
            $leitor = New-Object System.IO.StreamReader($_.Exception.Response.GetResponseStream())
            $detalhe = $leitor.ReadToEnd()
        } catch { }
        throw "POST $rota -> $([int]$_.Exception.Response.StatusCode) $detalhe"
    }
}

# Espera a API responder /actuator/health, ate 60 tentativas de 2s.
function Esperar-Api {
    for ($i = 0; $i -lt 60; $i++) {
        try {
            $saude = (Invoke-RestMethod "$Api/actuator/health" -TimeoutSec 3).status
            if ($saude -eq 'UP') { return $true }
        } catch { }
        Start-Sleep -Seconds 2
    }
    return $false
}

function Checar($descricao, $condicao, $detalhe) {
    if ($condicao) {
        Write-Output "  ok  $descricao"
    } else {
        $script:falhas++
        Write-Output "  FALHA  $descricao -> $detalhe"
    }
}

Write-Output "API alvo: $Api"

Write-Titulo 'saude da aplicacao'
Checar 'health UP' (Esperar-Api) 'a API nao respondeu UP em 120s'
Checar 'info com versao' ((Get-Api '/actuator/info').app.name -eq 'tradewind-backend') '/actuator/info sem app.name'
Checar 'endpoint de metricas' ((Invoke-WebRequest "$Api/actuator/prometheus" -UseBasicParsing -TimeoutSec 10).StatusCode -eq 200) '/actuator/prometheus nao respondeu 200'

Write-Titulo 'catalogo e estoque'
# Filtra por active=true em vez de pegar o primeiro da lista: o catalogo nao
# ordena so por recencia, e o item mais recente pode estar inativo.
$produtos = (Get-Api '/api/v1/products?active=true&size=5').content
Checar 'catalogo com ao menos 1 produto ativo' ($produtos.Count -ge 1) 'catalogo sem produtos ativos'
$estoque = Get-Api '/api/v1/inventory'
Checar 'linhas de estoque' ($estoque.Count -ge 1) 'nenhuma linha de inventario'

$alvo = $estoque | Where-Object { $_.reorderPoint -lt $_.availableQuantity } | Select-Object -First 1
if ($alvo) {
    $antes = $alvo.availableQuantity
    $pos = Invoke-Post "/api/v1/inventory/$($alvo.id)/adjust" @{ quantity = 5; reason = 'smoke: reposicao' }
    Checar 'reposicao +5' ($pos.availableQuantity -eq ($antes + 5)) "esperado $($antes + 5), veio $($pos.availableQuantity)"

    $pos2 = Invoke-Post "/api/v1/inventory/$($alvo.id)/adjust" @{ quantity = -1; reason = 'smoke: perda' }
    Checar 'perda -1' ($pos2.availableQuantity -eq ($antes + 4)) "esperado $($antes + 4), veio $($pos2.availableQuantity)"
} else {
    Write-Output '  pular  nenhuma linha com saldo acima do ponto de reposicao'
}

Write-Titulo 'regras de estoque'
# Executa algo que deve falhar e devolve o codigo HTTP obtido.
function Espera-Erro($scriptblock) {
    try {
        & $scriptblock
        return 0
    } catch {
        # Invoke-Post ja anexa "-> 409 {json}" na mensagem; GET so tem status.
        $achado = [regex]::Match($_.Exception.Message, '->\s*(\d{3})')
        if ($achado.Success) { return [int]$achado.Groups[1].Value }
        if ($_.Exception.Response) { return [int]$_.Exception.Response.StatusCode }
        return 0
    }
}
$negativo = Espera-Erro { Invoke-Post "/api/v1/inventory/$($alvo.id)/adjust" @{ quantity = -1000000; reason = 'smoke' } }
Checar 'estouro de estoque recusado' ($negativo -eq 409) "codigo $negativo, esperado 409"
$zero = Espera-Erro { Invoke-Post "/api/v1/inventory/$($alvo.id)/adjust" @{ quantity = 0; reason = 'smoke' } }
Checar 'quantidade zero recusada' ($zero -eq 409) "codigo $zero, esperado 409"
$uuid = Espera-Erro { Invoke-Post '/api/v1/inventory/nao-e-uuid/adjust' @{ quantity = 1; reason = 'smoke' } }
Checar 'uuid invalido devolve 400' ($uuid -eq 400) "codigo $uuid, esperado 400"
$rota = Espera-Erro { Get-Api '/api/v1/nao-existe' }
Checar 'rota inexistente devolve 404' ($rota -eq 404) "codigo $rota, esperado 404"

Write-Titulo 'cliente e privacidade'
$email = "smoke+$([guid]::NewGuid().ToString('N').Substring(0,8))@example.com"
$cliente = Invoke-Post '/api/v1/customers' @{ email = $email; displayName = 'Cliente Smoke'; locale = 'pt-BR'; countryCode = 'BR' }
Checar 'consentimento nasce falso' ($cliente.marketingConsent -eq $false) "veio $($cliente.marketingConsent)"
$exportado = Get-Api "/api/v1/customers/$($cliente.id)/export"
Checar 'export LGPD devolve o proprio cadastro' ($exportado.email -eq $email) "veio $($exportado.email)"

Write-Titulo 'checkout: pedido recusado (BRL)'
$skuRecusado = $produtos[0]
$pedidoRecusa = Invoke-Post '/api/v1/orders' @{
    customerEmail = $email; customerName = 'Cliente Smoke'
    locale = 'pt-BR'; countryCode = 'BR'; currency = 'BRL'
    items = @(@{ productId = $skuRecusado.id; quantity = 1 })
}
Checar 'pedido criado aguardando pagamento' ($pedidoRecusa.status -eq 'AWAITING_PAYMENT') "status $($pedidoRecusa.status)"
$pagamentoRecusa = Invoke-Post "/api/v1/orders/$($pedidoRecusa.id)/payments" @{ method = 'PIX'; outcome = 'DECLINED' }
Checar 'pagamento recusado' ($pagamentoRecusa.status -eq 'DECLINED') "status $($pagamentoRecusa.status)"
# Recusa e terminal: a API nao deixa o mesmo pedido ser pago de novo, senao
# quem erro o primeiro pagamento voltaria e levaria o estoque duas vezes.
$aposRecusa = Espera-Erro { Invoke-Post "/api/v1/orders/$($pedidoRecusa.id)/payments" @{ method = 'PIX'; outcome = 'APPROVED' } }
Checar 'recusado e terminal' ($aposRecusa -eq 409) "codigo $aposRecusa, esperado 409"

Write-Titulo 'checkout: pedido aprovado (USD)'
$skuAprovado = ($estoque | Where-Object { $_.sku -ne $skuRecusado.sku } | Select-Object -First 1)
$pedidoAprovado = Invoke-Post '/api/v1/orders' @{
    customerEmail = $email; customerName = 'Cliente Smoke'
    locale = 'en-US'; countryCode = 'US'; currency = 'USD'
    items = @(@{ productId = $skuAprovado.productId; quantity = 2 })
}
$pagamentoAprovado = Invoke-Post "/api/v1/orders/$($pedidoAprovado.id)/payments" @{ method = 'CARD'; outcome = 'APPROVED' }
Checar 'pagamento aprovado' ($pagamentoAprovado.status -eq 'APPROVED') "status $($pagamentoAprovado.status)"
Checar 'referencia de simulacao gerada' ($pagamentoAprovado.simulationReference -like 'SIM-*') "veio $($pagamentoAprovado.simulationReference)"
$confirmado = Get-Api "/api/v1/orders/$($pedidoAprovado.id)"
Checar 'pedido confirmado' ($confirmado.status -eq 'CONFIRMED') "status $($confirmado.status)"
$segundo = Espera-Erro { Invoke-Post "/api/v1/orders/$($pedidoAprovado.id)/payments" @{ method = 'CARD'; outcome = 'APPROVED' } }
Checar 'segundo pagamento bloqueado' ($segundo -eq 409) "codigo $segundo, esperado 409"

Write-Titulo 'anonimizacao (LGPD art. 16 / GDPR art. 17)'
Invoke-WebRequest -Method Delete -Uri "$Api/api/v1/customers/$($cliente.id)" -UseBasicParsing -TimeoutSec 20 | Out-Null
$anonimo = Get-Api "/api/v1/customers/$($cliente.id)"
Checar 'e-mail pseudonimizado' ($anonimo.email -like 'anon-*@anonymized.invalid') "veio $($anonimo.email)"
Checar 'nome apagado' ($anonimo.displayName -eq 'ANONYMIZED') "veio $($anonimo.displayName)"
# O historico de pedidos e registro contabil: sobrevive a anonimizacao. Ele vive
# no endpoint de export, e nao no cadastro, que so devolve os dados do titular.
$aposAnonimizacao = Get-Api "/api/v1/customers/$($cliente.id)/export"
Checar 'pedido preservado apos anonimizar' ($aposAnonimizacao.orders.Count -ge 2) "pedidos: $($aposAnonimizacao.orders.Count)"

Write-Titulo 'relatorios'
$painel = Get-Api '/api/v1/reports/dashboard?days=30'
Checar 'dashboard com janela de 30 dias' ($painel.windowDays -eq 30) "veio $($painel.windowDays)"
Checar 'taxa de conversao calculada' ($null -ne $painel.conversionRatePercent) 'campo ausente'
$recusas = Get-Api '/api/v1/reports/declined-payments?limit=10'
Checar 'lista de recusas devolve itens' ($recusas.Count -ge 1) "veio $($recusas.Count)"

Write-Output ''
if ($falhas -gt 0) {
    Write-Output "SMOKE FALHOU: $falhas verificacao(oes) com problema"
    exit 1
}
Write-Output 'SMOKE OK: todas as verificacoes passaram'
exit 0
