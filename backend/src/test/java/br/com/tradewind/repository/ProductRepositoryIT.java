package br.com.tradewind.repository;

import br.com.tradewind.domain.Product;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.lifecycle.Startable;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Teste de integracao real: Postgres de verdade, com o schema criado pelo
 * Flyway a partir dos mesmos arquivos de migracao usados em dev, no Docker e
 * no Supabase.
 *
 * <p>Existe para pegar o que mock nao pega: SQL invalido, mapeamento JPA,
 * constraint, divergencia entre entidade e schema.
 *
 * <p><b>Duas formas de executar:</b>
 * <ol>
 *   <li>Padrão: Testcontainers sobe um Postgres descartavel. Exige Docker
 *       com socket acessivel ao JVM.</li>
 *   <li>Com {@code -Dit.jdbc.url=jdbc:postgresql://localhost:5433/tradewind
 *       -Dit.jdbc.user=tradewind -Dit.jdbc.password=tradewind_dev}, reaproveita um
 *       Postgres ja em pe. Util quando o Testcontainers nao alcanca o Docker
 *       (named pipes do Docker Desktop no Windows) - o Postgres do
 *       {@code docker compose} do projeto serve.</li>
 * </ol>
 *
 * <p>Rodar com {@code mvnw verify -Pintegration}.
 */
@SpringBootTest
@ActiveProfiles("integration")
@Testcontainers
@DisplayName("ProductRepository - Postgres real")
class ProductRepositoryIT {

    /**
     * Quando definido, pula o Testcontainers e usa este Postgres. Formato:
     * {@code -Dit.jdbc.url=... -Dit.jdbc.user=... -Dit.jdbc.password=...}
     */
    private static final String EXTERNAL_URL = System.getProperty("it.jdbc.url");

    private static final boolean USE_EXTERNAL_DB = EXTERNAL_URL != null && !EXTERNAL_URL.isBlank();

    private static final PostgreSQLContainer<?> TESTCONTAINER = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("tradewind_it")
            .withUsername("tradewind")
            .withPassword("tradewind");

    @Container
    static final Startable DOCKER = USE_EXTERNAL_DB
            ? new Startable() {
                @Override public void start() { /* usa o Postgres ja em pe */ }
                @Override public void stop()  { /* nada a encerrar */ }
              }
            : TESTCONTAINER;

    @DynamicPropertySource
    static void datasourceProperties(DynamicPropertyRegistry registry) {
        if (USE_EXTERNAL_DB) {
            registry.add("spring.datasource.url", () -> EXTERNAL_URL);
            registry.add("spring.datasource.username",
                    () -> System.getProperty("it.jdbc.user", "tradewind"));
            registry.add("spring.datasource.password",
                    () -> System.getProperty("it.jdbc.password", "tradewind_dev"));
            return;
        }
        registry.add("spring.datasource.url", TESTCONTAINER::getJdbcUrl);
        registry.add("spring.datasource.username", TESTCONTAINER::getUsername);
        registry.add("spring.datasource.password", TESTCONTAINER::getPassword);
    }

    @Autowired
    private ProductRepository repository;

    @Autowired
    private InventoryRepository inventory;

    /**
     * SKUs criados pelo teste, removidos no fim de cada caso.
     *
     * <p>Na forma "Postgres ja em pe" o banco e o mesmo do desenvolvimento, e
     * produto de teste sobrando no catalogo aparece no dashboard sem que ninguem
     * tenha criado. A limpeza apaga o saldo antes do produto porque
     * {@code inventory.product_id} e uma FK restritiva.
     */
    private final List<String> skusCriados = new ArrayList<>();

    @AfterEach
    void limparProdutosCriados() {
        for (String sku : skusCriados) {
            repository.findBySku(sku).ifPresent(product -> {
                inventory.deleteByProductId(product.getId());
                repository.delete(product);
            });
        }
        repository.flush();
        skusCriados.clear();
    }

    @Test
    @DisplayName("a migration V1 cria a tabela e a V2 popula o catalogo")
    void migrationsDevemTerRodado() {
        long total = repository.count();
        assertThat(total).isGreaterThanOrEqualTo(5);
        assertThat(repository.findBySku("SKU-001")).isPresent();
    }

    @Test
    @DisplayName("save + findBySku persists e devolve os campos")
    void devePersistir() {
        Product novo = new Product();
        novo.setSku("SKU-IT-" + UUID.randomUUID());
        novo.setName("Produto de Integracao");
        novo.setPrice(new BigDecimal("10.50"));
        novo.setActive(true);

        repository.save(novo);
        repository.flush();
        skusCriados.add(novo.getSku());

        Optional<Product> encontrado = repository.findBySku(novo.getSku());
        assertThat(encontrado).isPresent();
        assertThat(encontrado.get().getName()).isEqualTo("Produto de Integracao");
        assertThat(encontrado.get().getPrice()).isEqualByComparingTo("10.50");
        assertThat(encontrado.get().getCreatedAt()).isNotNull();
    }

    @Test
    @DisplayName("a constraint de preco negativo e respeitada pelo banco")
    void deveRejeitarPrecoNegativo() {
        Product invalido = new Product();
        invalido.setSku("SKU-NEG-" + UUID.randomUUID());
        invalido.setName("Preco invalido");
        invalido.setPrice(new BigDecimal("-1.00"));
        invalido.setActive(true);

        assertThat(catchThrowableSave(invalido))
                .isNotNull();
        skusCriados.add(invalido.getSku());
    }

    @Test
    @DisplayName("sku unico gera violacao de constraint")
    void deveRejeitarSkuDuplicado() {
        Product duplicado = new Product();
        duplicado.setSku("SKU-001"); // ja existe na seed
        duplicado.setName("Duplicado");
        duplicado.setPrice(new BigDecimal("1.00"));
        duplicado.setActive(true);

        assertThat(catchThrowableSave(duplicado)).isNotNull();
    }

    @Test
    @DisplayName("search filtra por padrão LIKE, ativo e preco minimo")
    void deveFiltrarNaQuery() {
        Page<Product> pagina = repository.search(
                "%cafe%", null, null, PageRequest.of(0, 10, Sort.unsorted()));
        assertThat(pagina.getTotalElements()).isGreaterThan(0);

        Page<Product> ativos = repository.search(
                null, true, null, PageRequest.of(0, 10, Sort.unsorted()));
        assertThat(ativos.getContent()).allMatch(Product::isActive);

        Page<Product> caros = repository.search(
                null, null, new BigDecimal("50.00"), PageRequest.of(0, 10, Sort.unsorted()));
        assertThat(caros.getContent())
                .allMatch(p -> p.getPrice().compareTo(new BigDecimal("50.00")) >= 0);

        // parametro null nao pode virar erro de tipo no Postgres
        assertThat(repository.search(null, null, null, PageRequest.of(0, 10, Sort.unsorted()))
                .getTotalElements()).isGreaterThan(0);
    }

    private Throwable catchThrowableSave(Product product) {
        try {
            repository.saveAndFlush(product);
            return null;
        } catch (Throwable t) {
            return t;
        }
    }
}
