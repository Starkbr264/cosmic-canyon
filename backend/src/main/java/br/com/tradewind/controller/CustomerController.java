package br.com.tradewind.controller;

import br.com.tradewind.dto.CustomerRequest;
import br.com.tradewind.service.CustomerService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.util.UUID;

/**
 * Cadastro e direitos do titular.
 *
 * <p>Os dois endpoints finais nao sao cortesia: sao o que LGPD art. 18 e
 * GDPR art. 15/17 exigem que exista. Sem eles, "excluir meus dados" e uma
 * promessa sem implementacao.
 */
@RestController
@RequestMapping("/api/v1/customers")
@Tag(name = "Clientes", description = "Cadastro minimo, exportacao e anonimizacao")
public class CustomerController {

    private final CustomerService customers;

    public CustomerController(CustomerService customers) {
        this.customers = customers;
    }

    @PostMapping
    @Operation(summary = "Cadastra cliente",
            description = "Coleta minima: e-mail, nome, locale e pais. Consentimento de marketing e falso por tradewind.")
    public ResponseEntity<CustomerRequest.CustomerView> create(
            @Valid @RequestBody CustomerRequest request,
            UriComponentsBuilder uriBuilder) {

        var customer = customers.create(request);
        URI location = uriBuilder.path("/api/v1/customers/{id}")
                .buildAndExpand(customer.getId())
                .toUri();
        return ResponseEntity.created(location).body(CustomerRequest.CustomerView.from(customer));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Consulta cadastro")
    public CustomerRequest.CustomerView get(@PathVariable UUID id) {
        return CustomerRequest.CustomerView.from(customers.findById(id));
    }

    @GetMapping("/{id}/export")
    @Operation(summary = "Exporta os dados do titular",
            description = "Portabilidade exigida por LGPD art. 18 V e GDPR art. 20")
    public CustomerService.CustomerExport export(@PathVariable UUID id) {
        return customers.export(id);
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Anonimiza o titular",
            description = "Preserva os pedidos, que sao registro contabel; apaga o que identifica a pessoa.")
    public ResponseEntity<Void> anonymize(@PathVariable UUID id) {
        customers.anonymize(id);
        return ResponseEntity.noContent().build();
    }
}
