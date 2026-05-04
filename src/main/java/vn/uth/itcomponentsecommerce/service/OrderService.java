package vn.uth.itcomponentsecommerce.service;

import jakarta.persistence.OptimisticLockException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import vn.uth.itcomponentsecommerce.dto.*;
import vn.uth.itcomponentsecommerce.entity.*;
import vn.uth.itcomponentsecommerce.exception.OutOfStockException;
import vn.uth.itcomponentsecommerce.repository.OrderRepository;
import vn.uth.itcomponentsecommerce.repository.PaymentRepository;

import java.math.BigDecimal;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.Map;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class OrderService {

    private final OrderRepository orderRepository;
    private final PaymentRepository paymentRepository;
    private final CurrentUserService currentUserService;
    private final CartService cartService;
    private final InventoryService inventoryService;
    private final ProductExternalService productExternalService;
    private final NotificationService notificationService;
    private final SepayGatewayCheckoutService sepayGatewayCheckoutService;
    private final VoucherService voucherService;
    private final ReviewService reviewService;

    @Value("${app.order.pending-payment-timeout-minutes:15}")
    private int pendingPaymentTimeoutMinutes;
    @Value("${app.upload.dir:./uploads}")
    private String uploadDir;

    public OrderService(OrderRepository orderRepository,
                        PaymentRepository paymentRepository,
                        CurrentUserService currentUserService,
                        CartService cartService,
                        InventoryService inventoryService,
                        ProductExternalService productExternalService,
                        NotificationService notificationService,
                        SepayGatewayCheckoutService sepayGatewayCheckoutService,
                        VoucherService voucherService,
                        ReviewService reviewService) {
        this.orderRepository = orderRepository;
        this.paymentRepository = paymentRepository;
        this.currentUserService = currentUserService;
        this.cartService = cartService;
        this.inventoryService = inventoryService;
        this.productExternalService = productExternalService;
        this.notificationService = notificationService;
        this.sepayGatewayCheckoutService = sepayGatewayCheckoutService;
        this.voucherService = voucherService;
        this.reviewService = reviewService;
    }

    @Transactional
    public Order checkout(CheckoutRequest request) {
        User currentUser = currentUserService.requireCurrentUser();
        Cart currentCart = cartService.getCurrentUserCart();
        Map<Long, CartItem> cartItemByProductId = currentCart.getItems().stream()
                .collect(Collectors.toMap(item -> item.getProduct().getId(), Function.identity()));

        Order order = new Order();
        order.setOrderCode(generateOrderCode());
        order.setUser(currentUser);
        order.setPaymentMethod(request.getPaymentMethod());
        order.setRecipientPhone(request.getPhone());
        order.setRecipientEmail(request.getEmail());
        order.setShippingAddress(request.getAddress());
        order.setNote(request.getNote());
        order.setShippingFee(BigDecimal.ZERO);

        BigDecimal subtotal = BigDecimal.ZERO;
        for (CheckoutItemRequest itemRequest : request.getItems()) {
            CartItem cartItem = cartItemByProductId.get(itemRequest.getProductId());
            if (cartItem == null) {
                throw new IllegalArgumentException("Checkout item does not belong to cart");
            }
            Product product = cartItem.getProduct();
            if (product == null) {
                throw new IllegalArgumentException("Product not found in cart");
            }
            if (!cartItem.isSelected()) {
                throw new IllegalArgumentException("Checkout item must be selected");
            }
            if (!itemRequest.getQuantity().equals(cartItem.getQuantity())) {
                throw new IllegalArgumentException("Checkout quantity does not match cart quantity");
            }
            int availableStock = productExternalService.getAvailableStock(product.getId());
            if (availableStock < itemRequest.getQuantity()) {
                throw new OutOfStockException("Out of stock for product " + product.getId());
            }
            if (!product.isActive()) {
                throw new IllegalArgumentException("Product is inactive");
            }

            OrderItem orderItem = new OrderItem();
            orderItem.setOrder(order);
            orderItem.setProduct(product);
            orderItem.setProductName(product.getName());
            orderItem.setProductImage(product.getImageUrl());
            orderItem.setUnitPrice(product.getPrice());
            orderItem.setQuantity(itemRequest.getQuantity());
            orderItem.setLineTotal(product.getPrice().multiply(BigDecimal.valueOf(itemRequest.getQuantity())));
            orderItem.setWarrantyMonths(product.getWarrantyMonths());
            order.getItems().add(orderItem);
            subtotal = subtotal.add(orderItem.getLineTotal());
        }

        order.setSubtotal(subtotal);
        Voucher appliedVoucher = null;
        BigDecimal discount = BigDecimal.ZERO;
        if (request.getVoucherCode() != null && !request.getVoucherCode().isBlank()) {
            appliedVoucher = voucherService.findByCodeNormalized(request.getVoucherCode())
                    .orElseThrow(() -> new IllegalArgumentException("Mã voucher không hợp lệ"));
            voucherService.assertVoucherApplicable(appliedVoucher, subtotal);
            discount = voucherService.computeDiscount(appliedVoucher, subtotal);
            order.setVoucher(appliedVoucher);
        }
        order.setDiscount(discount);
        BigDecimal shipping = order.getShippingFee() != null ? order.getShippingFee() : BigDecimal.ZERO;
        order.setTotal(subtotal.subtract(discount).add(shipping));
        boolean isSepay = request.getPaymentMethod() == PaymentMethod.SEPAY;
        order.setStatus(isSepay ? OrderStatus.PENDING_PAYMENT : OrderStatus.PENDING_CONFIRMATION);

        // Reserve stock for both SePay and COD (ARCHITECTURE §6)
        reserveStockForPendingPayment(order);

        Order savedOrder = orderRepository.save(order);
        
        if (isSepay) {
            sepayGatewayCheckoutService.enrichCheckoutData(savedOrder);
        }

        Payment payment = new Payment();
        payment.setOrder(savedOrder);
        payment.setMethod(request.getPaymentMethod());
        payment.setStatus(PaymentStatus.PENDING);
        payment.setAmount(savedOrder.getTotal());
        if (savedOrder.getStatus() == OrderStatus.PENDING_PAYMENT) {
            payment.setTransactionId("TXN-" + UUID.randomUUID());
        }
        paymentRepository.save(payment);

        // clear selected items in cart after successful checkout
        Set<Long> purchasedProductIds = request.getItems().stream()
                .map(CheckoutItemRequest::getProductId)
                .collect(Collectors.toSet());
        for (Long productId : purchasedProductIds) {
            cartService.removeItem(productId);
        }

        if (!isSepay && appliedVoucher != null) {
            voucherService.incrementUsageIfPresent(appliedVoucher);
        }

        // Email xác nhận đặt hàng (cả COD và SEPAY) — SEPAY sẽ có thêm email xác nhận thanh toán sau IPN
        notificationService.sendOrderConfirmationEmail(
                savedOrder.getRecipientEmail(),
                savedOrder.getOrderCode(),
                savedOrder.getTotal(),
                savedOrder.getPaymentMethod()
        );

        return savedOrder;
    }

    @Transactional
    public Order updateStatus(Long orderId, UpdateOrderStatusRequest request) {
        Order order = orderRepository.findWithItemsById(orderId)
                .orElseThrow(() -> new IllegalArgumentException("Order not found"));

        OrderStatus oldStatus = order.getStatus();
        OrderStatus newStatus = request.getStatus();
        validateTransition(oldStatus, newStatus);

        if (newStatus == OrderStatus.PROCESSING) {
            reduceInventoryForOrder(order);
        } else if (newStatus == OrderStatus.DELIVERED) {
            if (order.getDeliveredAt() == null) {
                order.setDeliveredAt(LocalDateTime.now());
            }
            releaseReservedStockForPendingPayment(order, oldStatus);
        } else if (newStatus == OrderStatus.SHIPPING) {
            if (request.getTrackingNumber() == null || request.getTrackingNumber().isBlank()) {
                throw new IllegalArgumentException("trackingNumber is required when moving to SHIPPING");
            }
            order.setTrackingNumber(request.getTrackingNumber());
        } else if (newStatus == OrderStatus.CANCELLED) {
            releaseReservedStockForPendingPayment(order, oldStatus);
            rollbackInventoryForOrder(order, oldStatus);
            rollbackVoucherUsage(order, oldStatus);
            order.setCancelReason(request.getNote());
            markPaymentFailedIfPending(order);
        }
        if (newStatus == OrderStatus.REFUND_REQUESTED) {
            handleRefundTransition(order, oldStatus);
            reviewService.deleteByOrderId(orderId);
        } else if (newStatus == OrderStatus.REFUND_REJECTED) {
            order.setRefundRejectNote(request.getNote());
        } else if (newStatus == OrderStatus.RETURN_REFUND) {
            notificationService.sendReturnRefundFormEmail(order.getRecipientEmail(), order.getOrderCode());
        }

        order.setStatus(newStatus);
        Order saved = orderRepository.save(order);
        notificationService.sendOrderStatusChangedEmail(saved.getRecipientEmail(), saved.getOrderCode(), oldStatus, newStatus);
        if (newStatus == OrderStatus.SHIPPING) {
            notificationService.sendShippingNotificationEmail(saved.getRecipientEmail(), saved.getOrderCode(), saved.getTrackingNumber());
        }
        return saved;
    }

    private void handleRefundTransition(Order order, OrderStatus oldStatus) {
        // Release reserved stock (chỉ áp với status còn reserved như PENDING_PAYMENT/PENDING_CONFIRMATION)
        releaseReservedStockForPendingPayment(order, oldStatus);
        // Restore stock thực đã reduce ở PROCESSING (COD + SePay đều reduce ở PROCESSING)
        if (EnumSet.of(OrderStatus.PROCESSING, OrderStatus.SHIPPING, OrderStatus.DELIVERED).contains(oldStatus)) {
            for (OrderItem item : order.getItems()) {
                inventoryService.restoreStock(item.getProduct().getId(), item.getQuantity());
            }
        }
        // Rollback voucher usage nếu đã increment (PENDING_CONFIRMATION trở đi)
        if (order.getVoucher() != null
                && EnumSet.of(OrderStatus.PENDING_CONFIRMATION, OrderStatus.PROCESSING,
                              OrderStatus.SHIPPING, OrderStatus.DELIVERED).contains(oldStatus)) {
            voucherService.rollbackUsageIfPresent(order.getVoucher());
        }
    }

    @Transactional
    public Order cancelByCustomer(Long orderId, CancelOrderRequest request) {
        User currentUser = currentUserService.requireCurrentUser();
        Order order = orderRepository.findWithItemsById(orderId)
                .orElseThrow(() -> new IllegalArgumentException("Order not found"));

        // Validate ownership
        if (order.getUser() == null || !order.getUser().getId().equals(currentUser.getId())) {
            throw new IllegalArgumentException("Không tìm thấy đơn hàng");
        }

        if (!EnumSet.of(OrderStatus.PENDING_PAYMENT, OrderStatus.PENDING_CONFIRMATION, OrderStatus.PROCESSING)
                .contains(order.getStatus())) {
            throw new IllegalStateException("Cannot cancel order at current status");
        }

        OrderStatus old = order.getStatus();
        releaseReservedStockForPendingPayment(order, old);
        rollbackInventoryForOrder(order, old);
        rollbackVoucherUsage(order, old);
        order.setStatus(OrderStatus.CANCELLED);
        order.setCancelReason(request.getReason());
        markPaymentFailedIfPending(order);
        Order saved = orderRepository.save(order);
        notificationService.sendOrderStatusChangedEmail(saved.getRecipientEmail(), saved.getOrderCode(), old, OrderStatus.CANCELLED);
        return saved;
    }

    @Transactional
    public Order requestRefund(Long orderId, RefundRequest request) {
        User currentUser = currentUserService.requireCurrentUser();
        Order order = orderRepository.findWithItemsById(orderId)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy đơn hàng"));
        if (order.getUser() == null || !order.getUser().getId().equals(currentUser.getId())) {
            throw new IllegalArgumentException("Không tìm thấy đơn hàng");
        }
        // Allow refund from SHIPPING (REFUND_REJECTED không gọi qua đây — customer phải mở yêu cầu mới với ảnh)
        if (!EnumSet.of(OrderStatus.SHIPPING, OrderStatus.DELIVERED, OrderStatus.REFUND_REJECTED).contains(order.getStatus())) {
            throw new IllegalStateException("Chỉ đơn ở trạng thái đang giao, đã giao hoặc bị từ chối hoàn tiền mới được yêu cầu hoàn tiền");
        }

        OrderStatus old = order.getStatus();
        order.setStatus(OrderStatus.REFUND_REQUESTED);
        order.setRefundReason(request.getReason());
        order.setRefundEvidenceUrls(request.getEvidenceImageUrls() == null ? null : String.join(",", request.getEvidenceImageUrls()));
        // Reset reject note nếu đây là lần yêu cầu lại sau khi bị từ chối
        order.setRefundRejectNote(null);
        handleRefundTransition(order, old);
        reviewService.deleteByOrderId(orderId);
        Order saved = orderRepository.save(order);
        notificationService.sendOrderStatusChangedEmail(saved.getRecipientEmail(), saved.getOrderCode(), old, OrderStatus.REFUND_REQUESTED);
        return saved;
    }

    @Transactional
    public Order markDeliveredByCustomer(Long orderId) {
        User currentUser = currentUserService.requireCurrentUser();
        Order order = orderRepository.findWithItemsById(orderId)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy đơn hàng"));
        if (order.getUser() == null || !order.getUser().getId().equals(currentUser.getId())) {
            throw new IllegalArgumentException("Không tìm thấy đơn hàng");
        }
        if (order.getStatus() != OrderStatus.SHIPPING) {
            throw new IllegalStateException("Chỉ đơn đang giao mới có thể xác nhận đã nhận hàng");
        }
        OrderStatus old = order.getStatus();
        order.setStatus(OrderStatus.DELIVERED);
        if (order.getDeliveredAt() == null) {
            order.setDeliveredAt(LocalDateTime.now());
        }
        releaseReservedStockForPendingPayment(order, old);
        Order saved = orderRepository.save(order);
        notificationService.sendOrderStatusChangedEmail(saved.getRecipientEmail(), saved.getOrderCode(), old, OrderStatus.DELIVERED);
        return saved;
    }

    @Transactional
    public Order requestRefundWithUpload(Long orderId, String reason, MultipartFile[] evidenceImages) {
        if (reason == null || reason.isBlank()) {
            throw new IllegalArgumentException("refund reason is required");
        }
        if (reason.length() > 500) {
            throw new IllegalArgumentException("refund reason must be <= 500 characters");
        }
        List<String> evidenceUrls = saveRefundEvidenceFiles(orderId, evidenceImages);
        RefundRequest refundRequest = new RefundRequest();
        refundRequest.setReason(reason.trim());
        refundRequest.setEvidenceImageUrls(evidenceUrls);
        return requestRefund(orderId, refundRequest);
    }

    @Transactional
    public SepayOrderProcessResult markSepayPaidAndMoveToPendingConfirmation(String orderCode, String transactionId, BigDecimal transferAmount, String rawPayload) {
        Order order = orderRepository.findWithItemsByOrderCode(orderCode)
                .orElseThrow(() -> new IllegalArgumentException("Order not found"));

        if (order.getStatus() != OrderStatus.PENDING_PAYMENT) {
            return SepayOrderProcessResult.IGNORED_STATUS;
        }
        if (transferAmount.compareTo(order.getTotal()) < 0) {
            return SepayOrderProcessResult.IGNORED_AMOUNT;
        }

        Payment payment = paymentRepository.findByOrder_Id(order.getId())
                .orElseThrow(() -> new IllegalStateException("Payment record missing"));
        payment.setTransactionId(transactionId);
        payment.setStatus(PaymentStatus.SUCCESS);
        payment.setPaidAt(LocalDateTime.now());
        payment.setRawResponse(rawPayload);
        paymentRepository.save(payment);

        order.setStatus(OrderStatus.PENDING_CONFIRMATION);
        Order saved = orderRepository.save(order);
        if (saved.getVoucher() != null) {
            voucherService.incrementUsageIfPresent(saved.getVoucher());
        }
        // Chỉ gửi 1 email duy nhất cho sự kiện thanh toán SePay thành công
        // (sendSepayPaymentConfirmedEmail đã bao hàm thông báo status change)
        notificationService.sendSepayPaymentConfirmedEmail(saved.getRecipientEmail(), saved.getOrderCode());
        return SepayOrderProcessResult.MOVED_TO_PENDING_CONFIRMATION;
    }

    @Value("${app.order.payment-reminder-minutes-before-expiry:5}")
    private int paymentReminderMinutesBeforeExpiry;

    @Transactional
    public int sendPaymentReminderForExpiringOrders() {
        // Tìm các đơn PENDING_PAYMENT đã tồn tại đủ lâu để sắp hết hạn (timeout - reminderMinutes)
        // và chưa được nhắc trước đó (dùng note đánh dấu)
        int reminderWindowMinutes = pendingPaymentTimeoutMinutes - paymentReminderMinutesBeforeExpiry;
        if (reminderWindowMinutes <= 0) {
            return 0;
        }
        LocalDateTime threshold = LocalDateTime.now().minusMinutes(reminderWindowMinutes);
        List<Order> candidates = orderRepository.findByStatusAndCreatedAtBefore(OrderStatus.PENDING_PAYMENT, threshold);
        int count = 0;
        String marker = "[reminder-sent]";
        for (Order order : candidates) {
            String note = order.getNote();
            if (note != null && note.contains(marker)) {
                continue;
            }
            LocalDateTime expireAt = order.getCreatedAt() != null
                    ? order.getCreatedAt().plusMinutes(pendingPaymentTimeoutMinutes)
                    : null;
            notificationService.sendPaymentReminderEmail(order.getRecipientEmail(), order.getOrderCode(), expireAt);
            order.setNote(((note == null || note.isBlank()) ? "" : note + " ") + marker);
            orderRepository.save(order);
            count++;
        }
        return count;
    }

    @Transactional
    public int autoCancelExpiredPendingPaymentOrders() {
        LocalDateTime timeoutAt = LocalDateTime.now().minusMinutes(pendingPaymentTimeoutMinutes);
        List<Order> expiredOrders = orderRepository.findByStatusAndCreatedAtBefore(OrderStatus.PENDING_PAYMENT, timeoutAt);
        int changed = 0;
        for (Order order : expiredOrders) {
            OrderStatus old = order.getStatus();
            order.setStatus(OrderStatus.CANCELLED);
            order.setCancelReason("Auto-cancel after payment timeout");
            releaseReservedStockForPendingPayment(order, old);
            markPaymentFailedIfPending(order);
            orderRepository.save(order);
            notificationService.sendOrderStatusChangedEmail(order.getRecipientEmail(), order.getOrderCode(), old, OrderStatus.CANCELLED);
            changed++;
        }
        return changed;
    }

    @Transactional(readOnly = true)
    public List<Order> getMyOrders() {
        User currentUser = currentUserService.requireCurrentUser();
        List<Order> orders = orderRepository.findByUser_IdOrderByCreatedAtDesc(currentUser.getId());
        orders.forEach(this::enrichSepayDisplayInfo);
        return orders;
    }

    @Transactional(readOnly = true)
    public Order getMyOrder(Long orderId) {
        User currentUser = currentUserService.requireCurrentUser();
        Order order = orderRepository.findWithItemsById(orderId)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy đơn hàng"));
        if (order.getUser() == null || !order.getUser().getId().equals(currentUser.getId())) {
            throw new IllegalArgumentException("Không tìm thấy đơn hàng");
        }
        enrichSepayDisplayInfo(order);
        return order;
    }

    private void enrichSepayDisplayInfo(Order order) {
        if (order.getPaymentMethod() != PaymentMethod.SEPAY || order.getStatus() != OrderStatus.PENDING_PAYMENT) {
            return;
        }
        sepayGatewayCheckoutService.enrichCheckoutData(order);
    }

    private void reserveStockForPendingPayment(Order order) {
        List<OrderItem> reservedItems = new java.util.ArrayList<>();
        for (OrderItem item : order.getItems()) {
            boolean reserved = productExternalService.reserveStock(item.getProduct().getId(), item.getQuantity());
            if (!reserved) {
                for (OrderItem reservedItem : reservedItems) {
                    productExternalService.releaseStock(reservedItem.getProduct().getId(), reservedItem.getQuantity());
                }
                throw new OutOfStockException("Cannot reserve stock for product " + item.getProduct().getId());
            }
            reservedItems.add(item);
        }
    }

    private void releaseReservedStockForPendingPayment(Order order, OrderStatus previousStatus) {
        // Release reserved stock for all payment methods when cancelling/delivered/refund
        if (EnumSet.of(OrderStatus.PENDING_PAYMENT, OrderStatus.PENDING_CONFIRMATION, OrderStatus.PROCESSING, OrderStatus.SHIPPING)
                .contains(previousStatus)) {
            for (OrderItem item : order.getItems()) {
                productExternalService.releaseStock(item.getProduct().getId(), item.getQuantity());
            }
        }
    }

    private void reduceInventoryForOrder(Order order) {
        // Cả COD lẫn SePay đều reduce stock thực ở PROCESSING.
        // Trước đây SePay bỏ qua → stock leak khi đơn DELIVERED (chỉ release reserved, không bao giờ giảm stock thực).
        try {
            for (OrderItem item : order.getItems()) {
                inventoryService.reduceStock(item.getProduct().getId(), item.getQuantity());
            }
        } catch (OptimisticLockException | OutOfStockException ex) {
            throw ex;
        }
    }

    private void rollbackInventoryForOrder(Order order, OrderStatus previousStatus) {
        // Rollback khi cancel/refund từ PROCESSING trở đi cho cả COD lẫn SePay (đã reduce stock thực).
        if (previousStatus == OrderStatus.PROCESSING) {
            for (OrderItem item : order.getItems()) {
                inventoryService.restoreStock(item.getProduct().getId(), item.getQuantity());
            }
        }
    }

    @Transactional
    public void rollbackVoucherUsage(Order order, OrderStatus previousStatus) {
        // Rollback voucher when cancelling PENDING_CONFIRMATION or PROCESSING (ARCHITECTURE §8.5)
        if (EnumSet.of(OrderStatus.PENDING_CONFIRMATION, OrderStatus.PROCESSING).contains(previousStatus)
                && order.getVoucher() != null) {
            voucherService.rollbackUsageIfPresent(order.getVoucher());
        }
    }

    private void markPaymentFailedIfPending(Order order) {
        Payment payment = paymentRepository.findByOrder_Id(order.getId()).orElse(null);
        if (payment == null) {
            return;
        }
        if (payment.getStatus() == PaymentStatus.PENDING) {
            payment.setStatus(PaymentStatus.FAILED);
            paymentRepository.save(payment);
        }
    }

    private void validateTransition(OrderStatus oldStatus, OrderStatus newStatus) {
        if (oldStatus == newStatus) {
            return;
        }
        if (!allowedTransitions(oldStatus).contains(newStatus)) {
            throw new IllegalStateException("Invalid order status transition: " + oldStatus + " -> " + newStatus);
        }
    }

    private Set<OrderStatus> allowedTransitions(OrderStatus from) {
        return switch (from) {
            case PENDING_PAYMENT -> Set.of(OrderStatus.PENDING_CONFIRMATION, OrderStatus.CANCELLED);
            case PENDING_CONFIRMATION -> Set.of(OrderStatus.PROCESSING, OrderStatus.CANCELLED);
            case PROCESSING -> Set.of(OrderStatus.SHIPPING, OrderStatus.CANCELLED);
            case SHIPPING -> Set.of(OrderStatus.DELIVERED);
            case DELIVERED -> Set.of(OrderStatus.REFUND_REQUESTED);
            case REFUND_REQUESTED -> Set.of(OrderStatus.RETURN_REFUND, OrderStatus.REFUND_REJECTED);
            case CANCELLED, RETURN_REFUND, REFUND_REJECTED -> Set.of();
        };
    }

    private List<String> saveRefundEvidenceFiles(Long orderId, MultipartFile[] evidenceImages) {
        List<String> urls = new ArrayList<>();
        if (evidenceImages == null || evidenceImages.length == 0) {
            return urls;
        }
        if (evidenceImages.length > 3) {
            throw new IllegalArgumentException("maximum 3 evidence images are allowed");
        }

        Path refundDir = Path.of(uploadDir, "refunds", String.valueOf(orderId));
        try {
            Files.createDirectories(refundDir);
        } catch (IOException ex) {
            throw new IllegalStateException("Cannot create upload directory");
        }

        for (MultipartFile file : evidenceImages) {
            if (file == null || file.isEmpty()) {
                continue;
            }
            if (file.getSize() > 5L * 1024 * 1024) {
                throw new IllegalArgumentException("each evidence image must be <= 5MB");
            }
            String original = file.getOriginalFilename() == null ? "" : file.getOriginalFilename().toLowerCase();
            if (!(original.endsWith(".jpg") || original.endsWith(".jpeg") || original.endsWith(".png") || original.endsWith(".webp"))) {
                throw new IllegalArgumentException("only jpg, png, webp images are supported");
            }

            String extension = original.contains(".") ? original.substring(original.lastIndexOf('.')) : ".jpg";
            String fileName = "refund-" + UUID.randomUUID() + extension;
            Path target = refundDir.resolve(fileName);
            try {
                Files.copy(file.getInputStream(), target, StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException ex) {
                throw new IllegalStateException("Cannot store evidence image");
            }
            urls.add("/uploads/refunds/" + orderId + "/" + fileName);
        }
        return urls;
    }

    private String generateOrderCode() {
        return "ORD-" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss")) +
                "-" + UUID.randomUUID().toString().substring(0, 6).toUpperCase();
    }

    @Transactional(readOnly = true)
    public List<AdminOrderResponse> getOrdersForAdmin(OrderStatus statusFilter, String keyword, LocalDateTime fromDate, LocalDateTime toDate) {
        // Bắt đầu từ tập rộng nhất rồi filter trong-memory để hỗ trợ kết hợp filter (status + keyword + date)
        List<Order> orders;
        if (statusFilter != null) {
            orders = orderRepository.findByStatusOrderByCreatedAtDesc(statusFilter);
        } else {
            orders = orderRepository.findAllByOrderByCreatedAtDesc();
            // Trang Staff không cần thấy PENDING_PAYMENT (đơn chưa thanh toán)
            // Khách thanh toán xong sẽ tự chuyển sang PENDING_CONFIRMATION
            orders = orders.stream()
                    .filter(o -> o.getStatus() != OrderStatus.PENDING_PAYMENT)
                    .collect(Collectors.toList());
        }

        if (keyword != null && !keyword.isBlank()) {
            String kw = keyword.trim().toLowerCase();
            orders = orders.stream().filter(order -> {
                String code = order.getOrderCode() == null ? "" : order.getOrderCode().toLowerCase();
                String username = order.getUser() != null && order.getUser().getUsername() != null
                        ? order.getUser().getUsername().toLowerCase() : "";
                return code.contains(kw) || username.contains(kw);
            }).collect(Collectors.toList());
        }

        if (fromDate != null) {
            orders = orders.stream()
                    .filter(o -> o.getCreatedAt() != null && !o.getCreatedAt().isBefore(fromDate))
                    .collect(Collectors.toList());
        }
        if (toDate != null) {
            orders = orders.stream()
                    .filter(o -> o.getCreatedAt() != null && !o.getCreatedAt().isAfter(toDate))
                    .collect(Collectors.toList());
        }

        return orders.stream().map(AdminOrderResponse::from).toList();
    }

    @Transactional
    public Order rejectRefund(Long orderId, String rejectNote) {
        Order order = orderRepository.findWithItemsById(orderId)
                .orElseThrow(() -> new IllegalArgumentException("Order not found"));
        if (order.getStatus() != OrderStatus.REFUND_REQUESTED) {
            throw new IllegalStateException("Chi don o trang thai REFUND_REQUESTED moi co the tu choi hoan tien");
        }
        if (rejectNote == null || rejectNote.isBlank()) {
            throw new IllegalArgumentException("Ly do tu choi hoan tien la bat buoc");
        }
        order.setStatus(OrderStatus.REFUND_REJECTED);
        order.setRefundRejectNote(rejectNote.trim());
        Order saved = orderRepository.save(order);
        notificationService.sendRefundRejectedEmail(saved.getRecipientEmail(), saved.getOrderCode(), rejectNote.trim());
        return saved;
    }

    public enum SepayOrderProcessResult {
        MOVED_TO_PENDING_CONFIRMATION,
        IGNORED_STATUS,
        IGNORED_AMOUNT
    }
}
