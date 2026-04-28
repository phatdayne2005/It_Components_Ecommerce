package vn.uth.itcomponentsecommerce.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;
import vn.uth.itcomponentsecommerce.entity.PaymentMethod;

import java.util.ArrayList;
import java.util.List;

public class CheckoutRequest {

    @NotBlank(message = "phone is required")
    @Pattern(
            regexp = "^(\\+84|0)(3|5|7|8|9)\\d{8}$",
            message = "phone number must be a valid Vietnam mobile number"
    )
    private String phone;

    @NotBlank(message = "email is required")
    @Email(message = "email format is invalid")
    private String email;

    @NotBlank(message = "address is required")
    private String address;

    private String note;

    @jakarta.validation.constraints.NotNull(message = "paymentMethod is required")
    private PaymentMethod paymentMethod;

    @Valid
    @NotEmpty(message = "items are required")
    private List<CheckoutItemRequest> items = new ArrayList<>();

    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public String getAddress() { return address; }
    public void setAddress(String address) { this.address = address; }
    public String getNote() { return note; }
    public void setNote(String note) { this.note = note; }
    public PaymentMethod getPaymentMethod() { return paymentMethod; }
    public void setPaymentMethod(PaymentMethod paymentMethod) { this.paymentMethod = paymentMethod; }
    public List<CheckoutItemRequest> getItems() { return items; }
    public void setItems(List<CheckoutItemRequest> items) { this.items = items; }
}
