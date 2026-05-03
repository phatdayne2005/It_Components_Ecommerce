package vn.uth.itcomponentsecommerce.service;

import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.*;
import java.util.Set;
import java.util.UUID;

@Service
public class FileStorageService {

    private static final Set<String> ALLOWED_EXT =
            Set.of("jpg", "jpeg", "png", "webp", "gif", "bmp");
    private static final long MAX_BYTES = 5L * 1024 * 1024; // 5MB

    @Value("${app.upload.dir}")
    private String uploadDir;

    private Path root;

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
        if (dot > -1) ext = original.substring(dot + 1).toLowerCase();
        if (!ALLOWED_EXT.contains(ext))
            throw new IllegalArgumentException("Định dạng không hỗ trợ. Cho phép: " + ALLOWED_EXT);

        try {
            java.time.LocalDate today = java.time.LocalDate.now();
            String subDir = String.format("%04d/%02d", today.getYear(), today.getMonthValue());
            Path targetDir = root.resolve(subDir);
            Files.createDirectories(targetDir);

            String filename = UUID.randomUUID().toString().replace("-", "") + "." + ext;
            Path target = targetDir.resolve(filename);
            Files.copy(file.getInputStream(), target, StandardCopyOption.REPLACE_EXISTING);

            return "/uploads/" + subDir + "/" + filename;
        } catch (IOException e) {
            throw new RuntimeException("Lưu file thất bại: " + e.getMessage(), e);
        }
    }
}
