package vn.uth.itcomponentsecommerce.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class RejectRefundRequest {

    @NotBlank(message = "reject reason is required")
    @Size(max = 500, message = "reject reason must be <= 500 characters")
    private String rejectNote;

    public String getRejectNote() { return rejectNote; }
    public void setRejectNote(String rejectNote) { this.rejectNote = rejectNote; }
}
