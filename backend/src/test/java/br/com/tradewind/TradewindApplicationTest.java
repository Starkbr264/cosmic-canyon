package br.com.tradewind;

import br.com.tradewind.repository.ProductRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Smoke test do contexto completo: sobe controller + service + JPA + datasource
 * de verdade, sem mock nenhum.
 *
 * <p>Existe para pegar erro de infraestrutura que os testes com Mockito nao
 * veem - bean faltando, perfil sem datasource, propriedade nao resolvida. Foi
 * exatamente assim que apareceu o
 * {@code Failed to determine a suitable driver class} ao rodar a aplicacao
 * sem {@code SPRING_PROFILES_ACTIVE}: nenhum teste unitario acusaria aquilo,
 * porque mock nao depende de datasource.
 *
 * <p>Roda em segundos no H2 ({@code application-test.yml}). O schema real,
 * com migrations, e coberto pelo {@code ProductRepositoryIT}.
 */
@SpringBootTest
@ActiveProfiles("test")
@DisplayName("Contexto Spring completo (H2, sem Docker)")
class TradewindApplicationTest {

    @Autowired
    private WebApplicationContext webContext;

    @Autowired
    private ProductRepository repository;

    @Test
    @DisplayName("o contexto sobe: datasource, JPA e web layer")
    void contextoDeveSubir() {
        assertThat(repository).isNotNull();
        assertThat(webContext).isNotNull();
    }

    @Test
    @DisplayName("a API responde de verdade, do controller ate o banco")
    void apiDeveResponder() throws Exception {
        MockMvc mockMvc = MockMvcBuilders.webAppContextSetup(webContext).build();

        mockMvc.perform(get("/api/v1/products"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.totalElements").exists());
    }

    @Test
    @DisplayName("o healthcheck reporta o banco como UP")
    void healthDeveEstarUp() throws Exception {
        MockMvc mockMvc = MockMvcBuilders.webAppContextSetup(webContext).build();

        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
    }
}
