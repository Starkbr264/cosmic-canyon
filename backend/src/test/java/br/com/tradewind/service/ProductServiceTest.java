package br.com.tradewind.service;

import br.com.tradewind.domain.Product;
import br.com.tradewind.dto.ProductRequest;
import br.com.tradewind.exception.ConflictException;
import br.com.tradewind.exception.ResourceNotFoundException;
import br.com.tradewind.repository.ProductRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Teste unitario puro do {@link ProductService}.
 *
 * <p>Nao sobe o contexto do Spring: {@code MockitoExtension} cria o mock do
 * repositorio, injeta no service e valida a verificacao. Roda em milissegundos,
 * sem Docker e sem banco - por isso cobre as regras de negocio, que e onde
 * mora a logica.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("ProductService - Mockito")
class ProductServiceTest {

    @Mock
    private ProductRepository repository;

    @InjectMocks
    private ProductService service;

    @Captor
    private ArgumentCaptor<Product> productCaptor;

    private static Product existingProduct(UUID id) {
        return Product.builder()
                .id(id)
                .sku("SKU-001")
                .name("Cafe Torrado 500g")
                .description("Torra media")
                .price(new BigDecimal("39.90"))
                .active(true)
                .build();
    }

    private static ProductRequest createRequest() {
        return new ProductRequest(
                "SKU-010",
                "Chá Verde 100g",
                "Sencha",
                new BigDecimal("29.90"),
                true
        );
    }

    @Nested
    @DisplayName("create")
    class Create {

        @Test
        @DisplayName("salva e devolve o produto com o id gerado")
        void deveSalvarProduto() {
            ProductRequest request = createRequest();
            when(repository.existsBySku(request.sku())).thenReturn(false);
            when(repository.save(any(Product.class))).thenAnswer(inv -> {
                Product p = inv.getArgument(0);
                p.setId(UUID.randomUUID());
                return p;
            });

            Product result = service.create(request);

            assertThat(result.getId()).isNotNull();
            assertThat(result.getSku()).isEqualTo("SKU-010");
            assertThat(result.isActive()).isTrue();
            verify(repository).save(productCaptor.capture());
            assertThat(productCaptor.getValue().getName()).isEqualTo("Chá Verde 100g");
        }

        @Test
        @DisplayName("recusa sku duplicado sem tocar no save")
        void deveRecusarSkuDuplicado() {
            ProductRequest request = createRequest();
            when(repository.existsBySku(request.sku())).thenReturn(true);

            assertThatThrownBy(() -> service.create(request))
                    .isInstanceOf(ConflictException.class)
                    .hasMessageContaining("SKU-010");

            verify(repository, never()).save(any());
        }

        @Test
        @DisplayName("trata active nulo como true")
        void deveAssumirAtivoQuandoNull() {
            ProductRequest request = new ProductRequest(
                    "SKU-011", "Copo termico", null, new BigDecimal("59.90"), null);
            when(repository.existsBySku(request.sku())).thenReturn(false);
            when(repository.save(any(Product.class))).thenAnswer(inv -> inv.getArgument(0));

            Product result = service.create(request);

            assertThat(result.isActive()).isTrue();
        }
    }

    @Nested
    @DisplayName("findById")
    class FindById {

        @Test
        @DisplayName("retorna o produto quando existe")
        void deveRetornarProduto() {
            UUID id = UUID.randomUUID();
            Product product = existingProduct(id);
            when(repository.findById(id)).thenReturn(Optional.of(product));

            assertThat(service.findById(id)).isSameAs(product);
        }

        @Test
        @DisplayName("lanca NotFound quando nao existe")
        void deveLancarNotFound() {
            UUID id = UUID.randomUUID();
            when(repository.findById(id)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.findById(id))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining(id.toString());
        }
    }

    @Nested
    @DisplayName("update")
    class Update {

        @Test
        @DisplayName("atualiza quando o sku pertence ao proprio registro")
        void deveAtualizarSemConflitoDeSku() {
            UUID id = UUID.randomUUID();
            Product product = existingProduct(id);
            when(repository.findById(id)).thenReturn(Optional.of(product));
            when(repository.save(any(Product.class))).thenAnswer(inv -> inv.getArgument(0));

            ProductRequest request = new ProductRequest(
                    "SKU-001", "Cafe Torrado 500g (novo)", "Reprocessado",
                    new BigDecimal("44.90"), false);

            Product result = service.update(id, request);

            assertThat(result.getName()).isEqualTo("Cafe Torrado 500g (novo)");
            assertThat(result.getPrice()).isEqualByComparingTo("44.90");
            assertThat(result.isActive()).isFalse();
            // sku igual ao atual: nao deve nem consultar existsBySku
            verify(repository, never()).existsBySku(any());
        }

        @Test
        @DisplayName("bloqueia troca para um sku ja usado por outro produto")
        void deveBloquearSkuEmUso() {
            UUID id = UUID.randomUUID();
            Product product = existingProduct(id);
            when(repository.findById(id)).thenReturn(Optional.of(product));

            ProductRequest request = new ProductRequest(
                    "SKU-999", "Produto conflitante", null, new BigDecimal("10.00"), true);
            when(repository.existsBySku("SKU-999")).thenReturn(true);

            assertThatThrownBy(() -> service.update(id, request))
                    .isInstanceOf(ConflictException.class);

            verify(repository, never()).save(any());
        }
    }

    @Nested
    @DisplayName("delete")
    class Delete {

        @Test
        @DisplayName("remove o produto existente")
        void deveRemover() {
            UUID id = UUID.randomUUID();
            when(repository.findById(id)).thenReturn(Optional.of(existingProduct(id)));

            service.delete(id);

            verify(repository).delete(any(Product.class));
        }

        @Test
        @DisplayName("nao chama delete quando o produto nao existe")
        void naoDeveRemoverInexistente() {
            UUID id = UUID.randomUUID();
            when(repository.findById(id)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.delete(id))
                    .isInstanceOf(ResourceNotFoundException.class);

            verify(repository, never()).delete(any(Product.class));
        }
    }

    @Nested
    @DisplayName("search")
    class Search {

        @Test
        @DisplayName("normaliza termo em branco para null antes de consultar")
        void deveNormalizarTermoEmBranco() {
            when(repository.search(any(), any(), any(), any()))
                    .thenReturn(new org.springframework.data.domain.PageImpl<>(List.of()));

            service.search("   ", null, null,
                    org.springframework.data.domain.PageRequest.of(0, 20));

            verify(repository).search(isNull(), isNull(), isNull(), any());
        }

        @Test
        @DisplayName("monta o padrÃ£o LIKE em minÃºsculo e com curingas escapados")
        void deveMontarLikeComCuringasEscapados() {
            when(repository.search(any(), any(), any(), any()))
                    .thenReturn(new org.springframework.data.domain.PageImpl<>(List.of()));

            service.search("  CAFÉ 100%_off  ", null, null,
                    org.springframework.data.domain.PageRequest.of(0, 20));

            verify(repository).search(eq("%café 100\\%\\_off%"), isNull(), isNull(), any());
        }

        @Test
        @DisplayName("termo nulo nao vira wildcard que traz tudo")
        void naoDeveBuscarTudoSemTermo() {
            when(repository.search(any(), any(), any(), any()))
                    .thenReturn(new org.springframework.data.domain.PageImpl<>(List.of()));

            service.search(null, null, null, org.springframework.data.domain.PageRequest.of(0, 20));

            verify(repository).search(isNull(), isNull(), isNull(), any());
        }
    }

    @Test
    @DisplayName("findBySku delega ao repositorio")
    void deveDelegarFindBySku() {
        Product product = existingProduct(UUID.randomUUID());
        when(repository.findBySku("SKU-001")).thenReturn(Optional.of(product));

        assertThat(service.findBySku("SKU-001")).isSameAs(product);
        verify(repository).findBySku("SKU-001");
    }

    @Test
    @DisplayName("servico nao interage com o repositorio no construtor")
    void naoDeveInteragirNoConstrutor() {
        verifyNoInteractions(repository);
    }
}
