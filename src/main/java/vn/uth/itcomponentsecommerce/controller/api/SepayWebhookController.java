package vn.uth.itcomponentsecommerce.controller.api;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.ResponseEntity;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;
import vn.uth.itcomponentsecommerce.entity.Order;
import vn.uth.itcomponentsecommerce.entity.OrderStatus;
import vn.uth.itcomponentsecommerce.repository.OrderRepository;
import vn.uth.itcomponentsecommerce.service.SepayPollingService;
import vn.uth.itcomponentsecommerce.service.SepayWebhookService;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/payments/sepay")
public class SepayWebhookController {

    private final SepayWebhookService sepayWebhookService;
    private final SepayPollingService sepayPollingService;
    private final OrderRepository orderRepository;
    @Value("${app.sepay.secret-key:}")
    private String sepaySecretKey;

    public SepayWebhookController(SepayWebhookService sepayWebhookService,
                                  SepayPollingService sepayPollingService,
                                  OrderRepository orderRepository) {
        this.sepayWebhookService = sepayWebhookService;
        this.sepayPollingService = sepayPollingService;
        this.orderRepository = orderRepository;
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

    /**
     * Trigger polling ngay lập tức rồi trả về trạng thái đơn — dùng để page payment/success
     * gọi sau khi user redirect về từ SePay (vì localhost không nhận được webhook).
     */
    @PostMapping("/check/{orderCode}")
    public ResponseEntity<?> checkPaymentStatus(@PathVariable String orderCode) {
        // Trigger on-demand polling — chỉ cần có API token (không yêu cầu cron polling.enabled)
        boolean polled = false;
        if (sepayPollingService.canPollOnDemand()) {
            try {
                sepayPollingService.pollAndProcessNewTransactions();
                polled = true;
            } catch (Exception ex) {
                // log nhưng vẫn trả status hiện tại
            }
        }
        Order order = orderRepository.findByOrderCode(orderCode).orElse(null);
        if (order == null) {
            return ResponseEntity.status(404).body(Map.of("error", "Order not found"));
        }
        return ResponseEntity.ok(Map.of(
                "orderCode", order.getOrderCode(),
                "status", order.getStatus().name(),
                "paid", order.getStatus() != OrderStatus.PENDING_PAYMENT,
                "polled", polled,
                "pollingAvailable", sepayPollingService.canPollOnDemand()
        ));
    }
}

