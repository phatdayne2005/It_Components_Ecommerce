package vn.uth.itcomponentsecommerce.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import vn.uth.itcomponentsecommerce.entity.Product;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Dạng gọn cho danh sách / lưới sản phẩm (catalog, search).
 * Không lôi description / specifications về để giảm tải truy vấn.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ProductCardView(
        Long id,
        String sku,
        String name,
        String slug,
        String image,
        BigDecimal price,
        BigDecimal oldPrice,
        Integer stock,
        Long categoryId,
        String categoryName,
        Long brandId,
        String brandName,
        String badge
) {
    public static ProductCardView from(Product p) {
        return new ProductCardView(
                p.getId(),
                p.getSku(),
                p.getName(),
                p.getSlug(),
                p.getImageUrl(),
                p.getPrice(),
                p.getOldPrice(),
                p.getStock(),
                p.getCategory() != null ? p.getCategory().getId() : null,
                p.getCategory() != null ? p.getCategory().getName() : null,
                p.getBrand() != null ? p.getBrand().getId() : null,
                p.getBrand() != null ? p.getBrand().getName() : null,
                computeBadge(p.getPrice(), p.getOldPrice())
        );
    }

    private static String computeBadge(BigDecimal price, BigDecimal oldPrice) {
        if (oldPrice == null || price == null) return null;
        if (oldPrice.compareTo(price) <= 0) return null;
        BigDecimal diff = oldPrice.subtract(price);
        BigDecimal pct = diff.multiply(BigDecimal.valueOf(100))
                .divide(oldPrice, 0, RoundingMode.HALF_UP);
        return "-" + pct + "%";
    }
}
