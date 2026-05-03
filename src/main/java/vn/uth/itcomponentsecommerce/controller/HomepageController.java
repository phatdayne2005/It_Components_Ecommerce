package vn.uth.itcomponentsecommerce.controller;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import vn.uth.itcomponentsecommerce.entity.Category;
import vn.uth.itcomponentsecommerce.entity.Product;
import vn.uth.itcomponentsecommerce.repository.CategoryRepository;
import vn.uth.itcomponentsecommerce.repository.ProductRepository;
import vn.uth.itcomponentsecommerce.service.ProductService;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

@Controller
public class HomepageController {

    private final CategoryRepository categoryRepository;
    private final ProductRepository productRepository;
    private final ProductService productService;

    public HomepageController(CategoryRepository categoryRepository,
                              ProductRepository productRepository,
                              ProductService productService) {
        this.categoryRepository = categoryRepository;
        this.productRepository = productRepository;
        this.productService = productService;
    }

    @GetMapping({"/", "/home"})
    public String homepage(Model model) {
        model.addAttribute("siteName", "TechParts");
        model.addAttribute("tagline", "Linh kiện máy tính chính hãng - Giá tốt nhất");

        List<Category> rootCats = categoryRepository.findAll().stream()
                .filter(c -> c.getParent() == null)
                .sorted((a, b) -> {
                    int sa = a.getSortOrder() == null ? 0 : a.getSortOrder();
                    int sb = b.getSortOrder() == null ? 0 : b.getSortOrder();
                    if (sa != sb) return Integer.compare(sa, sb);
                    return a.getName().compareToIgnoreCase(b.getName());
                })
                .limit(8)
                .toList();

        List<HomeCategory> categories = rootCats.stream()
                .map(c -> new HomeCategory(
                        c.getName(),
                        c.getSlug(),
                        c.getIcon() == null ? "fa-cube" : c.getIcon(),
                        productRepository.countByCategory_Id(c.getId())
                ))
                .toList();
        model.addAttribute("categories", categories);

        List<HomeProduct> featured = productService.findFeatured().stream()
                .map(HomeProduct::from)
                .toList();
        model.addAttribute("featuredProducts", featured);

        List<HomeProduct> heroProducts = productRepository
                .findRandomSoldProducts(PageRequest.of(0, 6))
                .stream()
                .map(HomeProduct::from)
                .toList();
        model.addAttribute("heroProducts", heroProducts);

        return "index";
    }

    public record HomeCategory(String name, String slug, String icon, long count) {}

    public record HomeProduct(
            Long id, String name, String slug,
            String category, BigDecimal price, BigDecimal oldPrice,
            String image, String badge
    ) {
        public static HomeProduct from(Product p) {
            String badge = computeBadge(p.getPrice(), p.getOldPrice());
            return new HomeProduct(
                    p.getId(),
                    p.getName(),
                    p.getSlug(),
                    p.getCategory() != null ? p.getCategory().getName() : "",
                    p.getPrice(),
                    p.getOldPrice(),
                    p.getImageUrl(),
                    badge
            );
        }

        private static String computeBadge(BigDecimal price, BigDecimal oldPrice) {
            if (oldPrice == null || price == null) return null;
            if (oldPrice.compareTo(price) <= 0) return null;
            BigDecimal diff = oldPrice.subtract(price);
            BigDecimal pct = diff.multiply(BigDecimal.valueOf(100))
                    .divide(oldPrice, 0, RoundingMode.HALF_UP);
            return "-" + pct + "%";
        }
    }
}
