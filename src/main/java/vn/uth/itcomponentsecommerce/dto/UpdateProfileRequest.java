package vn.uth.itcomponentsecommerce.dto;

import jakarta.validation.constraints.Size;

public class UpdateProfileRequest {

    @Size(max = 120)
    private String fullName;

    @Size(max = 20)
    private String phone;

    public String getFullName() { return fullName; }
    public void setFullName(String fullName) { this.fullName = fullName; }
    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }
}
