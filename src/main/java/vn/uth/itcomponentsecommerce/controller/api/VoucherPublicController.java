package vn.uth.itcomponentsecommerce.controller.api;

import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import vn.uth.itcomponentsecommerce.dto.VoucherPreviewRequest;
import vn.uth.itcomponentsecommerce.dto.VoucherPreviewResponse;
import vn.uth.itcomponentsecommerce.entity.Voucher;
import vn.uth.itcomponentsecommerce.service.VoucherService;

import java.math.BigDecimal;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/vouchers")
public class VoucherPublicController {

    private final VoucherService voucherService;

    public VoucherPublicController(VoucherService voucherService) {
        this.voucherService = voucherService;
    }

    @PostMapping("/preview")
    public ResponseEntity<?> preview(@Valid @RequestBody VoucherPreviewRequest req) {
        try {
            if (req.getSubtotal() == null || req.getSubtotal().compareTo(BigDecimal.ZERO) < 0) {
                return ResponseEntity.badRequest().body(Map.of("message", "subtotal không hợp lệ"));
            }
            Voucher v = voucherService.findByCodeNormalized(req.getCode())
                    .orElseThrow(() -> new IllegalArgumentException("Mã voucher không hợp lệ"));
            voucherService.assertVoucherApplicable(v, req.getSubtotal());
            BigDecimal discount = voucherService.computeDiscount(v, req.getSubtotal());
            BigDecimal total = req.getSubtotal().subtract(discount);
            if (total.compareTo(BigDecimal.ZERO) < 0) {
                total = BigDecimal.ZERO;
            }
            return ResponseEntity.ok(new VoucherPreviewResponse(
                    req.getSubtotal(), discount, total, v.getCode(), v.getName()));
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(Map.of("message", ex.getMessage()));
        }
    }
}
