package vn.uth.itcomponentsecommerce.service;

import jakarta.persistence.OptimisticLockException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.uth.itcomponentsecommerce.entity.Product;
import vn.uth.itcomponentsecommerce.exception.OutOfStockException;
import vn.uth.itcomponentsecommerce.repository.ProductRepository;

@Service
public class InventoryService {

    private final ProductRepository productRepository;

    public InventoryService(ProductRepository productRepository) {
        this.productRepository = productRepository;
    }

    @Transactional
    public void reduceStock(Long productId, int quantity) {
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new IllegalArgumentException("Product not found: " + productId));
        if (product.getStock() < quantity) {
            throw new OutOfStockException("Out of stock for product " + productId);
        }
        product.setStock(product.getStock() - quantity);
        product.setSold(product.getSold() + quantity);
        flushWithOptimisticCheck(product);
    }

    @Transactional
    public void restoreStock(Long productId, int quantity) {
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new IllegalArgumentException("Product not found: " + productId));
        product.setStock(product.getStock() + quantity);
        int sold = product.getSold() == null ? 0 : product.getSold();
        product.setSold(Math.max(0, sold - quantity));
        flushWithOptimisticCheck(product);
    }

    private void flushWithOptimisticCheck(Product product) {
        try {
            productRepository.saveAndFlush(product);
        } catch (ObjectOptimisticLockingFailureException ex) {
            throw new OptimisticLockException("Concurrent stock update detected", ex);
        }
    }
}
