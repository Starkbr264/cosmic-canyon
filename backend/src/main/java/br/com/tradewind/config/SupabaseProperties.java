package br.com.tradewind.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuracao do Supabase (Settings -> API).
 *
 * <p>Usada pelo {@link br.com.tradewind.integration.SupabaseRestClient}, que le
 * dados direto pela API PostgREST do Supabase - util como referencia para
 * integracoes que nao passam pela aplicação.
 */
@ConfigurationProperties(prefix = "app.supabase")
public record SupabaseProperties(
        String url,
        String anonKey,
        boolean enabled
) {
    public SupabaseProperties {
        if (enabled && (url == null || url.isBlank())) {
            throw new IllegalStateException(
                    "app.supabase.enabled=true exige app.supabase.url (SUPABASE_URL)");
        }
    }
}
