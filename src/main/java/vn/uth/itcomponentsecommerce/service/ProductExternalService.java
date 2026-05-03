package vn.uth.itcomponentsecommerce.service;

import vn.uth.itcomponentsecommerce.dto.ProductDTO;

public interface ProductExternalService {

    ProductDTO getProductById(Long id);

    int getAvailableStock(Long productId);

    boolean reserveStock(Long productId, int quantity);

    void releaseStock(Long productId, int quantity);
}
