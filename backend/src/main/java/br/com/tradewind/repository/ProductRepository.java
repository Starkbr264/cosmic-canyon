package br.com.tradewind.repository;

import br.com.tradewind.domain.Product;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ProductRepository extends JpaRepository<Product, UUID> {

    Optional<Product> findBySku(String sku);

    boolean existsBySku(String sku);

    Page<Product> findByActiveTrue(Pageable pageable);

    /**
     * Filtro combinado. O parametro {@code pattern} ja vem pronto da camada de
     * servico ({@code %termo%} em minusculo).
     *
     * <p>Motivo: montar o padrÃ£o com {@code concat('%', :termo, '%')} dentro do
     * JPQL deixa o bind sem tipo definido, e o Postgres resolve a concatenacao
     * como {@code bytea}, estourando em {@code function lower(bytea) does not
     * exist}. Com o tradewind pronto, o bind e um VARCHAR e a query funciona.
     *
     * <p>A ordenacao fica a cargo do {@link Pageable}, por isso nao ha
     * {@code order by} aqui (duplicaria com o sort do pageavel).
     */
    @Query("""
            select p from Product p
             where (:active is null or p.active = :active)
               and (:pattern is null
                    or lower(p.name) like :pattern
                    or lower(p.sku)  like :pattern)
               and (:minPrice is null or p.price >= :minPrice)
            """)
    Page<Product> search(
            @Param("pattern") String pattern,
            @Param("active") Boolean active,
            @Param("minPrice") BigDecimal minPrice,
            Pageable pageable
    );
}
