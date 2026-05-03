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
    private final VoucherRepository voucherRepository;
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
                      VoucherRepository voucherRepository,
                      PasswordEncoder passwordEncoder) {
        this.roleRepository = roleRepository;
        this.userRepository = userRepository;
        this.categoryRepository = categoryRepository;
        this.brandRepository = brandRepository;
        this.productRepository = productRepository;
        this.voucherRepository = voucherRepository;
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
        seedVouchers();
    }

    private void seedVouchers() {
        if (voucherRepository.count() > 0) {
            return;
        }
        Voucher v = new Voucher();
        v.setCode("TECHPARTS10");
        v.setName("Giảm 10% tối đa 500.000đ");
        v.setDiscountType(Voucher.DiscountType.PERCENT);
        v.setDiscountValue(new BigDecimal("10"));
        v.setMinOrder(new BigDecimal("500000"));
        v.setMaxDiscount(new BigDecimal("500000"));
        v.setActive(true);
        v.setUsedCount(0);
        voucherRepository.save(v);
        log.info("Đã seed voucher mẫu TECHPARTS10.");
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
                        "20 nhân (8P+12E), 28 luồng, xung boost tối đa 5.6 GHz", 36, 25,
                        List.of(
                                new Spec("Dòng CPU", "Intel Core i7 (thế hệ 14 — Raptor Lake Refresh)"),
                                new Spec("Socket", "LGA1700"),
                                new Spec("Kiến trúc", "Hybrid Performance (P-core + E-core), tiến trình Intel 7"),
                                new Spec("Số nhân P-core / E-core", "8P + 12E"),
                                new Spec("Tổng luồng xử lý", "28"),
                                new Spec("Xung cơ bản (P-core)", "3.4 GHz"),
                                new Spec("Xung turbo tối đa (P-core)", "5.6 GHz"),
                                new Spec("Cache L3 (Intel Smart Cache)", "~33 MB"),
                                new Spec("TDP cơ bản / TDP tối đa", "125W / 253W"),
                                new Spec("Bộ nhớ hỗ trợ", "DDR5-5600 / DDR4-3200 (Dual-channel)"),
                                new Spec("PCIe từ CPU", "PCIe 5.0 / 4.0"),
                                new Spec("GPU tích hợp", "Intel UHD Graphics 770")
                        )),
                new ProductSeed(
                        "MB-ROG-B760F", "ASUS ROG STRIX B760-F GAMING WIFI", "Mainboard", "ASUS",
                        new BigDecimal("7290000"), new BigDecimal("8190000"),
                        "https://images.unsplash.com/photo-1518770660439-4636190af475?w=600",
                        "Bo mạch chủ chipset B760, hỗ trợ DDR5, WiFi 6E", 36, 12,
                        List.of(
                                new Spec("Chipset", "Intel B760"),
                                new Spec("Socket CPU", "LGA1700 (hỗ trợ Intel Gen 12/13/14)"),
                                new Spec("Form factor", "ATX (30.5 × 24.4 cm)"),
                                new Spec("Khe RAM", "4 × DDR5 DIMM (Dual-channel)"),
                                new Spec("Dung lượng RAM tối đa", "Lên đến 128 GB — kiểm tra QVL"),
                                new Spec("PCIe x16", "1 × PCIe 5.0 x16"),
                                new Spec("Khe M.2 NVMe", "3 × M.2 (hỗ trợ PCIe 4.0)"),
                                new Spec("LAN", "Intel 2.5Gb Ethernet"),
                                new Spec("Không dây", "WiFi 6E (802.11ax), Bluetooth 5.3"),
                                new Spec("Âm thanh", "ROG SupremeFX — Realtek / codec cao cấp"),
                                new Spec("USB", "USB 3.2 Gen 2x2 Type-C (20 Gbps), USB 3.2 Type-A nhiều cổng")
                        )),
                new ProductSeed(
                        "RAM-CV-32G-DDR5", "Corsair Vengeance RGB 32GB (2x16GB) DDR5 6000MHz", "RAM", "Corsair",
                        new BigDecimal("3490000"), new BigDecimal("3890000"),
                        "https://images.unsplash.com/photo-1562976540-1502c2145186?w=600",
                        "Kit RAM DDR5 6000MHz, RGB iCUE", 36, 40,
                        List.of(
                                new Spec("Chuẩn bộ nhớ", "DDR5 SDRAM (DIMM)"),
                                new Spec("Dung lượng kit", "32 GB (2 × 16 GB)"),
                                new Spec("Bus / XMP / EXPO", "6000 MT/s — hỗ trợ XMP 3.0 / EXPO (kiểm tra QVL mainboard)"),
                                new Spec("Thời gian / Điện áp", "CL36 — 1.35V (tham khảo thông tin stick)"),
                                new Spec("Hệ số màu RGB", "Có — đồng bộ Corsair iCUE"),
                                new Spec("Tản nhiệt", "Tấm tản nhôm trên mỗi thanh"),
                                new Spec("Bảo hành", "Theo điểm bán / hãng")
                        )),
                new ProductSeed(
                        "VGA-RTX4070S", "NVIDIA GeForce RTX 4070 SUPER 12GB", "VGA", "NVIDIA",
                        new BigDecimal("17990000"), new BigDecimal("19490000"),
                        "https://images.unsplash.com/photo-1591488320449-011701bb6704?w=600",
                        "Card đồ hoạ RTX 40 series, 12GB GDDR6X", 36, 8,
                        List.of(
                                new Spec("GPU", "NVIDIA GeForce RTX 4070 SUPER (Ada Lovelace)"),
                                new Spec("CUDA cores", "7168"),
                                new Spec("Xung boost (tham khảo)", "~2475 MHz"),
                                new Spec("Bộ nhớ", "12 GB GDDR6X"),
                                new Spec("Bus bộ nhớ", "192-bit"),
                                new Spec("Chuẩn giao tiếp", "PCI Express 4.0 x16"),
                                new Spec("Cổng xuất hình", "3 × DisplayPort 1.4a, 1 × HDMI 2.1"),
                                new Spec("TDP tham khảo (Founders)", "~220W — model AIC có thể khác"),
                                new Spec("Công suất nguồn gợi ý", "650W trở lên (GPU 16-pin / adapter theo hộp)")
                        )),
                new ProductSeed(
                        "SSD-SS990P-2TB", "Samsung 990 PRO 2TB NVMe PCIe 4.0", "SSD", "Samsung",
                        new BigDecimal("4290000"), new BigDecimal("4790000"),
                        "https://images.unsplash.com/photo-1597872200969-2b65d56bd16b?w=600",
                        "SSD M.2 NVMe đọc 7450 MB/s, ghi 6900 MB/s", 60, 30,
                        List.of(
                                new Spec("Giao tiếp", "NVMe 2.0 — PCIe 4.0 ×4"),
                                new Spec("Form factor", "M.2 2280"),
                                new Spec("Dung lượng", "2 TB"),
                                new Spec("NAND", "Samsung V-NAND 3-bit MLC"),
                                new Spec("DRAM cache", "Có (LPDDR4 tham khảo theo model)"),
                                new Spec("Đọc tuần tự (tối đa)", "Lên đến ~7450 MB/s"),
                                new Spec("Ghi tuần tự (tối đa)", "Lên đến ~6900 MB/s"),
                                new Spec("IOPS 4K QD1 (tham khảo)", "~1400K đọc / ~1550K ghi"),
                                new Spec("TBW (2TB, tham khảo)", "~1200 TBW"),
                                new Spec("Độ bền / MTBF", "Theo datasheet Samsung")
                        )),
                new ProductSeed(
                        "PSU-CMMWE-850", "Cooler Master MWE Gold 850W V2", "PSU", "Cooler Master",
                        new BigDecimal("2790000"), new BigDecimal("3190000"),
                        "https://images.unsplash.com/photo-1587202372634-32705e3bf49c?w=600",
                        "Nguồn 850W, chuẩn 80+ Gold, full modular", 60, 20,
                        List.of(
                                new Spec("Công suất danh định", "850W"),
                                new Spec("Hiệu suất", "80 PLUS Gold"),
                                new Spec("Dây nối", "Full modular"),
                                new Spec("Chuẩn form", "ATX"),
                                new Spec("Quạt", "140mm — chế độ quiet (Hybrid ON/Vòng quay theo tải)"),
                                new Spec("Bảo vệ", "OPP, OVP, UVP, OCP, OTP, SCP"),
                                new Spec("Hỗ trợ GPU mới", "Cổng 12VHPWR theo phiên bản bán lẻ"),
                                new Spec("Bảo hành", "Theo hãng / đại lý")
                        )),
                new ProductSeed(
                        "CASE-LL-O11-EVO", "Lian Li O11 Dynamic EVO", "Case", "Lian Li",
                        new BigDecimal("4590000"), new BigDecimal("4990000"),
                        "https://images.unsplash.com/photo-1587202372775-e229f172b9d7?w=600",
                        "Case mid-tower trong suốt, hỗ trợ E-ATX", 24, 15,
                        List.of(
                                new Spec("Form factor", "Mid-tower (dual chamber)"),
                                new Spec("Mainboard hỗ trợ", "E-ATX / ATX / Micro-ATX / Mini-ITX"),
                                new Spec("Giới hạn VGA (tham khảo)", "Dài tới ~458 mm (tùy cấu hình quạt / radiator)"),
                                new Spec("Radiator", "240/280/360 mm — vị trí top/side/bottom (theo hướng dẫn Lian Li)"),
                                new Spec("Khe mở rộng", "Nhiều khe PCIe (theo MB)"),
                                new Spec("Ổ cứng", "Khay 2.5\" / 3.5\" — bố trí theo kit"),
                                new Spec("Front I/O", "USB 3.2 Type-C, USB 3.0, audio âm thanh"),
                                new Spec("Vật liệu", "Nhôm / thép, kính cường lực hai mặt"),
                                new Spec("Màu", "Đen / Trắng — tùy SKU")
                        )),
                new ProductSeed(
                        "CPU-COOL-NHD15", "Noctua NH-D15 chromax.black", "Tản nhiệt", "Noctua",
                        new BigDecimal("2890000"), new BigDecimal("3190000"),
                        "https://images.unsplash.com/photo-1587202372616-b43abea06c2a?w=600",
                        "Tản khí cao cấp dual-tower, 6 ống đồng", 72, 18,
                        List.of(
                                new Spec("Loại", "Tản khí CPU — dual tower"),
                                new Spec("Tương thích socket", "Intel LGA1700 / AMD AM4 / AM5 (kiểm tra NM-i17xx kit)"),
                                new Spec("Quạt kèm theo", "2 × NF-A15 PWM chromax.black (140mm)"),
                                new Spec("Ống đồng × lá tản", "6 ống đồng — mật độ cao"),
                                new Spec("Chiều cao tháp (không quạt)", "~165 mm"),
                                new Spec("Chiều rộng (dual fan)", "~150 mm"),
                                new Spec("TDP gợi ý Noctua", "Over 220W (Phụ thuộc case & luồng gió)"),
                                new Spec("Vật liệu", "Đồng + nhôm, lớp phủ chromax.black"),
                                new Spec("Độ tin cậy quạt", "Ổ SSO2, MTBF cao (theo datasheet Noctua)")
                        )),
                new ProductSeed(
                        "VGA-RTX4090", "NVIDIA GeForce RTX 4090 24GB", "VGA", "NVIDIA",
                        new BigDecimal("48990000"), null,
                        "https://images.unsplash.com/photo-1591488320449-011701bb6704?w=600&q=80",
                        "Flagship card đồ hoạ thế hệ Ada Lovelace", 36, 3,
                        List.of(
                                new Spec("GPU", "NVIDIA GeForce RTX 4090"),
                                new Spec("Kiến trúc", "Ada Lovelace — TSMC 4N"),
                                new Spec("CUDA cores", "16384"),
                                new Spec("Bộ nhớ", "24 GB GDDR6X"),
                                new Spec("Bus bộ nhớ", "384-bit"),
                                new Spec("Xung boost (tham khảo)", "~2520 MHz"),
                                new Spec("TDP tham khảo", "~450W — model AIC khác nhau"),
                                new Spec("Công suất nguồn gợi ý", "850W trở lên (12VHPWR)"),
                                new Spec("Cổng xuất hình", "HDMI 2.1, DisplayPort 1.4a"),
                                new Spec("DLSS / Ray tracing", "DLSS 3 — RT Cores Gen 3")
                        )),
                new ProductSeed(
                        "CPU-RYZEN-7700X", "AMD Ryzen 7 7700X", "CPU", "AMD",
                        new BigDecimal("8490000"), new BigDecimal("9290000"),
                        "https://images.unsplash.com/photo-1591799264318-7e6ef8ddb7ea?w=600&q=80",
                        "4.5GHz Boost 5.4GHz / 8 nhân 16 luồng / 40MB cache / AM5", 36, 22,
                        List.of(
                                new Spec("Số nhân (Cores)", "8"),
                                new Spec("Số luồng (Threads)", "16"),
                                new Spec("Tốc độ xử lý", "Xung cơ bản 4.5 GHz, xung tối đa 5.4 GHz"),
                                new Spec("Bộ nhớ đệm L2", "8 MB"),
                                new Spec("Bộ nhớ đệm L3", "32 MB"),
                                new Spec("Tổng cache (L2 + L3)", "40 MB"),
                                new Spec("Mở khóa để ép xung", "Có"),
                                new Spec("Socket", "AM5"),
                                new Spec("Phiên bản PCI Express", "PCIe 5.0"),
                                new Spec("Giải pháp tản nhiệt (PIB)", "Không có sẵn — cần mua tản tương thích AM5"),
                                new Spec("TDP / TDP mặc định", "105 W"),
                                new Spec("Bộ nhớ hỗ trợ", "DDR5, 2 kênh — JEDEC / EXPO (kiểm tra bảng QVL mainboard)"),
                                new Spec("Nhóm sản phẩm", "AMD Ryzen Processors"),
                                new Spec("Công nghệ hỗ trợ", "AMD Zen 4 Core Architecture, AMD EXPO, AMD Ryzen Technologies")
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
            if ("CPU-RYZEN-7700X".equals(s.sku())) {
                p.setEditorialReview(sampleEditorialRyzen7700X());
            }
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

    private static String sampleEditorialRyzen7700X() {
        return """
                <p>Được ra mắt với nhiều cải tiến, thế hệ <strong>Ryzen 7000 Series</strong> mang lại những nguồn sức mạnh mới dành những bộ PC AMD. Với phân khúc cận cao cấp, đội đỏ mang đến cho người dùng chúng ta model <strong>AMD Ryzen 7 7700X</strong>. Cùng <strong>TechParts</strong> tìm hiểu chi tiết về vi xử lý đáng mong đợi này ngay sau đây nhé !</p>
                <h2>Thế hệ vi xử lý mạnh mẽ</h2>
                <p>Hoàn thiện dựa trên nền tảng kiến trúc tiên tiến, <strong>AMD Ryzen 7 7700X</strong> sở hữu cho mình <strong>8 nhân 16 luồng</strong> mang lại xung nhịp tối đa lên đến <strong>5.4GHz</strong>. Điều này mang lại cho CPU AMD tốc độ xử lý tác vụ mạnh mẽ và nhanh chóng, biến sản phẩm trở thành đối thủ cạnh tranh trực tiếp với những vi xử lý <strong>Intel Gen 13</strong> tiên tiến với phía còn lại khi đạt được những kết quả vô cùng tích cực thông qua những bài kiểm tra hiệu năng.</p>
                <figure>
                <img src="https://images.unsplash.com/photo-1591799264318-7e6ef8ddb7ea?w=900&q=80" alt="AMD Ryzen 7 7700X"/>
                <figcaption>TechParts - AMD Ryzen 7 7700X</figcaption>
                </figure>
                <h2>Trang bị những công nghệ tiên tiến</h2>
                <p><strong>AMD Ryzen 7 7700X</strong> mang đến cho người dùng của mình những công nghệ &quot;cây nhà lá vườn&quot; dành cho CPU gồm <strong>AMD EXPO</strong> và <strong>AMD Ryzen Technologies</strong>, nhằm hỗ trợ tốt nhất trong quá trình sử dụng.</p>
                <figure>
                <img src="https://images.unsplash.com/photo-1591799264318-7e6ef8ddb7ea?w=900&q=80" alt="AMD Ryzen 7 7700X"/>
                <figcaption>TechParts - AMD Ryzen 7 7700X</figcaption>
                </figure>
                <p>Đặc biệt, sau thời gian dài với socket <strong>AM4</strong> thì thế hệ <strong>7000 Series</strong> đã mang đến thế hệ socket tiếp theo với tên <strong>AM5</strong>. Nhờ vào điều này, bạn đã có thể kết hợp với những thế hệ linh kiện mới nhất như <strong>RAM DDR5</strong> trên mainboard để phù hợp nhất với nhu cầu build PC hiện nay.</p>
                """;
    }

    private record Spec(String name, String value) {}
}
