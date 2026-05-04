package vn.uth.itcomponentsecommerce.controller.api;

import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.bind.annotation.*;
import vn.uth.itcomponentsecommerce.dto.AdminOrderResponse;
import vn.uth.itcomponentsecommerce.dto.CancelOrderRequest;
import vn.uth.itcomponentsecommerce.dto.CheckoutRequest;
import vn.uth.itcomponentsecommerce.dto.RefundRequest;
import vn.uth.itcomponentsecommerce.dto.UpdateOrderStatusRequest;
import vn.uth.itcomponentsecommerce.entity.Order;
import vn.uth.itcomponentsecommerce.entity.OrderStatus;
import vn.uth.itcomponentsecommerce.service.OrderService;

import java.time.LocalDate;
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
        try {
            Order order = orderService.checkout(request);
            return ResponseEntity.ok(order);
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(java.util.Map.of("message", ex.getMessage()));
        }
    }

    @GetMapping("/my")
    public ResponseEntity<?> myOrders() {
        List<Order> orders = orderService.getMyOrders();
        return ResponseEntity.ok(orders);
    }

    @GetMapping("/admin/list")
    public ResponseEntity<?> adminListOrders(
            @RequestParam(required = false) OrderStatus status,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateTo) {
        java.time.LocalDateTime fromDt = dateFrom != null ? dateFrom.atStartOfDay() : null;
        java.time.LocalDateTime toDt = dateTo != null ? dateTo.atTime(23, 59, 59) : null;
        List<AdminOrderResponse> orders = orderService.getOrdersForAdmin(status, keyword, fromDt, toDt);
        return ResponseEntity.ok(orders);
    }

    @GetMapping("/detail/{id}")
    public ResponseEntity<?> myOrderDetail(@PathVariable Long id) {
        try {
            return ResponseEntity.ok(orderService.getMyOrder(id));
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.notFound().build();
        }
    }

    @PutMapping("/{id}/status")
    public ResponseEntity<?> updateStatus(@PathVariable Long id, @Valid @RequestBody UpdateOrderStatusRequest request) {
        try {
            Order order = orderService.updateStatus(id, request);
            return ResponseEntity.ok(order);
        } catch (IllegalStateException ex) {
            return ResponseEntity.badRequest().body(java.util.Map.of("message", ex.getMessage()));
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(java.util.Map.of("message", ex.getMessage()));
        }
    }

    @PostMapping("/{id}/cancel")
    public ResponseEntity<?> cancel(@PathVariable Long id, @Valid @RequestBody CancelOrderRequest request) {
        try {
            Order order = orderService.cancelByCustomer(id, request);
            return ResponseEntity.ok(order);
        } catch (IllegalStateException ex) {
            return ResponseEntity.badRequest().body(java.util.Map.of("message", ex.getMessage()));
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(java.util.Map.of("message", ex.getMessage()));
        }
    }

    @PostMapping("/{id}/mark-delivered")
    public ResponseEntity<?> markDelivered(@PathVariable Long id) {
        try {
            Order order = orderService.markDeliveredByCustomer(id);
            return ResponseEntity.ok(order);
        } catch (IllegalStateException ex) {
            return ResponseEntity.badRequest().body(java.util.Map.of("message", ex.getMessage()));
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(java.util.Map.of("message", ex.getMessage()));
        }
    }

    @PostMapping("/{id}/refund")
    public ResponseEntity<?> refund(@PathVariable Long id, @Valid @RequestBody RefundRequest request) {
        try {
            Order order = orderService.requestRefund(id, request);
            return ResponseEntity.ok(order);
        } catch (IllegalStateException ex) {
            return ResponseEntity.badRequest().body(java.util.Map.of("message", ex.getMessage()));
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(java.util.Map.of("message", ex.getMessage()));
        }
    }

    @PostMapping("/{id}/refund/upload")
    public ResponseEntity<?> refundWithUpload(@PathVariable Long id,
                                              @RequestParam("reason") String reason,
                                              @RequestParam(value = "evidenceImages", required = false) MultipartFile[] evidenceImages) {
        try {
            Order order = orderService.requestRefundWithUpload(id, reason, evidenceImages);
            return ResponseEntity.ok(order);
        } catch (IllegalStateException ex) {
            return ResponseEntity.badRequest().body(java.util.Map.of("message", ex.getMessage()));
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(java.util.Map.of("message", ex.getMessage()));
        }
    }

    @PutMapping("/{id}/refund/reject")
    public ResponseEntity<?> rejectRefund(@PathVariable Long id, @Valid @RequestBody vn.uth.itcomponentsecommerce.dto.RejectRefundRequest request) {
        try {
            Order order = orderService.rejectRefund(id, request.getRejectNote());
            return ResponseEntity.ok(order);
        } catch (IllegalStateException ex) {
            return ResponseEntity.badRequest().body(java.util.Map.of("message", ex.getMessage()));
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(java.util.Map.of("message", ex.getMessage()));
        }
    }
}

