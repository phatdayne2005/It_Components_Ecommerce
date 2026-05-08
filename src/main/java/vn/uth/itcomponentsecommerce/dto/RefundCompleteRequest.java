package vn.uth.itcomponentsecommerce.dto;

import jakarta.validation.constraints.Size;

public class RefundCompleteRequest {

    @Size(max = 500, message = "Ghi chú tối đa 500 ký tự")
    private String note;

    public String getNote() { return note; }
    public void setNote(String note) { this.note = note; }
}
