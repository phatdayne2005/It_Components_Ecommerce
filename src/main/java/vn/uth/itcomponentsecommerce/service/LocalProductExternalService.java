package vn.uth.itcomponentsecommerce.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.uth.itcomponentsecommerce.dto.ProductDTO;
import vn.uth.itcomponentsecommerce.entity.Product;
import vn.uth.itcomponentsecommerce.repository.ProductRepository;

@Service
public class LocalProductExternalService implements ProductExternalService {

    private final ProductRepository productRepository;

    public LocalProductExternalService(ProductRepository productRepository) {
        this.productRepository = productRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public ProductDTO getProductById(Long id) {
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Product not found: " + id));
        ProductDTO dto = new ProductDTO();
        dto.setId(product.getId());
        dto.setName(product.getName());
        dto.setPrice(product.getPrice());
        dto.setImageUrl(product.getImageUrl());
        dto.setActive(product.isActive());
        return dto;
    }

    @Override
    @Transactional(readOnly = true)
    public int getAvailableStock(Long productId) {
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new IllegalArgumentException("Product not found: " + productId));
        return product.getStock() == null ? 0 : product.getStock();
    }

    @Override
    @Transactional
    public boolean reserveStock(Long productId, int quantity) {
        if (quantity <= 0) {
            return false;
        }
        for (int attempt = 0; attempt < 3; attempt++) {
            Product product = productRepository.findById(productId)
                    .orElseThrow(() -> new IllegalArgumentException("Product not found: " + productId));
            int currentStock = product.getStock() == null ? 0 : product.getStock();
            if (currentStock < quantity) {
                return false;
            }
            product.setStock(currentStock - quantity);
            int sold = product.getSold() == null ? 0 : product.getSold();
            product.setSold(sold + quantity);
            try {
                productRepository.saveAndFlush(product);
                return true;
            } catch (org.springframework.orm.ObjectOptimisticLockingFailureException ex) {
                if (attempt == 2) {
                    return false;
                }
            }
        }
        return false;
    }

    @Override
    @Transactional
    public void releaseStock(Long productId, int quantity) {
        if (quantity <= 0) {
            return;
        }
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new IllegalArgumentException("Product not found: " + productId));
        int currentStock = product.getStock() == null ? 0 : product.getStock();
        int sold = product.getSold() == null ? 0 : product.getSold();
        product.setStock(currentStock + quantity);
        product.setSold(Math.max(0, sold - quantity));
        productRepository.save(product);
    }
}
