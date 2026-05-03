package vn.uth.itcomponentsecommerce.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import vn.uth.itcomponentsecommerce.entity.Product;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Dạng đầy đủ cho trang chi tiết sản phẩm: kèm gallery ảnh + thông số kỹ thuật.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ProductDetailView(
        Long id,
        String sku,
        String name,
        String slug,
        String shortDescription,
        String description,
        String editorialReview,
        BigDecimal price,
        BigDecimal oldPrice,
        Integer stock,
        Integer sold,
        Long viewCount,
        Integer warrantyMonths,
        String image,
        boolean active,
        Long categoryId,
        String categoryName,
        String categorySlug,
        Long brandId,
        String brandName,
        String brandSlug,
        List<String> images,
        List<SpecItem> specifications,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
    public record SpecItem(String name, String value) {}

    public static ProductDetailView from(Product p) {
        return new ProductDetailView(
                p.getId(),
                p.getSku(),
                p.getName(),
                p.getSlug(),
                p.getShortDescription(),
                p.getDescription(),
                p.getEditorialReview(),
                p.getPrice(),
                p.getOldPrice(),
                p.getStock(),
                p.getSold(),
                p.getViewCount(),
                p.getWarrantyMonths(),
                p.getImageUrl(),
                p.isActive(),
                p.getCategory() != null ? p.getCategory().getId() : null,
                p.getCategory() != null ? p.getCategory().getName() : null,
                p.getCategory() != null ? p.getCategory().getSlug() : null,
                p.getBrand() != null ? p.getBrand().getId() : null,
                p.getBrand() != null ? p.getBrand().getName() : null,
                p.getBrand() != null ? p.getBrand().getSlug() : null,
                p.getImages().stream().map(i -> i.getUrl()).toList(),
                p.getSpecifications().stream()
                        .map(s -> new SpecItem(s.getName(), s.getValue()))
                        .toList(),
                p.getCreatedAt(),
                p.getUpdatedAt()
        );
    }
}
