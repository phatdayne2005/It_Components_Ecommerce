package vn.uth.itcomponentsecommerce.service;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.*;
import java.time.Duration;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
public class FileStorageService {

    private static final Logger log = LoggerFactory.getLogger(FileStorageService.class);

    private static final Set<String> ALLOWED_EXT =
            Set.of("jpg", "jpeg", "png", "webp", "gif", "bmp");
    private static final long MAX_BYTES = 5L * 1024 * 1024; // 5MB

    /** Map từ Content-Type → extension fallback khi URL không có đuôi rõ ràng. */
    private static final Map<String, String> CONTENT_TYPE_TO_EXT = Map.of(
            "image/jpeg", "jpg",
            "image/jpg", "jpg",
            "image/png", "png",
            "image/webp", "webp",
            "image/gif", "gif",
            "image/bmp", "bmp"
    );

    @Value("${app.upload.dir}")
    private String uploadDir;

    private Path root;

    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(8))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    @PostConstruct
    void init() throws IOException {
        this.root = Paths.get(uploadDir).toAbsolutePath().normalize();
        Files.createDirectories(root);
    }

    /**
     * Lưu file ảnh và trả về URL public (vd: "/uploads/2026/04/uuid.png")
     */
    public String storeImage(MultipartFile file) {
        if (file == null || file.isEmpty())
            throw new IllegalArgumentException("File rỗng");
        if (file.getSize() > MAX_BYTES)
            throw new IllegalArgumentException("File quá lớn (tối đa 5MB)");

        String original = file.getOriginalFilename() == null ? "" : file.getOriginalFilename();
        String ext = "";
        int dot = original.lastIndexOf('.');
        if (dot > -1) ext = original.substring(dot + 1).toLowerCase(Locale.ROOT);
        if (!ALLOWED_EXT.contains(ext))
            throw new IllegalArgumentException("Định dạng không hỗ trợ. Cho phép: " + ALLOWED_EXT);

        try {
            Path target = newTargetPath(ext);
            Files.copy(file.getInputStream(), target, StandardCopyOption.REPLACE_EXISTING);
            return toPublicUrl(target);
        } catch (IOException e) {
            throw new RuntimeException("Lưu file thất bại: " + e.getMessage(), e);
        }
    }

    /**
     * Tải ảnh từ URL bên ngoài về thư mục upload local. Dùng để import sản phẩm
     * từ nguồn ngoài (vd GearVN) — sau khi import, app không còn phụ thuộc URL gốc.
     *
     * @return URL public dạng "/uploads/yyyy/MM/uuid.ext"
     * @throws IOException nếu network lỗi, content-type không phải ảnh, hoặc file quá lớn
     */
    public String storeFromUrl(String imageUrl) throws IOException {
        if (imageUrl == null || imageUrl.isBlank())
            throw new IllegalArgumentException("URL rỗng");

        URI uri;
        try {
            uri = URI.create(imageUrl.trim());
        } catch (IllegalArgumentException ex) {
            throw new IOException("URL không hợp lệ: " + imageUrl);
        }
        if (uri.getScheme() == null || !(uri.getScheme().equals("http") || uri.getScheme().equals("https")))
            throw new IOException("URL phải bắt đầu bằng http/https");

        HttpRequest req = HttpRequest.newBuilder(uri)
                .timeout(Duration.ofSeconds(15))
                .header("User-Agent", "Mozilla/5.0 (compatible; TechParts-Importer/1.0)")
                .GET()
                .build();

        HttpResponse<byte[]> res;
        try {
            res = http.send(req, HttpResponse.BodyHandlers.ofByteArray());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Tải bị gián đoạn", e);
        }
        if (res.statusCode() / 100 != 2)
            throw new IOException("HTTP " + res.statusCode() + " khi tải " + imageUrl);

        byte[] data = res.body();
        if (data == null || data.length == 0)
            throw new IOException("Phản hồi rỗng từ " + imageUrl);
        if (data.length > MAX_BYTES)
            throw new IOException("Ảnh quá lớn (" + data.length + " bytes, tối đa " + MAX_BYTES + ")");

        String contentType = res.headers().firstValue("Content-Type")
                .map(s -> s.toLowerCase(Locale.ROOT).split(";")[0].trim())
                .orElse("");
        String ext = resolveExtension(uri.getPath(), contentType);
        if (!ALLOWED_EXT.contains(ext))
            throw new IOException("Content-Type không hỗ trợ: " + contentType + " (url=" + imageUrl + ")");

        Path target = newTargetPath(ext);
        try (var in = new ByteArrayInputStream(data)) {
            Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
        }
        log.debug("Đã tải {} → {}", imageUrl, target);
        return toPublicUrl(target);
    }

    private static String resolveExtension(String urlPath, String contentType) {
        String ext = "";
        if (urlPath != null) {
            int dot = urlPath.lastIndexOf('.');
            int slash = urlPath.lastIndexOf('/');
            if (dot > slash && dot > -1) {
                ext = urlPath.substring(dot + 1).toLowerCase(Locale.ROOT);
                int q = ext.indexOf('?');
                if (q > -1) ext = ext.substring(0, q);
            }
        }
        if (!ALLOWED_EXT.contains(ext)) {
            ext = CONTENT_TYPE_TO_EXT.getOrDefault(contentType, "");
        }
        return ext;
    }

    private Path newTargetPath(String ext) throws IOException {
        java.time.LocalDate today = java.time.LocalDate.now();
        String subDir = String.format("%04d/%02d", today.getYear(), today.getMonthValue());
        Path targetDir = root.resolve(subDir);
        Files.createDirectories(targetDir);
        String filename = UUID.randomUUID().toString().replace("-", "") + "." + ext;
        return targetDir.resolve(filename);
    }

    private String toPublicUrl(Path target) {
        Path rel = root.relativize(target);
        // Use forward slash regardless of OS
        return "/uploads/" + rel.toString().replace('\\', '/');
    }
}
