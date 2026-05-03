package vn.uth.itcomponentsecommerce.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import vn.uth.itcomponentsecommerce.entity.Brand;
import vn.uth.itcomponentsecommerce.entity.Category;
import vn.uth.itcomponentsecommerce.entity.Product;
import vn.uth.itcomponentsecommerce.entity.ProductImage;
import vn.uth.itcomponentsecommerce.entity.ProductSpecification;
import vn.uth.itcomponentsecommerce.repository.BrandRepository;
import vn.uth.itcomponentsecommerce.repository.CategoryRepository;
import vn.uth.itcomponentsecommerce.repository.ProductRepository;

import java.io.IOException;
import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;

/**
 * Fetch sản phẩm từ GearVN (Shopify-based) qua endpoint công khai
 * {@code /collections/{handle}/products.json}, tải ảnh về local, parse bảng
 * thông số kỹ thuật trong {@code body_html} và lưu vào DB.
 *
 * Anti-duplicate: dựa trên {@code product.handle} (slug). Nếu slug đã có trong
 * DB sẽ bỏ qua, không ghi đè.
 */
@Service
public class GearVnImportService {

    private static final Logger log = LoggerFactory.getLogger(GearVnImportService.class);
    private static final String BASE_URL = "https://gearvn.com/collections/";

    private final ProductRepository productRepository;
    private final CategoryRepository categoryRepository;
    private final BrandRepository brandRepository;
    private final FileStorageService fileStorageService;
    private final ObjectMapper objectMapper;

    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(8))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    public GearVnImportService(ProductRepository productRepository,
                               CategoryRepository categoryRepository,
                               BrandRepository brandRepository,
                               FileStorageService fileStorageService,
                               ObjectMapper objectMapper) {
        this.productRepository = productRepository;
        this.categoryRepository = categoryRepository;
        this.brandRepository = brandRepository;
        this.fileStorageService = fileStorageService;
        this.objectMapper = objectMapper;
    }

    // ===== DTOs =====

    public record SpecPair(String name, String value) {}

    public record GearVnPreview(
            String handle,
            String title,
            String vendor,
            String productType,
            BigDecimal price,
            BigDecimal oldPrice,
            String mainImageUrl,
            List<String> imageUrls,
            List<SpecPair> specs,
            boolean duplicate,
            Long existingId
    ) {}

    public record PreviewPage(
            String handle,
            int page,
            int limit,
            int count,
            boolean hasMore,
            List<GearVnPreview> items
    ) {}

    public record ImportResult(int imported, int skipped, List<String> errors) {}

    // ===== Public API =====

    public PreviewPage preview(String collectionHandle, int page, int limit) {
        if (!GearVnCategoryMap.isSupported(collectionHandle))
            throw new IllegalArgumentException("Collection không được hỗ trợ: " + collectionHandle);
        if (page < 1) page = 1;
        if (limit < 1 || limit > 50) limit = 20;

        JsonNode root = fetchProductsJson(collectionHandle, page, limit);
        JsonNode products = root.path("products");
        List<GearVnPreview> items = new ArrayList<>();
        if (products.isArray()) {
            for (JsonNode p : products) {
                items.add(toPreview(p));
            }
        }
        boolean hasMore = items.size() == limit;
        return new PreviewPage(collectionHandle, page, limit, items.size(), hasMore, items);
    }

    /**
     * Import từng handle. Mỗi product được lưu trong transaction riêng (xem {@link #importOne})
     * để 1 lỗi không rollback cả batch.
     */
    public ImportResult importSelected(String collectionHandle, List<String> handles) {
        if (!GearVnCategoryMap.isSupported(collectionHandle))
            throw new IllegalArgumentException("Collection không được hỗ trợ: " + collectionHandle);
        if (handles == null || handles.isEmpty())
            return new ImportResult(0, 0, List.of());

        int imported = 0, skipped = 0;
        List<String> errors = new ArrayList<>();
        Category category = categoryRepository.findBySlug(
                GearVnCategoryMap.byHandle(collectionHandle).localCategorySlug()).orElse(null);

        for (String handle : handles) {
            if (handle == null || handle.isBlank()) continue;
            if (productRepository.existsBySlug(handle)) {
                skipped++;
                continue;
            }
            try {
                boolean ok = importOne(collectionHandle, handle, category);
                if (ok) imported++;
                else skipped++;
            } catch (Exception ex) {
                log.warn("Lỗi import {}: {}", handle, ex.getMessage());
                errors.add(handle + ": " + ex.getMessage());
            }
        }
        return new ImportResult(imported, skipped, errors);
    }

    // ===== Internal =====

    /**
     * Tìm product trên GearVN qua /products/{handle}.json (chính xác hơn re-fetch
     * cả collection), tải ảnh, parse spec, lưu vào DB.
     *
     * Lưu ý: không đặt {@code @Transactional} ở đây vì gọi nội bộ từ
     * {@link #importSelected} — proxy AOP không kích hoạt. Mỗi
     * {@code productRepository.save()} đã có transaction riêng (cascade children),
     * nếu lỗi xảy ra trước save() thì không có dữ liệu DB nào được ghi.
     */
    protected boolean importOne(String collectionHandle, String handle, Category category) throws IOException {
        if (productRepository.existsBySlug(handle)) return false;

        JsonNode singleRoot = fetchSingleProduct(handle);
        JsonNode p = singleRoot.path("product");
        if (p.isMissingNode() || p.isNull())
            throw new IOException("Sản phẩm không tồn tại trên GearVN: " + handle);

        String title = textOrEmpty(p, "title");
        String vendor = textOrEmpty(p, "vendor");
        String productType = textOrEmpty(p, "product_type");
        String bodyHtml = textOrEmpty(p, "body_html");
        BigDecimal price = firstVariantPrice(p, "price");
        BigDecimal oldPrice = firstVariantPrice(p, "compare_at_price");
        List<String> sourceImages = collectImageUrls(p);
        if (sourceImages.isEmpty())
            throw new IOException("Sản phẩm không có ảnh: " + handle);
        List<SpecPair> specs = parseSpecsFromBodyHtml(bodyHtml);

        // Resolve hoặc tạo brand theo vendor
        Brand brand = resolveBrand(vendor);

        // Tải tất cả ảnh về uploads/. Dùng ảnh đầu tải thành công làm imageUrl chính.
        List<String> localUrls = new ArrayList<>();
        for (String src : sourceImages) {
            try {
                localUrls.add(fileStorageService.storeFromUrl(src));
            } catch (IOException ex) {
                log.warn("Bỏ qua ảnh lỗi {}: {}", src, ex.getMessage());
            }
        }
        if (localUrls.isEmpty())
            throw new IOException("Không tải được ảnh nào cho " + handle);

        Product product = new Product();
        product.setName(title);
        product.setSlug(handle);
        product.setShortDescription(productType);
        product.setDescription(bodyHtml);
        product.setPrice(price != null ? price : BigDecimal.ZERO);
        product.setOldPrice((oldPrice != null && oldPrice.signum() > 0
                && (price == null || oldPrice.compareTo(price) > 0)) ? oldPrice : null);
        product.setStock(0);
        product.setSold(0);
        product.setImageUrl(localUrls.get(0));
        product.setActive(true);
        product.setCategory(category);
        product.setBrand(brand);

        int sortOrder = 0;
        for (String url : localUrls) {
            product.getImages().add(new ProductImage(product, url, sortOrder++));
        }
        int specOrder = 0;
        for (SpecPair sp : specs) {
            product.getSpecifications().add(new ProductSpecification(product, sp.name(), sp.value(), specOrder++));
        }

        productRepository.save(product);
        log.info("Đã import {} ({} ảnh, {} specs)", handle, localUrls.size(), specs.size());
        return true;
    }

    private Brand resolveBrand(String vendor) {
        if (vendor == null || vendor.isBlank()) return null;
        String name = vendor.trim();
        Optional<Brand> existing = brandRepository.findByNameIgnoreCase(name);
        if (existing.isPresent()) return existing.get();

        Brand b = new Brand();
        b.setName(name);
        String slug = SlugUtil.toSlug(name);
        // tránh đụng unique constraint nếu trùng slug từ brand khác (hiếm)
        if (brandRepository.existsBySlug(slug)) {
            slug = slug + "-" + Long.toHexString(System.currentTimeMillis() & 0xFFFFFL);
        }
        b.setSlug(slug);
        return brandRepository.save(b);
    }

    private GearVnPreview toPreview(JsonNode p) {
        String handle = textOrEmpty(p, "handle");
        Optional<Product> existing = productRepository.findBySlug(handle);
        BigDecimal price = firstVariantPrice(p, "price");
        BigDecimal oldPrice = firstVariantPrice(p, "compare_at_price");
        List<String> images = collectImageUrls(p);
        List<SpecPair> specs = parseSpecsFromBodyHtml(textOrEmpty(p, "body_html"));
        return new GearVnPreview(
                handle,
                textOrEmpty(p, "title"),
                textOrEmpty(p, "vendor"),
                textOrEmpty(p, "product_type"),
                price,
                (oldPrice != null && oldPrice.signum() > 0
                        && (price == null || oldPrice.compareTo(price) > 0)) ? oldPrice : null,
                images.isEmpty() ? null : images.get(0),
                images,
                specs,
                existing.isPresent(),
                existing.map(Product::getId).orElse(null)
        );
    }

    private JsonNode fetchProductsJson(String collectionHandle, int page, int limit) {
        URI uri = URI.create(BASE_URL + collectionHandle + "/products.json?page=" + page + "&limit=" + limit);
        return getJson(uri);
    }

    private JsonNode fetchSingleProduct(String handle) {
        URI uri = URI.create("https://gearvn.com/products/" + handle + ".json");
        return getJson(uri);
    }

    private JsonNode getJson(URI uri) {
        HttpRequest req = HttpRequest.newBuilder(uri)
                .timeout(Duration.ofSeconds(15))
                .header("User-Agent", "Mozilla/5.0 (compatible; TechParts-Importer/1.0)")
                .header("Accept", "application/json")
                .GET()
                .build();
        try {
            HttpResponse<String> res = http.send(req, HttpResponse.BodyHandlers.ofString());
            if (res.statusCode() / 100 != 2)
                throw new IOException("HTTP " + res.statusCode() + " từ " + uri);
            return objectMapper.readTree(res.body());
        } catch (IOException e) {
            throw new RuntimeException("Lỗi gọi GearVN: " + e.getMessage(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Bị gián đoạn khi gọi GearVN", e);
        }
    }

    private static String textOrEmpty(JsonNode parent, String field) {
        JsonNode n = parent.path(field);
        return n.isMissingNode() || n.isNull() ? "" : n.asText("");
    }

    private static BigDecimal firstVariantPrice(JsonNode product, String field) {
        JsonNode variants = product.path("variants");
        if (!variants.isArray() || variants.isEmpty()) return null;
        JsonNode v = variants.get(0).path(field);
        if (v.isMissingNode() || v.isNull()) return null;
        String s = v.asText("").trim();
        if (s.isEmpty() || s.equals("0") || s.equals("0.00")) return null;
        try {
            return new BigDecimal(s);
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private static List<String> collectImageUrls(JsonNode product) {
        List<String> urls = new ArrayList<>();
        JsonNode images = product.path("images");
        if (images.isArray()) {
            for (JsonNode img : images) {
                String src = textOrEmpty(img, "src");
                if (!src.isEmpty()) urls.add(src);
            }
        }
        // fallback: image.src nếu mảng images rỗng
        if (urls.isEmpty()) {
            String src = textOrEmpty(product.path("image"), "src");
            if (!src.isEmpty()) urls.add(src);
        }
        return urls;
    }

    /**
     * GearVN body_html thường chứa 1+ thẻ {@code <table>} với mỗi {@code <tr>}
     * có 2 ô: tên thông số / giá trị. Một số sản phẩm dùng {@code <strong>} cho key.
     */
    static List<SpecPair> parseSpecsFromBodyHtml(String html) {
        List<SpecPair> result = new ArrayList<>();
        if (html == null || html.isBlank()) return result;
        Document doc = Jsoup.parseBodyFragment(html);
        for (Element row : doc.select("table tr")) {
            var cells = row.select("td, th");
            if (cells.size() < 2) continue;
            String name = cells.get(0).text().trim();
            String value = cells.get(1).text().trim();
            if (name.isEmpty() || value.isEmpty()) continue;
            // Cắt theo limit cột DB (name=80, value=200)
            if (name.length() > 80) name = name.substring(0, 80);
            if (value.length() > 200) value = value.substring(0, 200);
            // bỏ qua dòng header trùng "Tên" / "Giá trị" nếu có
            if (equalsIgnoreCaseAny(name, "tên", "thông số", "đặc điểm", "specification")) continue;
            result.add(new SpecPair(name, value));
        }
        // dedupe theo name (giữ giá trị đầu)
        var seen = new java.util.HashSet<String>();
        var deduped = new ArrayList<SpecPair>();
        Iterator<SpecPair> it = result.iterator();
        while (it.hasNext()) {
            SpecPair sp = it.next();
            String key = sp.name().toLowerCase();
            if (seen.add(key)) deduped.add(sp);
        }
        return deduped;
    }

    private static boolean equalsIgnoreCaseAny(String s, String... candidates) {
        if (s == null) return false;
        for (String c : candidates) if (s.equalsIgnoreCase(c)) return true;
        return false;
    }
}
