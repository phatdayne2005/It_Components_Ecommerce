package vn.uth.itcomponentsecommerce.dto;

import java.util.Set;

public class ProfileResponse {
    private Long id;
    private String username;
    private String email;
    private String fullName;
    private String phone;
    private Set<String> roles;

    public ProfileResponse() {}

    public ProfileResponse(Long id, String username, String email, String fullName, String phone, Set<String> roles) {
        this.id = id;
        this.username = username;
        this.email = email;
        this.fullName = fullName;
        this.phone = phone;
        this.roles = roles;
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
    public Set<String> getRoles() { return roles; }
    public void setRoles(Set<String> roles) { this.roles = roles; }
}
