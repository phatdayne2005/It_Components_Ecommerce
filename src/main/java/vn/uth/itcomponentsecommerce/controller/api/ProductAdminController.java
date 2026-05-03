package vn.uth.itcomponentsecommerce.controller.api;

import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import vn.uth.itcomponentsecommerce.dto.ProductRequest;
import vn.uth.itcomponentsecommerce.entity.Product;
import vn.uth.itcomponentsecommerce.service.ProductService;

import java.math.BigDecimal;
import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/admin/products")
public class ProductAdminController {

    private final ProductService service;

    public ProductAdminController(ProductService service) {
        this.service = service;
    }

    @GetMapping
    public List<ProductView> list() {
        return service.findAll().stream().map(ProductView::from).collect(Collectors.toList());
    }

    @GetMapping("/{id}")
    public ProductView get(@PathVariable Long id) {
        return ProductView.from(service.findById(id));
    }

    @PostMapping
    public ResponseEntity<ProductView> create(@Valid @RequestBody ProductRequest req) {
        return ResponseEntity.ok(ProductView.from(service.create(req)));
    }

    @PutMapping("/{id}")
    public ProductView update(@PathVariable Long id, @Valid @RequestBody ProductRequest req) {
        return ProductView.from(service.update(id, req));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record ProductView(
            Long id, String sku, String name, String slug,
            String shortDescription, String description, String editorialReview,
            BigDecimal price, BigDecimal oldPrice,
            Integer stock, Integer sold, Integer warrantyMonths,
            String imageUrl, boolean active,
            Long categoryId, String categoryName,
            Long brandId, String brandName,
            List<String> imageUrls,
            List<SpecView> specifications
    ) {
        public static ProductView from(Product p) {
            return new ProductView(
                    p.getId(), p.getSku(), p.getName(), p.getSlug(),
                    p.getShortDescription(), p.getDescription(), p.getEditorialReview(),
                    p.getPrice(), p.getOldPrice(),
                    p.getStock(), p.getSold(), p.getWarrantyMonths(),
                    p.getImageUrl(), p.isActive(),
                    p.getCategory() != null ? p.getCategory().getId() : null,
                    p.getCategory() != null ? p.getCategory().getName() : null,
                    p.getBrand() != null ? p.getBrand().getId() : null,
                    p.getBrand() != null ? p.getBrand().getName() : null,
                    p.getImages().stream().map(i -> i.getUrl()).collect(Collectors.toList()),
                    p.getSpecifications().stream()
                            .map(s -> new SpecView(s.getName(), s.getValue()))
                            .collect(Collectors.toList())
            );
        }

        public record SpecView(String name, String value) {}
    }
}
