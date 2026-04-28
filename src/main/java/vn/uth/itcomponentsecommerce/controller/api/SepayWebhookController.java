package vn.uth.itcomponentsecommerce.controller.api;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.ResponseEntity;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;
import vn.uth.itcomponentsecommerce.service.SepayWebhookService;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/payments/sepay")
public class SepayWebhookController {

    private final SepayWebhookService sepayWebhookService;
    @Value("${app.sepay.secret-key:}")
    private String sepaySecretKey;

    public SepayWebhookController(SepayWebhookService sepayWebhookService) {
        this.sepayWebhookService = sepayWebhookService;
    }

    @PostMapping("/ipn")
    public ResponseEntity<?> ipn(
            @RequestHeader(value = "X-Secret-Key", required = false) String providedSecretKey,
            @RequestBody Map<String, Object> payload
    ) {
        if (sepaySecretKey == null || sepaySecretKey.isBlank() || !sepaySecretKey.equals(providedSecretKey)) {
            return ResponseEntity.status(401).body(Map.of("error", "Unauthorized"));
        }
        try {
            String result = sepayWebhookService.processGatewayIpn(payload);
            return ResponseEntity.ok(Map.of("status", result));
        } catch (DataIntegrityViolationException ex) {
            return ResponseEntity.ok(Map.of("status", "ignored_duplicate"));
        } catch (Exception ex) {
            return ResponseEntity.badRequest().body(Map.of("error", ex.getMessage()));
        }
    }
}

