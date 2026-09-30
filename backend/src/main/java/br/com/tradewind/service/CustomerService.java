package br.com.tradewind.service;

import br.com.tradewind.domain.Customer;
import br.com.tradewind.dto.CustomerRequest;
import br.com.tradewind.exception.ConflictException;
import br.com.tradewind.exception.ResourceNotFoundException;
import br.com.tradewind.repository.CustomerRepository;
import br.com.tradewind.repository.SalesOrderRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Cadastro, exportacao e anonimizacao do titular.
 *
 * <p>Dois cuidados valem explicitar porque sao requisito legal, nao preferencia:
 *
 * <ul>
 *   <li>{@code marketingConsent} nasce {@code false}. Consentimento implicito
 *       no cadastro nao e consentimento (LGPD art. 8 / GDPR art. 6.1.a).</li>
 *   <li>A exclusao anonimiza em vez de apagar. O pedido e registro contabel que
 *       precisa sobreviver ao titular (LGPD art. 16 / GDPR art. 17.3.b).</li>
 * </ul>
 */
@Service
@Transactional
public class CustomerService {

    private final CustomerRepository customers;
    private final SalesOrderRepository orders;

    public CustomerService(CustomerRepository customers, SalesOrderRepository orders) {
        this.customers = customers;
        this.orders = orders;
    }

    public Customer create(CustomerRequest request) {
        String email = request.email().trim().toLowerCase();
        if (customers.findByEmailIgnoreCase(email).isPresent()) {
            throw ConflictException.customEmailAlreadyExists(email);
        }
        Customer customer = new Customer();
        customer.setEmail(email);
        customer.setDisplayName(request.displayName().trim());
        customer.setLocale(request.locale());
        customer.setCountryCode(request.countryCode());
        return customers.save(customer);
    }

    @Transactional(readOnly = true)
    public Customer findById(UUID id) {
        return customers.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Cliente nao encontrado: " + id));
    }

    /** Portabilidade dos dados: LGPD art. 18 V e GDPR art. 20. */
    @Transactional(readOnly = true)
    public CustomerExport export(UUID id) {
        Customer customer = findById(id);
        return new CustomerExport(customer.getId(), customer.getEmail(), customer.getDisplayName(),
                customer.getLocale(), customer.getCountryCode(),
                orders.findAllByCustomerId(customer.getId()).stream()
                        .map(order -> new OrderSummary(order.getOrderNumber(), order.getStatus(),
                                order.getCurrency(), order.getGrandTotal(), order.getCreatedAt()))
                        .toList());
    }

    public void anonymize(UUID id) {
        Customer customer = findById(id);
        customer.setDisplayName("ANONYMIZED");
        customer.setEmail("anon-" + pseudonymize(customer.getEmail()) + "@anonymized.invalid");
        customer.setMarketingConsent(false);
    }

    /**
     * Hash estavel do e-mail original: permite reconciliar auditoria sem manter
     * o dado pessoal legivel. Hex de 8 bytes e suficiente para um indice local
     * e nao serve como alvo de ataque por tabela pre-computada.
     */
    private String pseudonymize(String email) {
        try {
            var digest = java.security.MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(email.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            return java.util.HexFormat.of().formatHex(hash, 0, 8);
        } catch (java.security.NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 indisponivel nesta JVM", ex);
        }
    }

    public record CustomerExport(UUID id, String email, String displayName, String locale,
                                 String countryCode, List<OrderSummary> orders) {
    }

    public record OrderSummary(String orderNumber, br.com.tradewind.domain.OrderStatus status,
                               String currency, java.math.BigDecimal grandTotal,
                               java.time.OffsetDateTime createdAt) {
    }
}
