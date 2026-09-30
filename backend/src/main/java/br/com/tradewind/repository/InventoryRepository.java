package br.com.tradewind.repository;

import br.com.tradewind.domain.Inventory;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface InventoryRepository extends JpaRepository<Inventory, UUID> {

    /**
     * O grafo de entidade e obrigatorio aqui: a listagem monta um DTO que le
     * produto e localizacao. Sem o fetch, o acesso ao proxy acontece ja com a
     * sessao fechada e o resultado e um {@code LazyInitializationException} — o
     * classico bug que so aparece quando a lista tem mais de um registro.
     *
     * <p>A consulta e escrita por extenso em vez de derivada porque
     * {@code findAllWithRefs} nao casa com nenhuma propriedade de {@code Inventory}
     * e o Spring Data trataria o sufixo como um caminho de atributo.
     */
    @EntityGraph(attributePaths = {"product", "location"})
    @Query("select i from Inventory i")
    List<Inventory> findAllWithRefs();

    @EntityGraph(attributePaths = {"product", "location"})
    @Override
    Optional<Inventory> findById(UUID id);

    /**
     * Usado pela limpeza do teste de integracao, que roda contra o mesmo banco
     * de desenvolvimento na forma "Postgres ja em pe". Sem este metodo o
     * produto criado pelo teste fica no catalogo e aparece no dashboard como se
     * alguem o tivesse cadastrado.
     */
    @Modifying
    @Query("delete from Inventory i where i.product.id = :productId")
    void deleteByProductId(@Param("productId") UUID productId);

    /**
     * Comparar coluna contra coluna e o que permite o alerta de reposicao ter
     * um unico ponto de verdade: o {@code reorder_point} cadastrado por local.
     */
    @Query("select count(i) from Inventory i where i.availableQuantity <= i.reorderPoint")
    long countLowStock();

    /**
     * Trava as linhas candidatas antes de decidir onde reservar.
     *
     * <p>O {@code PESSIMISTIC_WRITE} vira {@code SELECT ... FOR UPDATE}: dois
     * checkouts simultaneos ficam em fila no banco em vez de lerem o mesmo saldo
     * e venderem a mesma unidade duas vezes.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select i from Inventory i join fetch i.location where i.product.id = :productId and i.location.active = true order by i.availableQuantity desc")
    List<Inventory> lockAvailableByProduct(@Param("productId") UUID productId);
}