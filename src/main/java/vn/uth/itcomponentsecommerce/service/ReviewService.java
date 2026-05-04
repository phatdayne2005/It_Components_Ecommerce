package vn.uth.itcomponentsecommerce.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.uth.itcomponentsecommerce.dto.CreateReviewRequest;
import vn.uth.itcomponentsecommerce.dto.ReviewResponse;
import vn.uth.itcomponentsecommerce.dto.ReviewSummaryResponse;
import vn.uth.itcomponentsecommerce.entity.OrderStatus;
import vn.uth.itcomponentsecommerce.entity.Review;
import vn.uth.itcomponentsecommerce.entity.User;
import vn.uth.itcomponentsecommerce.repository.OrderRepository;
import vn.uth.itcomponentsecommerce.repository.ProductRepository;
import vn.uth.itcomponentsecommerce.repository.ReviewRepository;
import vn.uth.itcomponentsecommerce.repository.UserRepository;

import java.util.List;
import java.util.Optional;

@Service
public class ReviewService {

    private final ReviewRepository reviewRepository;
    private final ProductRepository productRepository;
    private final OrderRepository orderRepository;
    private final UserRepository userRepository;

    public ReviewService(ReviewRepository reviewRepository,
                         ProductRepository productRepository,
                         OrderRepository orderRepository,
                         UserRepository userRepository) {
        this.reviewRepository = reviewRepository;
        this.productRepository = productRepository;
        this.orderRepository = orderRepository;
        this.userRepository = userRepository;
    }

    @Transactional(readOnly = true)
    public List<ReviewResponse> listApproved(Long productId) {
        return reviewRepository.findByProduct_IdAndApprovedTrueOrderByCreatedAtDesc(productId).stream()
                .map(ReviewResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public ReviewSummaryResponse summary(Long productId) {
        long count = reviewRepository.countByProduct_IdAndApprovedTrue(productId);
        Double avg = reviewRepository.averageRatingByProduct_IdAndApprovedTrue(productId);
        double rounded = avg != null ? Math.round(avg * 10.0) / 10.0 : 0.0;
        return new ReviewSummaryResponse(count, rounded);
    }

    @Transactional(readOnly = true)
    public Optional<ReviewResponse> findMine(Long userId, Long productId, Long orderId) {
        if (orderId != null) {
            return reviewRepository.findByUser_IdAndProduct_IdAndOrder_Id(userId, productId, orderId)
                    .map(ReviewResponse::from);
        }
        List<Review> reviews = reviewRepository.findByUser_IdAndProduct_IdOrderByCreatedAtDesc(userId, productId);
        if (reviews.isEmpty()) return Optional.empty();
        return Optional.of(ReviewResponse.from(reviews.get(0)));
    }

    @Transactional
    public ReviewResponse createOrUpdate(Long userId, Long productId, CreateReviewRequest req) {
        if (!productRepository.existsById(productId)) {
            throw new IllegalArgumentException("Sản phẩm không tồn tại");
        }
        if (req.getOrderId() == null) {
            throw new IllegalArgumentException("orderId là bắt buộc");
        }
        // Validate: order phải thuộc về user và phải DELIVERED
        var order = orderRepository.findById(req.getOrderId())
                .orElseThrow(() -> new IllegalArgumentException("Đơn hàng không tồn tại"));
        if (order.getUser() == null || !order.getUser().getId().equals(userId)) {
            throw new IllegalArgumentException("Đơn hàng không hợp lệ");
        }
        if (order.getStatus() != OrderStatus.DELIVERED) {
            throw new IllegalStateException("Bạn chỉ có thể đánh giá sau khi đơn hàng đã giao (DELIVERED).");
        }
        // Validate: sản phẩm phải nằm trong order
        boolean productInOrder = order.getItems().stream()
                .anyMatch(item -> item.getProduct().getId().equals(productId));
        if (!productInOrder) {
            throw new IllegalArgumentException("Sản phẩm không nằm trong đơn hàng này.");
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User không tồn tại"));

        // Tìm review theo user + product + order (1 đơn = 1 review)
        Review review = reviewRepository
                .findByUser_IdAndProduct_IdAndOrder_Id(userId, productId, req.getOrderId())
                .orElseGet(Review::new);
        review.setUser(user);
        review.setProduct(productRepository.getReferenceById(productId));
        review.setOrder(orderRepository.getReferenceById(req.getOrderId()));
        review.setRating(req.getRating());
        review.setTitle(req.getTitle() != null && !req.getTitle().isBlank() ? req.getTitle().trim() : null);
        review.setComment(req.getComment());
        review.setApproved(true);
        reviewRepository.save(review);
        reviewRepository.flush();
        Review loaded = reviewRepository.findById(review.getId()).orElse(review);
        return ReviewResponse.from(loaded);
    }

    @Transactional
    public void deleteByOrderId(Long orderId) {
        // Xóa tất cả reviews gắn với order khi đơn chuyển sang REFUND_REQUESTED (ARCHITECTURE §10.4)
        reviewRepository.deleteByOrder_Id(orderId);
    }

    @Transactional
    public ReviewResponse updateReview(Long reviewId, Long userId, CreateReviewRequest req) {
        Review review = reviewRepository.findById(reviewId)
                .orElseThrow(() -> new IllegalArgumentException("Review không tồn tại"));
        if (review.getUser() == null || !review.getUser().getId().equals(userId)) {
            throw new IllegalArgumentException("Bạn không có quyền sửa đánh giá này.");
        }
        review.setRating(req.getRating());
        review.setTitle(req.getTitle() != null && !req.getTitle().isBlank() ? req.getTitle().trim() : null);
        review.setComment(req.getComment());
        reviewRepository.save(review);
        reviewRepository.flush();
        Review loaded = reviewRepository.findById(reviewId).orElse(review);
        return ReviewResponse.from(loaded);
    }

    @Transactional
    public void deleteReview(Long reviewId, Long userId) {
        Review review = reviewRepository.findById(reviewId)
                .orElseThrow(() -> new IllegalArgumentException("Review không tồn tại"));
        if (review.getUser() == null || !review.getUser().getId().equals(userId)) {
            throw new IllegalArgumentException("Bạn không có quyền xóa đánh giá này.");
        }
        reviewRepository.delete(review);
    }
}
