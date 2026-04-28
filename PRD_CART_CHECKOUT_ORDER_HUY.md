# PRD - Cart -> Checkout -> Order Status (Huy Scope)

## 1. Product Goal

Xây dựng luồng mua hàng giống Shopee ở mức demo cho website linh kiện:
- Giỏ hàng dễ thao tác
- Checkout rõ ràng với `SePay + COD`
- Theo dõi trạng thái đơn hàng trực quan
- Hoàn tiền cơ bản có ảnh minh chứng

## 2. Scope

### In Scope
- Cart (local + DB sync khi đăng nhập)
- Checkout
- Payment flow: `SEPAY`, `COD`
- Order state machine và thao tác user
- SePay timeout auto-cancel
- Email khi đổi trạng thái
- Refund request cơ bản (upload ảnh local)

### Out of Scope
- Module review hoàn chỉnh (Sơn phụ trách)
- Momo/VNPAY ở release hiện tại
- Dashboard phân tích nâng cao
- Refund workflow enterprise nhiều cấp duyệt

## 3. User Personas

- **Guest**: thêm vào giỏ local, xem cart/checkout.
- **Customer**: đặt hàng, theo dõi đơn, hủy đơn theo rule, gửi yêu cầu refund.
- **Staff/Admin**: cập nhật trạng thái vận hành đơn.

## 4. Key User Flows

1) Guest thêm sản phẩm vào cart local.  
2) Login -> auto merge local cart vào DB cart (có warning nếu cần).  
3) Customer checkout với thông tin nhận hàng + payment method.  
4) Nếu SePay: đơn vào `PENDING_PAYMENT`, hiển thị block thanh toán, timeout 30 phút tự hủy.  
5) Nếu COD: đơn vào `PENDING_CONFIRMATION`.  
6) Staff/Admin cập nhật trạng thái theo state machine.  
7) Customer thao tác: hủy đơn (đúng trạng thái), đã nhận hàng, mua lại, refund request.

## 5. Business Decisions (đã chốt)

- `/checkout`: guest vào được, submit thì yêu cầu login.
- `/cart`: public, hỗ trợ local cart.
- Auto merge local cart sau login.
- Badge cart dùng `totalQuantity`.
- Merge vượt tồn kho: clamp + warning.
- Product ẩn/xóa khi merge: drop item + warning.
- Cart total: chỉ tính item selected.
- Payment: `SEPAY + COD`.
- SePay stock: reserve ở `PENDING_PAYMENT`.
- SePay timeout: `30 phút`.
- Checkout cart rỗng: redirect `/cart` + thông báo.
- Cancel customer: `PENDING_PAYMENT`, `PENDING_CONFIRMATION`, `PROCESSING`.
- Khi hủy đơn: bắt buộc lưu `cancelReason` và cộng lại tồn kho theo rule phương thức thanh toán.
- Không cho hủy từ `SHIPPING`, `DELIVERED`, `REFUND_REQUESTED`, `RETURN_REFUND`, `REFUND_REJECTED`.
- Nút “Đã nhận hàng”: chỉ ở `SHIPPING`.
- Sau `DELIVERED`: hiển thị `Đánh giá` (placeholder), `Mua lại`, `Trả hàng/Hoàn tiền`.
- Refund image: tối đa 3 ảnh, jpg/png/webp, 5MB/ảnh, lưu local.
- Refund status:
  - customer submit -> `REFUND_REQUESTED`
  - staff approve -> `RETURN_REFUND`
  - staff reject -> `REFUND_REJECTED` (kèm lý do từ chối)
- Email Google Form: gửi sau khi staff duyệt refund.

## 6. Success Metrics (Demo-level)

- End-to-end flow chạy ổn định với cả SePay và COD.
- Không lỗi business rule chính trong checklist QA.
- Không lấn scope thành viên khác.
- UI thao tác đơn hàng dễ demo và dễ giải thích.

## 7. Acceptance Criteria

- Cart, Checkout, Order Tracking hoạt động nhất quán giữa guest/user.
- State transition đúng rule, chặn chuyển sai.
- Timeout SePay auto-cancel và rollback tồn đúng.
- Refund upload validate đúng giới hạn file.
- Mỗi trạng thái đổi có log/email notify tương ứng.
