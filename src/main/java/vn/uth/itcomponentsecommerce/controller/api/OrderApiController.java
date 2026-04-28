package vn.uth.itcomponentsecommerce.controller.api;

import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.bind.annotation.*;
import vn.uth.itcomponentsecommerce.dto.CancelOrderRequest;
import vn.uth.itcomponentsecommerce.dto.CheckoutRequest;
import vn.uth.itcomponentsecommerce.dto.RefundRequest;
import vn.uth.itcomponentsecommerce.dto.UpdateOrderStatusRequest;
import vn.uth.itcomponentsecommerce.entity.Order;
import vn.uth.itcomponentsecommerce.service.OrderService;

import java.util.List;

@RestController
@RequestMapping("/api/v1/orders")
public class OrderApiController {

    private final OrderService orderService;

    public OrderApiController(OrderService orderService) {
        this.orderService = orderService;
    }

    @PostMapping("/checkout")
    public ResponseEntity<?> checkout(@Valid @RequestBody CheckoutRequest request) {
        Order order = orderService.checkout(request);
        return ResponseEntity.ok(order);
    }

    @GetMapping("/my")
    public ResponseEntity<?> myOrders() {
        List<Order> orders = orderService.getMyOrders();
        return ResponseEntity.ok(orders);
    }

    @PutMapping("/{id}/status")
    public ResponseEntity<?> updateStatus(@PathVariable Long id, @Valid @RequestBody UpdateOrderStatusRequest request) {
        Order order = orderService.updateStatus(id, request);
        return ResponseEntity.ok(order);
    }

    @PostMapping("/{id}/cancel")
    public ResponseEntity<?> cancel(@PathVariable Long id, @Valid @RequestBody CancelOrderRequest request) {
        Order order = orderService.cancelByCustomer(id, request);
        return ResponseEntity.ok(order);
    }

    @PostMapping("/{id}/refund")
    public ResponseEntity<?> refund(@PathVariable Long id, @Valid @RequestBody RefundRequest request) {
        Order order = orderService.requestRefund(id, request);
        return ResponseEntity.ok(order);
    }

    @PostMapping("/{id}/refund/upload")
    public ResponseEntity<?> refundWithUpload(@PathVariable Long id,
                                              @RequestParam("reason") String reason,
                                              @RequestParam(value = "evidenceImages", required = false) MultipartFile[] evidenceImages) {
        Order order = orderService.requestRefundWithUpload(id, reason, evidenceImages);
        return ResponseEntity.ok(order);
    }
}

