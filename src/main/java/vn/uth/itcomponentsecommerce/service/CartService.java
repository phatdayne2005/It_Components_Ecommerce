package vn.uth.itcomponentsecommerce.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.uth.itcomponentsecommerce.dto.CartItemDTO;
import vn.uth.itcomponentsecommerce.dto.CartMergeResponseDTO;
import vn.uth.itcomponentsecommerce.dto.MergeCartRequest;
import vn.uth.itcomponentsecommerce.entity.Cart;
import vn.uth.itcomponentsecommerce.entity.CartItem;
import vn.uth.itcomponentsecommerce.entity.Product;
import vn.uth.itcomponentsecommerce.entity.User;
import vn.uth.itcomponentsecommerce.repository.CartItemRepository;
import vn.uth.itcomponentsecommerce.repository.CartRepository;
import vn.uth.itcomponentsecommerce.repository.ProductRepository;

import java.util.List;
import java.util.ArrayList;

@Service
public class CartService {

    private final CartRepository cartRepository;
    private final CartItemRepository cartItemRepository;
    private final ProductRepository productRepository;
    private final ProductExternalService productExternalService;
    private final CurrentUserService currentUserService;

    public CartService(CartRepository cartRepository,
                       CartItemRepository cartItemRepository,
                       ProductRepository productRepository,
                       ProductExternalService productExternalService,
                       CurrentUserService currentUserService) {
        this.cartRepository = cartRepository;
        this.cartItemRepository = cartItemRepository;
        this.productRepository = productRepository;
        this.productExternalService = productExternalService;
        this.currentUserService = currentUserService;
    }

    @Transactional
    public Cart getCurrentUserCart() {
        User user = currentUserService.requireCurrentUser();
        return cartRepository.findByUser_Id(user.getId()).orElseGet(() -> {
            Cart cart = new Cart();
            cart.setUser(user);
            return cartRepository.save(cart);
        });
    }

    @Transactional
    public CartMergeResponseDTO mergeLocalCartToDatabase(MergeCartRequest request) {
        User user = currentUserService.requireCurrentUser();
        Cart cart = cartRepository.findByUser_Id(user.getId()).orElseGet(() -> {
            Cart c = new Cart();
            c.setUser(user);
            return cartRepository.save(c);
        });

        List<String> warnings = new ArrayList<>();
        for (CartItemDTO incoming : request.getItems()) {
            if (incoming.getQuantity() == null || incoming.getQuantity() <= 0) {
                warnings.add("Skipped invalid quantity for productId=" + incoming.getProductId());
                continue;
            }

            Product product = productRepository.findById(incoming.getProductId()).orElse(null);
            if (product == null) {
                warnings.add("Product not found: " + incoming.getProductId());
                continue;
            }

            CartItem dbItem = cartItemRepository.findByCart_IdAndProduct_Id(cart.getId(), product.getId())
                    .orElseGet(() -> {
                        CartItem item = new CartItem();
                        item.setCart(cart);
                        item.setProduct(product);
                        item.setQuantity(0);
                        item.setSelected(true);
                        return item;
                    });

            int mergedQuantity = dbItem.getQuantity() + incoming.getQuantity();
            int availableStock = productExternalService.getAvailableStock(product.getId());
            if (availableStock <= 0) {
                warnings.add("Out of stock for productId=" + product.getId());
                continue;
            }
            if (mergedQuantity > availableStock) {
                warnings.add("Adjusted quantity to available stock for productId=" + product.getId());
                mergedQuantity = availableStock;
            }

            dbItem.setQuantity(mergedQuantity);
            dbItem.setSelected(incoming.isSelected());
            cartItemRepository.save(dbItem);
        }

        CartMergeResponseDTO response = new CartMergeResponseDTO();
        response.setCart(cartRepository.findById(cart.getId()).orElseThrow());
        response.setWarnings(warnings);
        response.setHasWarning(!warnings.isEmpty());
        return response;
    }

    @Transactional
    public Cart updateQuantity(Long productId, int quantity) {
        if (quantity <= 0) {
            throw new IllegalArgumentException("Quantity must be > 0");
        }
        int availableStock = productExternalService.getAvailableStock(productId);
        if (quantity > availableStock) {
            throw new IllegalArgumentException("Quantity exceeds available stock");
        }
        Cart cart = getCurrentUserCart();
        CartItem item = cartItemRepository.findByCart_IdAndProduct_Id(cart.getId(), productId)
                .orElseThrow(() -> new IllegalArgumentException("Cart item not found"));
        item.setQuantity(quantity);
        cartItemRepository.save(item);
        return cart;
    }

    @Transactional
    public Cart removeItem(Long productId) {
        Cart cart = getCurrentUserCart();
        cartItemRepository.findByCart_IdAndProduct_Id(cart.getId(), productId)
                .ifPresent(cartItemRepository::delete);
        return cart;
    }

    @Transactional
    public Cart selectItem(Long productId, boolean selected) {
        Cart cart = getCurrentUserCart();
        CartItem item = cartItemRepository.findByCart_IdAndProduct_Id(cart.getId(), productId)
                .orElseThrow(() -> new IllegalArgumentException("Cart item not found"));
        item.setSelected(selected);
        cartItemRepository.save(item);
        return cart;
    }

    @Transactional(readOnly = true)
    public List<CartItem> getSelectedItemsForCurrentUser() {
        Cart cart = getCurrentUserCart();
        return cartItemRepository.findByCart_Id(cart.getId()).stream()
                .filter(CartItem::isSelected)
                .toList();
    }
}
