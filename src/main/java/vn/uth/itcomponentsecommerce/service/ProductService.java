package vn.uth.itcomponentsecommerce.service;

import jakarta.persistence.EntityNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.uth.itcomponentsecommerce.dto.ProductRequest;
import vn.uth.itcomponentsecommerce.entity.Product;
import vn.uth.itcomponentsecommerce.entity.ProductImage;
import vn.uth.itcomponentsecommerce.entity.ProductSpecification;
import vn.uth.itcomponentsecommerce.repository.BrandRepository;
import vn.uth.itcomponentsecommerce.repository.CategoryRepository;
import vn.uth.itcomponentsecommerce.repository.ProductRepository;

import java.util.List;

@Service
public class ProductService {

    private final ProductRepository productRepository;
    private final CategoryRepository categoryRepository;
    private final BrandRepository brandRepository;

    public ProductService(ProductRepository productRepository,
                          CategoryRepository categoryRepository,
                          BrandRepository brandRepository) {
        this.productRepository = productRepository;
        this.categoryRepository = categoryRepository;
        this.brandRepository = brandRepository;
    }

    @Transactional(readOnly = true)
    public List<Product> findAll() {
        return productRepository.findAll();
    }

    @Transactional(readOnly = true)
    public List<Product> findFeatured() {
        return productRepository.findTop8ByActiveTrueOrderByIdDesc();
    }

    @Transactional(readOnly = true)
    public Product findById(Long id) {
        return productRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Product id " + id + " không tồn tại"));
    }

    @Transactional
    public Product create(ProductRequest req) {
        Product p = new Product();
        apply(p, req);
        return productRepository.save(p);
    }

    @Transactional
    public Product update(Long id, ProductRequest req) {
        Product p = findById(id);
        apply(p, req);
        return productRepository.save(p);
    }

    @Transactional
    public void delete(Long id) {
        if (!productRepository.existsById(id))
            throw new EntityNotFoundException("Product id " + id + " không tồn tại");
        productRepository.deleteById(id);
    }

    private void apply(Product p, ProductRequest req) {
        p.setName(req.getName());
        p.setSku(blankToNull(req.getSku()));
        p.setShortDescription(req.getShortDescription());
        p.setDescription(req.getDescription());
        p.setPrice(req.getPrice());
        p.setOldPrice(req.getOldPrice());
        p.setStock(req.getStock() == null ? 0 : req.getStock());
        p.setWarrantyMonths(req.getWarrantyMonths());
        p.setImageUrl(req.getImageUrl());
        p.setActive(req.isActive());

        String slug = (req.getSlug() == null || req.getSlug().isBlank())
                ? SlugUtil.toSlug(req.getName())
                : SlugUtil.toSlug(req.getSlug());
        p.setSlug(slug);

        if (req.getCategoryId() != null) {
            p.setCategory(categoryRepository.findById(req.getCategoryId())
                    .orElseThrow(() -> new EntityNotFoundException("Category id " + req.getCategoryId() + " không tồn tại")));
        } else {
            p.setCategory(null);
        }

        if (req.getBrandId() != null) {
            p.setBrand(brandRepository.findById(req.getBrandId())
                    .orElseThrow(() -> new EntityNotFoundException("Brand id " + req.getBrandId() + " không tồn tại")));
        } else {
            p.setBrand(null);
        }

        // Replace gallery images
        p.getImages().clear();
        if (req.getImageUrls() != null) {
            int order = 0;
            for (String url : req.getImageUrls()) {
                if (url != null && !url.isBlank()) {
                    p.getImages().add(new ProductImage(p, url.trim(), order++));
                }
            }
        }

        // Replace specifications
        p.getSpecifications().clear();
        if (req.getSpecifications() != null) {
            int order = 0;
            for (var s : req.getSpecifications()) {
                if (s.getName() != null && !s.getName().isBlank()
                        && s.getValue() != null && !s.getValue().isBlank()) {
                    p.getSpecifications().add(
                            new ProductSpecification(p, s.getName().trim(), s.getValue().trim(), order++));
                }
            }
        }
    }

    private static String blankToNull(String s) {
        return (s == null || s.isBlank()) ? null : s.trim();
    }
}
