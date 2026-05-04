package vn.uth.itcomponentsecommerce.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.Set;

public class AdminUpdateUserRequest {

    @Email
    @Size(max = 120)
    private String email;

    @Size(min = 6, max = 100)
    private String password; // null/blank = không đổi

    @Size(max = 120)
    private String fullName;

    @Pattern(regexp = "^$|^(\\+84|0)(3|5|7|8|9)\\d{8}$",
            message = "phone number must be a valid Vietnam mobile number")
    private String phone;

    private Set<String> roles;
    private Boolean enabled;

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }
    public String getFullName() { return fullName; }
    public void setFullName(String fullName) { this.fullName = fullName; }
    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }
    public Set<String> getRoles() { return roles; }
    public void setRoles(Set<String> roles) { this.roles = roles; }
    public Boolean getEnabled() { return enabled; }
    public void setEnabled(Boolean enabled) { this.enabled = enabled; }
}
