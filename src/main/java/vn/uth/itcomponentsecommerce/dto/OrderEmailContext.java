package vn.uth.itcomponentsecommerce.dto;

import vn.uth.itcomponentsecommerce.entity.Order;
import vn.uth.itcomponentsecommerce.entity.OrderItem;
import vn.uth.itcomponentsecommerce.entity.PaymentMethod;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Snapshot từ {@link Order} dùng cho các email gửi qua {@code @Async}.
 * Tránh {@link org.hibernate.LazyInitializationException} khi method gửi email
 * chạy ở thread khác sau khi Hibernate session đã đóng.
 *
 * <p>Gọi {@link #from(Order)} ở caller (trong transaction còn mở) để snapshot,
 * rồi truyền context này vào {@code NotificationService}.
 */
public final class OrderEmailContext {

    public final String orderCode;
    public final BigDecimal subtotal;
    public final BigDecimal discount;
    public final BigDecimal shippingFee;
    public final BigDecimal total;
    public final PaymentMethod paymentMethod;
    public final String recipientEmail;
    public final String recipientName;
    public final String recipientPhone;
    public final String shippingAddress;
    public final String trackingNumber;
    public final LocalDateTime createdAt;
    public final List<Item> items;

    public static final class Item {
        public final String name;
        public final int quantity;
        public final BigDecimal unitPrice;
        public final BigDecimal lineTotal;

        public Item(String name, int quantity, BigDecimal unitPrice, BigDecimal lineTotal) {
            this.name = name;
            this.quantity = quantity;
            this.unitPrice = unitPrice;
            this.lineTotal = lineTotal;
        }
    }

    private OrderEmailContext(Order order) {
        this.orderCode = order.getOrderCode();
        this.subtotal = order.getSubtotal();
        this.discount = order.getDiscount();
        this.shippingFee = order.getShippingFee();
        this.total = order.getTotal();
        this.paymentMethod = order.getPaymentMethod();
        this.recipientEmail = order.getRecipientEmail();
        this.recipientName = order.getRecipientName();
        this.recipientPhone = order.getRecipientPhone();
        this.shippingAddress = order.getShippingAddress();
        this.trackingNumber = order.getTrackingNumber();
        this.createdAt = order.getCreatedAt();
        List<Item> snapshot = new ArrayList<>();
        if (order.getItems() != null) {
            for (OrderItem item : order.getItems()) {
                snapshot.add(new Item(
                        item.getProductName() != null ? item.getProductName() : "(sản phẩm)",
                        item.getQuantity() != null ? item.getQuantity() : 0,
                        item.getUnitPrice() != null ? item.getUnitPrice() : BigDecimal.ZERO,
                        item.getLineTotal() != null ? item.getLineTotal() : BigDecimal.ZERO
                ));
            }
        }
        this.items = snapshot;
    }

    public static OrderEmailContext from(Order order) {
        return new OrderEmailContext(order);
    }
}
