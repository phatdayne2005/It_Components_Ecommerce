package vn.uth.itcomponentsecommerce.dto;

import vn.uth.itcomponentsecommerce.entity.Order;
import vn.uth.itcomponentsecommerce.entity.OrderItem;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public class AdminOrderResponse {

    private Long id;
    private String orderCode;
    private String status;
    private String paymentMethod;
    private BigDecimal subtotal;
    private BigDecimal discount;
    private BigDecimal shippingFee;
    private BigDecimal total;
    private String recipientName;
    private String recipientPhone;
    private String recipientEmail;
    private String shippingAddress;
    private String note;
    private String trackingNumber;
    private String cancelReason;
    private String refundReason;
    private String refundEvidenceUrls;
    private String refundRejectNote;
    private LocalDateTime createdAt;
    private LocalDateTime deliveredAt;
    private String userUsername;
    private List<AdminOrderItemResponse> items;

    public static AdminOrderResponse from(Order o) {
        AdminOrderResponse x = new AdminOrderResponse();
        x.id = o.getId();
        x.orderCode = o.getOrderCode();
        x.status = o.getStatus() != null ? o.getStatus().name() : null;
        x.paymentMethod = o.getPaymentMethod() != null ? o.getPaymentMethod().name() : null;
        x.subtotal = o.getSubtotal();
        x.discount = o.getDiscount();
        x.shippingFee = o.getShippingFee();
        x.total = o.getTotal();
        x.recipientName = o.getRecipientName();
        x.recipientPhone = o.getRecipientPhone();
        x.recipientEmail = o.getRecipientEmail();
        x.shippingAddress = o.getShippingAddress();
        x.note = o.getNote();
        x.trackingNumber = o.getTrackingNumber();
        x.cancelReason = o.getCancelReason();
        x.refundReason = o.getRefundReason();
        x.refundEvidenceUrls = o.getRefundEvidenceUrls();
        x.refundRejectNote = o.getRefundRejectNote();
        x.createdAt = o.getCreatedAt();
        x.deliveredAt = o.getDeliveredAt();
        if (o.getUser() != null) {
            x.userUsername = o.getUser().getUsername();
        }
        if (o.getItems() != null) {
            x.items = o.getItems().stream().map(AdminOrderItemResponse::from).toList();
        } else {
            x.items = List.of();
        }
        return x;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getOrderCode() { return orderCode; }
    public void setOrderCode(String orderCode) { this.orderCode = orderCode; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getPaymentMethod() { return paymentMethod; }
    public void setPaymentMethod(String paymentMethod) { this.paymentMethod = paymentMethod; }
    public BigDecimal getSubtotal() { return subtotal; }
    public void setSubtotal(BigDecimal subtotal) { this.subtotal = subtotal; }
    public BigDecimal getDiscount() { return discount; }
    public void setDiscount(BigDecimal discount) { this.discount = discount; }
    public BigDecimal getShippingFee() { return shippingFee; }
    public void setShippingFee(BigDecimal shippingFee) { this.shippingFee = shippingFee; }
    public BigDecimal getTotal() { return total; }
    public void setTotal(BigDecimal total) { this.total = total; }
    public String getRecipientName() { return recipientName; }
    public void setRecipientName(String recipientName) { this.recipientName = recipientName; }
    public String getRecipientPhone() { return recipientPhone; }
    public void setRecipientPhone(String recipientPhone) { this.recipientPhone = recipientPhone; }
    public String getRecipientEmail() { return recipientEmail; }
    public void setRecipientEmail(String recipientEmail) { this.recipientEmail = recipientEmail; }
    public String getShippingAddress() { return shippingAddress; }
    public void setShippingAddress(String shippingAddress) { this.shippingAddress = shippingAddress; }
    public String getNote() { return note; }
    public void setNote(String note) { this.note = note; }
    public String getTrackingNumber() { return trackingNumber; }
    public void setTrackingNumber(String trackingNumber) { this.trackingNumber = trackingNumber; }
    public String getCancelReason() { return cancelReason; }
    public void setCancelReason(String cancelReason) { this.cancelReason = cancelReason; }
    public String getRefundReason() { return refundReason; }
    public void setRefundReason(String refundReason) { this.refundReason = refundReason; }
    public String getRefundEvidenceUrls() { return refundEvidenceUrls; }
    public void setRefundEvidenceUrls(String refundEvidenceUrls) { this.refundEvidenceUrls = refundEvidenceUrls; }
    public String getRefundRejectNote() { return refundRejectNote; }
    public void setRefundRejectNote(String refundRejectNote) { this.refundRejectNote = refundRejectNote; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getDeliveredAt() { return deliveredAt; }
    public void setDeliveredAt(LocalDateTime deliveredAt) { this.deliveredAt = deliveredAt; }
    public String getUserUsername() { return userUsername; }
    public void setUserUsername(String userUsername) { this.userUsername = userUsername; }
    public List<AdminOrderItemResponse> getItems() { return items; }
    public void setItems(List<AdminOrderItemResponse> items) { this.items = items; }

    public static class AdminOrderItemResponse {
        private Long productId;
        private String productName;
        private String productImage;
        private Integer quantity;
        private BigDecimal unitPrice;
        private BigDecimal lineTotal;

        public static AdminOrderItemResponse from(OrderItem item) {
            AdminOrderItemResponse x = new AdminOrderItemResponse();
            if (item.getProduct() != null) {
                x.productId = item.getProduct().getId();
            }
            x.productName = item.getProductName();
            x.productImage = item.getProductImage();
            x.quantity = item.getQuantity();
            x.unitPrice = item.getUnitPrice();
            x.lineTotal = item.getLineTotal();
            return x;
        }

        public Long getProductId() { return productId; }
        public void setProductId(Long productId) { this.productId = productId; }
        public String getProductName() { return productName; }
        public void setProductName(String productName) { this.productName = productName; }
        public String getProductImage() { return productImage; }
        public void setProductImage(String productImage) { this.productImage = productImage; }
        public Integer getQuantity() { return quantity; }
        public void setQuantity(Integer quantity) { this.quantity = quantity; }
        public BigDecimal getUnitPrice() { return unitPrice; }
        public void setUnitPrice(BigDecimal unitPrice) { this.unitPrice = unitPrice; }
        public BigDecimal getLineTotal() { return lineTotal; }
        public void setLineTotal(BigDecimal lineTotal) { this.lineTotal = lineTotal; }
    }
}
