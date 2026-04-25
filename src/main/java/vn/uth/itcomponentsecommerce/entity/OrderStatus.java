package vn.uth.itcomponentsecommerce.entity;

public enum OrderStatus {
    PENDING,      // Vừa đặt, chờ xác nhận / chờ thanh toán
    CONFIRMED,    // Đã xác nhận
    PAID,         // Đã thanh toán
    SHIPPING,     // Đang giao
    COMPLETED,    // Hoàn tất
    CANCELLED,    // Đã huỷ
    RETURNED      // Đã hoàn trả
}
