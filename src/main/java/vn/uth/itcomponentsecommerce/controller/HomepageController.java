package vn.uth.itcomponentsecommerce.controller;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import java.util.List;
import java.util.Map;

@Controller
public class        HomepageController {

    @GetMapping({"/", "/home"})
    public String homepage(Model model) {
        model.addAttribute("siteName", "TechParts");
        model.addAttribute("tagline", "Linh kiện máy tính chính hãng - Giá tốt nhất");

        model.addAttribute("categories", List.of(
                Map.of("name", "CPU", "icon", "fa-microchip", "count", 42),
                Map.of("name", "Mainboard", "icon", "fa-server", "count", 36),
                Map.of("name", "RAM", "icon", "fa-memory", "count", 58),
                Map.of("name", "VGA", "icon", "fa-display", "count", 24),
                Map.of("name", "SSD/HDD", "icon", "fa-hard-drive", "count", 71),
                Map.of("name", "PSU", "icon", "fa-plug", "count", 29),
                Map.of("name", "Case", "icon", "fa-cube", "count", 33),
                Map.of("name", "Tản nhiệt", "icon", "fa-fan", "count", 47)
        ));

        model.addAttribute("featuredProducts", List.of(
                Map.of(
                        "id", 1,
                        "name", "Intel Core i7-14700K",
                        "category", "CPU",
                        "price", 10990000,
                        "oldPrice", 12490000,
                        "image", "https://images.unsplash.com/photo-1591799264318-7e6ef8ddb7ea?w=400",
                        "badge", "HOT"
                ),
                Map.of(
                        "id", 2,
                        "name", "ASUS ROG STRIX B760-F GAMING",
                        "category", "Mainboard",
                        "price", 7290000,
                        "oldPrice", 8190000,
                        "image", "https://images.unsplash.com/photo-1518770660439-4636190af475?w=400",
                        "badge", "-11%"
                ),
                Map.of(
                        "id", 3,
                        "name", "Corsair Vengeance RGB 32GB DDR5",
                        "category", "RAM",
                        "price", 3490000,
                        "oldPrice", 3890000,
                        "image", "https://images.unsplash.com/photo-1562976540-1502c2145186?w=400",
                        "badge", "MỚI"
                ),
                Map.of(
                        "id", 4,
                        "name", "NVIDIA RTX 4070 SUPER 12GB",
                        "category", "VGA",
                        "price", 17990000,
                        "oldPrice", 19490000,
                        "image", "https://images.unsplash.com/photo-1591488320449-011701bb6704?w=400",
                        "badge", "HOT"
                ),
                Map.of(
                        "id", 5,
                        "name", "Samsung 990 PRO 2TB NVMe",
                        "category", "SSD",
                        "price", 4290000,
                        "oldPrice", 4790000,
                        "image", "https://images.unsplash.com/photo-1597872200969-2b65d56bd16b?w=400",
                        "badge", "-10%"
                ),
                Map.of(
                        "id", 6,
                        "name", "Cooler Master MWE Gold 850W",
                        "category", "PSU",
                        "price", 2790000,
                        "oldPrice", 3190000,
                        "image", "https://images.unsplash.com/photo-1587202372634-32705e3bf49c?w=400",
                        "badge", "BÁN CHẠY"
                ),
                Map.of(
                        "id", 7,
                        "name", "Lian Li O11 Dynamic EVO",
                        "category", "Case",
                        "price", 4590000,
                        "oldPrice", 4990000,
                        "image", "https://images.unsplash.com/photo-1587202372775-e229f172b9d7?w=400",
                        "badge", "MỚI"
                ),
                Map.of(
                        "id", 8,
                        "name", "Noctua NH-D15 chromax.black",
                        "category", "Tản nhiệt",
                        "price", 2890000,
                        "oldPrice", 3190000,
                        "image", "https://images.unsplash.com/photo-1587202372616-b43abea06c2a?w=400",
                        "badge", "-9%"
                )
        ));

        return "index";
    }
}
