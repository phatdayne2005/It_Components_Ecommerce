# QA Release Checklist - Cart, Checkout, Order State Machine

Use this checklist for every release touching cart, checkout, SePay webhook, or order status logic.

## Source of truth

This checklist is synced with:
- `PRD_CART_CHECKOUT_ORDER_HUY.md`
- `SPEC_CART_CHECKOUT_ORDER_HUY.md`
- `TECHNICAL_DESIGN_CART_CHECKOUT_ORDER_HUY.md`
- `CONTEXT_SYSTEM_PROMPT_CART_CHECKOUT_ORDER_HUY.md`

## Scope

- Frontend stack in scope: HTML/CSS/JS + Tailwind + Thymeleaf
- Cart: local + DB merge (`/api/v1/carts/**`)
- Checkout: `POST /api/v1/orders/checkout`
- My Orders flow: `GET /api/v1/orders/my`
- Customer actions: cancel/refund/rebuy/mark delivered
- Staff/Admin status update: `PUT /api/v1/orders/{id}/status`
- SePay webhook + timeout cron

## Environment baseline

- Backend running at `http://localhost:8080`
- MySQL running and schema updated
- Seed data has at least one active high-stock product
- Webhook API key configured: `app.sepay.webhook-api-key`
- Test users:
  - customer account
  - staff/admin account (for status update API)

## Rules to verify

- Cart total is based on selected items only.
- Header badge uses `totalQuantity`.
- Auto merge local cart after login.
- Merge overstock -> clamp + warning.
- Merge deleted/inactive product -> drop + warning.
- SePay timeout is `30 minutes`.
- Cancel requires `cancelReason`.
- Cancel allowed only at:
  - `PENDING_PAYMENT`
  - `PENDING_CONFIRMATION`
  - `PROCESSING`
- Cancel forbidden at:
  - `SHIPPING`
  - `DELIVERED`
  - `REFUND_REQUESTED`
  - `RETURN_REFUND`
  - `REFUND_REJECTED`
- Cancel must restore stock by payment/status rule:
  - SePay cancel from `PENDING_PAYMENT`, `PENDING_CONFIRMATION`, `PROCESSING` -> release reserved stock
  - COD cancel from `PROCESSING` -> restore stock
- Refund flow:
  - customer submit from `DELIVERED` -> `REFUND_REQUESTED`
  - staff approve -> `RETURN_REFUND`
  - staff reject -> `REFUND_REJECTED` + reject note
- Google Form email is sent only when staff approves refund.

---

## 24 Core QA Cases

### Cart and merge

### 1) Guest add/update/remove local cart item
- **Expected:** local cart updates immediately, totals update by selected items.

### 2) Header badge shows totalQuantity
- **Expected:** badge equals sum of all quantities in cart (not selected only).

### 3) Auto merge local cart on login
- **Expected:** local items merged to DB cart automatically.

### 4) Merge with overstock item
- **Expected:** quantity clamped to available stock + warning returned.

### 5) Merge with deleted/inactive product
- **Expected:** invalid item dropped + warning returned.

### 6) Merge warning persistence and dismiss
- **Expected:** warning appears in both `/cart` and `/checkout`, stays until user closes.

### Checkout

### 7) Guest can open `/checkout` but cannot submit
- **Expected:** submit action redirects/forces login.

### 8) Checkout with empty selected items
- **Expected:** redirect to `/cart?checkoutError=cart_empty`.

### 9) Checkout invalid field format
- **Expected:** field-level validation format (`fieldId`, `errorMessage`).

### 10) Checkout COD success
- **Expected:** order status `PENDING_CONFIRMATION`.

### 11) Checkout SePay success
- **Expected:** order status `PENDING_PAYMENT`, reserved stock applied.

### Order status, cancel, inventory

### 12) Customer cancel at `PENDING_PAYMENT`
- **Expected:** status `CANCELLED`, `cancelReason` saved, SePay stock restored.

### 13) Customer cancel at `PENDING_CONFIRMATION`
- **Expected:** status `CANCELLED`, `cancelReason` saved, stock rollback rule applied.

### 14) Customer cancel at `PROCESSING` (COD)
- **Expected:** status `CANCELLED`, stock restored.

### 15) Customer cancel at `PROCESSING` (SePay)
- **Expected:** status `CANCELLED`, reserved stock released.

### 16) Customer cannot cancel at `SHIPPING`
- **Expected:** reject with conflict/error, no state change.

### 17) Invalid transition rejection
- **Expected:** direct invalid jump (example `PENDING_CONFIRMATION -> SHIPPING`) returns `409`.

### 18) `SHIPPING` requires `trackingNumber`
- **Expected:** missing tracking number rejected.

### 19) Customer "Đã nhận hàng" action
- **Expected:** only available at `SHIPPING`, moves to `DELIVERED`.

### SePay webhook and timeout

### 20) Webhook invalid API key
- **Expected:** `403`.

### 21) Webhook idempotency
- **Expected:** duplicate transaction returns ignored reason.

### 22) Webhook insufficient amount
- **Expected:** ignored reason `insufficient_amount`, no status promotion.

### 23) Timeout auto-cancel at 30 minutes
- **Precondition:** SePay order in `PENDING_PAYMENT`.
- **Expected:** order auto `CANCELLED`, stock restored.

### Refund

### 24) Refund submit and staff decision flow
- **Steps:**
  - customer submit from `DELIVERED` with images
  - staff approve/reject
- **Expected:**
  - submit -> `REFUND_REQUESTED`
  - approve -> `RETURN_REFUND` + Google Form email sent
  - reject -> `REFUND_REJECTED` + reject note saved

---

## Pass/Fail template

- Total cases: 24
- Passed:
- Failed:
- Blocked:

## Failure report template

- Case ID:
- Endpoint/Page:
- Request payload:
- Actual status/body:
- Expected:
- Data setup/log evidence:

## Sign-off

- QA Engineer:
- Date:
- Release tag/commit:
- Final decision: `GO` / `NO-GO`
