package vn.uth.itcomponentsecommerce.search;

import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import org.springframework.data.jpa.domain.Specification;
import vn.uth.itcomponentsecommerce.dto.CatalogQuery;
import vn.uth.itcomponentsecommerce.entity.Product;
import vn.uth.itcomponentsecommerce.entity.ProductSpecification;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * JPA {@link Specification} cho lọc/tìm kiếm Product theo {@link CatalogQuery}.
 *
 * Mỗi method trả về 1 specification rời, có thể compose bằng {@code Specification.where(...).and(...)}.
 * Method {@link #build(CatalogQuery)} ghép tất cả thành một Specification cuối.
 */
public final class ProductSpecs {

    private ProductSpecs() {}

    public static Specification<Product> activeOnly() {
        return (root, query, cb) -> cb.isTrue(root.get("active"));
    }

    public static Specification<Product> nameLike(String keyword) {
        if (keyword == null || keyword.isBlank()) return null;
        String like = "%" + keyword.trim().toLowerCase() + "%";
        return (root, query, cb) -> cb.or(
                cb.like(cb.lower(root.get("name")), like),
                cb.like(cb.lower(root.get("sku")), like),
                cb.like(cb.lower(root.get("shortDescription")), like),
                cb.like(cb.lower(root.get("description")), like)
        );
    }

    public static Specification<Product> inCategory(Long categoryId) {
        if (categoryId == null) return null;
        return (root, query, cb) -> cb.equal(root.get("category").get("id"), categoryId);
    }

    public static Specification<Product> inCategorySlug(String slug) {
        if (slug == null || slug.isBlank()) return null;
        return (root, query, cb) -> cb.equal(root.get("category").get("slug"), slug);
    }

    public static Specification<Product> inBrands(List<Long> brandIds) {
        if (brandIds == null || brandIds.isEmpty()) return null;
        return (root, query, cb) -> root.get("brand").get("id").in(brandIds);
    }

    public static Specification<Product> priceBetween(BigDecimal min, BigDecimal max) {
        if (min == null && max == null) return null;
        return (root, query, cb) -> {
            if (min != null && max != null) {
                return cb.between(root.get("price"), min, max);
            }
            if (min != null) {
                return cb.greaterThanOrEqualTo(root.get("price"), min);
            }
            return cb.lessThanOrEqualTo(root.get("price"), max);
        };
    }

    public static Specification<Product> inStock(Boolean inStock) {
        if (inStock == null || !inStock) return null;
        return (root, query, cb) -> cb.greaterThan(root.get("stock"), 0);
    }

    /**
     * Một spec name có nhiều value -> EXISTS với value IN (...).
     * Đây là điều kiện AND vào query chính.
     */
    public static Specification<Product> hasSpec(String name, List<String> values) {
        if (name == null || name.isBlank() || values == null || values.isEmpty()) return null;
        List<String> cleanValues = values.stream()
                .filter(v -> v != null && !v.isBlank())
                .map(String::trim)
                .toList();
        if (cleanValues.isEmpty()) return null;

        return (root, query, cb) -> {
            assert query != null;
            Subquery<Long> sub = query.subquery(Long.class);
            Root<ProductSpecification> spec = sub.from(ProductSpecification.class);
            sub.select(spec.get("id"));
            sub.where(
                    cb.equal(spec.get("product").get("id"), root.get("id")),
                    cb.equal(spec.get("name"), name.trim()),
                    spec.get("value").in(cleanValues)
            );
            return cb.exists(sub);
        };
    }

    /**
     * Build specification tổng hợp từ {@link CatalogQuery}.
     * Mặc định luôn lọc {@code active = true}.
     */
    public static Specification<Product> build(CatalogQuery q) {
        List<Specification<Product>> parts = new ArrayList<>();
        parts.add(activeOnly());
        if (q == null) {
            return combineAnd(parts);
        }
        parts.add(nameLike(q.getQ()));
        parts.add(inCategory(q.getCategoryId()));
        parts.add(inCategorySlug(q.getCategorySlug()));
        parts.add(inBrands(q.getBrandIds()));
        parts.add(priceBetween(q.getMinPrice(), q.getMaxPrice()));
        parts.add(inStock(q.getInStock()));

        if (q.getSpecs() != null) {
            for (Map.Entry<String, List<String>> e : q.getSpecs().entrySet()) {
                parts.add(hasSpec(e.getKey(), e.getValue()));
            }
        }
        return combineAnd(parts);
    }

    private static Specification<Product> combineAnd(List<Specification<Product>> parts) {
        Specification<Product> result = null;
        for (Specification<Product> s : parts) {
            if (s == null) continue;
            result = (result == null) ? s : result.and(s);
        }
        // fallback an toàn: trả về điều kiện trivially-true nếu tất cả null
        if (result == null) {
            result = (root, query, cb) -> cb.conjunction();
        }
        // distinct cần thiết khi có subquery EXISTS để tránh trùng do join
        Specification<Product> distinct = (root, query, cb) -> {
            if (query != null) query.distinct(true);
            return null;
        };
        return result.and(distinct);
    }

    /**
     * Tiện ích tránh tạo Predicate khi spec phụ trả null.
     */
    @SuppressWarnings("unused")
    private static Predicate notNull(Predicate p) {
        return p;
    }
}
