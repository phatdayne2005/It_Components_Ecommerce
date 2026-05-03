package vn.uth.itcomponentsecommerce.controller;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
public class CheckoutPageController {

    @GetMapping("/cart")
    public String cartPage(Model model) {
        model.addAttribute("siteName", "TechParts");
        model.addAttribute("pageTitle", "Giỏ hàng - TechParts");
        return "cart";
    }

    @GetMapping("/checkout")
    public String checkoutPage(Model model) {
        model.addAttribute("siteName", "TechParts");
        model.addAttribute("pageTitle", "Thanh toán - TechParts");
        return "checkout";
    }

    @GetMapping("/orders/my")
    public String myOrdersPage(Model model) {
        model.addAttribute("siteName", "TechParts");
        model.addAttribute("pageTitle", "Đơn hàng của tôi - TechParts");
        return "my-orders";
    }

    @GetMapping("/account")
    public String accountPage(Model model) {
        model.addAttribute("siteName", "TechParts");
        model.addAttribute("pageTitle", "Tài khoản - TechParts");
        return "account";
    }


}
