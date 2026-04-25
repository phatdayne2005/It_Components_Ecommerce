package vn.uth.itcomponentsecommerce.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import vn.uth.itcomponentsecommerce.entity.CartItem;

public interface CartItemRepository extends JpaRepository<CartItem, Long> {
}
