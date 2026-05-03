package vn.uth.itcomponentsecommerce.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public class VoucherPreviewRequest {
    @NotBlank
    private String code;
    @NotNull
    private BigDecimal subtotal;

    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }
    public BigDecimal getSubtotal() { return subtotal; }
    public void setSubtotal(BigDecimal subtotal) { this.subtotal = subtotal; }
}
