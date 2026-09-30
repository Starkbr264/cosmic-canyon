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

    /**
     * Apagar o produto colidiria com o historico que ele tem. A saida correta e
     * desativar, nao destruir o registro.
     *
     * <p>A mensagem diz quantas pendencias existem em cada tabela de proposito:
     * um "nao pode remover" seco deixa o usuario sem saber se falta Estoque,
     * pedido, ou as duas coisas.
     */
    public static ConflictException productHasReferences(String sku, long inventoryRows, long orderItemRows) {
        StringBuilder detail = new StringBuilder("Produto ").append(sku)
                .append(" nao pode ser removido porque ");
        detail.append(inventoryRows == 1 ? "tem 1 linha de estoque" : "tem " + inventoryRows + " linhas de estoque");
        if (inventoryRows > 0 && orderItemRows > 0) {
            detail.append(" e ");
        }
        if (orderItemRows > 1) {
            detail.append("aparece em ").append(orderItemRows).append(" itens de pedido");
        } else if (orderItemRows == 1) {
            detail.append("aparece em 1 item de pedido");
        } else if (inventoryRows == 0) {
            detail.append("nao tem estoque nem itens de pedido");
        }
        return new ConflictException(detail
                + ". Desative-o (active=false) em vez de remover: o historico precisa ser preservado.");
    }
}
