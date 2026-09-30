package br.com.tradewind.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.UUID;

@Schema(name = "CustomerRequest", description = "Cadastro de cliente. Coleta minima: sem documento, telefone ou endereco.")
public record CustomerRequest(

        @NotBlank @Email @Size(max = 320) String email,

        @NotBlank @Size(max = 160) String displayName,

        @Pattern(regexp = "[a-z]{2}(-[A-Z]{2})?", message = "deve usar locale BCP-47, por exemplo pt-BR")
        String locale,

        @Pattern(regexp = "[A-Z]{2}", message = "deve usar ISO-3166 alpha-2, por exemplo BR")
        String countryCode) {

    public record CustomerView(UUID id, String email, String displayName, String locale,
                               String countryCode, boolean marketingConsent) {

        public static CustomerView from(br.com.tradewind.domain.Customer customer) {
            return new CustomerView(customer.getId(), customer.getEmail(), customer.getDisplayName(),
                    customer.getLocale(), customer.getCountryCode(), customer.isMarketingConsent());
        }
    }
}
