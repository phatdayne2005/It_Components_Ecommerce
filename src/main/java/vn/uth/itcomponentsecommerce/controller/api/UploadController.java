package vn.uth.itcomponentsecommerce.controller.api;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import vn.uth.itcomponentsecommerce.service.FileStorageService;

import java.util.Map;

@RestController
@RequestMapping("/api/admin/upload")
public class UploadController {

    private final FileStorageService storage;

    public UploadController(FileStorageService storage) {
        this.storage = storage;
    }

    @PostMapping(consumes = "multipart/form-data")
    public ResponseEntity<?> upload(@RequestParam("file") MultipartFile file) {
        try {
            String url = storage.storeImage(file);
            return ResponseEntity.ok(Map.of("url", url));
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(Map.of("message", ex.getMessage()));
        }
    }
}
