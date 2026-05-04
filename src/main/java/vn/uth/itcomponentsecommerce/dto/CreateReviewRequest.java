package vn.uth.itcomponentsecommerce.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public class CreateReviewRequest {

    @NotNull
    private Long orderId;

    @NotNull @Min(1) @Max(5)
    private Integer rating;

    @Size(max = 200)
    private String title;

    @NotBlank
    @Size(min = 5, max = 2000)
    private String comment;

    public Long getOrderId() { return orderId; }
    public void setOrderId(Long orderId) { this.orderId = orderId; }
    public Integer getRating() { return rating; }
    public void setRating(Integer rating) { this.rating = rating; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getComment() { return comment; }
    public void setComment(String comment) { this.comment = comment; }
}
