package vn.uth.itcomponentsecommerce.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import vn.uth.itcomponentsecommerce.entity.Payment;

import java.util.Optional;

public interface PaymentRepository extends JpaRepository<Payment, Long> {
    Optional<Payment> findByTransactionId(String transactionId);
    Optional<Payment> findByOrder_Id(Long orderId);
}
