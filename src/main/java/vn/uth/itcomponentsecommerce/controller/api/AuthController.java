package vn.uth.itcomponentsecommerce.controller.api;

import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.web.bind.annotation.*;
import vn.uth.itcomponentsecommerce.dto.AuthResponse;
import vn.uth.itcomponentsecommerce.dto.LoginRequest;
import vn.uth.itcomponentsecommerce.dto.RegisterRequest;
import vn.uth.itcomponentsecommerce.service.AuthService;

import java.util.Map;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/register")
    public ResponseEntity<?> register(@Valid @RequestBody RegisterRequest req) {
        try {
            AuthResponse res = authService.register(req);
            return ResponseEntity.ok(res);
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(Map.of("message", ex.getMessage()));
        }
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@Valid @RequestBody LoginRequest req) {
        try {
            AuthResponse res = authService.login(req);
            return ResponseEntity.ok(res);
        } catch (BadCredentialsException ex) {
            return ResponseEntity.status(401).body(Map.of("message", "Sai username hoặc password"));
        } catch (Exception ex) {
            return ResponseEntity.status(401).body(Map.of("message", "Đăng nhập thất bại: " + ex.getMessage()));
        }
    }
}
