package vn.uth.itcomponentsecommerce.service;

import jakarta.persistence.EntityNotFoundException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import vn.uth.itcomponentsecommerce.dto.CatalogQuery;
import vn.uth.itcomponentsecommerce.dto.ProductCardView;
import vn.uth.itcomponentsecommerce.dto.ProductDetailView;
import vn.uth.itcomponentsecommerce.dto.ProductRequest;
import vn.uth.itcomponentsecommerce.dto.SpecFacet;
import vn.uth.itcomponentsecommerce.entity.Brand;
import vn.uth.itcomponentsecommerce.entity.Product;
import vn.uth.itcomponentsecommerce.entity.ProductImage;
import vn.uth.itcomponentsecommerce.entity.ProductSpecification;
import vn.uth.itcomponentsecommerce.repository.BrandRepository;
import vn.uth.itcomponentsecommerce.repository.CategoryRepository;
import vn.uth.itcomponentsecommerce.repository.ProductRepository;
import vn.uth.itcomponentsecommerce.repository.ProductSpecificationRepository;
import vn.uth.itcomponentsecommerce.search.ProductSpecs;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class ProductService {

    private final ProductRepository productRepository;
    private final CategoryRepository categoryRepository;
    private final BrandRepository brandRepository;
    private final ProductSpecificationRepository specRepository;

    /**
     * Cấu hình các spec name được phép hiện trên sidebar facet (tránh sidebar quá dài).
     * Có thể override qua application.yaml: app.catalog.facet-keys=Socket,Bus,...
     */
    @Value("${app.catalog.facet-keys:Socket,Chipset,Form factor,Khe RAM,Loại,Bus,CL,GPU,VRAM,Chuẩn,Dung lượng,Công suất,Modular,ATX,PCIe,WiFi}")
    private List<String> allowedFacetKeys;

    public ProductService(ProductRepository productRepository,
                          CategoryRepository categoryRepository,
                          BrandRepository brandRepository,
                          ProductSpecificationRepository specRepository) {
        this.productRepository = productRepository;
        this.categoryRepository = categoryRepository;
        this.brandRepository = brandRepository;
        this.specRepository = specRepository;
    }

    // ===== Existing admin/home methods =====

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

    // ===== New catalog/search methods =====

    /**
     * Tìm kiếm + lọc sản phẩm cho trang catalog / API public.
     * Trả về Page&lt;ProductCardView&gt; (dạng gọn) để giảm tải truy vấn.
     */
    @Transactional(readOnly = true)
    public Page<ProductCardView> searchCatalog(CatalogQuery q, Pageable pageable) {
        Specification<Product> spec = ProductSpecs.build(q);
        return productRepository.findAll(spec, pageable).map(ProductCardView::from);
    }

    /**
     * Lấy chi tiết sản phẩm theo slug, kèm images + specifications.
     * Mặc định KHÔNG tăng view-count (gọi {@link #incrementViewCount(Long)} riêng nếu cần).
     */
    @Transactional(readOnly = true)
    public ProductDetailView getDetailBySlug(String slug) {
        Product p = productRepository.findBySlug(slug)
                .orElseThrow(() -> new EntityNotFoundException("Product slug '" + slug + "' không tồn tại"));
        // chạm vào collection trong phạm vi transaction để hibernate khởi tạo lazy
        p.getImages().size();
        p.getSpecifications().size();
        return ProductDetailView.from(p);
    }

    /**
     * Lấy danh sách brand có sản phẩm active theo category.
     * Nếu categoryId hoặc categorySlug được truyền thì chỉ lấy brand thuộc category đó.
     * Nếu không truyền (cả hai đều null/blank) thì trả về tất cả brands.
     */
    @Transactional(readOnly = true)
    public List<Brand> getBrandsByCategory(Long categoryId, String categorySlug) {
        if (categoryId != null) {
            List<Brand> brands = productRepository.findDistinctBrandsByCategoryId(categoryId);
            brands.sort((a, b) -> a.getName().compareToIgnoreCase(b.getName()));
            return brands;
        } else if (categorySlug != null && !categorySlug.isBlank()) {
            List<Brand> brands = productRepository.findDistinctBrandsByCategorySlug(categorySlug);
            brands.sort((a, b) -> a.getName().compareToIgnoreCase(b.getName()));
            return brands;
        } else {
            return brandRepository.findAll().stream()
                    .sorted((a, b) -> a.getName().compareToIgnoreCase(b.getName()))
                    .toList();
        }
    }

    /**
     * Tăng view-count atomically. Tách transaction riêng để failure
     * không phá luồng đọc chính.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void incrementViewCount(Long productId) {
        productRepository.incrementViewCount(productId);
    }

    /**
     * Lấy facet sidebar (Socket / Bus / Chuẩn / ...).
     * Nếu truyền {@code categoryId} hoặc {@code categorySlug} thì giới hạn trong danh mục đó.
     */
    @Transactional(readOnly = true)
    public List<SpecFacet> getFacets(Long categoryId, String categorySlug) {
        List<Object[]> rows;
        if (categoryId != null) {
            rows = specRepository.aggregateFacetsByCategory(categoryId);
        } else if (categorySlug != null && !categorySlug.isBlank()) {
            rows = specRepository.aggregateFacetsByCategorySlug(categorySlug);
        } else {
            rows = specRepository.aggregateFacets();
        }
        return groupFacets(rows);
    }

    private List<SpecFacet> groupFacets(List<Object[]> rows) {
        Set<String> allowed = new java.util.HashSet<>(allowedFacetKeys);
        Map<String, List<SpecFacet.FacetValue>> grouped = new LinkedHashMap<>();
        for (Object[] row : rows) {
            String name = (String) row[0];
            String value = (String) row[1];
            long count = ((Number) row[2]).longValue();
            if (name == null || value == null) continue;
            if (!allowed.contains(name)) continue;
            grouped.computeIfAbsent(name, k -> new ArrayList<>())
                    .add(new SpecFacet.FacetValue(value, count));
        }
        List<SpecFacet> result = new ArrayList<>(grouped.size());
        grouped.forEach((name, values) -> result.add(new SpecFacet(name, values)));
        return result;
    }

    // ===== private helpers =====

    private void apply(Product p, ProductRequest req) {
        p.setName(req.getName());
        p.setSku(blankToNull(req.getSku()));
        p.setShortDescription(req.getShortDescription());
        p.setDescription(req.getDescription());
        p.setEditorialReview(req.getEditorialReview());
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
