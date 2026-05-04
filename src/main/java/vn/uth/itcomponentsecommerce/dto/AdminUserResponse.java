package vn.uth.itcomponentsecommerce.dto;

import vn.uth.itcomponentsecommerce.entity.Role;
import vn.uth.itcomponentsecommerce.entity.User;

import java.time.LocalDateTime;
import java.util.Set;
import java.util.stream.Collectors;

public class AdminUserResponse {
    private Long id;
    private String username;
    private String email;
    private String fullName;
    private String phone;
    private boolean enabled;
    private Set<String> roles;
    private LocalDateTime createdAt;

    public static AdminUserResponse from(User u) {
        AdminUserResponse r = new AdminUserResponse();
        r.id = u.getId();
        r.username = u.getUsername();
        r.email = u.getEmail();
        r.fullName = u.getFullName();
        r.phone = u.getPhone();
        r.enabled = u.isEnabled();
        r.roles = u.getRoles() == null ? Set.of()
                : u.getRoles().stream().map(Role::getName).collect(Collectors.toSet());
        r.createdAt = u.getCreatedAt();
        return r;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public String getFullName() { return fullName; }
    public void setFullName(String fullName) { this.fullName = fullName; }
    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }
    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public Set<String> getRoles() { return roles; }
    public void setRoles(Set<String> roles) { this.roles = roles; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
