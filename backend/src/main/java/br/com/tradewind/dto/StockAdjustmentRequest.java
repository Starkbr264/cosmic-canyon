package br.com.tradewind.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Ajuste manual de saldo.
 *
 * <p>A quantidade aceita valor negativo de proposito: contagem divergente, perda,
 * avaria e devolução existem em qualquer operação real, e um endpoint que só
 * aceita reposição deixa o saldo impossível de corrigir. O teto existe para o
 * cliente não enviar o {@code Integer.MIN_VALUE} por engano.
 *
 * <p>Zero é recusado pelo serviço, junto com a mensagem que nomeia a
 * localização — é uma regra de negócio, não um erro de formato.
 */
public record StockAdjustmentRequest(
        @NotNull @Min(-1_000_000) @Max(1_000_000) Integer quantity,
        @NotBlank @Size(max = 255) String reason) {
}