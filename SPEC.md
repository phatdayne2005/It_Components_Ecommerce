# TechParts Ecommerce - Công Việc Cần Làm

> File này ghi nhận toàn bộ công việc cần thực hiện, trạng thái hiện tại, và tiến độ.
> Cập nhật sau mỗi phiên làm việc.
>
> **File tham chiếu quan trọng:** `ARCHITECTURE.md` — chứa toàn bộ kiến trúc, Q&A tổng hợp từ luồng 1-10, và mọi quyết định thiết kế.

---

## MỤC LỤC

1. [Công Việc Hoàn Thành](#1-công-việc-hoàn-thành)
2. [Công Việc Đang Thực Hiện](#2-công-việc-đang-thực-hiện)
3. [Công Việc Chưa Làm](#3-công-việc-chưa-làm)
4. [Chi Tiết Luồng Nghiệp Vụ](#4-chi-tiết-luồng-nghiệp-vụ)
5. [Q\&A - Câu Hỏi và Câu Trả Lời](#5-qa---câu-hỏi-và-câu-trả-lời)
6. [Danh Sách Công Việc (TODO)](#6-danh-sách-công-việc-todo)
7. [Ghi Chú](#7-ghi-chú)

---

## 1. CÔNG VIỆC HOÀN THÀNH

| # | Công việc | Ghi chú |
|---|---|---|
| ✅ | Xây dựng project Spring Boot + Thymeleaf cơ bản | Entity, Repository, Service, Controller đầy đủ |
| ✅ | Authentication (JWT) | Login, Register, Spring Security |
| ✅ | Product Catalog | Tìm kiếm, lọc, phân trang, chi tiết sản phẩm |
| ✅ | Shopping Cart | Guest localStorage + Server-side, merge on login |
| ✅ | Checkout | COD + SePay gateway |
| ✅ | Order Management | Full workflow (PENDING_PAYMENT → DELIVERED) |
| ✅ | Refund Request | Customer upload ảnh refund |
| ✅ | Reviews | CRUD cơ bản (tạo, xem, sửa, xóa) |
| ✅ | Wishlist | Add/remove products |
| ✅ | Admin Dashboard | CRUD categories, brands, products, vouchers |
| ✅ | GearVN Import | Import sản phẩm từ GearVN |
| ✅ | Email Notifications (cơ bản) | Gửi email khi đơn hàng thay đổi trạng thái |
| ✅ | Customer markDelivered endpoint | POST /api/v1/orders/{id}/mark-delivered — fix bug 403 do PUT /status STAFF-only |
| ✅ | Customer requestRefund ownership check | OrderService.requestRefund — chặn user truy cập đơn của user khác |
| ✅ | Voucher atomic increment | VoucherRepository.incrementUsedCountAtomic — chống race khi nhiều order cùng dùng voucher cuối |
| ✅ | Staff filter combinations | getOrdersForAdmin nay kết hợp được status + keyword + date cùng lúc |

---

## 2. CÔNG VIỆC ĐANG THỰC HIỆN

| # | Công việc | Trạng thái | Ghi chú |
|---|---|---|---|
| 🔄 | Luồng 9 - Refund nâng cao | Đang bàn | Xem chi tiết [Luồng 9](#luồng-9-refund-nâng-cao) |
| 🔄 | Luồng 10 - Reviews nâng cao | Đang bàn | Xem chi tiết [Luồng 10](#luồng-10-reviews-nâng-cao) |

---

## 3. CÔNG VIỆC CHƯA LÀM

### 3.1 — Email Notifications

| # | Công việc | Chi tiết |
|---|---|---|
| ✅ | Email thông báo từ chối refund | NotificationService.sendRefundRejectedEmail (HTML + UTF-8) |
| ✅ | Email gửi form hoàn tiền | NotificationService.sendReturnRefundFormEmail |
| ✅ | Email nhắc thanh toán | NotificationService.sendPaymentReminderEmail + CronjobService chạy mỗi phút |
| ✅ | Email xác nhận đơn hàng | NotificationService.sendOrderConfirmationEmail (gọi tại OrderService.checkout) |
| ✅ | Email thông báo giao hàng | NotificationService.sendShippingNotificationEmail (gọi khi status SHIPPING) |
| ✅ | Encoding UTF-8 + HTML | Toàn bộ email đã chuyển sang MimeMessage UTF-8, có HTML wrapper |

### 3.2 — Các chức năng khác

| # | Công việc | Chi tiết |
|---|---|---|
| ✅ | Trang Staff (tab "Đơn hàng") | staff.html + staff.js — duyệt, giao, duyệt refund, từ chối refund |
| ✅ | Đánh giá sản phẩm (Luồng 10) | ReviewService theo orderId, deleteByOrderId khi refund |
| ✅ | Nút "Trả hàng/Hoàn tiền" hiện ở SHIPPING | my-orders.js — hiện ở SHIPPING + DELIVERED |
| ✅ | Nút "Đã nhận hàng" ở SHIPPING | POST /api/v1/orders/{id}/mark-delivered (customer-only endpoint) |
| ✅ | Refund nâng cao (Luồng 9) | requestRefundWithUpload + reject flow |
| ✅ | Block checkout khi có merge warnings | checkout.js disable submit button đến khi user xác nhận đã kiểm tra |
| ✅ | Toast message chi tiết từ server | CartService.updateQuantity nay trả về tên sản phẩm + stock thực tế |

---

## 4. CHI TIẾT LUỒNG NGHIỆP VỤ

---

### LUỒNG 9: REFUND NÂNG CAO

#### Mô tả:
Khi khách hàng muốn trả hàng/hoàn tiền sau khi nhận hàng.

#### Trạng thái đơn hàng (OrderStatus):
```
PENDING_PAYMENT
PENDING_CONFIRMATION
PROCESSING
SHIPPING ──────────────► DELIVERED
  │                         │
  │                         ▼
  │                   REFUND_REQUESTED
  │                         │
  │                         ▼
  │                   REFUND_REJECTED
  │                         │
  │                         ▼
  │                   RETURN_REFUND
  │
  ▼
CANCELLED
```

#### Chi tiết từng bước:

**Bước 1: Khách xác nhận đã nhận hàng**
- Trạng thái: **SHIPPING**
- Hiển thị 2 nút: **[Đã nhận hàng]** và **[Trả hàng/Hoàn tiền]**
- Click "Đã nhận hàng" → Trạng thái chuyển → **DELIVERED**
- Click "Trả hàng/Hoàn tiền" → Chuyển luôn sang **REFUND_REQUESTED**

**Bước 2: Khách yêu cầu hoàn tiền**
- Trạng thái: **SHIPPING** 
- Click "Trả hàng/Hoàn tiền" → Mở modal upload ảnh:
  ```
  ┌─────────────────────────────────────────────┐
  │  Yêu cầu hoàn tiền                        │
  │  ─────────────────────────────────────────  │
  │  Lý do hoàn tiền: [Dropdown ▼]             │
  │  Mô tả chi tiết:                           │
  │  ┌─────────────────────────────────────┐   │
  │  │                                     │   │
  │  └─────────────────────────────────────┘   │
  │                                             │
  │  Hình ảnh minh chứng:                      │
  │  ┌─────────────────────────────────────┐   │
  │  │     📁 Kéo thả hoặc click để tải    │   │
  │  └─────────────────────────────────────┘   │
  │  (Tối đa 3 ảnh, mỗi ảnh tối đa 5MB)       │
  │                                             │
  │  [Hủy]              [Gửi yêu cầu]         │
  └─────────────────────────────────────────────┘
  ```

**Bước 3: Staff xử lý refund**
- Xem yêu cầu refund trên trang staff
- Có thể: Duyệt hoặc Từ chối

**Bước 4: Thông báo cho khách**
- Duyệt → Đơn chuyển → **RETURN_REFUND**
- Từ chối → Đơn chuyển → **REFUND_REJECTED**

**Bước 5: Hoàn tiền**
- Sau khi đơn chuyển sang RETURN_REFUND
- Hoàn tiền thủ công sau khi nhận được thông tin trong link google forms từ khách hàng.

#### Thay đổi với Reviews:
- Khi đơn chuyển sang **REFUND_REQUESTED** → Xóa tất cả reviews liên quan đến các item trong đơn đó

#### API Endpoints cần thêm/sửa:

| Method | Endpoint | Mô tả |
|---|---|---|
| PUT | `/api/v1/orders/{id}/status` | Cập nhật trạng thái (bao gồm DELIVERED, REFUND_REQUESTED) |
| POST | `/api/v1/orders/{id}/refund` | Gửi yêu cầu refund (không cần ảnh) |
| POST | `/api/v1/orders/{id}/refund/upload` | Upload ảnh refund (đã có) |

#### Frontend cần sửa:

**`my-orders.html`** — Trạng thái SHIPPING:
```html
<!-- Khi status = SHIPPING -->
<div class="order-actions">
    <button onclick="markDelivered(orderId)">[Đã nhận hàng]</button>
    <button onclick="showRefundModal(orderId)">[Trả hàng/Hoàn tiền]</button>
</div>
```

**`my-orders.js`** — Xử lý:
```javascript
// Mark as delivered
async function markDelivered(orderId) {
    await fetch(`/api/v1/orders/${orderId}/status`, {
        method: 'PUT',
        body: JSON.stringify({ status: 'DELIVERED' })
    });
    location.reload();
}

// Show refund modal
async function showRefundModal(orderId) {
    // Mở modal, yêu cầu lý do + upload ảnh
    // Gửi refund request
}
```

---

### LUỒNG 10: REVIEWS NÂNG CAO

#### Mô tả:
Khách hàng đánh giá sản phẩm sau khi mua và nhận hàng.

#### Quy tắc:

| # | Quy tắc | Chi tiết |
|---|---|---|
| 1 | Ai được review | User có ít nhất 1 Order (của sản phẩm đó) với status = **DELIVERED** |
| 2 | Mỗi đơn mua | 1 review cho mỗi sản phẩm trong đơn |
| 3 | Mua nhiều cái trong 1 đơn | 1 review cho sản phẩm (không phải 1 review cho mỗi cái) |
| 4 | Mua nhiều đơn | Mỗi đơn DELIVERED → 1 review riêng cho sản phẩm đó |
| 5 | Một user có thể có nhiều review cho cùng 1 sản phẩm | Nếu mua nhiều lần qua nhiều đơn |
| 6 | Chỉnh sửa | **Cho phép** chỉnh sửa review |
| 7 | Xóa | **Cho phép** xóa review |
| 8 | Refund | Khi đơn chuyển sang **REFUND_REQUESTED** → **Xóa** review của các item trong đơn đó |

#### Sơ đồ Review:

```
Order #A (DELIVERED) → Item: CPU i5-12400 → Review #1 (user: A, product: CPU)
Order #B (DELIVERED) → Item: CPU i5-12400 → Review #2 (user: A, product: CPU)
Order #C (DELIVERED) → Item: CPU i5-12400 x3 → Review #3 (user: B, product: CPU)

→ User A có 2 review cho CPU i5-12400 (vì mua 2 lần qua 2 đơn)
→ User B có 1 review cho CPU i5-12400 (mua 1 lần, 3 cái trong đơn)
→ Total: 3 review cho CPU i5-12400
```

#### Nút "ĐÁNH GIÁ" trên my-orders:

```
┌─────────────────────────────────────────────────────┐
│  Đơn #123 - DELIVERED - 02/05/2026                 │
│  ─────────────────────────────────────────────────  │
│                                                     │
│  ┌─────────────────────────────────────────────────┐│
│  │  CPU Intel Core i5-12400 x2                    ││
│  │  Bảo hành: 24 tháng                            ││
│  │                                                 ││
│  │  [Chi tiết]  [ĐÁNH GIÁ]  [Mua lại]           ││
│  │                                                 ││
│  │  ┌─ Review của bạn ──────────────────────────┐││
│  │  │  ★★★★☆ (4/5)                              │││
│  │  │  "Sản phẩm tốt, giao nhanh"               │││
│  │  │  [Sửa đánh giá]  [Xóa đánh giá]          │││
│  │  └────────────────────────────────────────────┘││
│  └─────────────────────────────────────────────────┘│
│                                                     │
│  ┌─────────────────────────────────────────────────┐│
│  │  RAM Kingston 16GB DDR4 x1                      ││
│  │  Bảo hành: 36 tháng                            ││
│  │                                                 ││
│  │  [Chi tiết]  [ĐÁNH GIÁ]  [Mua lại]           ││
│  │  (Chưa có đánh giá)                           ││
│  └─────────────────────────────────────────────────┘│
└─────────────────────────────────────────────────────┘
```

#### Form review inline:

```
┌─ Review Inline ─────────────────────────────────────┐
│                                                      │
│  Bạn đánh giá sản phẩm này như thế nào?           │
│                                                      │
│  ★ ★ ★ ★ ★                                          │
│  (hover: thay đổi màu sao, click: chọn rating)     │
│                                                      │
│  Tiêu đề đánh giá:                                 │
│  ┌────────────────────────────────────────────────┐ │
│  │ Sản phẩm tốt, đáng mua                        │ │
│  └────────────────────────────────────────────────┘ │
│                                                      │
│  Nội dung đánh giá:                                 │
│  ┌────────────────────────────────────────────────┐ │
│  │ Dùng được 2 tháng, ổn định, không lỗi gì.    │ │
│  │ Giao hàng nhanh, đóng gói cẩn thận.          │ │
│  └────────────────────────────────────────────────┘ │
│                                                      │
│  [Hủy]                    [Gửi đánh giá]           │
└──────────────────────────────────────────────────────┘
```

#### Nút "Trả hàng/Hoàn tiền" + "Đã nhận hàng" (SHIPPING):

```
┌─────────────────────────────────────────────────────┐
│  Đơn #456 - SHIPPING - 01/05/2026                  │
│  ─────────────────────────────────────────────────  │
│                                                     │
│  ┌─────────────────────────────────────────────────┐│
│  │  CPU Intel Core i5-12400 x2                    ││
│  │  Đơn vị vận chuyển: GHTK - Đang giao          ││
│  │                                                 ││
│  │  [Đã nhận hàng]  [Trả hàng/Hoàn tiền]        ││
│  └─────────────────────────────────────────────────┘│
│                                                     │
└─────────────────────────────────────────────────────┘
```

#### Bảng trạng thái hiển thị:

| Trạng thái | Các nút/Action |
|---|---|
| PENDING_PAYMENT | Chỉ thông tin, không action item |
| PENDING_CONFIRMATION | Chỉ thông tin |
| PROCESSING | Chỉ thông tin |
| **SHIPPING** | **[Đã nhận hàng]** + **[Trả hàng/Hoàn tiền]** |
| **DELIVERED** | **[Chi tiết]** + **[ĐÁNH GIÁ]** + **[Mua lại]** |
| REFUND_REQUESTED | Review đã bị XÓA (nếu đã viết) |
| REFUND_REJECTED | [Chi tiết] + [Yêu cầu hoàn tiền lại] |
| RETURN_REFUND | [Chi tiết] |
| CANCELLED | [Chi tiết] |

#### API Endpoints:

| Method | Endpoint | Mô tả |
|---|---|---|
| GET | `/api/v1/products/{productId}/reviews/me?orderId={orderId}` | Lấy review của user cho sản phẩm này (theo order) |
| POST | `/api/v1/products/{productId}/reviews` | Tạo review mới |
| PUT | `/api/v1/products/{productId}/reviews/{reviewId}` | Cập nhật review |
| DELETE | `/api/v1/products/{productId}/reviews/{reviewId}` | Xóa review |

#### Request body khi tạo/cập nhật review:

```json
POST /api/v1/products/{productId}/reviews
{
    "orderId": 123,
    "rating": 5,
    "title": "Sản phẩm tốt",
    "comment": "Dùng ổn định, giao hàng nhanh..."
}
```

#### Review hiển thị ở đâu:

1. **Trang chi tiết sản phẩm** (`catalog/detail.html`):
   - Hiển thị tất cả review đã duyệt của sản phẩm
   - Rating trung bình, số lượng review
   - Danh sách review với thông tin user, ngày, nội dung

2. **Trang my-orders**:
   - Review của user cho sản phẩm trong đơn đó
   - Có nút Sửa/Xóa

#### Entity Review — Cần thêm trường:

```java
@Entity
public class Review {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "user_id")
    private User user;

    @ManyToOne
    @JoinColumn(name = "product_id")
    private Product product;

    @ManyToOne
    @JoinColumn(name = "order_id")  // ← THÊM MỚI: gắn với order cụ thể
    private Order order;

    private Integer rating;
    private String title;
    private String comment;
    private Boolean approved;
    private LocalDateTime createdAt;
}
```

#### Repository — Cần thêm method:

```java
// ReviewRepository.java
Optional<Review> findByUserIdAndProductIdAndOrderId(Long userId, Long productId, Long orderId);
List<Review> findByProductIdAndApprovedTrueOrderByCreatedAtDesc(Long productId);
List<Review> findByOrderId(Long orderId);
void deleteByOrderId(Long orderId);  // Xóa review khi refund
```

#### Service — Cần thêm logic:

```java
// ReviewService.java
public ReviewDto createOrUpdate(Long productId, Long orderId, Long userId, ReviewRequest request) {
    // 1. Kiểm tra user có order DELIVERED của sản phẩm này không
    // 2. Kiểm tra đã có review cho order này chưa
    // 3. Nếu có → cập nhật
    // 4. Nếu không → tạo mới
}

public void deleteByOrderId(Long orderId) {
    // Xóa review khi đơn chuyển sang REFUND_REQUESTED
}
```

#### Frontend:

**`my-orders.html`** — Thêm cột review inline:
```html
<div class="order-items">
    <!-- Mỗi item -->
    <div class="order-item" data-product-id="123" data-order-id="456">
        <!-- ... product info ... -->
        <div class="item-actions">
            <a href="/products/{slug}">Chi tiết</a>
            <button class="btn-review" onclick="openReviewForm(123, 456)">ĐÁNH GIÁ</button>
            <button class="btn-rebuy" onclick="rebuy(123)">Mua lại</button>
        </div>
        <div class="item-review-section" id="review-123-456">
            <!-- Review inline sẽ render ở đây -->
        </div>
    </div>
</div>
```

**`my-orders.js`** — Xử lý review:
```javascript
// Mở form review
async function openReviewForm(productId, orderId) {
    // Fetch review hiện tại (nếu có)
    // Hiển thị form inline
}

// Gửi review
async function submitReview(productId, orderId) {
    const data = {
        orderId: orderId,
        rating: 5,
        title: document.getElementById('review-title').value,
        comment: document.getElementById('review-comment').value
    };
    await fetch(`/api/v1/products/${productId}/reviews`, {
        method: 'POST',
        headers: buildJsonHeaders(),
        body: JSON.stringify(data)
    });
    // Re-fetch và hiển thị review inline
    await loadReviewForItem(productId, orderId);
}

// Sửa review
async function editReview(productId, reviewId) {
    // Mở form với dữ liệu cũ
}

// Xóa review
async function deleteReview(productId, reviewId) {
    if (confirm('Bạn có chắc muốn xóa đánh giá này?')) {
        await fetch(`/api/v1/products/${productId}/reviews/${reviewId}`, {
            method: 'DELETE'
        });
        // Hiển thị lại nút ĐÁNH GIÁ
    }
}
```

**`catalog/detail.html`** — Phần hiển thị review:
```html
<div id="reviews-section">
    <h3>Đánh giá sản phẩm</h3>
    <div class="reviews-summary">
        ★★★★☆ 4.5/5 - 12 đánh giá
    </div>
    <div id="reviews-list">
        <!-- Render danh sách review -->
    </div>
</div>
```

---

## 5. Q&A - CÂU HỎI VÀ CÂU TRẢ LỜI

> Phần này ghi lại tất cả câu hỏi đã hỏi và câu trả lời trong quá trình phát triển.
> Giúp tránh lạc đường và hiểu đúng yêu cầu.

---

### Q&A LUỒNG 10 — REVIEWS

#### Q1: Mỗi sản phẩm có thể có nhiều review từ 1 user không?

**A:** Có. User có thể mua và đánh giá nhiều lần. Mỗi đơn hàng DELIVERED cho 1 sản phẩm → được viết 1 review riêng.
- Mua CPU ở đơn #A (DELIVERED) → review #1
- Mua CPU ở đơn #B (DELIVERED) → review #2
- User có 2 review cho cùng 1 sản phẩm

#### Q2: Ai được phép viết review?

**A:** User có ít nhất 1 Order với sản phẩm đó và status = **DELIVERED**.
- Nếu mua 2 cái CPU trong 1 đơn hàng → được viết 1 review cho sản phẩm đó (không phải 1 review cho mỗi cái)

#### Q3: Nút "ĐÁNH GIÁ" nằm ở đâu?

**A:** Trên trang "Đơn hàng của tôi" (`my-orders.html`). Khi order status = DELIVERED, mỗi item hiển thị:
- [Chi tiết] [ĐÁNH GIÁ] [Mua lại]
- Click "ĐÁNH GIÁ" → mở form review inline ngay tại chỗ (không popup mới)

#### Q4: User có thể chỉnh sửa review cũ không? Hay chỉ xem?

**A:** Option C: Viết 1 lần, **có thể SỬA** và **có thể XÓA và viết lại**.

#### Q5: Nếu khách nhận hàng rồi, sau đó yêu cầu refund → review có bị xóa không?

**A:** **Review sẽ bị XÓA.** Vì đơn chuyển sang REFUND_REQUESTED → đơn không còn DELIVERED nữa → review bị xóa.

#### Q6: Nút [Trả hàng/Hoàn tiền] hiện ở trạng thái nào?

**A:** Trên trang "Đơn hàng của tôi" (my-orders.html), khi order status = **SHIPPING**:
- 2 nút: [Trả hàng/Hoàn tiền] + [Đã nhận hàng]
- Click "Trả hàng/Hoàn tiền" → mở modal upload ảnh refund

#### Q7: Review gửi đi đâu? Hiển thị ở đâu?

**A:**
- Gửi đi: Lưu vào database
- Frontend re-fetch reviews
- Hiển thị: Trên trang **chi tiết sản phẩm** (`catalog/detail.html`)
- Form review: Inline trên trang **my-orders**

#### Q8: Sau khi gửi review, hiển thị ở đâu?

**A:** Hiển thị **inline ngay dưới item** trong trang my-orders. Sau đó cũng hiển thị trên trang chi tiết sản phẩm.

#### Q9: Có cho phép chỉnh sửa review không?

**A:** **Có**, cho phép chỉnh sửa review đã gửi (không bắt buộc xóa mới sửa được).

---

### Q&A LUỒNG 9 — REFUND

#### Q10: Khi nào khách thấy nút "Trả hàng/Hoàn tiền"?

**A:** Khi order status = **SHIPPING**. Cùng hàng với nút [Đã nhận hàng].

#### Q11: Khi nào khách thấy nút "Đã nhận hàng"?

**A:** Khi order status = **SHIPPING**. Click → chuyển trạng thái sang DELIVERED.

#### Q12: Hoàn tiền xử lý như thế nào?

**A:** Sau khi đơn chuyển sang RETURN_REFUND (staffstaff duyệt refund):
- Hoàn tiền thủ công
- Sau khi nhận được thông tin trong link google forms từ khách hàng

#### Q13: Refund có ảnh hưởng đến review không?

**A:** **Có.** Khi đơn chuyển sang REFUND_REQUESTED → Xóa tất cả reviews liên quan đến các item trong đơn đó.

---

### Q&A LUỒNG 1-4 — GIỎ HÀNG & CHECKOUT

#### Q15: Modal hủy đơn — dùng cách nào?

**A:** Thay `window.prompt()` bằng **modal form đẹp** có trường "Lý do hủy". **Lý do hủy là BẮT BUỘC** — không cho gửi nếu để trống.

#### Q16: Khi khách hủy PENDING_CONFIRMATION, voucher có được hoàn lại không?

**A:** **CÓ** — Khi hủy PENDING_CONFIRMATION hoặc PROCESSING, **hoàn lại voucher** (usedCount - 1) cho khách.

#### Q17: PENDING_PAYMENT (SePay) — khách có thể hủy thủ công không?

**A:** **CÓ** — Khách có thể hủy thủ công trong thời gian chờ thanh toán (thay vì chỉ auto-cancel timeout).

---

### Q&A LUỒNG 9 — REFUND

#### Q18: Lý do từ chối refund có bắt buộc nhập không?

**A:** **BẮT BUỘC.** Staff phải nhập lý do từ chối trong modal form. Server trả lỗi 400 nếu gửi mà không có lý do. Email gửi khách phải kèm `refundRejectNote`.

#### Q19: Refund form email (Google Forms) gửi cho khách gồm những trường nào?

**A:** Khi duyệt refund (RETURN_REFUND), gửi email kèm link Google Forms. Form gồm:

| Trường | Mô tả |
|---|---|
| Họ và tên | Người nhận tiền |
| Số điện thoại | Liên hệ |
| Địa chỉ lấy hàng | Địa chỉ để staff đến lấy lại đơn hàng |
| Thời gian có thể nhận | Thời gian staff có thể đến lấy hàng |
| Số tài khoản | STK ngân hàng để staff chuyển khoản |
| Tên ngân hàng | Ngân hàng của khách |
| Ghi chú | Thông tin bổ sung |

---

### Q&A KHÁC

#### Q14: Nút [Trả hàng/Hoàn tiền] thay vì hiện ở DELIVERED, giờ hiện ở SHIPPING?

**A:** Đúng. [Trả hàng/Hoàn tiền] + [Đã nhận hàng] hiện ở **SHIPPING**. Nút [ĐÁNH GIÁ] hiện ở **DELIVERED**.

---

### Q&A STAFF PAGE

#### Q20: Staff page filter theo những tiêu chí nào?

**A:** Trang Staff (tab "Đơn hàng") hỗ trợ filter:

| Tiêu chí | Chi tiết |
|---|---|
| Trạng thái | Tất cả / Chờ xác nhận / Đang xử lý / Đang giao / Đã giao / Refund |
| Theo ngày | Từ ngày → Đến ngày |
| Theo mã đơn | Tìm theo orderCode |
| Theo khách hàng | Tìm theo tên/username |

#### Q21: Giao diện duyệt refund gồm những gì?

**A:** Khi xem đơn REFUND_REQUESTED:

| Thành phần | Chi tiết |
|---|---|
| Lý do khách yêu cầu | Hiển thị `refundReason` |
| Ảnh minh chứng | Danh sách ảnh, click để xem lớn |
| Nút "Chấp nhận hoàn tiền" | Chuyển → RETURN_REFUND |
| Nút "Từ chối" | Bắt buộc nhập lý do từ chối (refundRejectNote) |

---

## 6. DANH SÁCH CÔNG VIỆC (TODO)

### Priority 1 (Cần làm ngay)

- [x] **Luồng 8.1**: Thêm nút "Đã nhận hàng" + "Trả hàng/Hoàn tiền" ở trạng thái **SHIPPING** (thay vì DELIVERED)
- [x] **Luồng 8.2**: Xử lý action "Đã nhận hàng" → chuyển status → DELIVERED
- [x] **Luồng 8.3**: Xử lý action "Trả hàng/Hoàn tiền" (SHIPPING) → mở modal upload ảnh → chuyển → REFUND_REQUESTED
- [x] **Luồng 8.4**: Thay `window.prompt()` hủy đơn bằng **modal form** với trường "Lý do hủy" **BẮT BUỘC**
- [x] **Luồng 8.5**: Khách hủy thủ công PENDING_PAYMENT (SePay)
- [x] **Luồng 8.6**: Hoàn voucher khi hủy PENDING_CONFIRMATION/PROCESSING
- [x] **Luồng 10.1**: Thêm trường `order_id` vào entity `Review`
- [x] **Luồng 10.2**: Cập nhật ReviewRepository với các method mới (findByUserIdAndProductIdAndOrderId, findByOrderId, deleteByOrderId)
- [x] **Luồng 10.3**: Cập nhật ReviewService — kiểm tra quyền theo order, tạo/sửa/xóa review
- [x] **Luồng 10.4**: Cập nhật ReviewApiController — thêm orderId vào request, hỗ trợ 1 user có nhiều review cho 1 sản phẩm (mỗi đơn = 1 review)
- [x] **Luồng 10.5**: Cập nhật my-orders.html — thêm nút ĐÁNH GIÁ (DELIVERED) + review inline
- [x] **Luồng 10.6**: Cập nhật my-orders.js — xử lý form review inline, Sửa/Xóa review
- [x] **Luồng 10.7**: Khi refund (REFUND_REQUESTED) → xóa review liên quan đến order đó

### Priority 2 (Làm sau)

- [x] **Staff API**: `PUT /api/v1/orders/{id}/refund/reject` — từ chối refund (bắt buộc kèm rejectNote)
- [x] **Staff API**: `GET /api/v1/orders/admin/list` — danh sách đơn hàng với filter (trạng thái, ngày, mã đơn, khách hàng)
- [x] **Staff Page**: Tạo trang Staff (`/staff`) với tab "Đơn hàng" (filter: trạng thái, ngày, mã đơn, khách hàng)
- [x] **Staff Page**: Giao diện duyệt refund (lý do, ảnh, nút Chấp nhận/Từ chối)
- [x] **Email 1**: Email thông báo từ chối refund (Staff gửi, kèm refundRejectNote, **bắt buộc nhập lý do**)
- [x] **Block merge warnings**: Block checkout khi có cảnh báo merge cart
- [x] **Toast chi tiết**: Hiển thị message chi tiết từ server trong toast (changeQty error)

### Priority 3 (Tốt hơn nếu có)

- [ ] Trang quản lý refund cho admin
- [ ] Dashboard thống kê reviews cho admin
- [ ] Phê duyệt reviews tự động (hoặc manual approve)

---

## 7. GHI CHÚ

- **Ngày tạo**: 04/05/2026
- **Người tạo**: TechParts Dev Team (Huy)
- **Cập nhật lần cuối**: 04/05/2026 — pass review/bug-fix sweep

## 8. CHANGELOG (review/bug-fix sweep 04/05/2026)

### Bugs đã fix

| # | Vùng | Mô tả ngắn | File:line |
|---|---|---|---|
| B1 | Customer / Security | `PUT /api/v1/orders/*/status` chỉ STAFF/ADMIN gọi được nên nút "Đã nhận hàng" của khách bị 403. Tách endpoint riêng `POST /mark-delivered` cho customer (có ownership check). | OrderService.markDeliveredByCustomer, OrderApiController, my-orders.js |
| B2 | Refund | `requestRefund()` thiếu ownership check — user khác có thể yêu cầu hoàn tiền đơn của người khác. | OrderService.requestRefund |
| B3 | Refund | Cho phép requestRefund từ REFUND_REJECTED (đúng theo flow "Yêu cầu hoàn tiền lại"). Reset refundRejectNote khi yêu cầu lại. | OrderService.requestRefund |
| B4 | Refund modal UI | Thông báo "Vui lòng chọn lý do" hiển thị nhầm element của lỗi ảnh. Tách `refundReasonError`. | my-orders.html, my-orders.js |
| B5 | Email | SimpleMailMessage không UTF-8 → mất dấu tiếng Việt. Chuyển sang MimeMessage + UTF-8 + HTML body. | NotificationService |
| B6 | Email | Thiếu email confirmation, payment reminder, shipping. Đã wire đầy đủ + cronjob nhắc thanh toán. | NotificationService, OrderService, CronjobService |
| B7 | Voucher | Race condition khi 2 đơn cùng tiêu nốt voucher cuối. Chuyển sang atomic UPDATE qua JPQL. | VoucherRepository, VoucherService |
| B8 | Cart | Thông báo lỗi `updateQuantity` chung chung, không rõ stock còn lại / tên sản phẩm. | CartService.updateQuantity |
| B9 | Checkout | Merge cart cảnh báo nhưng vẫn cho checkout. Block nút "Đặt hàng" cho đến khi user bấm "Tôi đã kiểm tra & đồng ý". | checkout.js |
| B10 | Staff filter | `getOrdersForAdmin` chỉ áp 1 trong các filter (status XOR keyword XOR date). Đã kết hợp được cả 3 cùng lúc. | OrderService.getOrdersForAdmin |
| B11 | Staff filter dropdown | Thiếu PENDING_PAYMENT, REFUND_REJECTED, RETURN_REFUND. | staff.html |
| B12 | Refund evidence | `String.join` throw NPE khi list null. Đã null-check. | OrderService.requestRefund |
| B13 | Wishlist trang chủ | Nút yêu thích trên trang chủ gửi POST `/api/v1/wishlist/products/${p.id}` (literal string) → 400. Thiếu prefix `th:` ở `data-product-id` và `data-product-name` nên Thymeleaf không nội suy. | `templates/index.html:147-148` |
| F1 (feature) | Checkout — chọn nguồn SĐT/Email | Thêm radio "Dùng thông tin cá nhân" / "Nhập thủ công" tại trang thanh toán. Phone vẫn `@NotBlank` ở DTO + `required` ở form. Profile mode lock field nào có sẵn, field nào trong profile rỗng (vd. SĐT) thì cho user nhập + hiện hint. Manual mode clear cả 2. | `checkout.html`, `checkout.js`, `CheckoutRequest.java` |
| F2 (feature) | SePay polling thay webhook | Thêm `SepayPollingService` + cronjob fixedDelay. Bật bằng `app.sepay.polling.enabled=true` (env `SEPAY_POLLING_ENABLED`, `SEPAY_API_TOKEN`). Phù hợp dev localhost không có domain public. Idempotent qua `SepayTransaction.transactionId` unique. | `SepayPollingService.java`, `CronjobService.java`, `application.yaml` |

### Files đã sửa

- `service/OrderService.java`
- `service/NotificationService.java` (rewrite)
- `service/CartService.java`
- `service/VoucherService.java`
- `service/CronjobService.java`
- `repository/VoucherRepository.java`
- `controller/api/OrderApiController.java`
- `resources/static/js/my-orders.js`
- `resources/static/js/checkout.js`
- `resources/templates/my-orders.html`
- `resources/templates/staff/staff.html`
- `resources/application.yaml`

---

## FILE THAM CHIẾU

| File | Mục đích |
|---|---|
| `ARCHITECTURE.md` | Kiến trúc tổng thể, Q&A tổng hợp luồng 1-10, mọi quyết định thiết kế |
| `SPEC.md` | Danh sách công việc (TODO), tiến độ, trạng thái hoàn thành |

**Cách dùng:**
- Mở `ARCHITECTURE.md` để hiểu cấu trúc dự án, quy tắc nghiệp vụ, Q&A
- Mở `SPEC.md` để xem công việc cần làm, đánh dấu hoàn thành

