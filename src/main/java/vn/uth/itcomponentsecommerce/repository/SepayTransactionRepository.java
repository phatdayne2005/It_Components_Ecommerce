package vn.uth.itcomponentsecommerce.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import vn.uth.itcomponentsecommerce.entity.SepayTransaction;

import java.util.Optional;

public interface SepayTransactionRepository extends JpaRepository<SepayTransaction, Long> {
    boolean existsByTransactionId(String transactionId);
    Optional<SepayTransaction> findByTransactionId(String transactionId);
}
