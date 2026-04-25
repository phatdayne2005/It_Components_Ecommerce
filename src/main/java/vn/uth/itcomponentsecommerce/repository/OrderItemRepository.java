package vn.uth.itcomponentsecommerce.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import vn.uth.itcomponentsecommerce.entity.OrderItem;

public interface OrderItemRepository extends JpaRepository<OrderItem, Long> {
}
