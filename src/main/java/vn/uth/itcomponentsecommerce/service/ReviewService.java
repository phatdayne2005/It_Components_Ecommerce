package vn.uth.itcomponentsecommerce.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.uth.itcomponentsecommerce.dto.CreateReviewRequest;
import vn.uth.itcomponentsecommerce.dto.ReviewResponse;
import vn.uth.itcomponentsecommerce.dto.ReviewSummaryResponse;
import vn.uth.itcomponentsecommerce.entity.OrderStatus;
import vn.uth.itcomponentsecommerce.entity.Review;
import vn.uth.itcomponentsecommerce.entity.User;
import vn.uth.itcomponentsecommerce.repository.OrderItemRepository;
import vn.uth.itcomponentsecommerce.repository.ProductRepository;
import vn.uth.itcomponentsecommerce.repository.ReviewRepository;
import vn.uth.itcomponentsecommerce.repository.UserRepository;

import java.util.List;
import java.util.Optional;

@Service
public class ReviewService {

    private final ReviewRepository reviewRepository;
    private final ProductRepository productRepository;
    private final OrderItemRepository orderItemRepository;
    private final UserRepository userRepository;

    public ReviewService(ReviewRepository reviewRepository,
                         ProductRepository productRepository,
                         OrderItemRepository orderItemRepository,
                         UserRepository userRepository) {
        this.reviewRepository = reviewRepository;
        this.productRepository = productRepository;
        this.orderItemRepository = orderItemRepository;
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
    public Optional<ReviewResponse> findMine(Long userId, Long productId) {
        return reviewRepository.findByUser_IdAndProduct_Id(userId, productId)
                .map(ReviewResponse::from);
    }

    @Transactional
    public ReviewResponse createOrUpdate(Long userId, Long productId, CreateReviewRequest req) {
        if (!productRepository.existsById(productId)) {
            throw new IllegalArgumentException("Sản phẩm không tồn tại");
        }
        if (!orderItemRepository.existsByProduct_IdAndOrder_User_IdAndOrder_Status(productId, userId, OrderStatus.DELIVERED)) {
            throw new IllegalStateException("Bạn chỉ có thể đánh giá sau khi đơn hàng đã giao (DELIVERED).");
        }
        User user = userRepository.findById(userId).orElseThrow(() -> new IllegalStateException("User không tồn tại"));
        Review review = reviewRepository.findByUser_IdAndProduct_Id(userId, productId).orElseGet(Review::new);
        review.setUser(user);
        review.setProduct(productRepository.getReferenceById(productId));
        review.setRating(req.getRating());
        review.setTitle(req.getTitle() != null && !req.getTitle().isBlank() ? req.getTitle().trim() : null);
        review.setComment(req.getComment());
        review.setApproved(true);
        reviewRepository.save(review);
        reviewRepository.flush();
        Review loaded = reviewRepository.findById(review.getId()).orElse(review);
        return ReviewResponse.from(loaded);
    }
}
