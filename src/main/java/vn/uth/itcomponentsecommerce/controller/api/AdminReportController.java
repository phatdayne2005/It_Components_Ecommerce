package vn.uth.itcomponentsecommerce.controller.api;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import vn.uth.itcomponentsecommerce.dto.RevenueReportResponse;
import vn.uth.itcomponentsecommerce.service.AdminReportService;

@RestController
@RequestMapping("/api/admin/reports")
public class AdminReportController {

    private final AdminReportService adminReportService;

    public AdminReportController(AdminReportService adminReportService) {
        this.adminReportService = adminReportService;
    }

    @GetMapping("/revenue")
    public ResponseEntity<RevenueReportResponse> revenue(
            @RequestParam(name = "days", defaultValue = "30") int days) {
        return ResponseEntity.ok(adminReportService.revenueLastDays(days));
    }
}
