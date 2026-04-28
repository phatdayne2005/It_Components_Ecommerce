package vn.uth.itcomponentsecommerce.controller.api;

import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import vn.uth.itcomponentsecommerce.dto.CartMergeResponseDTO;
import vn.uth.itcomponentsecommerce.dto.MergeCartRequest;
import vn.uth.itcomponentsecommerce.entity.Cart;
import vn.uth.itcomponentsecommerce.service.CartService;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/carts")
public class CartApiController {

    private final CartService cartService;

    public CartApiController(CartService cartService) {
        this.cartService = cartService;
    }

    @GetMapping
    public Cart getMyCart() {
        return cartService.getCurrentUserCart();
    }

    @PostMapping("/merge")
    public ResponseEntity<?> mergeLocalCart(@Valid @RequestBody MergeCartRequest request) {
        CartMergeResponseDTO response = cartService.mergeLocalCartToDatabase(request);
        return ResponseEntity.ok(response);
    }

    @PatchMapping("/items/{productId}/quantity")
    public ResponseEntity<?> updateQuantity(@PathVariable Long productId, @RequestParam int quantity) {
        Cart cart = cartService.updateQuantity(productId, quantity);
        return ResponseEntity.ok(cart);
    }

    @PatchMapping("/items/{productId}/select")
    public ResponseEntity<?> selectItem(@PathVariable Long productId, @RequestParam boolean selected) {
        Cart cart = cartService.selectItem(productId, selected);
        return ResponseEntity.ok(cart);
    }

    @DeleteMapping("/items/{productId}")
    public ResponseEntity<?> removeItem(@PathVariable Long productId) {
        Cart cart = cartService.removeItem(productId);
        return ResponseEntity.ok(cart);
    }

    @GetMapping("/summary")
    public Map<String, Object> summary() {
        Cart cart = cartService.getCurrentUserCart();
        int totalQuantity = cart.getItems().stream().mapToInt(i -> i.getQuantity() == null ? 0 : i.getQuantity()).sum();
        int selectedQuantity = cart.getItems().stream()
                .filter(i -> i.getQuantity() != null && i.isSelected())
                .mapToInt(i -> i.getQuantity())
                .sum();
        return Map.of(
                "totalItems", cart.getItems().size(),
                "totalQuantity", totalQuantity,
                "selectedQuantity", selectedQuantity
        );
    }
}

