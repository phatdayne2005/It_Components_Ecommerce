package vn.uth.itcomponentsecommerce.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.ArrayList;
import java.util.List;

public class RefundRequest {

    @NotBlank(message = "refund reason is required")
    @Size(max = 500, message = "refund reason must be <= 500 characters")
    private String reason;

    private List<String> evidenceImageUrls = new ArrayList<>();

    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }
    public List<String> getEvidenceImageUrls() { return evidenceImageUrls; }
    public void setEvidenceImageUrls(List<String> evidenceImageUrls) { this.evidenceImageUrls = evidenceImageUrls; }
}
