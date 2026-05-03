package vn.uth.itcomponentsecommerce.controller.api;

import jakarta.validation.constraints.NotBlank;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import vn.uth.itcomponentsecommerce.service.GearVnCategoryMap;
import vn.uth.itcomponentsecommerce.service.GearVnImportService;

import java.util.List;
import java.util.Map;

/**
 * REST endpoints cho admin import sản phẩm từ GearVN.
 * Tất cả endpoints yêu cầu role ADMIN (xem SecurityConfig: /api/admin/**).
 */
@RestController
@RequestMapping("/api/admin/import/gearvn")
public class GearVnImportController {

    private final GearVnImportService importService;

    public GearVnImportController(GearVnImportService importService) {
        this.importService = importService;
    }

    /** Danh sách collection được hỗ trợ + label hiển thị + category local đích. */
    @GetMapping("/collections")
    public List<GearVnCategoryMap.CollectionOption> collections() {
        return GearVnCategoryMap.options();
    }

    /** Preview 1 trang sản phẩm từ GearVN, đánh dấu sản phẩm đã trùng slug. */
    @GetMapping("/preview")
    public ResponseEntity<?> preview(@RequestParam String handle,
                                     @RequestParam(defaultValue = "1") int page,
                                     @RequestParam(defaultValue = "20") int limit) {
        try {
            return ResponseEntity.ok(importService.preview(handle, page, limit));
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(Map.of("message", ex.getMessage()));
        } catch (RuntimeException ex) {
            return ResponseEntity.status(502).body(Map.of("message",
                    "Không lấy được dữ liệu từ GearVN: " + ex.getMessage()));
        }
    }

    /** Import các product handle do admin chọn. */
    @PostMapping("/import")
    public ResponseEntity<?> importSelected(@RequestBody ImportPayload payload) {
        if (payload == null || payload.handle() == null || payload.handle().isBlank())
            return ResponseEntity.badRequest().body(Map.of("message", "Thiếu collection handle"));
        if (payload.handles() == null || payload.handles().isEmpty())
            return ResponseEntity.badRequest().body(Map.of("message", "Chưa chọn sản phẩm nào"));
        try {
            GearVnImportService.ImportResult result =
                    importService.importSelected(payload.handle(), payload.handles());
            return ResponseEntity.ok(result);
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(Map.of("message", ex.getMessage()));
        }
    }

    public record ImportPayload(@NotBlank String handle, List<String> handles) {}
}
