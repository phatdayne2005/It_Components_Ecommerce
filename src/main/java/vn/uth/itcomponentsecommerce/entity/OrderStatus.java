package vn.uth.itcomponentsecommerce.entity;

public enum OrderStatus {
    PENDING_PAYMENT,      // Chờ thanh toán SePay
    PENDING_CONFIRMATION, // Chờ shop xác nhận
    PROCESSING,           // Đang xử lý, trừ tồn kho
    SHIPPING,             // Đang giao
    DELIVERED,            // Đã giao
    REFUND_REQUESTED,     // Khách đã gửi yêu cầu hoàn tiền
    REFUND_REJECTED,      // Yêu cầu hoàn tiền bị từ chối
    CANCELLED,            // Đã hủy
    RETURN_REFUND         // Yêu cầu hoàn tiền
}
