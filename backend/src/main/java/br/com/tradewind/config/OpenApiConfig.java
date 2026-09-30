package br.com.tradewind.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * Metadados do contrato OpenAPI 3.0 que alimentam o Swagger UI.
 *
 * <p>O {@code /v3/api-docs} gerado aqui e a fonte da colecao Postman e do
 * cliente gerado para o frontend, entao tudo aqui precisa ser coerente.
 */
@Configuration
public class OpenApiConfig {

    private final String devServerUrl;

    public OpenApiConfig(@Value("${app.openapi.dev-server-url:http://localhost:8080}") String devServerUrl) {
        this.devServerUrl = devServerUrl;
    }

    @Bean
    public OpenAPI tradewindOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("API Tradewind")
                        .version("1.0.0")
                        .description("""
                                API de referencia do template tradewind-fullstack.

                                Roda contra o Postgres do Docker Compose (perfil `local`) ou
                                contra o Supabase (perfil `supabase`) usando o mesmo schema
                                gerenciado por Flyway.
                                """)
                        .contact(new Contact().name("Equipe Tradewind").email("dev@tradewind.local"))
                        .license(new License().name("MIT")))
                .servers(List.of(
                        new Server().url(devServerUrl).description("Local / Docker"),
                        new Server().url("https://api.tradewind.exemplo.com").description("Producao")))
                .components(new Components());
    }
}
