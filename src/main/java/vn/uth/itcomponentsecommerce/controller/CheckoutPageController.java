package vn.uth.itcomponentsecommerce.controller;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
public class CheckoutPageController {

    @GetMapping("/cart")
    public String cartPage() {
        return "redirect:/checkout";
    }

    @GetMapping("/checkout")
    public String checkoutPage(Model model) {
        model.addAttribute("siteName", "TechParts");
        model.addAttribute("pageTitle", "Checkout - TechParts");
        return "checkout";
    }

    @GetMapping("/orders/my")
    public String myOrdersPage(Model model) {
        model.addAttribute("siteName", "TechParts");
        model.addAttribute("pageTitle", "Đơn hàng của tôi - TechParts");
        return "my-orders";
    }

    @GetMapping("/payment/success")
    public String paymentSuccess(@RequestParam(value = "orderCode", required = false) String orderCode) {
        if (orderCode == null || orderCode.isBlank()) {
            return "redirect:/orders/my?payment=success";
        }
        return "redirect:/orders/my?payment=success&orderCode=" + orderCode;
    }

    @GetMapping("/payment/error")
    public String paymentError(@RequestParam(value = "orderCode", required = false) String orderCode) {
        if (orderCode == null || orderCode.isBlank()) {
            return "redirect:/orders/my?payment=error";
        }
        return "redirect:/orders/my?payment=error&orderCode=" + orderCode;
    }

    @GetMapping("/payment/cancel")
    public String paymentCancel(@RequestParam(value = "orderCode", required = false) String orderCode) {
        if (orderCode == null || orderCode.isBlank()) {
            return "redirect:/orders/my?payment=cancel";
        }
        return "redirect:/orders/my?payment=cancel&orderCode=" + orderCode;
    }
}
