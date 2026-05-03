package vn.uth.itcomponentsecommerce.controller;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import vn.uth.itcomponentsecommerce.dto.CatalogQuery;
import vn.uth.itcomponentsecommerce.dto.ProductCardView;
import vn.uth.itcomponentsecommerce.dto.ProductDetailView;
import vn.uth.itcomponentsecommerce.dto.SpecFacet;
import vn.uth.itcomponentsecommerce.entity.Brand;
import vn.uth.itcomponentsecommerce.entity.Category;
import vn.uth.itcomponentsecommerce.repository.BrandRepository;
import vn.uth.itcomponentsecommerce.repository.CategoryRepository;
import vn.uth.itcomponentsecommerce.service.ProductService;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Controller
public class ProductCatalogController {
    private static final int DEFAULT_SIZE = 12;

    private final ProductService productService;
    private final CategoryRepository categoryRepository;
    private final BrandRepository brandRepository;

    public ProductCatalogController(ProductService productService,
                                    CategoryRepository categoryRepository,
                                    BrandRepository brandRepository) {
        this.productService = productService;
        this.categoryRepository = categoryRepository;
        this.brandRepository = brandRepository;
    }

    @GetMapping("/products")
    public String catalog(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String categorySlug,
            @RequestParam(required = false) List<Long> brandIds,
            @RequestParam(required = false) String priceRange,
            @RequestParam(required = false) BigDecimal minPrice,
            @RequestParam(required = false) BigDecimal maxPrice,
            @RequestParam(required = false) Boolean inStock,
            @RequestParam(required = false, defaultValue = "newest") String sort,
            @RequestParam(required = false, defaultValue = "0") int page,
            @RequestParam MultiValueMap<String, String> allParams,
            Model model
    ) {
        return renderCatalog(q, categorySlug, brandIds, priceRange, minPrice, maxPrice, inStock, sort, page, allParams, model);
    }

    @GetMapping("/products/category/{categorySlug}")
    public String catalogByCategory(
            @PathVariable String categorySlug,
            @RequestParam(required = false) String q,
            @RequestParam(required = false) List<Long> brandIds,
            @RequestParam(required = false) String priceRange,
            @RequestParam(required = false) BigDecimal minPrice,
            @RequestParam(required = false) BigDecimal maxPrice,
            @RequestParam(required = false) Boolean inStock,
            @RequestParam(required = false, defaultValue = "newest") String sort,
            @RequestParam(required = false, defaultValue = "0") int page,
            @RequestParam MultiValueMap<String, String> allParams,
            Model model
    ) {
        return renderCatalog(q, categorySlug, brandIds, priceRange, minPrice, maxPrice, inStock, sort, page, allParams, model);
    }

    @GetMapping("/products/{slug}")
    public String detail(@PathVariable String slug, Model model) {
        ProductDetailView product = productService.getDetailBySlug(slug);
        productService.incrementViewCount(product.id());

        model.addAttribute("siteName", "TechParts");
        model.addAttribute("product", product);
        model.addAttribute("pageTitle", product.name() + " - TechParts");
        model.addAttribute("facets", productService.getFacets(product.categoryId(), null));
        return "catalog/detail";
    }

    private String renderCatalog(
            String q,
            String categorySlug,
            List<Long> brandIds,
            String priceRange,
            BigDecimal minPrice,
            BigDecimal maxPrice,
            Boolean inStock,
            String sort,
            int page,
            MultiValueMap<String, String> allParams,
            Model model
    ) {
        CatalogQuery query = new CatalogQuery();
        query.setQ(q);
        query.setCategorySlug(categorySlug);
        query.setBrandIds(brandIds == null ? List.of() : brandIds);
        query.setInStock(inStock);
        query.setSpecs(parseSpecs(allParams));

        PageRequest pageRequest = PageRequest.of(Math.max(page, 0), DEFAULT_SIZE, resolveSort(sort));
        Page<ProductCardView> products = productService.searchCatalog(query, pageRequest);

        Optional<Category> activeCategory = categorySlug == null ? Optional.empty() : categoryRepository.findBySlug(categorySlug);
        List<SpecFacet> facets = productService.getFacets(
                activeCategory.map(Category::getId).orElse(null),
                activeCategory.isPresent() ? null : categorySlug
        );

        List<Category> categories = categoryRepository.findAll().stream()
                .filter(c -> c.getParent() == null)
                .sorted((a, b) -> a.getName().compareToIgnoreCase(b.getName()))
                .toList();
        List<Brand> brands = productService.getBrandsByCategory(
                activeCategory.map(Category::getId).orElse(null),
                activeCategory.isPresent() ? null : categorySlug
        );

        List<PriceRangeOption> priceRanges = buildPriceRanges(activeCategory.map(Category::getSlug).orElse(null));
        String effectivePriceRange = applyPriceRange(query, priceRange, minPrice, maxPrice, priceRanges);

        model.addAttribute("siteName", "TechParts");
        model.addAttribute("pageTitle", "Danh mục linh kiện - TechParts");
        model.addAttribute("products", products);
        model.addAttribute("facets", facets);
        model.addAttribute("categories", categories);
        model.addAttribute("brands", brands);
        model.addAttribute("activeCategory", activeCategory.orElse(null));
        model.addAttribute("selectedBrandIds", query.getBrandIds());
        model.addAttribute("selectedSpecs", query.getSpecs());
        model.addAttribute("priceRanges", priceRanges);
        model.addAttribute("selectedPriceRange", effectivePriceRange);
        model.addAttribute("q", q);
        model.addAttribute("minPrice", query.getMinPrice());
        model.addAttribute("maxPrice", query.getMaxPrice());
        model.addAttribute("inStock", Boolean.TRUE.equals(inStock));
        model.addAttribute("sort", sort);
        return "catalog/list";
    }

    private static String applyPriceRange(CatalogQuery query,
                                          String selectedKey,
                                          BigDecimal minPrice,
                                          BigDecimal maxPrice,
                                          List<PriceRangeOption> options) {
        if (selectedKey != null && !selectedKey.isBlank()) {
            for (PriceRangeOption option : options) {
                if (option.key().equals(selectedKey)) {
                    query.setMinPrice(option.min());
                    query.setMaxPrice(option.max());
                    return option.key();
                }
            }
        }

        query.setMinPrice(minPrice);
        query.setMaxPrice(maxPrice);
        if (minPrice == null && maxPrice == null) return "";
        for (PriceRangeOption option : options) {
            if (option.matches(minPrice, maxPrice)) {
                return option.key();
            }
        }
        return "";
    }

    private static List<PriceRangeOption> buildPriceRanges(String categorySlug) {
        BigDecimal million = BigDecimal.valueOf(1_000_000L);
        if ("ram".equalsIgnoreCase(categorySlug)) {
            return List.of(
                    new PriceRangeOption("under_2", "Dưới 2tr", null, million.multiply(BigDecimal.valueOf(2))),
                    new PriceRangeOption("2_4", "Từ 2 tới 4tr", million.multiply(BigDecimal.valueOf(2)), million.multiply(BigDecimal.valueOf(4))),
                    new PriceRangeOption("4_6", "Từ 4 tới 6tr", million.multiply(BigDecimal.valueOf(4)), million.multiply(BigDecimal.valueOf(6))),
                    new PriceRangeOption("6_10", "Từ 6 tới 10tr", million.multiply(BigDecimal.valueOf(6)), million.multiply(BigDecimal.TEN)),
                    new PriceRangeOption("over_10", "Trên 10 triệu", million.multiply(BigDecimal.TEN), null)
            );
        }
        if ("mainboard".equalsIgnoreCase(categorySlug)) {
            return List.of(
                    new PriceRangeOption("under_3", "Dưới 3tr", null, million.multiply(BigDecimal.valueOf(3))),
                    new PriceRangeOption("3_5", "Từ 3 tới 5tr", million.multiply(BigDecimal.valueOf(3)), million.multiply(BigDecimal.valueOf(5))),
                    new PriceRangeOption("5_8", "Từ 5 tới 8tr", million.multiply(BigDecimal.valueOf(5)), million.multiply(BigDecimal.valueOf(8))),
                    new PriceRangeOption("8_12", "Từ 8 tới 12tr", million.multiply(BigDecimal.valueOf(8)), million.multiply(BigDecimal.valueOf(12))),
                    new PriceRangeOption("over_12", "Trên 12 triệu", million.multiply(BigDecimal.valueOf(12)), null)
            );
        }
        if ("vga".equalsIgnoreCase(categorySlug)) {
            return List.of(
                    new PriceRangeOption("under_10", "Dưới 10tr", null, million.multiply(BigDecimal.TEN)),
                    new PriceRangeOption("10_20", "Từ 10 tới 20tr", million.multiply(BigDecimal.TEN), million.multiply(BigDecimal.valueOf(20))),
                    new PriceRangeOption("20_30", "Từ 20 tới 30tr", million.multiply(BigDecimal.valueOf(20)), million.multiply(BigDecimal.valueOf(30))),
                    new PriceRangeOption("30_50", "Từ 30 tới 50tr", million.multiply(BigDecimal.valueOf(30)), million.multiply(BigDecimal.valueOf(50))),
                    new PriceRangeOption("over_50", "Trên 50 triệu", million.multiply(BigDecimal.valueOf(50)), null)
            );
        }
        return List.of(
                new PriceRangeOption("under_5", "Dưới 5tr", null, million.multiply(BigDecimal.valueOf(5))),
                new PriceRangeOption("5_10", "Từ 5 tới 10tr", million.multiply(BigDecimal.valueOf(5)), million.multiply(BigDecimal.TEN)),
                new PriceRangeOption("10_20", "Từ 10 tới 20tr", million.multiply(BigDecimal.TEN), million.multiply(BigDecimal.valueOf(20))),
                new PriceRangeOption("20_40", "Từ 20 tới 40tr", million.multiply(BigDecimal.valueOf(20)), million.multiply(BigDecimal.valueOf(40))),
                new PriceRangeOption("over_40", "Trên 40 triệu", million.multiply(BigDecimal.valueOf(40)), null)
        );
    }

    private static Map<String, List<String>> parseSpecs(MultiValueMap<String, String> params) {
        Map<String, List<String>> map = new LinkedHashMap<>();
        for (Map.Entry<String, List<String>> e : params.entrySet()) {
            String key = e.getKey();
            if (key == null || !key.startsWith("specs[") || !key.endsWith("]")) continue;
            String name = key.substring(6, key.length() - 1).trim();
            if (name.isEmpty()) continue;
            for (String value : e.getValue()) {
                if (value == null || value.isBlank()) continue;
                map.computeIfAbsent(name, k -> new ArrayList<>()).add(value.trim());
            }
        }
        return map;
    }

    private static Sort resolveSort(String sort) {
        if ("price_asc".equalsIgnoreCase(sort)) return Sort.by(Sort.Direction.ASC, "price");
        if ("price_desc".equalsIgnoreCase(sort)) return Sort.by(Sort.Direction.DESC, "price");
        if ("sold".equalsIgnoreCase(sort)) return Sort.by(Sort.Direction.DESC, "sold");
        if ("view".equalsIgnoreCase(sort)) return Sort.by(Sort.Direction.DESC, "viewCount");
        if ("name".equalsIgnoreCase(sort)) return Sort.by(Sort.Direction.ASC, "name");
        return Sort.by(Sort.Direction.DESC, "id");
    }

    public record PriceRangeOption(String key, String label, BigDecimal min, BigDecimal max) {
        public boolean matches(BigDecimal currentMin, BigDecimal currentMax) {
            boolean minOk = (min == null && currentMin == null) || (min != null && min.compareTo(currentMin) == 0);
            boolean maxOk = (max == null && currentMax == null) || (max != null && max.compareTo(currentMax) == 0);
            return minOk && maxOk;
        }
    }
}
