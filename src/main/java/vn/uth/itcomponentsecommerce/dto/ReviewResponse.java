package vn.uth.itcomponentsecommerce.dto;

import vn.uth.itcomponentsecommerce.entity.Review;

import java.time.LocalDateTime;

public class ReviewResponse {
    private Long id;
    private String authorDisplay;
    private int rating;
    private String title;
    private String comment;
    private LocalDateTime createdAt;

    public static ReviewResponse from(Review r) {
        ReviewResponse x = new ReviewResponse();
        x.setId(r.getId());
        x.setAuthorDisplay(maskUser(r.getUser() != null ? r.getUser().getUsername() : null));
        x.setRating(r.getRating());
        x.setTitle(r.getTitle());
        x.setComment(r.getComment());
        x.setCreatedAt(r.getCreatedAt());
        return x;
    }

    private static String maskUser(String username) {
        if (username == null || username.isBlank()) return "Khách";
        if (username.length() <= 2) return "***";
        return username.substring(0, Math.min(3, username.length())) + "***";
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getAuthorDisplay() { return authorDisplay; }
    public void setAuthorDisplay(String authorDisplay) { this.authorDisplay = authorDisplay; }
    public int getRating() { return rating; }
    public void setRating(int rating) { this.rating = rating; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getComment() { return comment; }
    public void setComment(String comment) { this.comment = comment; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
