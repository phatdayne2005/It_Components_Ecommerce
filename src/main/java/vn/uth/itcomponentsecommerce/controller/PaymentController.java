package vn.uth.itcomponentsecommerce.controller;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
public class PaymentController {

    @GetMapping("/payment/success")
    public String paymentSuccess(@RequestParam(required = false) String orderCode, Model model) {
        model.addAttribute("orderCode", orderCode);
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
