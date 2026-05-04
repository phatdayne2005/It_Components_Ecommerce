package vn.uth.itcomponentsecommerce.service;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.uth.itcomponentsecommerce.dto.AdminCreateUserRequest;
import vn.uth.itcomponentsecommerce.dto.AdminUpdateUserRequest;
import vn.uth.itcomponentsecommerce.dto.AdminUserResponse;
import vn.uth.itcomponentsecommerce.entity.Role;
import vn.uth.itcomponentsecommerce.entity.User;
import vn.uth.itcomponentsecommerce.repository.RoleRepository;
import vn.uth.itcomponentsecommerce.repository.UserRepository;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class AdminUserService {

    private static final Set<String> ALLOWED_ROLES = Set.of("ROLE_USER", "ROLE_STAFF", "ROLE_ADMIN");

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;

    public AdminUserService(UserRepository userRepository,
                            RoleRepository roleRepository,
                            PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional(readOnly = true)
    public List<AdminUserResponse> listAll(String keyword) {
        List<User> users = userRepository.findAll();
        if (keyword != null && !keyword.isBlank()) {
            String kw = keyword.trim().toLowerCase();
            users = users.stream().filter(u ->
                    (u.getUsername() != null && u.getUsername().toLowerCase().contains(kw)) ||
                    (u.getEmail() != null && u.getEmail().toLowerCase().contains(kw)) ||
                    (u.getFullName() != null && u.getFullName().toLowerCase().contains(kw))
            ).collect(Collectors.toList());
        }
        users.sort((a, b) -> Long.compare(b.getId() == null ? 0 : b.getId(), a.getId() == null ? 0 : a.getId()));
        return users.stream().map(AdminUserResponse::from).toList();
    }

    @Transactional
    public AdminUserResponse create(AdminCreateUserRequest req) {
        if (userRepository.existsByUsername(req.getUsername())) {
            throw new IllegalArgumentException("Username đã tồn tại");
        }
        if (userRepository.existsByEmail(req.getEmail())) {
            throw new IllegalArgumentException("Email đã tồn tại");
        }
        User u = new User();
        u.setUsername(req.getUsername().trim());
        u.setEmail(req.getEmail().trim().toLowerCase());
        u.setPassword(passwordEncoder.encode(req.getPassword()));
        u.setFullName(req.getFullName());
        u.setPhone(emptyToNull(req.getPhone()));
        u.setEnabled(req.getEnabled() == null ? true : req.getEnabled());
        u.setRoles(resolveRoles(req.getRoles()));
        userRepository.save(u);
        return AdminUserResponse.from(u);
    }

    @Transactional
    public AdminUserResponse update(Long id, AdminUpdateUserRequest req) {
        User u = userRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy user"));

        if (req.getEmail() != null && !req.getEmail().isBlank() && !req.getEmail().equalsIgnoreCase(u.getEmail())) {
            if (userRepository.existsByEmail(req.getEmail())) {
                throw new IllegalArgumentException("Email đã tồn tại");
            }
            u.setEmail(req.getEmail().trim().toLowerCase());
        }
        if (req.getPassword() != null && !req.getPassword().isBlank()) {
            u.setPassword(passwordEncoder.encode(req.getPassword()));
        }
        if (req.getFullName() != null) {
            u.setFullName(req.getFullName());
        }
        if (req.getPhone() != null) {
            u.setPhone(emptyToNull(req.getPhone()));
        }
        if (req.getEnabled() != null) {
            u.setEnabled(req.getEnabled());
        }
        if (req.getRoles() != null && !req.getRoles().isEmpty()) {
            u.setRoles(resolveRoles(req.getRoles()));
        }
        userRepository.save(u);
        return AdminUserResponse.from(u);
    }

    @Transactional
    public void delete(Long id, Long currentUserId) {
        if (currentUserId != null && currentUserId.equals(id)) {
            throw new IllegalArgumentException("Không thể tự xóa tài khoản của chính bạn");
        }
        User u = userRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy user"));
        // Bảo vệ admin cuối cùng
        if (hasRole(u, "ROLE_ADMIN") && countAdmins() <= 1) {
            throw new IllegalArgumentException("Không thể xóa admin duy nhất của hệ thống");
        }
        userRepository.delete(u);
    }

    private Set<Role> resolveRoles(Set<String> rawRoles) {
        Set<String> normalized = new HashSet<>();
        if (rawRoles == null || rawRoles.isEmpty()) {
            normalized.add("ROLE_USER");
        } else {
            for (String r : rawRoles) {
                if (r == null || r.isBlank()) continue;
                String name = r.trim().toUpperCase();
                if (!name.startsWith("ROLE_")) name = "ROLE_" + name;
                if (!ALLOWED_ROLES.contains(name)) {
                    throw new IllegalArgumentException("Role không hợp lệ: " + r + ". Chỉ chấp nhận USER, STAFF, ADMIN.");
                }
                normalized.add(name);
            }
            if (normalized.isEmpty()) {
                normalized.add("ROLE_USER");
            }
        }
        Set<Role> roles = new HashSet<>();
        for (String name : normalized) {
            Role role = roleRepository.findByName(name)
                    .orElseGet(() -> roleRepository.save(new Role(name)));
            roles.add(role);
        }
        return roles;
    }

    private boolean hasRole(User u, String roleName) {
        return u.getRoles() != null && u.getRoles().stream()
                .anyMatch(r -> roleName.equals(r.getName()));
    }

    private long countAdmins() {
        return userRepository.findAll().stream()
                .filter(u -> hasRole(u, "ROLE_ADMIN"))
                .count();
    }

    private String emptyToNull(String s) {
        return (s == null || s.isBlank()) ? null : s.trim();
    }
}
