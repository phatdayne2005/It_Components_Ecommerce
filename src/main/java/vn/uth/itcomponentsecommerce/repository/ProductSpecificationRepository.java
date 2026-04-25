package vn.uth.itcomponentsecommerce.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import vn.uth.itcomponentsecommerce.entity.ProductSpecification;

import java.util.List;

public interface ProductSpecificationRepository extends JpaRepository<ProductSpecification, Long> {

    List<ProductSpecification> findByProduct_Id(Long productId);

    List<ProductSpecification> findByName(String name);

    /**
     * Lấy facet (name, value, count) cho toàn bộ sản phẩm active.
     * Dùng cho sidebar filter ở trang catalog.
     *
     * Trả về Object[]{ specName, specValue, count }, đã sort theo (name, count desc).
     */
    @Query("""
            SELECT s.name, s.value, COUNT(DISTINCT p.id)
              FROM ProductSpecification s
              JOIN s.product p
             WHERE p.active = true
             GROUP BY s.name, s.value
             ORDER BY s.name ASC, COUNT(DISTINCT p.id) DESC
            """)
    List<Object[]> aggregateFacets();

    /**
     * Như trên nhưng giới hạn theo category.
     */
    @Query("""
            SELECT s.name, s.value, COUNT(DISTINCT p.id)
              FROM ProductSpecification s
              JOIN s.product p
             WHERE p.active = true
               AND p.category.id = :categoryId
             GROUP BY s.name, s.value
             ORDER BY s.name ASC, COUNT(DISTINCT p.id) DESC
            """)
    List<Object[]> aggregateFacetsByCategory(@Param("categoryId") Long categoryId);

    /**
     * Như trên nhưng theo slug danh mục (tiện cho URL kiểu /products/category/cpu).
     */
    @Query("""
            SELECT s.name, s.value, COUNT(DISTINCT p.id)
              FROM ProductSpecification s
              JOIN s.product p
             WHERE p.active = true
               AND p.category.slug = :slug
             GROUP BY s.name, s.value
             ORDER BY s.name ASC, COUNT(DISTINCT p.id) DESC
            """)
    List<Object[]> aggregateFacetsByCategorySlug(@Param("slug") String slug);
}
