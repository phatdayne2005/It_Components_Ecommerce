package vn.uth.itcomponentsecommerce.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import vn.uth.itcomponentsecommerce.entity.*;
import vn.uth.itcomponentsecommerce.repository.*;
import vn.uth.itcomponentsecommerce.service.SlugUtil;

import java.math.BigDecimal;
import java.util.*;

@Component
public class DataSeeder implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DataSeeder.class);

    private final RoleRepository roleRepository;
    private final UserRepository userRepository;
    private final CategoryRepository categoryRepository;
    private final BrandRepository brandRepository;
    private final ProductRepository productRepository;
    private final PasswordEncoder passwordEncoder;

    @Value("${app.admin.username}") private String adminUsername;
    @Value("${app.admin.password}") private String adminPassword;
    @Value("${app.admin.email}")    private String adminEmail;
    @Value("${app.seed.enabled:true}") private boolean seedEnabled;

    public DataSeeder(RoleRepository roleRepository,
                      UserRepository userRepository,
                      CategoryRepository categoryRepository,
                      BrandRepository brandRepository,
                      ProductRepository productRepository,
                      PasswordEncoder passwordEncoder) {
        this.roleRepository = roleRepository;
        this.userRepository = userRepository;
        this.categoryRepository = categoryRepository;
        this.brandRepository = brandRepository;
        this.productRepository = productRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public void run(String... args) {
        seedRolesAndAdmin();
        if (!seedEnabled) {
            log.info("app.seed.enabled=false — bỏ qua seed dữ liệu mẫu.");
            return;
        }
        seedCategories();
        seedBrands();
        seedProducts();
    }

    private void seedRolesAndAdmin() {
        Role adminRole = roleRepository.findByName("ROLE_ADMIN")
                .orElseGet(() -> roleRepository.save(new Role("ROLE_ADMIN")));
        Role userRole = roleRepository.findByName("ROLE_USER")
                .orElseGet(() -> roleRepository.save(new Role("ROLE_USER")));

        if (userRepository.existsByUsername(adminUsername)) return;

        User admin = new User();
        admin.setUsername(adminUsername);
        admin.setEmail(adminEmail);
        admin.setPassword(passwordEncoder.encode(adminPassword));
        admin.setFullName("Quản trị viên");
        admin.setEnabled(true);
        Set<Role> roles = new HashSet<>();
        roles.add(adminRole);
        roles.add(userRole);
        admin.setRoles(roles);
        userRepository.save(admin);
        log.info("Đã tạo admin: {} / {}", adminUsername, adminPassword);
    }

    private void seedCategories() {
        if (categoryRepository.count() > 0) return;

        // Top-level categories phổ biến trong cửa hàng linh kiện PC
        Object[][] data = {
                {"CPU", "fa-microchip", "Bộ vi xử lý Intel, AMD"},
                {"Mainboard", "fa-server", "Bo mạch chủ các loại"},
                {"RAM", "fa-memory", "Bộ nhớ DDR4, DDR5"},
                {"VGA", "fa-display", "Card màn hình rời"},
                {"SSD", "fa-hard-drive", "Ổ cứng thể rắn NVMe / SATA"},
                {"HDD", "fa-database", "Ổ cứng cơ truyền thống"},
                {"PSU", "fa-plug", "Nguồn máy tính"},
                {"Case", "fa-cube", "Vỏ máy tính"},
                {"Tản nhiệt", "fa-fan", "Tản khí và tản nước"},
                {"Màn hình", "fa-tv", "Màn hình gaming, văn phòng"},
                {"Bàn phím", "fa-keyboard", "Bàn phím cơ, gaming"},
                {"Chuột", "fa-computer-mouse", "Chuột gaming, văn phòng"}
        };
        int order = 0;
        for (Object[] row : data) {
            Category c = new Category();
            c.setName((String) row[0]);
            c.setIcon((String) row[1]);
            c.setDescription((String) row[2]);
            c.setSlug(SlugUtil.toSlug((String) row[0]));
            c.setSortOrder(order++);
            categoryRepository.save(c);
        }
        log.info("Đã seed {} danh mục.", data.length);
    }

    private void seedBrands() {
        if (brandRepository.count() > 0) return;

        String[][] data = {
                {"Intel", "https://logo.clearbit.com/intel.com"},
                {"AMD", "https://logo.clearbit.com/amd.com"},
                {"NVIDIA", "https://logo.clearbit.com/nvidia.com"},
                {"ASUS", "https://logo.clearbit.com/asus.com"},
                {"MSI", "https://logo.clearbit.com/msi.com"},
                {"Gigabyte", "https://logo.clearbit.com/gigabyte.com"},
                {"Corsair", "https://logo.clearbit.com/corsair.com"},
                {"Samsung", "https://logo.clearbit.com/samsung.com"},
                {"Western Digital", "https://logo.clearbit.com/westerndigital.com"},
                {"Cooler Master", "https://logo.clearbit.com/coolermaster.com"},
                {"Lian Li", "https://logo.clearbit.com/lian-li.com"},
                {"Noctua", "https://logo.clearbit.com/noctua.at"},
                {"Logitech", "https://logo.clearbit.com/logitech.com"},
                {"G.Skill", "https://logo.clearbit.com/gskill.com"}
        };
        for (String[] row : data) {
            Brand b = new Brand();
            b.setName(row[0]);
            b.setLogoUrl(row[1]);
            b.setSlug(SlugUtil.toSlug(row[0]));
            brandRepository.save(b);
        }
        log.info("Đã seed {} thương hiệu.", data.length);
    }

    private void seedProducts() {
        if (productRepository.count() > 0) return;

        Map<String, Category> cats = new HashMap<>();
        categoryRepository.findAll().forEach(c -> cats.put(c.getName(), c));
        Map<String, Brand> brands = new HashMap<>();
        brandRepository.findAll().forEach(b -> brands.put(b.getName(), b));

        List<ProductSeed> seeds = List.of(
                new ProductSeed(
                        "CPU-I7-14700K", "Intel Core i7-14700K", "CPU", "Intel",
                        new BigDecimal("10990000"), new BigDecimal("12490000"),
                        "https://images.unsplash.com/photo-1591799264318-7e6ef8ddb7ea?w=600",
                        "20 nhân (8P+12E), 28 luồng, xung tối đa 5.6GHz", 36, 25,
                        List.of(
                                new Spec("Socket", "LGA1700"),
                                new Spec("Số nhân", "20 (8P+12E)"),
                                new Spec("Số luồng", "28"),
                                new Spec("Xung cơ bản", "3.4 GHz"),
                                new Spec("Xung tối đa", "5.6 GHz"),
                                new Spec("TDP", "125W")
                        )),
                new ProductSeed(
                        "MB-ROG-B760F", "ASUS ROG STRIX B760-F GAMING WIFI", "Mainboard", "ASUS",
                        new BigDecimal("7290000"), new BigDecimal("8190000"),
                        "https://images.unsplash.com/photo-1518770660439-4636190af475?w=600",
                        "Bo mạch chủ chipset B760, hỗ trợ DDR5, WiFi 6E", 36, 12,
                        List.of(
                                new Spec("Socket", "LGA1700"),
                                new Spec("Chipset", "Intel B760"),
                                new Spec("Form factor", "ATX"),
                                new Spec("Khe RAM", "4 x DDR5"),
                                new Spec("PCIe", "PCIe 5.0 x16"),
                                new Spec("WiFi", "WiFi 6E")
                        )),
                new ProductSeed(
                        "RAM-CV-32G-DDR5", "Corsair Vengeance RGB 32GB (2x16GB) DDR5 6000MHz", "RAM", "Corsair",
                        new BigDecimal("3490000"), new BigDecimal("3890000"),
                        "https://images.unsplash.com/photo-1562976540-1502c2145186?w=600",
                        "Kit RAM DDR5 6000MHz, RGB iCUE", 36, 40,
                        List.of(
                                new Spec("Loại", "DDR5"),
                                new Spec("Dung lượng", "32GB (2x16GB)"),
                                new Spec("Bus", "6000 MHz"),
                                new Spec("CL", "CL36"),
                                new Spec("RGB", "Có")
                        )),
                new ProductSeed(
                        "VGA-RTX4070S", "NVIDIA GeForce RTX 4070 SUPER 12GB", "VGA", "NVIDIA",
                        new BigDecimal("17990000"), new BigDecimal("19490000"),
                        "https://images.unsplash.com/photo-1591488320449-011701bb6704?w=600",
                        "Card đồ hoạ RTX 40 series, 12GB GDDR6X", 36, 8,
                        List.of(
                                new Spec("GPU", "RTX 4070 SUPER"),
                                new Spec("VRAM", "12GB GDDR6X"),
                                new Spec("Bus", "192-bit"),
                                new Spec("Cổng", "3x DP, 1x HDMI"),
                                new Spec("Yêu cầu nguồn", "650W")
                        )),
                new ProductSeed(
                        "SSD-SS990P-2TB", "Samsung 990 PRO 2TB NVMe PCIe 4.0", "SSD", "Samsung",
                        new BigDecimal("4290000"), new BigDecimal("4790000"),
                        "https://images.unsplash.com/photo-1597872200969-2b65d56bd16b?w=600",
                        "SSD M.2 NVMe đọc 7450 MB/s, ghi 6900 MB/s", 60, 30,
                        List.of(
                                new Spec("Chuẩn", "NVMe PCIe Gen4 x4"),
                                new Spec("Form factor", "M.2 2280"),
                                new Spec("Dung lượng", "2 TB"),
                                new Spec("Đọc tuần tự", "7450 MB/s"),
                                new Spec("Ghi tuần tự", "6900 MB/s")
                        )),
                new ProductSeed(
                        "PSU-CMMWE-850", "Cooler Master MWE Gold 850W V2", "PSU", "Cooler Master",
                        new BigDecimal("2790000"), new BigDecimal("3190000"),
                        "https://images.unsplash.com/photo-1587202372634-32705e3bf49c?w=600",
                        "Nguồn 850W, chuẩn 80+ Gold, full modular", 60, 20,
                        List.of(
                                new Spec("Công suất", "850W"),
                                new Spec("Chuẩn", "80+ Gold"),
                                new Spec("Modular", "Full modular"),
                                new Spec("ATX", "ATX 3.0")
                        )),
                new ProductSeed(
                        "CASE-LL-O11-EVO", "Lian Li O11 Dynamic EVO", "Case", "Lian Li",
                        new BigDecimal("4590000"), new BigDecimal("4990000"),
                        "https://images.unsplash.com/photo-1587202372775-e229f172b9d7?w=600",
                        "Case mid-tower trong suốt, hỗ trợ E-ATX", 24, 15,
                        List.of(
                                new Spec("Form factor", "Mid-tower"),
                                new Spec("Hỗ trợ MB", "E-ATX, ATX, mATX, ITX"),
                                new Spec("Vật liệu", "Thép, kính cường lực"),
                                new Spec("Màu", "Đen / Trắng")
                        )),
                new ProductSeed(
                        "CPU-COOL-NHD15", "Noctua NH-D15 chromax.black", "Tản nhiệt", "Noctua",
                        new BigDecimal("2890000"), new BigDecimal("3190000"),
                        "https://images.unsplash.com/photo-1587202372616-b43abea06c2a?w=600",
                        "Tản khí cao cấp dual-tower, 6 ống đồng", 72, 18,
                        List.of(
                                new Spec("Loại", "Tản khí dual-tower"),
                                new Spec("Quạt", "2x NF-A15 PWM 140mm"),
                                new Spec("Số ống đồng", "6"),
                                new Spec("TDP hỗ trợ", "Đến 220W")
                        )),
                new ProductSeed(
                        "VGA-RTX4090", "NVIDIA GeForce RTX 4090 24GB", "VGA", "NVIDIA",
                        new BigDecimal("48990000"), null,
                        "https://images.unsplash.com/photo-1591488320449-011701bb6704?w=600&q=80",
                        "Flagship card đồ hoạ thế hệ Ada Lovelace", 36, 3,
                        List.of(
                                new Spec("GPU", "RTX 4090"),
                                new Spec("VRAM", "24GB GDDR6X"),
                                new Spec("Bus", "384-bit")
                        )),
                new ProductSeed(
                        "CPU-RYZEN-7700X", "AMD Ryzen 7 7700X", "CPU", "AMD",
                        new BigDecimal("8490000"), new BigDecimal("9290000"),
                        "https://images.unsplash.com/photo-1591799264318-7e6ef8ddb7ea?w=600&q=80",
                        "8 nhân Zen 4, xung boost 5.4GHz", 36, 22,
                        List.of(
                                new Spec("Socket", "AM5"),
                                new Spec("Số nhân", "8"),
                                new Spec("Số luồng", "16"),
                                new Spec("Xung tối đa", "5.4 GHz")
                        ))
        );

        for (ProductSeed s : seeds) {
            Product p = new Product();
            p.setSku(s.sku);
            p.setName(s.name);
            p.setSlug(SlugUtil.toSlug(s.name));
            p.setShortDescription(s.shortDesc);
            p.setDescription(s.shortDesc);
            p.setPrice(s.price);
            p.setOldPrice(s.oldPrice);
            p.setStock(s.stock);
            p.setWarrantyMonths(s.warrantyMonths);
            p.setImageUrl(s.image);
            p.setActive(true);
            p.setCategory(cats.get(s.categoryName));
            p.setBrand(brands.get(s.brandName));

            int specOrder = 0;
            for (Spec sp : s.specs) {
                p.getSpecifications().add(new ProductSpecification(p, sp.name, sp.value, specOrder++));
            }
            p.getImages().add(new ProductImage(p, s.image, 0));
            productRepository.save(p);
        }
        log.info("Đã seed {} sản phẩm mẫu.", seeds.size());
    }

    private record ProductSeed(
            String sku, String name, String categoryName, String brandName,
            BigDecimal price, BigDecimal oldPrice, String image,
            String shortDesc, int warrantyMonths, int stock,
            List<Spec> specs
    ) {}

    private record Spec(String name, String value) {}
}
