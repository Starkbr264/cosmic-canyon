package br.com.tradewind.integration;

import br.com.tradewind.config.SupabaseProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;

/**
 * Leitura direta na API PostgREST do Supabase, sem passar pela aplicacao.
 *
 * <p>So e instanciado quando {@code app.supabase.enabled=true}, o que mantem
 * o perfil {@code local} livre de dependencia externa. Serve como referencia
 * para relatorios que devem bater na fonte em tempo real, em vez do banco da API.
 */
@Component
@ConditionalOnProperty(name = "app.supabase.enabled", havingValue = "true")
public class SupabaseRestClient {

    private final RestClient restClient;

    public SupabaseRestClient(RestClient.Builder builder, SupabaseProperties properties) {
        this.restClient = builder
                .baseUrl(properties.url() + "/rest/v1")
                .defaultHeader("apikey", properties.anonKey())
                .defaultHeader("Authorization", "Bearer " + properties.anonKey())
                .defaultHeader("Accept-Profile", "public")
                .build();
    }

    /** Conta linhas ativas direto no PostgREST usando o header Prefer: count=exact. */
    @SuppressWarnings("unchecked")
    public long countActiveProducts() {
        List<Map<String, Object>> rows = restClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/products")
                        .queryParam("select", "id")
                        .queryParam("active", "eq.true")
                        .queryParam("limit", "1")
                        .build())
                .header("Prefer", "count=exact")
                .retrieve()
                .body(List.class);

        return rows == null ? 0 : rows.size();
    }
}
