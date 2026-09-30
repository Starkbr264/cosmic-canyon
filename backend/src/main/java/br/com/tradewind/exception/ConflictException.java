package br.com.tradewind.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Conflito de estado: a operacao faria sentido, mas colide com o que ja existe.
 *
 * <p>Exemplo classico: criar um produto com um {@code sku} ja cadastrado.
 * Nao e 404 (o recurso alvo da requisicao nao existe) nem 400 (o payload e
 * valido) - e 409.
 */
@ResponseStatus(HttpStatus.CONFLICT)
public class ConflictException extends RuntimeException {

    public ConflictException(String message) {
        super(message);
    }

    public static ConflictException skuAlreadyExists(String sku) {
        return new ConflictException("Ja existe um produto com o sku: " + sku);
    }

    public static ConflictException customEmailAlreadyExists(String email) {
        return new ConflictException("Ja existe um cliente com o e-mail: " + email);
    }
}
