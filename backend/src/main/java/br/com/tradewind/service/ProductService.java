package br.com.tradewind.service;

import br.com.tradewind.domain.Product;
import br.com.tradewind.dto.ProductRequest;
import br.com.tradewind.exception.ConflictException;
import br.com.tradewind.exception.ResourceNotFoundException;
import br.com.tradewind.repository.ProductRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Regras de negocio do catalogo.
 *
 * <p>Classe sem anotacoes de infraestrutura de proposito: e o alvo ideal dos
 * mocks do Mockito, ja que depende apenas do {@link ProductRepository}.
 */
@Service
@Transactional
public class ProductService {

    private final ProductRepository repository;

    public ProductService(ProductRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public Product findById(UUID id) {
        return repository.findById(id).orElseThrow(() -> ResourceNotFoundException.product(id));
    }

    @Transactional(readOnly = true)
    public Product findBySku(String sku) {
        return repository.findBySku(sku).orElseThrow(() -> ResourceNotFoundException.product(sku));
    }

    @Transactional(readOnly = true)
    public Page<Product> search(String term, Boolean active, BigDecimal minPrice, Pageable pageable) {
        return repository.search(toLikePattern(term), active, minPrice, pageable);
    }

    /**
     * Normaliza o termo em um tradewind {@code LIKE} seguro.
     *
     * <p>Escapa {@code %} e {@code _} para que o usuario nao consiga injetar
     * coringa, e normaliza para minusculo porque a coluna e comparada com
     * {@code lower()}.
     */
    private static String toLikePattern(String term) {
        if (term == null || term.isBlank()) {
            return null;
        }
        String escaped = term.trim()
                .toLowerCase()
                .replace("\\", "\\\\")
                .replace("%", "\\%")
                .replace("_", "\\_");
        return "%" + escaped + "%";
    }

    public Product create(ProductRequest request) {
        if (repository.existsBySku(request.sku())) {
            throw ConflictException.skuAlreadyExists(request.sku());
        }
        Product product = new Product();
        apply(product, request);
        return repository.save(product);
    }

    public Product update(UUID id, ProductRequest request) {
        Product product = findById(id);

        if (!product.getSku().equals(request.sku()) && repository.existsBySku(request.sku())) {
            throw ConflictException.skuAlreadyExists(request.sku());
        }

        apply(product, request);
        return repository.save(product);
    }

    public void delete(UUID id) {
        Product product = findById(id);
        repository.delete(product);
    }

    private void apply(Product product, ProductRequest request) {
        product.setSku(request.sku());
        product.setName(request.name());
        product.setDescription(request.description());
        product.setPrice(request.price());
        product.setActive(request.active() == null || request.active());
    }
}
