package vn.uth.itcomponentsecommerce.repository;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import vn.uth.itcomponentsecommerce.entity.Order;
import vn.uth.itcomponentsecommerce.entity.OrderStatus;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface OrderRepository extends JpaRepository<Order, Long> {
    @EntityGraph(attributePaths = {"items", "items.product", "payment", "voucher"})
    List<Order> findByUser_IdOrderByCreatedAtDesc(Long userId);
    List<Order> findByStatus(OrderStatus status);
    List<Order> findByStatusAndCreatedAtBefore(OrderStatus status, LocalDateTime createdAtBefore);
    Optional<Order> findByOrderCode(String orderCode);

    @EntityGraph(attributePaths = {"items", "items.product", "payment", "voucher"})
    Optional<Order> findWithItemsById(Long id);

    @EntityGraph(attributePaths = {"items", "items.product", "payment", "voucher"})
    Optional<Order> findWithItemsByOrderCode(String orderCode);

    @Query(value = "SELECT DATE(created_at), COALESCE(SUM(total),0), COUNT(*) " +
            "FROM orders WHERE status NOT IN ('CANCELLED','PENDING_PAYMENT') AND created_at >= :from " +
            "GROUP BY DATE(created_at) ORDER BY DATE(created_at)", nativeQuery = true)
    List<Object[]> dailyRevenueRows(@Param("from") LocalDateTime from);

    @Query(value = "SELECT COALESCE(SUM(total),0) FROM orders WHERE status NOT IN ('CANCELLED','PENDING_PAYMENT') AND created_at >= :from", nativeQuery = true)
    BigDecimal sumRevenueSince(@Param("from") LocalDateTime from);

    @EntityGraph(attributePaths = {"items", "items.product", "user", "payment", "voucher"})
    List<Order> findByStatusOrderByCreatedAtDesc(OrderStatus status);

    @EntityGraph(attributePaths = {"items", "items.product", "user", "payment", "voucher"})
    List<Order> findAllByOrderByCreatedAtDesc();

    @EntityGraph(attributePaths = {"items", "items.product", "user", "payment", "voucher"})
    List<Order> findByUser_UsernameContainingIgnoreCaseOrderByCreatedAtDesc(String keyword);

    @EntityGraph(attributePaths = {"items", "items.product", "user", "payment", "voucher"})
    List<Order> findByOrderCodeContainingIgnoreCaseOrderByCreatedAtDesc(String orderCode);

    @EntityGraph(attributePaths = {"items", "items.product", "user", "payment", "voucher"})
    List<Order> findByCreatedAtBetweenOrderByCreatedAtDesc(LocalDateTime from, LocalDateTime to);
}
