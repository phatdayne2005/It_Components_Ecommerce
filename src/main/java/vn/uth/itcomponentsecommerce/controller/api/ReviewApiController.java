package vn.uth.itcomponentsecommerce.controller.api;

import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import vn.uth.itcomponentsecommerce.dto.CreateReviewRequest;
import vn.uth.itcomponentsecommerce.dto.ReviewResponse;
import vn.uth.itcomponentsecommerce.dto.ReviewSummaryResponse;
import vn.uth.itcomponentsecommerce.service.CurrentUserService;
import vn.uth.itcomponentsecommerce.service.ReviewService;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/products/{productId}/reviews")
public class ReviewApiController {

    private final ReviewService reviewService;
    private final CurrentUserService currentUserService;

    public ReviewApiController(ReviewService reviewService, CurrentUserService currentUserService) {
        this.reviewService = reviewService;
        this.currentUserService = currentUserService;
    }

    @GetMapping
    public List<ReviewResponse> list(@PathVariable Long productId) {
        return reviewService.listApproved(productId);
    }

    @GetMapping("/summary")
    public ReviewSummaryResponse summary(@PathVariable Long productId) {
        return reviewService.summary(productId);
    }

    @GetMapping("/me")
    public ResponseEntity<?> mine(@PathVariable Long productId,
                                   @RequestParam(required = false) Long orderId) {
        try {
            var user = currentUserService.requireCurrentUser();
            var result = reviewService.findMine(user.getId(), productId, orderId);
            return result.map(r -> ResponseEntity.ok(r))
                    .orElseGet(() -> ResponseEntity.noContent().build());
        } catch (Exception ex) {
            return ResponseEntity.status(401).body(Map.of("message", "Can dang nhap"));
        }
    }

    @PostMapping
    public ResponseEntity<?> create(@PathVariable Long productId,
                                    @Valid @RequestBody CreateReviewRequest req) {
        try {
            var user = currentUserService.requireCurrentUser();
            ReviewResponse r = reviewService.createOrUpdate(user.getId(), productId, req);
            return ResponseEntity.ok(r);
        } catch (IllegalStateException ex) {
            return ResponseEntity.badRequest().body(Map.of("message", ex.getMessage()));
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(Map.of("message", ex.getMessage()));
        } catch (Exception ex) {
            return ResponseEntity.status(401).body(Map.of("message", "Can dang nhap"));
        }
    }

    @PutMapping("/{reviewId}")
    public ResponseEntity<?> update(@PathVariable Long productId,
                                    @PathVariable Long reviewId,
                                    @Valid @RequestBody CreateReviewRequest req) {
        try {
            var user = currentUserService.requireCurrentUser();
            ReviewResponse r = reviewService.updateReview(reviewId, user.getId(), req);
            return ResponseEntity.ok(r);
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(Map.of("message", ex.getMessage()));
        } catch (Exception ex) {
            return ResponseEntity.status(401).body(Map.of("message", "Can dang nhap"));
        }
    }

    @DeleteMapping("/{reviewId}")
    public ResponseEntity<?> delete(@PathVariable Long productId,
                                   @PathVariable Long reviewId) {
        try {
            var user = currentUserService.requireCurrentUser();
            reviewService.deleteReview(reviewId, user.getId());
            return ResponseEntity.noContent().build();
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(Map.of("message", ex.getMessage()));
        } catch (Exception ex) {
            return ResponseEntity.status(401).body(Map.of("message", "Can dang nhap"));
        }
    }
}
