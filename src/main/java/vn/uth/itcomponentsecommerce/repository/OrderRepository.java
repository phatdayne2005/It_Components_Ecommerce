package vn.uth.itcomponentsecommerce.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import vn.uth.itcomponentsecommerce.entity.Order;
import vn.uth.itcomponentsecommerce.entity.OrderStatus;

import java.util.List;
import java.util.Optional;

public interface OrderRepository extends JpaRepository<Order, Long> {
    List<Order> findByUser_IdOrderByCreatedAtDesc(Long userId);
    List<Order> findByStatus(OrderStatus status);
    Optional<Order> findByOrderCode(String orderCode);
}
