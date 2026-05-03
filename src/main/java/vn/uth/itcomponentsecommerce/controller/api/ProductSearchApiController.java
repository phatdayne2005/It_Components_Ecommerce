package vn.uth.itcomponentsecommerce.controller.api;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import vn.uth.itcomponentsecommerce.dto.CatalogQuery;
import vn.uth.itcomponentsecommerce.dto.PageResponse;
import vn.uth.itcomponentsecommerce.dto.ProductCardView;
import vn.uth.itcomponentsecommerce.dto.ProductDetailView;
import vn.uth.itcomponentsecommerce.dto.SpecFacet;
import vn.uth.itcomponentsecommerce.entity.Brand;
import vn.uth.itcomponentsecommerce.service.ProductService;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/products")
public class ProductSearchApiController {
    private static final int MAX_PAGE_SIZE = 60;
    private static final List<String> ALLOWED_SORT_FIELDS = List.of("id", "price", "createdAt", "name", "sold", "viewCount");

    private final ProductService service;

    public ProductSearchApiController(ProductService service) {
        this.service = service;
    }

    @GetMapping
    public PageResponse<ProductCardView> search(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) String categorySlug,
            @RequestParam(required = false) List<Long> brandIds,
            @RequestParam(required = false) String priceRange,
            @RequestParam(required = false) BigDecimal minPrice,
            @RequestParam(required = false) BigDecimal maxPrice,
            @RequestParam(required = false) Boolean inStock,
            @RequestParam MultiValueMap<String, String> allParams,
            @PageableDefault(size = 24, sort = "id", direction = Sort.Direction.DESC) Pageable pageable
    ) {
        CatalogQuery query = new CatalogQuery();
        query.setQ(q);
        query.setCategoryId(categoryId);
        query.setCategorySlug(categorySlug);
        if (brandIds != null) query.setBrandIds(brandIds);
        Range resolved = resolveRange(priceRange, minPrice, maxPrice);
        query.setMinPrice(resolved.min());
        query.setMaxPrice(resolved.max());
        query.setInStock(inStock);
        query.setSpecs(parseSpecs(allParams));

        Pageable safe = sanitize(pageable);
        return PageResponse.from(service.searchCatalog(query, safe));
    }

    @GetMapping("/{slug}")
    public ProductDetailView detail(@PathVariable String slug) {
        ProductDetailView view = service.getDetailBySlug(slug);
        service.incrementViewCount(view.id());
        return view;
    }

    @GetMapping("/facets")
    public List<SpecFacet> facets(
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) String categorySlug
    ) {
        return service.getFacets(categoryId, categorySlug);
    }

    @GetMapping("/brands")
    public List<Brand> brands(
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) String categorySlug
    ) {
        return service.getBrandsByCategory(categoryId, categorySlug);
    }

    @GetMapping("/suggest")
    public List<ProductCardView> suggest(
            @RequestParam(required = false) String q,
            @RequestParam(required = false, defaultValue = "10") Integer limit
    ) {
        if (q == null || q.isBlank()) return Collections.emptyList();
        int safeLimit = (limit == null || limit < 1) ? 10 : Math.min(limit, 20);

        CatalogQuery query = new CatalogQuery();
        query.setQ(q.trim());
        Pageable pageable = PageRequest.of(0, safeLimit, Sort.by(Sort.Direction.DESC, "viewCount"));
        return service.searchCatalog(query, pageable).getContent();
    }

    private static Map<String, List<String>> parseSpecs(MultiValueMap<String, String> all) {
        Map<String, List<String>> result = new LinkedHashMap<>();
        if (all == null) return result;
        for (Map.Entry<String, List<String>> e : all.entrySet()) {
            String key = e.getKey();
            if (key == null) continue;
            if (!key.startsWith("specs[") || !key.endsWith("]") || key.length() <= 7) continue;

            String name = key.substring(6, key.length() - 1).trim();
            if (name.isEmpty()) continue;

            List<String> values = new ArrayList<>();
            for (String value : e.getValue()) {
                if (value != null && !value.isBlank()) values.add(value.trim());
            }
            if (!values.isEmpty()) result.put(name, values);
        }
        return result;
    }

    private static Pageable sanitize(Pageable pageable) {
        int size = Math.min(pageable.getPageSize(), MAX_PAGE_SIZE);
        Sort sort = pageable.getSort();
        boolean allValid = sort.stream().allMatch(order -> ALLOWED_SORT_FIELDS.contains(order.getProperty()));
        Sort safeSort = allValid ? sort : Sort.by(Sort.Direction.DESC, "id");
        return PageRequest.of(pageable.getPageNumber(), size, safeSort);
    }

    private static Range resolveRange(String priceRange, BigDecimal minPrice, BigDecimal maxPrice) {
        if (minPrice != null || maxPrice != null) {
            return new Range(minPrice, maxPrice);
        }
        if (priceRange == null || priceRange.isBlank()) {
            return new Range(null, null);
        }

        BigDecimal m = BigDecimal.valueOf(1_000_000L);
        return switch (priceRange) {
            // default ranges
            case "under_5" -> new Range(null, m.multiply(BigDecimal.valueOf(5)));
            case "5_10" -> new Range(m.multiply(BigDecimal.valueOf(5)), m.multiply(BigDecimal.TEN));
            case "10_20" -> new Range(m.multiply(BigDecimal.TEN), m.multiply(BigDecimal.valueOf(20)));
            case "20_40" -> new Range(m.multiply(BigDecimal.valueOf(20)), m.multiply(BigDecimal.valueOf(40)));
            case "over_40" -> new Range(m.multiply(BigDecimal.valueOf(40)), null);

            // ram ranges
            case "under_2" -> new Range(null, m.multiply(BigDecimal.valueOf(2)));
            case "2_4" -> new Range(m.multiply(BigDecimal.valueOf(2)), m.multiply(BigDecimal.valueOf(4)));
            case "4_6" -> new Range(m.multiply(BigDecimal.valueOf(4)), m.multiply(BigDecimal.valueOf(6)));
            case "6_10" -> new Range(m.multiply(BigDecimal.valueOf(6)), m.multiply(BigDecimal.TEN));
            case "over_10" -> new Range(m.multiply(BigDecimal.TEN), null);

            // mainboard ranges
            case "under_3" -> new Range(null, m.multiply(BigDecimal.valueOf(3)));
            case "3_5" -> new Range(m.multiply(BigDecimal.valueOf(3)), m.multiply(BigDecimal.valueOf(5)));
            case "5_8" -> new Range(m.multiply(BigDecimal.valueOf(5)), m.multiply(BigDecimal.valueOf(8)));
            case "8_12" -> new Range(m.multiply(BigDecimal.valueOf(8)), m.multiply(BigDecimal.valueOf(12)));
            case "over_12" -> new Range(m.multiply(BigDecimal.valueOf(12)), null);

            // vga ranges
            case "under_10" -> new Range(null, m.multiply(BigDecimal.TEN));
            case "20_30" -> new Range(m.multiply(BigDecimal.valueOf(20)), m.multiply(BigDecimal.valueOf(30)));
            case "30_50" -> new Range(m.multiply(BigDecimal.valueOf(30)), m.multiply(BigDecimal.valueOf(50)));
            case "over_50" -> new Range(m.multiply(BigDecimal.valueOf(50)), null);

            default -> new Range(null, null);
        };
    }

    private record Range(BigDecimal min, BigDecimal max) {}
}
