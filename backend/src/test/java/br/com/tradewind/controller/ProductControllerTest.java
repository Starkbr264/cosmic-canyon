package br.com.tradewind.controller;

import br.com.tradewind.domain.Product;
import br.com.tradewind.service.ProductService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Teste do {@link ProductController} isolando o service com mock.
 *
 * <p>Sobe apenas a camada web (nao o banco, nao o JPA), entao o controller e
 * testado de verdade: routing, serializacao, validacao do Bean Validation e o
 * formato de erro RFC 9457.
 *
 * <p>Usa {@code @MockitoBean} (Boot 3.4+), que substitui o {@code @MockBean},
 * hoje descontinuado.
 */
@WebMvcTest(ProductController.class)
@DisplayName("ProductController - MockMvc + Mockito")
class ProductControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ProductService service;

    private static Product product(UUID id) {
        return Product.builder()
                .id(id)
                .sku("SKU-001")
                .name("Cafe Torrado 500g")
                .description("Torra media")
                .price(new BigDecimal("39.90"))
                .active(true)
                .build();
    }

    @Test
    @DisplayName("GET /api/v1/products/{id} devolve 200 e o corpo do produto")
    void deveBuscarPorId() throws Exception {
        UUID id = UUID.randomUUID();
        when(service.findById(id)).thenReturn(product(id));

        mockMvc.perform(get("/api/v1/products/{id}", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id.toString()))
                .andExpect(jsonPath("$.sku").value("SKU-001"))
                .andExpect(jsonPath("$.price").value(39.90));
    }

    @Test
    @DisplayName("GET inexistente devolve 404 em Problem Details")
    void deveDevolver404() throws Exception {
        UUID id = UUID.randomUUID();
        when(service.findById(id)).thenThrow(new br.com.tradewind.exception.ResourceNotFoundException(
                "Produto nao encontrado: " + id));

        mockMvc.perform(get("/api/v1/products/{id}", id))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.title").value("Recurso nao encontrado"))
                .andExpect(jsonPath("$.detail").value("Produto nao encontrado: " + id));
    }

    @Test
    @DisplayName("GET lista devolve 200 com o envelope de pagina")
    void deveListar() throws Exception {
        when(service.search(any(), any(), any(), any()))
                .thenReturn(new PageImpl<>(List.of(product(UUID.randomUUID())),
                        PageRequest.of(0, 20), 1));

        mockMvc.perform(get("/api/v1/products")
                        .param("active", "true")
                        .param("page", "0")
                        .param("size", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(20))
                .andExpect(jsonPath("$.first").value(true));
    }

    @Test
    @DisplayName("POST valido devolve 201 com header Location")
    void deveCriarComLocation() throws Exception {
        UUID id = UUID.randomUUID();
        when(service.create(any())).thenReturn(product(id));

        mockMvc.perform(post("/api/v1/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "sku": "SKU-001",
                                  "name": "Cafe Torrado 500g",
                                  "description": "Torra media",
                                  "price": 39.90,
                                  "active": true
                                }
                                """))
                .andExpect(status().isCreated())
                // Location sai absoluto porque o UriComponentsBuilder injeta o host/esquema
                .andExpect(header().string("Location",
                        org.hamcrest.Matchers.endsWith("/api/v1/products/" + id)))
                .andExpect(jsonPath("$.id").value(id.toString()));
    }

    @Test
    @DisplayName("POST com price ausente devolve 400 e nao chama o service")
    void deveRejeitarPayloadInvalido() throws Exception {
        mockMvc.perform(post("/api/v1/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "sku": "SKU-001", "name": "Sem preco" }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Validacao falhou"))
                .andExpect(jsonPath("$.fields.price").exists())
                .andExpect(jsonPath("$.fields.sku").doesNotExist());

        verify(service, never()).create(any());
    }

    @Test
    @DisplayName("POST com preco negativo devolve 400")
    void deveRejeitarPrecoNegativo() throws Exception {
        mockMvc.perform(post("/api/v1/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "sku": "SKU-002", "name": "Ruim", "price": -1.00 }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fields.price").exists());
    }

    @Test
    @DisplayName("POST com sku duplicado devolve 409 Conflict")
    void deveDevolver409EmSkuDuplicado() throws Exception {
        when(service.create(any())).thenThrow(
                br.com.tradewind.exception.ConflictException.skuAlreadyExists("SKU-001"));

        mockMvc.perform(post("/api/v1/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "sku": "SKU-001", "name": "Repetido", "price": 10.00 }
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.title").value("Conflito de recurso"))
                .andExpect(jsonPath("$.detail").value("Ja existe um produto com o sku: SKU-001"));
    }

    @Test

    void deveAtualizar() throws Exception {
        UUID id = UUID.randomUUID();
        when(service.update(eq(id), any())).thenReturn(product(id));

        mockMvc.perform(put("/api/v1/products/{id}", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "sku": "SKU-001", "name": "Cafe Torrado 500g", "price": 44.90 }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id.toString()));
    }

    @Test
    @DisplayName("DELETE devolve 204 e sem corpo")
    void deveRemover() throws Exception {
        UUID id = UUID.randomUUID();

        mockMvc.perform(delete("/api/v1/products/{id}", id))
                .andExpect(status().isNoContent())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
                        .content().string(""));

        verify(service).delete(id);
    }

    @Test
    @DisplayName("GET /by-sku/{sku} delega para o service pelo sku")
    void deveBuscarPorSku() throws Exception {
        when(service.findBySku("SKU-001")).thenReturn(product(UUID.randomUUID()));

        mockMvc.perform(get("/api/v1/products/by-sku/{sku}", "SKU-001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sku").value("SKU-001"));

        verify(service).findBySku("SKU-001");
    }

    @Test
    @DisplayName("size fora do intervalo e limitado em vez de quebrar")
    void deveLimitarSize() throws Exception {
        when(service.search(isNull(), isNull(), isNull(), any()))
                .thenReturn(new PageImpl<>(List.of()));

        mockMvc.perform(get("/api/v1/products").param("size", "9999"))
                .andExpect(status().isOk());

        var captor = org.mockito.ArgumentCaptor.forClass(org.springframework.data.domain.Pageable.class);
        verify(service).search(isNull(), isNull(), isNull(), captor.capture());
        org.assertj.core.api.Assertions.assertThat(captor.getValue().getPageSize()).isEqualTo(100);
    }
}
