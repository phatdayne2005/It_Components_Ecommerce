package vn.uth.itcomponentsecommerce.controller.api;

import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import vn.uth.itcomponentsecommerce.dto.*;
import vn.uth.itcomponentsecommerce.entity.Voucher;
import vn.uth.itcomponentsecommerce.service.CurrentUserService;
import vn.uth.itcomponentsecommerce.service.UserProfileService;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/me")
public class UserProfileController {

    private final UserProfileService userProfileService;
    private final CurrentUserService currentUserService;

    public UserProfileController(UserProfileService userProfileService, CurrentUserService currentUserService) {
        this.userProfileService = userProfileService;
        this.currentUserService = currentUserService;
    }

    @GetMapping
    public ProfileResponse get() {
        return userProfileService.getProfile(currentUserService.requireCurrentUser());
    }

    @PatchMapping
    public ResponseEntity<?> patch(@RequestBody UpdateProfileRequest req) {
        try {
            return ResponseEntity.ok(userProfileService.updateProfile(currentUserService.requireCurrentUser(), req));
        } catch (Exception ex) {
            return ResponseEntity.badRequest().body(Map.of("message", ex.getMessage()));
        }
    }

    @PostMapping("/password")
    public ResponseEntity<?> changePassword(@Valid @RequestBody ChangePasswordRequest req) {
        try {
            userProfileService.changePassword(currentUserService.requireCurrentUser(), req);
            return ResponseEntity.ok(Map.of("message", "Đã đổi mật khẩu"));
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(Map.of("message", ex.getMessage()));
        }
    }
}
