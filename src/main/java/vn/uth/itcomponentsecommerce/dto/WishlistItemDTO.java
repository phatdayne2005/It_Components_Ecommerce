package vn.uth.itcomponentsecommerce.dto;

import vn.uth.itcomponentsecommerce.entity.WishlistItem;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record WishlistItemDTO(
        Long id,
        Long productId,
        String productName,
        String productSlug,
        String productImage,
        BigDecimal productPrice,
        String productCategory,
        LocalDateTime addedAt
) {
    public static WishlistItemDTO from(WishlistItem item) {
        return new WishlistItemDTO(
                item.getId(),
                item.getProduct().getId(),
                item.getProduct().getName(),
                item.getProduct().getSlug(),
                item.getProduct().getImageUrl(),
                item.getProduct().getPrice(),
                item.getProduct().getCategory() != null ? item.getProduct().getCategory().getName() : "",
                item.getCreatedAt()
        );
    }
}
