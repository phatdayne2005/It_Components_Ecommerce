package vn.uth.itcomponentsecommerce.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import vn.uth.itcomponentsecommerce.entity.ProductSpecification;

import java.util.List;

public interface ProductSpecificationRepository extends JpaRepository<ProductSpecification, Long> {
    List<ProductSpecification> findByProduct_Id(Long productId);
    List<ProductSpecification> findByName(String name);
}
