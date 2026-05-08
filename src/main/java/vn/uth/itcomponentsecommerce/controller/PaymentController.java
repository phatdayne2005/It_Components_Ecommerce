package vn.uth.itcomponentsecommerce.controller;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import vn.uth.itcomponentsecommerce.entity.Order;
import vn.uth.itcomponentsecommerce.entity.PaymentMethod;
import vn.uth.itcomponentsecommerce.repository.OrderRepository;

@Controller
public class PaymentController {

    private final OrderRepository orderRepository;

    public PaymentController(OrderRepository orderRepository) {
        this.orderRepository = orderRepository;
    }

    @GetMapping("/payment/success")
    public String paymentSuccess(@RequestParam(required = false) String orderCode, Model model) {
        model.addAttribute("orderCode", orderCode);
        // Mặc định coi như COD để fallback nếu không tra được order (ví dụ orderCode rỗng/sai)
        String paymentMethod = "COD";
        java.math.BigDecimal total = null;
        if (orderCode != null && !orderCode.isBlank()) {
            Order order = orderRepository.findByOrderCode(orderCode).orElse(null);
            if (order != null) {
                paymentMethod = order.getPaymentMethod() != null ? order.getPaymentMethod().name() : "COD";
                total = order.getTotal();
            }
        }
        model.addAttribute("paymentMethod", paymentMethod);
        model.addAttribute("orderTotal", total);
        return "payment/success";
    }

    @GetMapping("/payment/error")
    public String paymentError(@RequestParam(required = false) String orderCode, Model model) {
        model.addAttribute("orderCode", orderCode);
        return "payment/error";
    }

    @GetMapping("/payment/cancel")
    public String paymentCancel(@RequestParam(required = false) String orderCode, Model model) {
        model.addAttribute("orderCode", orderCode);
        return "payment/cancel";
    }

}
