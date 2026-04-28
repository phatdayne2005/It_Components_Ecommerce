# Spec - Cart/Checkout/Order (Huy)

## 1. Functional Requirements

## 1.1 Cart
- FR-CART-01: Guest cart lưu localStorage key `local_cart_items`.
- FR-CART-02: User cart lưu DB và thao tác qua `/api/v1/carts/**`.
- FR-CART-03: Sau login, local cart tự merge vào DB.
- FR-CART-04: Realtime update số lượng/xóa/chọn item.
- FR-CART-05: Tổng tiền cart tính theo item `selected`.
- FR-CART-06: Header badge hiển thị `totalQuantity`.

## 1.2 Checkout
- FR-CHK-01: Field bắt buộc: `phone`, `email`, `address`, `paymentMethod`.
- FR-CHK-02: `paymentMethod` cho phép `SEPAY`, `COD`.
- FR-CHK-03: Checkout chỉ từ item selected.
- FR-CHK-04: Guest submit checkout bị ép login.
- FR-CHK-05: Nếu cart rỗng khi submit -> redirect `/cart?checkoutError=cart_empty`.

## 1.3 Order Status
- FR-ORD-01: Initial status:
  - SePay -> `PENDING_PAYMENT`
  - COD -> `PENDING_CONFIRMATION`
- FR-ORD-02: Allowed transitions:
  - `PENDING_PAYMENT` -> `PENDING_CONFIRMATION | CANCELLED`
  - `PENDING_CONFIRMATION` -> `PROCESSING | CANCELLED`
  - `PROCESSING` -> `SHIPPING | CANCELLED`
  - `SHIPPING` -> `DELIVERED`
  - `DELIVERED` -> `REFUND_REQUESTED`
  - `REFUND_REQUESTED` -> `RETURN_REFUND | REFUND_REJECTED`
- FR-ORD-03: Customer cancel chỉ ở `PENDING_PAYMENT`, `PENDING_CONFIRMATION`, `PROCESSING`.
- FR-ORD-03a: Customer bấm hủy đơn bắt buộc nhập lý do và hệ thống phải lưu `cancelReason`.
- FR-ORD-03b: Customer không được hủy từ `SHIPPING`, `DELIVERED`, `REFUND_REQUESTED`, `RETURN_REFUND`, `REFUND_REJECTED`.
- FR-ORD-04: `SHIPPING` bắt buộc `trackingNumber`.
- FR-ORD-05: Customer chỉ bấm “Đã nhận hàng” ở `SHIPPING`.
- FR-ORD-06: API update status chỉ cho `STAFF/ADMIN`.

## 1.4 Inventory Rules
- FR-INV-01: SePay reserve stock ở `PENDING_PAYMENT`.
- FR-INV-02: SePay timeout/hủy ở `PENDING_PAYMENT` -> release stock.
- FR-INV-03: COD reduce stock khi vào `PROCESSING`.
- FR-INV-04: COD cancel từ `PROCESSING` -> restore stock.
- FR-INV-05: Hủy đơn phải cộng lại tồn kho theo trạng thái trước hủy:
  - SePay: `PENDING_PAYMENT`, `PENDING_CONFIRMATION`, `PROCESSING` -> release reserved stock.
  - COD: `PROCESSING` -> restore stock.

## 1.5 SePay
- FR-SEPAY-01: Webhook idempotent theo `transaction_id` unique.
- FR-SEPAY-02: Nếu amount < order total -> ignored.
- FR-SEPAY-03: Timeout `30 phút` auto-cancel.
- FR-SEPAY-04: Trên trang đơn hàng, đơn SePay ở `PENDING_PAYMENT` phải hiển thị payment block.

## 1.6 Refund
- FR-RFD-01: Customer submit refund từ `DELIVERED`.
- FR-RFD-02: Status đổi sang `REFUND_REQUESTED`.
- FR-RFD-02a: Staff duyệt -> `RETURN_REFUND`.
- FR-RFD-02b: Staff từ chối -> `REFUND_REJECTED` + lưu `refundRejectNote`.
- FR-RFD-03: Upload ảnh local:
  - max 3 files
  - mỗi file <= 5MB
  - định dạng `jpg/jpeg/png/webp`
- FR-RFD-04: Gửi email hướng dẫn Google Form sau khi staff duyệt refund (khi chuyển `REFUND_REQUESTED` -> `RETURN_REFUND`).

## 1.7 Warning/Notification
- FR-WARN-01: Merge warnings hiển thị ở cả `/cart` và `/checkout`.
- FR-WARN-02: Warning giữ persistent tới khi user đóng.
- FR-NOTI-01: Gửi email khi đổi trạng thái đơn.

## 2. API Contracts (minimum)

- `POST /api/v1/orders/checkout`
- `GET /api/v1/orders/my`
- `PUT /api/v1/orders/{id}/status` (STAFF/ADMIN)
- `POST /api/v1/orders/{id}/cancel`
- `POST /api/v1/orders/{id}/refund` (JSON URL mode fallback)
- `POST /api/v1/orders/{id}/refund/upload` (multipart)
- `POST /api/v1/payments/sepay/webhook`

## 3. Non-Functional

- NFR-01: Demo response ổn định, không crash với input invalid.
- NFR-02: Validate lỗi trả về message rõ ràng.
- NFR-03: Không yêu cầu infrastructure ngoài local DB + local uploads cho demo.
