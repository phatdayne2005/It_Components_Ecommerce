package vn.uth.itcomponentsecommerce.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import vn.uth.itcomponentsecommerce.entity.OrderItem;
import vn.uth.itcomponentsecommerce.entity.OrderStatus;

public interface OrderItemRepository extends JpaRepository<OrderItem, Long> {
    boolean existsByProduct_IdAndOrder_User_IdAndOrder_Status(Long productId, Long userId, OrderStatus status);
}
