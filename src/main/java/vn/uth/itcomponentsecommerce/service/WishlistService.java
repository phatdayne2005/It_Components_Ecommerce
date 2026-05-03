package vn.uth.itcomponentsecommerce.service;

import jakarta.persistence.EntityNotFoundException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.uth.itcomponentsecommerce.entity.Product;
import vn.uth.itcomponentsecommerce.entity.User;
import vn.uth.itcomponentsecommerce.entity.WishlistItem;
import vn.uth.itcomponentsecommerce.repository.ProductRepository;
import vn.uth.itcomponentsecommerce.repository.UserRepository;
import vn.uth.itcomponentsecommerce.repository.WishlistRepository;

import java.util.List;

@Service
public class WishlistService {

    private final WishlistRepository wishlistRepository;
    private final UserRepository userRepository;
    private final ProductRepository productRepository;

    public WishlistService(WishlistRepository wishlistRepository,
                           UserRepository userRepository,
                           ProductRepository productRepository) {
        this.wishlistRepository = wishlistRepository;
        this.userRepository = userRepository;
        this.productRepository = productRepository;
    }

    private User getCurrentUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || auth.getPrincipal().equals("anonymousUser")) {
            return null;
        }
        String username = auth.getName();
        return userRepository.findByUsername(username).orElse(null);
    }

    @Transactional(readOnly = true)
    public List<WishlistItem> getMyWishlist() {
        User user = getCurrentUser();
        if (user == null) return List.of();
        return wishlistRepository.findByUserIdOrderByCreatedAtDesc(user.getId());
    }

    @Transactional(readOnly = true)
    public List<Long> getMyWishlistProductIds() {
        User user = getCurrentUser();
        if (user == null) return List.of();
        return wishlistRepository.findProductIdsByUserId(user.getId());
    }

    @Transactional
    public boolean addToWishlist(Long productId) {
        User user = getCurrentUser();
        if (user == null) return false;

        if (wishlistRepository.existsByUserIdAndProductId(user.getId(), productId)) {
            return true;
        }

        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new EntityNotFoundException("Product not found"));
        WishlistItem item = new WishlistItem(user, product);
        wishlistRepository.save(item);
        return true;
    }

    @Transactional
    public boolean removeFromWishlist(Long productId) {
        User user = getCurrentUser();
        if (user == null) return false;

        wishlistRepository.deleteByUserIdAndProductId(user.getId(), productId);
        return true;
    }

    @Transactional(readOnly = true)
    public boolean isInWishlist(Long productId) {
        User user = getCurrentUser();
        if (user == null) return false;
        return wishlistRepository.existsByUserIdAndProductId(user.getId(), productId);
    }

    @Transactional(readOnly = true)
    public long getWishlistCount() {
        User user = getCurrentUser();
        if (user == null) return 0;
        return wishlistRepository.countByUserId(user.getId());
    }
}
