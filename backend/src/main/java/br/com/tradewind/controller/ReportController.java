package br.com.tradewind.controller;

import br.com.tradewind.service.ReportService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/reports")
@Tag(name = "Relatorios", description = "Indicadores do dashboard")
public class ReportController {

    private final ReportService reports;

    public ReportController(ReportService reports) {
        this.reports = reports;
    }

    @GetMapping("/dashboard")
    @Operation(summary = "Painel consolidado",
            description = "Vendas, receita por moeda, produtos mais vendidos, conversao e estoque em alerta.")
    public ReportService.Dashboard dashboard(@RequestParam(defaultValue = "30") int days) {
        return reports.dashboard(Math.max(1, Math.min(days, 365)));
    }

    @GetMapping("/declined-payments")
    @Operation(summary = "Pagamentos recusados",
            description = "Lista os eventos de recusa, que e o sinal monitorado pelo modulo antifraude simulado.")
    public List<ReportService.DeclinedPayment> declinedPayments(@RequestParam(defaultValue = "50") int limit) {
        return reports.declinedPayments(Math.max(1, Math.min(limit, 500)));
    }
}
