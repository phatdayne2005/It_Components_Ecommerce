package vn.uth.itcomponentsecommerce.dto;

import java.math.BigDecimal;

public class VoucherPreviewResponse {
    private BigDecimal subtotal;
    private BigDecimal discount;
    private BigDecimal total;
    private String code;
    private String name;

    public VoucherPreviewResponse() {}

    public VoucherPreviewResponse(BigDecimal subtotal, BigDecimal discount, BigDecimal total, String code, String name) {
        this.subtotal = subtotal;
        this.discount = discount;
        this.total = total;
        this.code = code;
        this.name = name;
    }

    public BigDecimal getSubtotal() { return subtotal; }
    public void setSubtotal(BigDecimal subtotal) { this.subtotal = subtotal; }
    public BigDecimal getDiscount() { return discount; }
    public void setDiscount(BigDecimal discount) { this.discount = discount; }
    public BigDecimal getTotal() { return total; }
    public void setTotal(BigDecimal total) { this.total = total; }
    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
}
