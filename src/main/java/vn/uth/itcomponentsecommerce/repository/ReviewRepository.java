package vn.uth.itcomponentsecommerce.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import vn.uth.itcomponentsecommerce.entity.Review;

import java.util.List;
import java.util.Optional;

public interface ReviewRepository extends JpaRepository<Review, Long> {
    List<Review> findByProduct_IdAndApprovedTrueOrderByCreatedAtDesc(Long productId);
    long countByProduct_IdAndApprovedTrue(Long productId);

    @Query("SELECT AVG(r.rating) FROM Review r WHERE r.product.id = :productId AND r.approved = true")
    Double averageRatingByProduct_IdAndApprovedTrue(@Param("productId") Long productId);

    // 1 user + 1 sản phẩm + 1 đơn = 1 review (ARCHITECTURE §11)
    Optional<Review> findByUser_IdAndProduct_IdAndOrder_Id(Long userId, Long productId, Long orderId);

    // Tìm review của user cho 1 sản phẩm (nhiều đơn → nhiều review)
    List<Review> findByUser_IdAndProduct_IdOrderByCreatedAtDesc(Long userId, Long productId);

    // Xóa reviews khi đơn chuyển sang REFUND_REQUESTED (ARCHITECTURE §11.8)
    void deleteByOrder_Id(Long orderId);

    // Tìm tất cả reviews gắn với 1 order
    List<Review> findByOrder_Id(Long orderId);
}
