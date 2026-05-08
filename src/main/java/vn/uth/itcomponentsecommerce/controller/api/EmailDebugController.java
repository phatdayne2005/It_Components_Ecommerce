package vn.uth.itcomponentsecommerce.controller.api;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import vn.uth.itcomponentsecommerce.service.NotificationService;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Debug endpoints để chẩn đoán email từ trình duyệt.
 * Có thể xóa sau khi confirm email hoạt động.
 *
 *  GET  /api/v1/debug/email/health         → in giá trị config (không lộ password)
 *  POST /api/v1/debug/email/send?to=...    → gửi test email SYNC, trả lỗi cụ thể nếu fail
 */
@RestController
@RequestMapping("/api/v1/debug/email")
public class EmailDebugController {

    private final NotificationService notificationService;

    public EmailDebugController(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @GetMapping("/health")
    public ResponseEntity<?> health() {
        // NotificationService log config tại @PostConstruct rồi, endpoint này chỉ cho kiểm nhanh từ browser
        return ResponseEntity.ok(Map.of(
                "hint", "Xem log Spring Boot console để thấy 'EMAIL CONFIG AT STARTUP' block.",
                "testSendUrl", "/api/v1/debug/email/send?to=YOUR_EMAIL"
        ));
    }

    @PostMapping("/send")
    public ResponseEntity<?> sendTest(@RequestParam(name = "to") String to) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("to", to);
        try {
            notificationService.sendTestMailSync(to);
            result.put("status", "OK");
            result.put("message", "Đã gửi. Check inbox + spam.");
            return ResponseEntity.ok(result);
        } catch (Exception ex) {
            result.put("status", "FAIL");
            result.put("errorClass", ex.getClass().getName());
            result.put("errorMessage", ex.getMessage());
            // Tìm root cause — auth fail thường nằm sâu trong cause chain
            Throwable cause = ex.getCause();
            int depth = 0;
            while (cause != null && depth < 10) {
                result.put("cause" + depth, cause.getClass().getName() + ": " + cause.getMessage());
                cause = cause.getCause();
                depth++;
            }
            return ResponseEntity.status(500).body(result);
        }
    }
}
