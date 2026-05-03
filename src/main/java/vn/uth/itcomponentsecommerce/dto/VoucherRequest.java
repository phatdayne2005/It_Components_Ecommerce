package vn.uth.itcomponentsecommerce.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import vn.uth.itcomponentsecommerce.entity.Voucher;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public class VoucherRequest {
    @NotBlank
    @Size(max = 50)
    private String code;
    @NotBlank
    @Size(max = 200)
    private String name;
    @NotNull
    private Voucher.DiscountType discountType;
    @NotNull
    private BigDecimal discountValue;
    private BigDecimal minOrder;
    private BigDecimal maxDiscount;
    private LocalDateTime validFrom;
    private LocalDateTime validTo;
    private Integer usageLimit;
    private boolean active = true;

    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public Voucher.DiscountType getDiscountType() { return discountType; }
    public void setDiscountType(Voucher.DiscountType discountType) { this.discountType = discountType; }
    public BigDecimal getDiscountValue() { return discountValue; }
    public void setDiscountValue(BigDecimal discountValue) { this.discountValue = discountValue; }
    public BigDecimal getMinOrder() { return minOrder; }
    public void setMinOrder(BigDecimal minOrder) { this.minOrder = minOrder; }
    public BigDecimal getMaxDiscount() { return maxDiscount; }
    public void setMaxDiscount(BigDecimal maxDiscount) { this.maxDiscount = maxDiscount; }
    public LocalDateTime getValidFrom() { return validFrom; }
    public void setValidFrom(LocalDateTime validFrom) { this.validFrom = validFrom; }
    public LocalDateTime getValidTo() { return validTo; }
    public void setValidTo(LocalDateTime validTo) { this.validTo = validTo; }
    public Integer getUsageLimit() { return usageLimit; }
    public void setUsageLimit(Integer usageLimit) { this.usageLimit = usageLimit; }
    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }
}
