package br.com.tradewind;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/**
 * Ponto de entrada da API.
 *
 * <p>Padroes de configuracao disponíveis:
 * <ul>
 *   <li>{@code local}    - Postgres do Docker Compose (default)</li>
 *   <li>{@code supabase} - Postgres gerenciado do Supabase</li>
 *   <li>{@code test}     - H2 em memoria, para a suite de testes</li>
 * </ul>
 */
@SpringBootApplication
@ConfigurationPropertiesScan
public class TradewindApplication {

    public static void main(String[] args) {
        SpringApplication.run(TradewindApplication.class, args);
    }
}
