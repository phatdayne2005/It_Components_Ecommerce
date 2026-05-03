package vn.uth.itcomponentsecommerce.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import vn.uth.itcomponentsecommerce.entity.OrderStatus;

public class UpdateOrderStatusRequest {

    @NotNull(message = "status is required")
    private OrderStatus status;

    @Size(max = 100, message = "tracking number must be <= 100 characters")
    private String trackingNumber;

    @Size(max = 500, message = "note must be <= 500 characters")
    private String note;

    public OrderStatus getStatus() { return status; }
    public void setStatus(OrderStatus status) { this.status = status; }
    public String getTrackingNumber() { return trackingNumber; }
    public void setTrackingNumber(String trackingNumber) { this.trackingNumber = trackingNumber; }
    public String getNote() { return note; }
    public void setNote(String note) { this.note = note; }
}
