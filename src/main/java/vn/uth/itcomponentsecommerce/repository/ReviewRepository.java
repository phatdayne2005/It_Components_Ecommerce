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

    Optional<Review> findByUser_IdAndProduct_Id(Long userId, Long productId);
}
