package vn.uth.itcomponentsecommerce.controller.api;

import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import vn.uth.itcomponentsecommerce.dto.VoucherRequest;
import vn.uth.itcomponentsecommerce.entity.Voucher;
import vn.uth.itcomponentsecommerce.service.AdminVoucherService;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin/vouchers")
public class AdminVoucherController {

    private final AdminVoucherService adminVoucherService;

    public AdminVoucherController(AdminVoucherService adminVoucherService) {
        this.adminVoucherService = adminVoucherService;
    }

    @GetMapping
    public List<Voucher> list() {
        return adminVoucherService.list();
    }

    @GetMapping("/{id}")
    public Voucher get(@PathVariable Long id) {
        return adminVoucherService.get(id);
    }

    @PostMapping
    public ResponseEntity<?> create(@Valid @RequestBody VoucherRequest req) {
        try {
            return ResponseEntity.ok(adminVoucherService.create(req));
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(Map.of("message", ex.getMessage()));
        }
    }

    @PutMapping("/{id}")
    public ResponseEntity<?> update(@PathVariable Long id, @Valid @RequestBody VoucherRequest req) {
        try {
            return ResponseEntity.ok(adminVoucherService.update(id, req));
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(Map.of("message", ex.getMessage()));
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        adminVoucherService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
