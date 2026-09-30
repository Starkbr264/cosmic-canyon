package br.com.tradewind.repository;

import br.com.tradewind.domain.Payment;
import br.com.tradewind.domain.PaymentStatus;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface PaymentRepository extends JpaRepository<Payment, UUID> {

    /** Contagem direto no indice, sem materializar os registros. */
    long countByStatus(PaymentStatus status);

    /**
     * Recusa mais recente primeiro, ja limitado no banco.
     * {@code findAll()} seguido de {@code sort().limit()} em Java funciona num
     * projeto academico e quebra no primeiro mes com volume real.
     */
    @EntityGraph(attributePaths = {"order"})
    List<Payment> findByStatusOrderByProcessedAtDesc(PaymentStatus status, Pageable pageable);
}