package vn.uth.itcomponentsecommerce.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import vn.uth.itcomponentsecommerce.entity.ProductImage;

public interface ProductImageRepository extends JpaRepository<ProductImage, Long> {
}
