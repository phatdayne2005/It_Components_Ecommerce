package vn.uth.itcomponentsecommerce.controller.api;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import vn.uth.itcomponentsecommerce.dto.WishlistItemDTO;
import vn.uth.itcomponentsecommerce.service.WishlistService;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/wishlist")
public class WishlistApiController {

    private final WishlistService wishlistService;

    public WishlistApiController(WishlistService wishlistService) {
        this.wishlistService = wishlistService;
    }

    @GetMapping
    public ResponseEntity<List<WishlistItemDTO>> getWishlist() {
        List<WishlistItemDTO> items = wishlistService.getMyWishlist().stream()
                .map(WishlistItemDTO::from)
                .toList();
        return ResponseEntity.ok(items);
    }

    @GetMapping("/ids")
    public ResponseEntity<List<Long>> getWishlistProductIds() {
        List<Long> ids = wishlistService.getMyWishlistProductIds();
        return ResponseEntity.ok(ids);
    }

    @PostMapping("/products/{productId}")
    public ResponseEntity<?> addToWishlist(@PathVariable Long productId) {
        boolean added = wishlistService.addToWishlist(productId);
        if (!added) {
            return ResponseEntity.status(401).body(Map.of("error", "Vui lòng đăng nhập"));
        }
        long count = wishlistService.getWishlistCount();
        return ResponseEntity.ok(Map.of("success", true, "inWishlist", true, "count", count));
    }

    @DeleteMapping("/products/{productId}")
    public ResponseEntity<?> removeFromWishlist(@PathVariable Long productId) {
        boolean removed = wishlistService.removeFromWishlist(productId);
        if (!removed) {
            return ResponseEntity.status(401).body(Map.of("error", "Vui lòng đăng nhập"));
        }
        long count = wishlistService.getWishlistCount();
        return ResponseEntity.ok(Map.of("success", true, "inWishlist", false, "count", count));
    }

    @GetMapping("/products/{productId}/check")
    public ResponseEntity<Map<String, Object>> checkWishlist(@PathVariable Long productId) {
        boolean inWishlist = wishlistService.isInWishlist(productId);
        long count = wishlistService.getWishlistCount();
        return ResponseEntity.ok(Map.of("inWishlist", inWishlist, "count", count));
    }
}
