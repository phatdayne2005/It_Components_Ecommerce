package vn.uth.itcomponentsecommerce.repository;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import vn.uth.itcomponentsecommerce.entity.Product;

import java.util.List;

public interface ProductRepository extends JpaRepository<Product, Long> {

    @EntityGraph(attributePaths = {"category", "brand"})
    List<Product> findTop8ByActiveTrueOrderByIdDesc();

    List<Product> findByCategory_Id(Long categoryId);

    List<Product> findByBrand_Id(Long brandId);

    long countByCategory_Id(Long categoryId);
}
