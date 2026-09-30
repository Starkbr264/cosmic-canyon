package br.com.tradewind.controller;

import br.com.tradewind.domain.Product;
import br.com.tradewind.dto.PageResponse;
import br.com.tradewind.dto.ProductRequest;
import br.com.tradewind.dto.ProductResponse;
import br.com.tradewind.service.ProductService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.UriComponentsBuilder;

import java.math.BigDecimal;
import java.net.URI;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/products")
@Tag(name = "Produtos", description = "CRUD do catalogo de produtos")
public class ProductController {

    private final ProductService service;

    public ProductController(ProductService service) {
        this.service = service;
    }

    @GetMapping
    @Operation(summary = "Lista produtos", description = "Filtros sao combinados; todos sao opcionais.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Pagina de produtos",
                    content = @Content(schema = @Schema(implementation = PageResponse.class)))
    })
    public PageResponse<ProductResponse> list(
            @Parameter(description = "Busca por nome ou sku")
            @RequestParam(required = false) String term,

            @Parameter(description = "Filtra por disponibilidade")
            @RequestParam(required = false) Boolean active,

            @Parameter(description = "Preco minimo")
            @RequestParam(required = false) BigDecimal minPrice,

            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        // O desempate por SKU nao e enfeite: sem ele, dois produtos com o mesmo
        // createdAt (o seed insere os cinco de uma vez) saem em ordem que o
        // Postgres escolhe, e a paginacao passa a repetir ou pular linha entre
        // requisicoes. A ordem secundaria precisa ser unica para ser estavel.
        Pageable pageable = PageRequest.of(
                Math.max(page, 0),
                Math.min(Math.max(size, 1), 100),
                Sort.by(Sort.Order.desc("createdAt"), Sort.Order.asc("sku"))
        );

        return PageResponse.from(service.search(term, active, minPrice, pageable), ProductResponse::from);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Busca produto por id")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Produto encontrado"),
            @ApiResponse(responseCode = "404", description = "Produto inexistente")
    })
    public ProductResponse getById(@PathVariable UUID id) {
        return ProductResponse.from(service.findById(id));
    }

    @GetMapping("/by-sku/{sku}")
    @Operation(summary = "Busca produto por sku")
    public ProductResponse getBySku(@PathVariable String sku) {
        return ProductResponse.from(service.findBySku(sku));
    }

    @PostMapping
    @Operation(summary = "Cria produto")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Produto criado"),
            @ApiResponse(responseCode = "400", description = "Payload invalido"),
            @ApiResponse(responseCode = "409", description = "Sku ja cadastrado")
    })
    public ResponseEntity<ProductResponse> create(
            @Valid @RequestBody ProductRequest request,
            UriComponentsBuilder uriBuilder) {

        Product created = service.create(request);
        URI location = uriBuilder.path("/api/v1/products/{id}")
                .buildAndExpand(created.getId())
                .toUri();

        return ResponseEntity.created(location).body(ProductResponse.from(created));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Atualiza produto")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Produto atualizado"),
            @ApiResponse(responseCode = "404", description = "Produto inexistente"),
            @ApiResponse(responseCode = "409", description = "Sku ja usado por outro produto")
    })
    public ProductResponse update(@PathVariable UUID id, @Valid @RequestBody ProductRequest request) {
        return ProductResponse.from(service.update(id, request));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Remove produto")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Produto removido"),
            @ApiResponse(responseCode = "404", description = "Produto inexistente")
    })
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        service.delete(id);
        return ResponseEntity.status(HttpStatus.NO_CONTENT).build();
    }
}
