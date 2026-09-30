package br.com.tradewind.controller;

import br.com.tradewind.dto.InventoryView;
import br.com.tradewind.dto.StockAdjustmentRequest;
import br.com.tradewind.service.InventoryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/inventory")
@Tag(name = "Estoque", description = "Saldo por produto e localizacao")
public class InventoryController {

    private final InventoryService inventory;

    public InventoryController(InventoryService inventory) {
        this.inventory = inventory;
    }

    @GetMapping
    @Operation(summary = "Lista saldos",
            description = "`onlyLowStock=true` devolve so o que esta no ou abaixo do ponto de reposicao.")
    public List<InventoryView> list(@RequestParam(defaultValue = "false") boolean onlyLowStock) {
        return inventory.listInventory(onlyLowStock);
    }

    @PostMapping("/{id}/adjust")
    @Operation(summary = "Ajusta saldo manualmente",
            description = "Quantidade positiva repõe, negativa consome. Todo ajuste vira movimentacao com motivo.")
    public InventoryView adjust(@PathVariable UUID id, @Valid @RequestBody StockAdjustmentRequest request) {
        return inventory.adjust(id, request);
    }

    @GetMapping("/forecast")
    @Operation(summary = "Previsao de demanda",
            description = "Media movel das saidas confirmadas por janela. Sugere reposicao e dias de cobertura.")
    public List<InventoryService.ForecastRow> forecast(@RequestParam(defaultValue = "7") int windowDays) {
        return inventory.forecast(Math.max(1, Math.min(windowDays, 90)));
    }
}
