package vn.uth.itcomponentsecommerce.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Entity
@Table(name = "orders", indexes = {
        @Index(name = "idx_order_user", columnList = "user_id"),
        @Index(name = "idx_order_status", columnList = "status")
})
public class Order {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Version
    private Long version;

    @Column(name = "order_code", unique = true, length = 30)
    private String orderCode;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    @JsonIgnore
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private OrderStatus status = OrderStatus.PENDING_CONFIRMATION;

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_method", nullable = false, length = 20)
    private PaymentMethod paymentMethod;

    // Snapshot địa chỉ giao tại thời điểm đặt (không phụ thuộc Address bị xoá sau đó)
    @Column(name = "recipient_name", length = 120)
    private String recipientName;

    @Column(name = "recipient_phone", length = 20)
    private String recipientPhone;

    @Column(name = "recipient_email", length = 150)
    private String recipientEmail;

    @Column(name = "shipping_address", length = 500)
    private String shippingAddress;

    @Column(precision = 14, scale = 2)
    private BigDecimal subtotal;

    @Column(name = "shipping_fee", precision = 14, scale = 2)
    private BigDecimal shippingFee;

    @Column(precision = 14, scale = 2)
    private BigDecimal discount;

    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal total;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "voucher_id")
    private Voucher voucher;

    @Column(length = 500)
    private String note;

    @Column(name = "tracking_number", length = 100)
    private String trackingNumber;

    @Column(name = "cancel_reason", length = 500)
    private String cancelReason;

    @Column(name = "refund_reason", length = 500)
    private String refundReason;

    @Column(name = "refund_evidence_urls", columnDefinition = "TEXT")
    private String refundEvidenceUrls;

    @Column(name = "refund_reject_note", length = 500)
    private String refundRejectNote;

    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<OrderItem> items = new ArrayList<>();

    @OneToOne(mappedBy = "order", cascade = CascadeType.ALL)
    private Payment payment;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Transient
    private String sepayTransferContent;
    @Transient
    private String sepayCheckoutActionUrl;
    @Transient
    private Map<String, String> sepayCheckoutFields;

    @PrePersist
    void onCreate() {
        this.createdAt = LocalDateTime.now();
        this.updatedAt = this.createdAt;
    }

    @PreUpdate
    void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getVersion() { return version; }
    public void setVersion(Long version) { this.version = version; }
    public String getOrderCode() { return orderCode; }
    public void setOrderCode(String orderCode) { this.orderCode = orderCode; }
    public User getUser() { return user; }
    public void setUser(User user) { this.user = user; }
    public OrderStatus getStatus() { return status; }
    public void setStatus(OrderStatus status) { this.status = status; }
    public PaymentMethod getPaymentMethod() { return paymentMethod; }
    public void setPaymentMethod(PaymentMethod paymentMethod) { this.paymentMethod = paymentMethod; }
    public String getRecipientName() { return recipientName; }
    public void setRecipientName(String recipientName) { this.recipientName = recipientName; }
    public String getRecipientPhone() { return recipientPhone; }
    public void setRecipientPhone(String recipientPhone) { this.recipientPhone = recipientPhone; }
    public String getRecipientEmail() { return recipientEmail; }
    public void setRecipientEmail(String recipientEmail) { this.recipientEmail = recipientEmail; }
    public String getShippingAddress() { return shippingAddress; }
    public void setShippingAddress(String shippingAddress) { this.shippingAddress = shippingAddress; }
    public BigDecimal getSubtotal() { return subtotal; }
    public void setSubtotal(BigDecimal subtotal) { this.subtotal = subtotal; }
    public BigDecimal getShippingFee() { return shippingFee; }
    public void setShippingFee(BigDecimal shippingFee) { this.shippingFee = shippingFee; }
    public BigDecimal getDiscount() { return discount; }
    public void setDiscount(BigDecimal discount) { this.discount = discount; }
    public BigDecimal getTotal() { return total; }
    public void setTotal(BigDecimal total) { this.total = total; }
    public Voucher getVoucher() { return voucher; }
    public void setVoucher(Voucher voucher) { this.voucher = voucher; }
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
    public List<OrderItem> getItems() { return items; }
    public void setItems(List<OrderItem> items) { this.items = items; }
    public Payment getPayment() { return payment; }
    public void setPayment(Payment payment) { this.payment = payment; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
    public String getSepayTransferContent() { return sepayTransferContent; }
    public void setSepayTransferContent(String sepayTransferContent) { this.sepayTransferContent = sepayTransferContent; }
    public String getSepayCheckoutActionUrl() { return sepayCheckoutActionUrl; }
    public void setSepayCheckoutActionUrl(String sepayCheckoutActionUrl) { this.sepayCheckoutActionUrl = sepayCheckoutActionUrl; }
    public Map<String, String> getSepayCheckoutFields() { return sepayCheckoutFields; }
    public void setSepayCheckoutFields(Map<String, String> sepayCheckoutFields) { this.sepayCheckoutFields = sepayCheckoutFields; }
}
