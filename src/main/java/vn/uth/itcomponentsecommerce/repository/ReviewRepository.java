package vn.uth.itcomponentsecommerce.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import vn.uth.itcomponentsecommerce.entity.Review;

import java.util.List;

public interface ReviewRepository extends JpaRepository<Review, Long> {
    List<Review> findByProduct_IdAndApprovedTrueOrderByCreatedAtDesc(Long productId);
    long countByProduct_IdAndApprovedTrue(Long productId);
}
