# TechParts Ecommerce - Kiến Trúc & Q&A Tổng Hợp

> File này tổng hợp toàn bộ Q&A từ các phiên chat, ghi lại mọi quyết định thiết kế và yêu cầu nghiệp vụ.
> Đây là **file tham chiếu** — mọi code phải tuân theo các quy tắc ghi trong đây.

---

## MỤC LỤC

1. [Tổng quan dự án](#1-tổng-quan-dự-án)
2. [Quy tắc phân quyền](#2-quy-tắc-phân-quyền)
3. [Luồng 1-2: Giỏ hàng Guest & Login + Merge](#3-luồng-12-giỏ-hàng-guest--login--merge)
4. [Luồng 3: Logged-in User Thêm giỏ](#4-luồng-3-logged-in-user-thêm-giỏ)
5. [Luồng 4: Xem Giỏ & Chỉnh sửa](#5-luồng-4-xem-giỏ--chỉnh-sửa)
6. [Luồng 5: COD Checkout](#6-luồng-5-cod-checkout)
7. [Luồng 6: SePay Checkout](#7-luồng-6-sepay-checkout)
8. [Luồng 7: Order State Machine](#8-luồng-7-order-state-machine)
9. [Luồng 8: Trạng thái đơn hàng & Actions](#9-luồng-8-trạng-thái-đơn-hàng--actions)
10. [Luồng 9: Refund Nâng cao](#10-luồng-9-refund-nâng-cao)
11. [Luồng 10: Reviews Nâng cao](#11-luồng-10-reviews-nâng-cao)
12. [Staff Page - Trang quản lý đơn hàng](#12-staff-page--trang-quản-lý-đơn-hàng)
13. [SepayTransaction](#13-sepaytransaction)
14. [Email Notifications (CHƯA CODE)](#14-email-notifications-chưa-code)
15. [Cấu trúc Database & Relationships](#15-cấu-trúc-database--relationships)

---

## 1. TỔNG QUAN DỰ ÁN

### Thông tin chung

| Thông tin | Chi tiết |
|---|---|
| Tên dự án | TechParts Ecommerce |
| Mô tả | Website thương mại điện tử linh kiện máy tính |
| Công nghệ | Spring Boot 4.0.6, Java 21, MySQL 8.0, Thymeleaf, JWT |
| Người phụ trách | Huy |

### Vai trò hệ thống

| Role | Quyền hạn |
|---|---|
| **USER** | Xem sản phẩm, mua hàng, xem đơn, viết review |
| **STAFF** | Duyệt đơn, giao hàng, duyệt refund, xem dashboard |
| **ADMIN** | Full access (CRUD products, categories, brands, vouchers, orders, reports) |

### Công nghệ & Kiến trúc

```
Frontend:  Thymeleaf + HTML/CSS/JS (Vanilla) + Tailwind CSS
Backend:   Spring Boot 4.0.6, Java 21
Database:  MySQL 8.0 (JPA/Hibernate ddl-auto: update)
Security:  Spring Security + JWT
Payment:   SePay Gateway (Bank transfer) + COD
Email:     Spring Mail (gmail SMTP)
Upload:    Local file storage ./uploads/
Build:     Maven
```

### Danh sách Entities

| Entity | Mô tả |
|---|---|
| User | id, username, email, password, fullName, phone, enabled, roles |
| Product | id, sku, slug, name, price, stock, sold, viewCount, imageUrl, active, category, brand, images, specifications |
| Order | id, orderCode, user, status, paymentMethod, recipient info, address, totals, items, payment, refund info |
| OrderItem | id, order, product, snapshots (name, price, image, warrantyMonths...) |
| Cart | id, user (OneToOne) |
| CartItem | id, cart, product, quantity, selected |
| Review | id, user, product, rating (1-5), title, comment, approved, createdAt |
| Voucher | id, code, discountType, discountValue, minOrder, maxDiscount, usageLimit, valid dates |
| Payment | id, order, method, status, transactionId, amount, paidAt |
| Category | id, name, slug, parent (self-referencing) |
| Brand | id, name, slug, logoUrl |
| SepayTransaction | id, transactionId, orderCode, transferAmount, rawPayload, createdAt |
| WishlistItem | id, user, product |
| Address | id, user, recipientName, phone, addressLine, ward, district, city, isDefault |
| ProductImage | id, product, url, alt, sortOrder |
| ProductSpecification | id, product, name, value, sortOrder |

### Database Schema

```
User (1) <-> (N) Cart
User (1) <-> (N) WishlistItem
User (1) <-> (N) Address
User (N) <-> (N) Role (user_roles join table)
Product (1) <-> (N) CartItem
Product (1) <-> (N) OrderItem
Product (1) <-> (N) Review
Product (1) <-> (N) WishlistItem
Product (N) <-> (1) Category
Product (N) <-> (1) Brand
Product (1) <-> (N) ProductImage
Product (1) <-> (N) ProductSpecification
Cart (1) <-> (N) CartItem
Order (1) <-> (1) Payment
Order (1) <-> (N) OrderItem
SepayTransaction: standalone, NO foreign key to Order
  (Dữ liệu trên web https://my.sepay.vn/ tách biệt)
```

---

## 2. QUY TẮC PHÂN QUYỀN

| Câu hỏi | Quyết định |
|---|---|
| Staff có được mua hàng trên hệ thống không? | **CẤM** — Staff bị cấm thao tác mua hàng |
| Ai xác nhận đơn hàng? | **STAFF** — Duyệt đơn, giao hàng, duyệt refund |
| Trang quản lý đơn hàng cho staff nằm ở đâu? | Trên **trang Staff** (tab "Đơn hàng"), **chưa có** — cần tạo |
| Admin dashboard ở đâu? | `/admin` |

---

## 3. LUỒNG 1-2: GIỎ HÀNG GUEST & LOGIN + MERGE

### 3.1 Guest thêm vào giỏ (chưa login)

| Thông tin | Chi tiết |
|---|---|
| Lưu ở đâu | localStorage key `"local_cart_items"` |
| Cấu trúc dữ liệu | `{productId, name, price, quantity, selected}` |
| Stock validation | **KHÔNG** — Guest chưa login nên không gọi API, không validate stock |
| Max quantity | Giới hạn bởi `data-stock` attribute trên button |

### 3.2 Login → Merge Cart

| Thông tin | Chi tiết |
|---|---|
| Khi nào merge | Khi user login thành công |
| Logic merge | Gọi `POST /api/v1/carts/merge` với dữ liệu localStorage |
| Cảnh báo (warnings) | Hiển thị box vàng nếu item trong local cart không còn đủ stock |
| **Block khi có warnings** | **CÓ** — Khách **bị block** không cho checkout nếu có cảnh báo |
| Xóa localStorage | Sau khi merge thành công |

### 3.3 Race Condition Stock

| Câu hỏi | Quyết định |
|---|---|
| Race condition khi stock thay đổi giữa lúc xem và lúc đặt? | **Demo nên khó xảy ra** — Giữ nguyên, không cần xử lý đặc biệt |

### 3.4 Toast Messages

| Câu hỏi | Quyết định |
|---|---|
| Toast message khi fail | Hiển thị **message chi tiết từ server response**, không hiển thị message chung chung |

---

## 4. LUỒNG 3: LOGGED-IN USER THÊM GIỎ

| Thông tin | Chi tiết |
|---|---|
| Mỗi lần thêm | Gọi API → stock validated **real-time** |
| Khi thêm thành công | Toast success |
| Khi fail (stock, auth) | Toast với **message chi tiết từ server** |

---

## 5. LUỒNG 4: XEM GIỎ & CHỈNH SỬA

| Thông tin | Chi tiết |
|---|---|
| Guest cart stock validation | **Chưa cần làm** |
| Xóa nhiều item | Giữ nguyên behavior hiện tại (acceptable cho demo) |

---

## 6. LUỒNG 5: COD CHECKOUT

| Thông tin | Chi tiết |
|---|---|
| Reserve stock | **CÓ** — COD cũng reserve stock ngay khi tạo order (giống SePay) |
| Trạng thái khởi tạo | PENDING_CONFIRMATION (không qua PENDING_PAYMENT) |
| Staff xác nhận | Staff duyệt → chuyển PROCESSING → SHIPPING |

---

## 7. LUỒNG 6: SEPAY CHECKOUT

### 7.1 Luồng thanh toán SePay

| Bước | Mô tả |
|---|---|
| 1 | User chọn SePay → Order tạo với status **PENDING_PAYMENT** |
| 2 | Frontend auto-submit form ẩn → redirect đến SePay gateway |
| 3 | SePay redirect về `/payment/success?orderCode=XXX` |
| 4 | Hoặc SePay gửi webhook đến `/api/v1/payments/sepay/ipn` |
| 5 | Server validate transaction → đánh dấu order đã thanh toán |

### 7.2 Trang Success

| Câu hỏi | Quyết định |
|---|---|
| Trang success hiển thị gì khi chưa có IPN? | Hiển thị message **"Đang chờ xác nhận từ SePay..."** |

### 7.3 Trang Cancel/Error

| Trạng thái | Hành vi |
|---|---|
| Cancel | User hủy thanh toán SePay → redirect về trang cancel |
| Error | Thanh toán lỗi → redirect về trang error |

---

## 8. LUỒNG 7: ORDER STATE MACHINE

### 8.1 Trạng thái đơn hàng (OrderStatus)

```
PENDING_PAYMENT ────────────────────────────────→ PENDING_CONFIRMATION ──────────────────→ PROCESSING ──────────────────→ SHIPPING ──────────────→ DELIVERED
    │                                                         │                              │                        │                         │
    │                                                         │                              │                        │                         │
    │                                                         ↓                              ↓                        ↓                         ↓
    └──────────────────→ CANCELLED                      CANCELLED                      CANCELLED             REFUND_REQUESTED ───→ REFUND_REJECTED / RETURN_REFUND
       (timeout 15-30 phút)                           (khách hủy)                  (admin/staff hủy)          (khách yêu cầu)
```

| Status | Mô tả |
|---|---|
| PENDING_PAYMENT | Chờ thanh toán SePay (COD không qua status này) |
| PENDING_CONFIRMATION | Đã thanh toán / COD → chờ staff xác nhận |
| PROCESSING | Staff đã xác nhận → đang xử lý |
| SHIPPING | Đang giao hàng |
| DELIVERED | Đã giao thành công |
| REFUND_REQUESTED | Khách yêu cầu hoàn tiền |
| REFUND_REJECTED | Yêu cầu hoàn tiền bị từ chối |
| RETURN_REFUND | Hoàn tiền thành công |
| CANCELLED | Đơn đã hủy |

### 8.2 Ai xác nhận / chuyển trạng thái

| Action | Ai làm |
|---|---|
| Xác nhận đơn (PENDING_CONFIRMATION → PROCESSING) | STAFF |
| Chuyển PROCESSING → SHIPPING | STAFF |
| Chuyển SHIPPING → DELIVERED | STAFF |
| Hủy đơn PENDING_CONFIRMATION/PROCESSING | STAFF hoặc ADMIN |
| Yêu cầu hoàn tiền | USER (khách hàng) |
| Duyệt hoàn tiền | STAFF |
| Từ chối hoàn tiền | STAFF |

### 8.3 Auto-cancel

| Thông tin | Chi tiết |
|---|---|
| PENDING_PAYMENT timeout | 15-30 phút |
| Logic | Cronjob chạy mỗi 1 phút |
| Khi timeout | Auto-cancel order, restore stock |
| **Hủy PENDING_PAYMENT thủ công** | **CÓ** — Khách có thể hủy thủ công trong thời gian chờ thanh toán |

### 8.4 Hủy đơn — Modal thay vì window.prompt

| Thông tin | Chi tiết |
|---|---|
| Cách nhập lý do hiện tại | `window.prompt()` — chưa đẹp |
| Cách nhập lý do **MỚI** | **Modal form đẹp** với trường "Lý do hủy" |
| Lý do hủy | **BẮT BUỘC** — không cho gửi nếu để trống |
| Hủy PENDING_CONFIRMATION/PROCESSING | Staff/Admin hủy → **Hoàn lại voucher** cho khách |
| Hủy PENDING_PAYMENT (SePay) | Khách hủy thủ công → restore stock |

### 8.5 Hủy PENDING_CONFIRMATION → Voucher

| Câu hỏi | Quyết định |
|---|---|
| Cancel khi PENDING_CONFIRMATION (COD chưa xác nhận) | **Hoàn lại voucher** cho khách (tăng usedCount - 1) |
| Cancel khi PROCESSING | **Hoàn lại voucher** cho khách |

---

## 9. LUỒNG 8: TRẠNG THÁI ĐƠN HÀNG & ACTIONS

### 9.1 Hiển thị nút theo trạng thái

| Trạng thái | Các nút/Action |
|---|---|
| PENDING_PAYMENT | Thông tin + countdown timeout |
| PENDING_CONFIRMATION | [Hủy đơn] |
| PROCESSING | [Hủy đơn] |
| **SHIPPING** | **[Đã nhận hàng]** + **[Trả hàng/Hoàn tiền]** |
| **DELIVERED** | **[Chi tiết]** + **[ĐÁNH GIÁ]** + **[Mua lại]** |
| REFUND_REQUESTED | [Chi tiết] + (Review đã bị XÓA) |
| REFUND_REJECTED | [Chi tiết] + [Yêu cầu hoàn tiền lại] |
| RETURN_REFUND | [Chi tiết] |
| CANCELLED | [Chi tiết] |

### 9.2 Nút "Đã nhận hàng"

| Thông tin | Chi tiết |
|---|---|
| Trạng thái | Chỉ hiện khi order status = **SHIPPING** |
| Hành vi | Click → chuyển trạng thái → **DELIVERED** |

### 9.3 Nút "Trả hàng/Hoàn tiền"

| Thông tin | Chi tiết |
|---|---|
| Trạng thái | Hiện khi order status = **SHIPPING** (cùng hàng với "Đã nhận hàng") |
| Hành vi | Click → mở modal upload ảnh refund → chuyển → **REFUND_REQUESTED** |
| Form modal | Lý do (dropdown) + mô tả + upload ảnh (tối đa 3 ảnh, mỗi ảnh tối đa 5MB) |

### 9.4 Nút "ĐÁNH GIÁ"

| Thông tin | Chi tiết |
|---|---|
| Trạng thái | Chỉ hiện khi order status = **DELIVERED** |
| Vị trí | Trang "Đơn hàng của tôi" (`my-orders.html`) |
| Mỗi item | Hiển thị: [Chi tiết đơn] [ĐÁNH GIÁ] [Mua lại] |
| Form | Inline ngay tại chỗ (không popup mới) |

### 9.5 Nút Hủy đơn — Modal thay vì window.prompt

| Thông tin | Chi tiết |
|---|---|
| Trigger | PENDING_PAYMENT / PENDING_CONFIRMATION / PROCESSING |
| Cách nhập | **Modal form** thay vì `window.prompt()` |
| Trường "Lý do hủy" | **BẮT BUỘC** — không cho gửi nếu để trống |
| Khi gửi | Lưu vào `cancelReason` trong Order |

```
┌─────────────────────────────────────────────┐
│  Hủy đơn hàng                            │
│  ─────────────────────────────────────────  │
│  Bạn có chắc muốn hủy đơn này?           │
│                                             │
│  Lý do hủy: *                             │
│  ┌─────────────────────────────────────┐   │
│  │                                     │   │
│  └─────────────────────────────────────┘   │
│  (Bắt buộc nhập)                          │
│                                             │
│  [Hủy bỏ]         [Xác nhận hủy]        │
└─────────────────────────────────────────────┘
```

---

## 10. LUỒNG 9: REFUND NÂNG CAO

### 10.1 Quy trình refund

```
SHIPPING (khách nhấn "Trả hàng/Hoàn tiền")
  ↓
REFUND_REQUESTED (khách gửi yêu cầu + upload ảnh)
  ↓
STAFF duyệt/refute
  ↓
REFUND_REJECTED (từ chối)
  HOẶC
RETURN_REFUND (duyệt → hoàn tiền)
```

### 10.2 Modal Refund

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
│  │     Kéo thả hoặc click để tải     │   │
│  └─────────────────────────────────────┘   │
│  (Tối đa 3 ảnh, mỗi ảnh tối đa 5MB)       │
│                                             │
│  [Hủy]              [Gửi yêu cầu]         │
└─────────────────────────────────────────────┘
```

### 10.3 Hoàn tiền

| Thông tin | Chi tiết |
|---|---|
| Khi nào hoàn tiền | Sau khi đơn chuyển sang RETURN_REFUND (admin duyệt) |
| Phương thức hoàn | Hoàn tiền thủ công |
| Thông tin hoàn | Sau khi nhận được thông tin trong link Google Forms từ khách hàng |

### 10.4 Refund & Reviews

| Thông tin | Chi tiết |
|---|---|
| Khi đơn chuyển REFUND_REQUESTED | **Xóa tất cả reviews** liên quan đến các item trong đơn đó |
| Khi đơn REFUND_REJECTED | Review đã bị xóa ở bước trên — không khôi phục tự động |

### 10.5 Email Refund

| Email | Trigger | Chi tiết |
|---|---|---|
| Email từ chối refund | Staff từ chối yêu cầu hoàn tiền | Gửi cho khách + **refundRejectNote** (lý do từ chối). **BẮT BUỘC nhập lý do từ chối — không cho gửi nếu rỗng** |
| Email gửi form hoàn tiền | Staff duyệt refund (RETURN_REFUND) | Gửi link Google Forms cho khách nhập thông tin hoàn tiền |

### 10.6 Refund Form Email (Google Forms)

Khi staff duyệt refund (RETURN_REFUND), email gửi khách kèm link Google Forms. Form bao gồm:

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

## 11. LUỒNG 10: REVIEWS NÂNG CAO

### 11.1 Ai được review

| Quy tắc | Chi tiết |
|---|---|
| Điều kiện | User có ít nhất 1 Order với sản phẩm đó và status = **DELIVERED** |
| Mỗi đơn mua | 1 review cho mỗi sản phẩm trong đơn |
| Mua nhiều cái trong 1 đơn | 1 review cho sản phẩm đó (không phải 1 review cho mỗi cái) |
| Mua nhiều đơn | Mỗi đơn DELIVERED → 1 review riêng cho sản phẩm đó |
| 1 user + 1 sản phẩm | Có thể có **nhiều review** (nếu mua nhiều lần qua nhiều đơn) |

### 11.2 Sơ đồ Review

```
Order #A (DELIVERED) → Item: CPU i5-12400 → Review #1 (user: A, product: CPU, order: A)
Order #B (DELIVERED) → Item: CPU i5-12400 → Review #2 (user: A, product: CPU, order: B)
Order #C (DELIVERED) → Item: CPU i5-12400 x3 → Review #3 (user: B, product: CPU, order: C)

→ User A có 2 review cho CPU i5-12400 (vì mua 2 lần qua 2 đơn)
→ User B có 1 review cho CPU i5-12400
→ Total: 3 review cho CPU i5-12400
```

### 11.3 Nút ĐÁNH GIÁ trên my-orders

```
┌─────────────────────────────────────────────────────┐
│  Đơn #123 - DELIVERED - 02/05/2026                 │
│  ─────────────────────────────────────────────────  │
│  ┌─────────────────────────────────────────────────┐│
│  │  CPU Intel Core i5-12400 x2                    ││
│  │  Bảo hành: 24 tháng                            ││
│  │  [Chi tiết]  [ĐÁNH GIÁ]  [Mua lại]           ││
│  │                                                 ││
│  │  ★★★★☆ (4/5)                                  ││
│  │  "Sản phẩm tốt, giao nhanh"                   ││
│  │  [Sửa đánh giá]  [Xóa đánh giá]              ││
│  └─────────────────────────────────────────────────┘│
│  ┌─────────────────────────────────────────────────┐│
│  │  RAM Kingston 16GB DDR4 x1                      ││
│  │  Bảo hành: 36 tháng                            ││
│  │  [Chi tiết]  [ĐÁNH GIÁ]  [Mua lại]           ││
│  │  (Chưa có đánh giá)                           ││
│  └─────────────────────────────────────────────────┘│
└─────────────────────────────────────────────────────┘
```

### 11.4 Form Review Inline

```
┌─ Review Inline ─────────────────────────────────────┐
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
│  └────────────────────────────────────────────────┘ │
│                                                      │
│  [Hủy]                    [Gửi đánh giá]           │
└──────────────────────────────────────────────────────┘
```

### 11.5 Sửa & Xóa Review

| Hành vi | Chi tiết |
|---|---|
| Chỉnh sửa | **Cho phép** — viết 1 lần, có thể chỉnh sửa |
| Xóa | **Cho phép** — xóa và viết lại |
| Không bắt buộc xóa mới sửa | Sửa trực tiếp, không cần xóa trước |

### 11.6 Sau khi gửi review

| Bước | Chi tiết |
|---|---|
| 1 | Lưu vào database |
| 2 | Frontend re-fetch reviews |
| 3 | Hiển thị inline ngay dưới item trong my-orders |
| 4 | Review cũng hiển thị trên trang chi tiết sản phẩm |

### 11.7 Hiển thị review

| Vị trí | Chi tiết |
|---|---|
| **Trang chi tiết sản phẩm** (`catalog/detail.html`) | Hiển thị tất cả review đã duyệt của sản phẩm: rating trung bình, số lượng, danh sách review (user, ngày, nội dung) |
| **Trang my-orders** | Review của user cho sản phẩm trong đơn đó, có nút Sửa/Xóa |

### 11.8 API Endpoints Reviews

| Method | Endpoint | Mô tả |
|---|---|---|
| GET | `/api/v1/products/{productId}/reviews/me?orderId={orderId}` | Lấy review của user cho sản phẩm (theo order) |
| POST | `/api/v1/products/{productId}/reviews` | Tạo review mới |
| PUT | `/api/v1/products/{productId}/reviews/{reviewId}` | Cập nhật review |
| DELETE | `/api/v1/products/{productId}/reviews/{reviewId}` | Xóa review |

### 11.9 Request Body

```json
POST /api/v1/products/{productId}/reviews
{
    "orderId": 123,
    "rating": 5,
    "title": "Sản phẩm tốt",
    "comment": "Dùng ổn định, giao hàng nhanh..."
}
```

### 11.10 Entity Review — Thêm trường

```java
@Entity
public class Review {
    @ManyToOne
    @JoinColumn(name = "order_id")  // ← THÊM: gắn với order cụ thể
    private Order order;

    // các trường hiện có: user, product, rating, title, comment, approved, createdAt
}
```

---

## 12. STAFF PAGE — TRANG QUẢN LÝ ĐƠN HÀNG

> Trang Staff (`/staff`) — tab "Đơn hàng" để duyệt đơn, giao hàng, duyệt refund.

### 12.1 Tính năng chính

| Tính năng | Mô tả |
|---|---|
| Xem danh sách đơn hàng | Tất cả đơn hàng trên hệ thống |
| Filter theo trạng thái | Tất cả / Chờ xác nhận / Đang xử lý / Đang giao / Đã giao / Refund |
| Filter theo ngày | Lọc đơn theo ngày đặt |
| Filter theo mã đơn | Tìm đơn theo orderCode |
| Filter theo khách hàng | Tìm đơn theo tên/username khách hàng |
| Xác nhận đơn hàng | PENDING_CONFIRMATION → PROCESSING |
| Chuyển giao hàng | PROCESSING → SHIPPING |
| Xác nhận đã giao | SHIPPING → DELIVERED |
| Duyệt refund | REFUND_REQUESTED → RETURN_REFUND |
| Từ chối refund | REFUND_REQUESTED → REFUND_REJECTED |

### 12.2 Giao diện Duyệt Refund

Khi xem đơn có trạng thái **REFUND_REQUESTED**:

```
┌─────────────────────────────────────────────────────┐
│  Đơn #123 - REFUND_REQUESTED                      │
│  Khách: Nguyễn Văn A - 03/05/2026               │
│  ─────────────────────────────────────────────────  │
│                                                     │
│  Lý do khách yêu cầu hoàn:                      │
│  "Sản phẩm bị lỗi, không hoạt động"            │
│                                                     │
│  Hình ảnh minh chứng:                            │
│  [img1.jpg]  [img2.jpg]  [img3.jpg]              │
│  (click để xem lớn)                              │
│                                                     │
│  ┌─────────────────────────────────────────────────┐│
│  │  Hành động:                                   ││
│  │                                                 ││
│  │  [Chấp nhận hoàn tiền]  ← chuyển → RETURN_REFUND││
│  │                                                 ││
│  │  [Từ chối]                                    ││
│  │  Lý do từ chối: *                            ││
│  │  ┌──────────────────────────────────────┐     ││
│  │  │                                      │     ││
│  │  └──────────────────────────────────────┘     ││
│  │  (BẮT BUỘC nhập lý do - không cho gửi nếu rỗng)││
│  │  [Xác nhận từ chối]                        ││
│  └─────────────────────────────────────────────────┘│
└─────────────────────────────────────────────────────┘
```

### 12.3 Filter Bar

```
┌────────────────────────────────────────────────────────────────────┐
│  Filter: [Tất cả ▼]  Từ: [date]  Đến: [date]  Tìm: [orderCode]  │
│           [Tìm theo khách hàng ▼]  [Tìm kiếm]  [Đặt lại]       │
└────────────────────────────────────────────────────────────────────┘

Dropdown trạng thái:
- Tất cả
- Chờ xác nhận (PENDING_CONFIRMATION)
- Đang xử lý (PROCESSING)
- Đang giao (SHIPPING)
- Đã giao (DELIVERED)
- Refund (REFUND_REQUESTED, REFUND_REJECTED, RETURN_REFUND)
```

### 12.4 API Endpoints cho Staff

| Method | Endpoint | Mô tả |
|---|---|---|
| GET | `/api/v1/staff/orders` | Danh sách đơn (filter by status, date, code, customer) |
| PUT | `/api/v1/orders/{id}/status` | Cập nhật trạng thái (xác nhận, giao, duyệt refund, từ chối refund) |
| PUT | `/api/v1/orders/{id}/refund/reject` | Từ chối refund (bắt buộc kèm refundRejectNote) |

### 12.5 Request khi từ chối refund

```json
PUT /api/v1/orders/{id}/refund/reject
{
    "rejectNote": "Lý do từ chối: Sản phẩm không nằm trong chính sách bảo hành"
}
```
*(BẮT BUỘC có rejectNote, server trả lỗi 400 nếu rỗng)*

---

## 13. SEPAYTRANSACTION

| Thông tin | Chi tiết |
|---|---|
| Order_id foreign key | **KHÔNG CÓ** — SepayTransaction tách biệt với Order |
| Lý do | Dữ liệu trên web https://my.sepay.vn/ tách biệt, không cần FK |
| Logic IPN | Xử lý trong `SepayWebhookService` (tách riêng để tránh circular dependency) |
| Refund info | Lưu trong Order entity (refundReason, refundEvidenceUrls, refundRejectNote) |

---

## 13. EMAIL NOTIFICATIONS (CHƯA CODE)

> **Lưu ý:** Phần email chưa được code — ghi nhận để làm sau.

| # | Email | Trigger | Chi tiết |
|---|---|---|---|
| 1 | Email thông báo từ chối refund | Staff từ chối yêu cầu hoàn tiền | Gửi cho khách + **refundRejectNote**. **BẮT BUỘC nhập lý do — không cho gửi nếu rỗng** |
| 2 | Email gửi form hoàn tiền | Staff duyệt refund (RETURN_REFUND) | Kèm link Google Forms — form có: họ tên, SĐT, địa chỉ lấy hàng, thời gian lấy, STK, ngân hàng |
| 3 | Email nhắc thanh toán | Order PENDING_PAYMENT (trước khi timeout) | Nhắc khách thanh toán |
| 4 | Email xác nhận đơn hàng | Tạo đơn hàng thành công | Gửi cho khách |
| 5 | Email thông báo giao hàng | Đơn chuyển sang SHIPPING | Thông báo bắt đầu giao |

---

## 14. CẤU TRÚC DATABASE & RELATIONSHIPS

### 14.1 Entity Relationship Diagram (Text)

```
┌──────────┐       ┌─────────────┐       ┌──────────┐
│   User   │1    N │ WishlistItem│ N    1│ Product  │
└──────────┘       └─────────────┘       └──────────┘
    │                                            │
    │ 1    N                                     │
┌──────────┐       ┌──────────┐                 │
│   Cart   │1    N │ CartItem  │───────────────┘
└──────────┘       └──────────┘
    │ 1    N
┌──────────┐ 1    N ┌──────────┐       ┌──────────┐
│  Order   │───────│OrderItem │ N    1│ Product  │
└──────────┘       └──────────┘       └──────────┘
    │
    │ 1    1
┌──────────┐
│ Payment  │
└──────────┘
    │
    │ N    N (SepayTransaction tách biệt)
┌──────────────────────┐
│ SepayTransaction     │  ← KHÔNG có FK đến Order
└──────────────────────┘
```

### 14.2 Review Relationship (mới)

```
┌──────────┐       ┌──────────┐       ┌──────────┐
│   User   │1    N │  Review  │ N    1│ Product  │
└──────────┘       └──────────┘       └──────────┘
                       │
                       │ 1    1
                       ▼
                  ┌──────────┐
                  │  Order   │
                  └──────────┘
```

---

## 15. GHI CHÚ

- **Ngày tạo file**: 04/05/2026
- **File gốc**: SPEC.md (danh sách công việc + tiến độ)
- **File này**: ARCHITECTURE.md (kiến trúc + Q&A tổng hợp)
- **Phiên bản**: 1.0
