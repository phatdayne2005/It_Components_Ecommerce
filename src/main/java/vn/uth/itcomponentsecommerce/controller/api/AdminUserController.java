package vn.uth.itcomponentsecommerce.controller.api;

import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import vn.uth.itcomponentsecommerce.dto.AdminCreateUserRequest;
import vn.uth.itcomponentsecommerce.dto.AdminUpdateUserRequest;
import vn.uth.itcomponentsecommerce.dto.AdminUserResponse;
import vn.uth.itcomponentsecommerce.entity.User;
import vn.uth.itcomponentsecommerce.service.AdminUserService;
import vn.uth.itcomponentsecommerce.service.CurrentUserService;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin/users")
public class AdminUserController {

    private final AdminUserService adminUserService;
    private final CurrentUserService currentUserService;

    public AdminUserController(AdminUserService adminUserService, CurrentUserService currentUserService) {
        this.adminUserService = adminUserService;
        this.currentUserService = currentUserService;
    }

    @GetMapping
    public ResponseEntity<List<AdminUserResponse>> list(
            @RequestParam(required = false) String keyword) {
        return ResponseEntity.ok(adminUserService.listAll(keyword));
    }

    @PostMapping
    public ResponseEntity<?> create(@Valid @RequestBody AdminCreateUserRequest req) {
        try {
            return ResponseEntity.ok(adminUserService.create(req));
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(Map.of("message", ex.getMessage()));
        }
    }

    @PutMapping("/{id}")
    public ResponseEntity<?> update(@PathVariable Long id, @Valid @RequestBody AdminUpdateUserRequest req) {
        try {
            return ResponseEntity.ok(adminUserService.update(id, req));
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(Map.of("message", ex.getMessage()));
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> delete(@PathVariable Long id) {
        try {
            User current = currentUserService.requireCurrentUser();
            adminUserService.delete(id, current.getId());
            return ResponseEntity.ok(Map.of("message", "Đã xóa user"));
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(Map.of("message", ex.getMessage()));
        }
    }
}
