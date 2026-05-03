package vn.uth.itcomponentsecommerce.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.QueryHints;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.QueryHint;
import org.springframework.lang.NonNull;
import vn.uth.itcomponentsecommerce.entity.Product;

import java.util.List;
import java.util.Optional;

public interface ProductRepository extends JpaRepository<Product, Long>, JpaSpecificationExecutor<Product> {

    @EntityGraph(attributePaths = {"category", "brand"})
    List<Product> findTop8ByActiveTrueOrderByIdDesc();

    @EntityGraph(attributePaths = {"category", "brand"})
    Page<Product> findByActiveTrue(Pageable pageable);

    @EntityGraph(attributePaths = {"category", "brand"})
    Optional<Product> findBySlug(String slug);

    boolean existsBySlug(String slug);

    List<Product> findByCategory_Id(Long categoryId);

    List<Product> findByBrand_Id(Long brandId);

    long countByCategory_Id(Long categoryId);

    /**
     * Override {@code findAll(Specification, Pageable)} từ {@link JpaSpecificationExecutor}
     * để áp EntityGraph cho category + brand, tránh N+1 khi map sang ProductCardView.
     * Lưu ý: vì DB là MySQL, dùng read-only hint giúp Hibernate bỏ dirty-check entities.
     */
    @Override
    @EntityGraph(attributePaths = {"category", "brand"})
    @QueryHints(@QueryHint(name = "org.hibernate.readOnly", value = "true"))
    @NonNull
    Page<Product> findAll(@NonNull org.springframework.data.jpa.domain.Specification<Product> spec,
                          @NonNull Pageable pageable);

    /**
     * Tăng view-count atomically (không cần load entity vào persistence context).
     */
    @Modifying
    @Query("UPDATE Product p SET p.viewCount = p.viewCount + 1 WHERE p.id = :id")
    int incrementViewCount(@Param("id") Long id);
}
