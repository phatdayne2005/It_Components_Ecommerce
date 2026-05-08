(function () {
    'use strict';

    const listEl = document.getElementById('ordersList');
    const loadingEl = document.getElementById('ordersLoading');
    const emptyEl = document.getElementById('ordersEmpty');
    const paymentNoticeEl = document.getElementById('orderPaymentNotice');
    const summaryErrorEl = document.getElementById('orderSummaryError');

    // --- Cancel modal ---
    const cancelModal = document.getElementById('cancelModal');
    const cancelReasonInput = document.getElementById('cancelReasonInput');
    const cancelReasonError = document.getElementById('cancelReasonError');
    const cancelModalConfirm = document.getElementById('cancelModalConfirm');
    const cancelModalClose = document.getElementById('cancelModalClose');
    const cancelModalCancel = document.getElementById('cancelModalCancel');
    let pendingCancelOrderId = null;

    // --- Refund modal ---
    const refundModal = document.getElementById('refundModal');
    const refundReasonSelect = document.getElementById('refundReasonSelect');
    const refundReasonError = document.getElementById('refundReasonError');
    const refundDescInput = document.getElementById('refundDescInput');
    const refundImageDropzone = document.getElementById('refundImageDropzone');
    const refundImageInput = document.getElementById('refundImageInput');
    const refundImagePreview = document.getElementById('refundImagePreview');
    const refundImageError = document.getElementById('refundImageError');
    const refundModalConfirm = document.getElementById('refundModalConfirm');
    const refundModalClose = document.getElementById('refundModalClose');
    const refundModalCancel = document.getElementById('refundModalCancel');
    let pendingRefundOrderId = null;
    let pendingRefundFiles = [];

    // --- Refund bank info modal ---
    const refundBankModal = document.getElementById('refundBankModal');
    const refundBankNameInput = document.getElementById('refundBankNameInput');
    const refundBankNameError = document.getElementById('refundBankNameError');
    const refundBankAccountNumberInput = document.getElementById('refundBankAccountNumberInput');
    const refundBankAccountNumberError = document.getElementById('refundBankAccountNumberError');
    const refundBankAccountHolderInput = document.getElementById('refundBankAccountHolderInput');
    const refundBankAccountHolderError = document.getElementById('refundBankAccountHolderError');
    const refundBankNoteInput = document.getElementById('refundBankNoteInput');
    const refundBankModalConfirm = document.getElementById('refundBankModalConfirm');
    const refundBankModalClose = document.getElementById('refundBankModalClose');
    const refundBankModalCancel = document.getElementById('refundBankModalCancel');
    let pendingRefundBankOrderId = null;

    // --- Review modal ---
    const reviewModal = document.getElementById('reviewModal');
    const reviewProductInfo = document.getElementById('reviewProductInfo');
    const starRating = document.getElementById('starRating');
    const starRatingError = document.getElementById('starRatingError');
    const reviewTitleInput = document.getElementById('reviewTitleInput');
    const reviewCommentInput = document.getElementById('reviewCommentInput');
    const reviewCommentError = document.getElementById('reviewCommentError');
    const reviewModalSubmit = document.getElementById('reviewModalSubmit');
    const reviewModalClose = document.getElementById('reviewModalClose');
    const reviewModalCancel = document.getElementById('reviewModalCancel');
    let pendingReviewOrderId = null;
    let pendingReviewProductId = null;
    let pendingReviewExistingId = null; // null = create, number = update
    let selectedRating = 0;

    function api() { return window.TechPartsApi; }

    function buildJsonHeaders() {
        const a = api();
        return a && a.buildJsonHeaders ? a.buildJsonHeaders() : { 'Accept': 'application/json' };
    }

    function buildMultipartHeaders() {
        const a = api();
        return a && a.buildMultipartHeaders ? a.buildMultipartHeaders() : { 'Accept': 'application/json' };
    }

    function isLoggedIn() {
        const a = api();
        return a && a.isLoggedIn ? a.isLoggedIn() : false;
    }

    init();

    async function init() {
        if (!isLoggedIn()) {
            window.location.href = '/login';
            return;
        }
        renderPaymentNoticeFromQuery();
        bindModalEvents();
        await loadOrders();
    }

    // ─────────────────────────────────────────────
    // MODAL BINDINGS
    // ─────────────────────────────────────────────
    function bindModalEvents() {
        // Cancel modal
        cancelModalClose.addEventListener('click', closeCancelModal);
        cancelModalCancel.addEventListener('click', closeCancelModal);
        cancelModal.addEventListener('click', function (e) {
            if (e.target === cancelModal) closeCancelModal();
        });
        cancelReasonInput.addEventListener('input', function () {
            cancelReasonError.classList.add('hidden');
        });
        cancelModalConfirm.addEventListener('click', confirmCancelModal);

        // Refund modal
        refundModalClose.addEventListener('click', closeRefundModal);
        refundModalCancel.addEventListener('click', closeRefundModal);
        refundModal.addEventListener('click', function (e) {
            if (e.target === refundModal) closeRefundModal();
        });
        refundReasonSelect.addEventListener('change', function () {
            if (refundReasonSelect.value && refundReasonError) refundReasonError.classList.add('hidden');
        });
        refundImageDropzone.addEventListener('click', function () { refundImageInput.click(); });
        refundImageInput.addEventListener('change', handleRefundImageSelect);
        refundModalConfirm.addEventListener('click', confirmRefundModal);

        // Refund bank info modal
        if (refundBankModal) {
            refundBankModalClose.addEventListener('click', closeRefundBankModal);
            refundBankModalCancel.addEventListener('click', closeRefundBankModal);
            refundBankModal.addEventListener('click', function (e) {
                if (e.target === refundBankModal) closeRefundBankModal();
            });
            [refundBankNameInput, refundBankAccountNumberInput, refundBankAccountHolderInput].forEach(function (input) {
                input.addEventListener('input', function () {
                    const errId = input.id.replace('Input', 'Error');
                    const errEl = document.getElementById(errId);
                    if (errEl) errEl.classList.add('hidden');
                });
            });
            refundBankModalConfirm.addEventListener('click', confirmRefundBankModal);
        }

        // Review modal
        reviewModalClose.addEventListener('click', closeReviewModal);
        reviewModalCancel.addEventListener('click', closeReviewModal);
        reviewModal.addEventListener('click', function (e) {
            if (e.target === reviewModal) closeReviewModal();
        });
        reviewCommentInput.addEventListener('input', function () {
            reviewCommentError.classList.add('hidden');
        });
        starRating.querySelectorAll('.star-btn').forEach(function (btn) {
            btn.addEventListener('click', function () {
                selectedRating = parseInt(btn.dataset.value);
                starRatingError.classList.add('hidden');
                updateStarDisplay(selectedRating);
            });
            btn.addEventListener('mouseenter', function () {
                const val = parseInt(btn.dataset.value);
                highlightStars(val);
            });
            btn.addEventListener('mouseleave', function () {
                highlightStars(selectedRating);
            });
        });
        reviewModalSubmit.addEventListener('click', confirmReviewModal);
    }

    function updateStarDisplay(rating) {
        starRating.querySelectorAll('.star-btn').forEach(function (btn) {
            const val = parseInt(btn.dataset.value);
            btn.classList.toggle('text-amber-400', val <= rating);
            btn.classList.toggle('text-slate-300', val > rating);
        });
    }

    function highlightStars(rating) {
        starRating.querySelectorAll('.star-btn').forEach(function (btn) {
            const val = parseInt(btn.dataset.value);
            btn.classList.toggle('text-amber-400', val <= rating);
            btn.classList.toggle('text-slate-300', val > rating);
        });
    }

    function openReviewModal(orderId, productId, productName, existingReview) {
        pendingReviewOrderId = orderId;
        pendingReviewProductId = productId;
        pendingReviewExistingId = existingReview ? existingReview.id : null;
        reviewProductInfo.textContent = productName;
        reviewTitleInput.value = existingReview && existingReview.title ? existingReview.title : '';
        reviewCommentInput.value = existingReview && existingReview.comment ? existingReview.comment : '';
        selectedRating = existingReview && existingReview.rating ? existingReview.rating : 0;
        updateStarDisplay(selectedRating);
        starRatingError.classList.add('hidden');
        reviewCommentError.classList.add('hidden');
        reviewModalSubmit.textContent = existingReview ? 'Cập nhật đánh giá' : 'Gửi đánh giá';
        reviewModal.classList.remove('hidden');
        reviewModal.classList.add('flex');
    }

    function closeReviewModal() {
        reviewModal.classList.add('hidden');
        reviewModal.classList.remove('flex');
        pendingReviewOrderId = null;
        pendingReviewProductId = null;
        pendingReviewExistingId = null;
        selectedRating = 0;
    }

    async function confirmReviewModal() {
        if (!selectedRating) {
            starRatingError.classList.remove('hidden');
            return;
        }
        const comment = reviewCommentInput.value.trim();
        if (comment.length < 5) {
            reviewCommentError.classList.remove('hidden');
            reviewCommentInput.focus();
            return;
        }
        const title = reviewTitleInput.value.trim();
        const body = {
            orderId: pendingReviewOrderId,
            rating: selectedRating,
            title: title || null,
            comment: comment
        };
        // Snapshot ids BEFORE closeReviewModal(), close sẽ set chúng = null
        const productIdSnapshot = pendingReviewProductId;
        const existingIdSnapshot = pendingReviewExistingId;
        if (productIdSnapshot == null) {
            showSummaryError('Không xác định được sản phẩm để đánh giá.');
            return;
        }
        closeReviewModal();
        try {
            if (existingIdSnapshot) {
                await putJson('/api/v1/products/' + productIdSnapshot + '/reviews/' + existingIdSnapshot, body);
                showSummarySuccess('Cập nhật đánh giá thành công.');
            } else {
                await postJson('/api/v1/products/' + productIdSnapshot + '/reviews', body);
                showSummarySuccess('Gửi đánh giá thành công.');
            }
            await loadOrders();
        } catch (err) {
            showSummaryError(err.message || 'Gửi đánh giá thất bại.');
        }
    }

    function openCancelModal(orderId) {
        pendingCancelOrderId = orderId;
        cancelReasonInput.value = '';
        cancelReasonError.classList.add('hidden');
        cancelModal.classList.remove('hidden');
        cancelModal.classList.add('flex');
        cancelReasonInput.focus();
    }

    function closeCancelModal() {
        cancelModal.classList.add('hidden');
        cancelModal.classList.remove('flex');
        pendingCancelOrderId = null;
    }

    async function confirmCancelModal() {
        const reason = cancelReasonInput.value.trim();
        if (!reason) {
            cancelReasonError.classList.remove('hidden');
            cancelReasonInput.focus();
            return;
        }
        // Snapshot orderId TRƯỚC khi close modal (close sẽ set pendingCancelOrderId = null)
        const orderIdSnapshot = pendingCancelOrderId;
        if (orderIdSnapshot == null) {
            showSummaryError('Không xác định được đơn hàng để hủy.');
            return;
        }
        closeCancelModal();
        try {
            await postJson('/api/v1/orders/' + orderIdSnapshot + '/cancel', { reason: reason });
            showSummarySuccess('Hủy đơn hàng thành công.');
            await loadOrders();
        } catch (err) {
            showSummaryError(err.message || 'Hủy đơn hàng thất bại.');
        }
    }

    function openRefundModal(orderId) {
        pendingRefundOrderId = orderId;
        pendingRefundFiles = [];
        refundReasonSelect.value = '';
        refundDescInput.value = '';
        refundImagePreview.innerHTML = '';
        refundImageError.textContent = '';
        refundImageError.classList.add('hidden');
        if (refundReasonError) refundReasonError.classList.add('hidden');
        if (refundImageInput) refundImageInput.value = '';
        refundModal.classList.remove('hidden');
        refundModal.classList.add('flex');
    }

    function closeRefundModal() {
        refundModal.classList.add('hidden');
        refundModal.classList.remove('flex');
        pendingRefundOrderId = null;
        pendingRefundFiles = [];
    }

    function handleRefundImageSelect() {
        const files = Array.from(refundImageInput.files || []);
        const valid = files.filter(function (f) {
            return f.size <= 5 * 1024 * 1024;
        });
        if (valid.length > 3) {
            refundImageError.textContent = 'Tối đa 3 ảnh được phép tải lên.';
            refundImageError.classList.remove('hidden');
            return;
        }
        pendingRefundFiles = valid;
        renderRefundPreviews(pendingRefundFiles);
        refundImageError.classList.add('hidden');
    }

    function renderRefundPreviews(files) {
        refundImagePreview.innerHTML = '';
        files.forEach(function (file) {
            var url = URL.createObjectURL(file);
            var div = document.createElement('div');
            div.className = 'relative w-20 h-20 rounded-lg overflow-hidden border border-slate-200';
            div.innerHTML = '<img src="' + url + '" class="w-full h-full object-cover"/>';
            refundImagePreview.appendChild(div);
        });
    }

    async function confirmRefundModal() {
        const reasonVal = refundReasonSelect.value;
        const descVal = refundDescInput.value.trim();
        if (!reasonVal) {
            if (refundReasonError) {
                refundReasonError.textContent = 'Vui lòng chọn lý do hoàn tiền.';
                refundReasonError.classList.remove('hidden');
            }
            refundReasonSelect.focus();
            return;
        }
        const reason = descVal ? (reasonVal + ': ' + descVal) : reasonVal;
        const orderIdSnapshot = pendingRefundOrderId;
        const filesSnapshot = pendingRefundFiles;
        closeRefundModal();
        try {
            await postRefundWithUpload(orderIdSnapshot, reason, filesSnapshot);
            showSummarySuccess('Yêu cầu hoàn tiền đã được gửi.');
            await loadOrders();
        } catch (err) {
            showSummaryError(err.message || 'Gửi yêu cầu hoàn tiền thất bại.');
        }
    }

    // ─────────────────────────────────────────────
    // REFUND BANK INFO MODAL
    // ─────────────────────────────────────────────
    function openRefundBankModal(order) {
        if (!refundBankModal) return;
        pendingRefundBankOrderId = order.id;
        refundBankNameInput.value = order.refundBankName || '';
        refundBankAccountNumberInput.value = order.refundBankAccountNumber || '';
        refundBankAccountHolderInput.value = order.refundBankAccountHolder || '';
        refundBankNoteInput.value = order.refundBankNote || '';
        [refundBankNameError, refundBankAccountNumberError, refundBankAccountHolderError].forEach(function (el) {
            if (el) el.classList.add('hidden');
        });
        refundBankModal.classList.remove('hidden');
        refundBankModal.classList.add('flex');
        setTimeout(function () { refundBankNameInput.focus(); }, 50);
    }

    function closeRefundBankModal() {
        if (!refundBankModal) return;
        refundBankModal.classList.add('hidden');
        refundBankModal.classList.remove('flex');
        pendingRefundBankOrderId = null;
    }

    async function confirmRefundBankModal() {
        const bankName = refundBankNameInput.value.trim();
        const accountNumber = refundBankAccountNumberInput.value.trim();
        const accountHolder = refundBankAccountHolderInput.value.trim();
        const note = refundBankNoteInput.value.trim();
        let hasError = false;
        if (!bankName) { refundBankNameError.classList.remove('hidden'); hasError = true; }
        if (!accountNumber) { refundBankAccountNumberError.classList.remove('hidden'); hasError = true; }
        if (!accountHolder) { refundBankAccountHolderError.classList.remove('hidden'); hasError = true; }
        if (hasError) return;

        const orderIdSnapshot = pendingRefundBankOrderId;
        if (!orderIdSnapshot) {
            showSummaryError('Không xác định được đơn hàng để gửi thông tin tài khoản.');
            return;
        }
        refundBankModalConfirm.disabled = true;
        try {
            await postJson('/api/v1/orders/' + orderIdSnapshot + '/refund/bank-info', {
                bankName: bankName,
                accountNumber: accountNumber,
                accountHolder: accountHolder,
                note: note || null
            });
            closeRefundBankModal();
            showSummarySuccess('Đã gửi thông tin tài khoản. CSKH sẽ chuyển khoản trong thời gian sớm nhất.');
            await loadOrders();
        } catch (err) {
            showSummaryError(err.message || 'Gửi thông tin tài khoản thất bại.');
        } finally {
            refundBankModalConfirm.disabled = false;
        }
    }

    // ─────────────────────────────────────────────
    // PAYMENT NOTICE
    // ─────────────────────────────────────────────
    function renderPaymentNoticeFromQuery() {
        if (!paymentNoticeEl) return;
        const params = new URLSearchParams(window.location.search || '');
        const payment = params.get('payment');
        const orderCode = params.get('orderCode');
        if (!payment) {
            paymentNoticeEl.innerHTML = '';
            return;
        }
        const orderLabel = orderCode ? (' cho đơn ' + escapeHtml(orderCode)) : '';
        if (payment === 'success') {
            paymentNoticeEl.innerHTML = '<div class="rounded-lg border border-amber-200 bg-amber-50 text-amber-700 text-sm p-3 flex items-center gap-2">' +
                '<i class="fa-solid fa-spinner fa-spin"></i>' +
                '<span>Đang đối soát giao dịch SePay' + orderLabel + '… Vui lòng đợi.</span>' +
                '</div>';
            if (orderCode) {
                pollSepayStatus(orderCode);
            }
        } else if (payment === 'error') {
            paymentNoticeEl.innerHTML = '<div class="rounded-lg border border-red-200 bg-red-50 text-red-700 text-sm p-3">Thanh toán SePay thất bại' + orderLabel + '. Bạn có thể thử lại hoặc chọn phương thức khác.</div>';
        } else if (payment === 'cancel') {
            paymentNoticeEl.innerHTML = '<div class="rounded-lg border border-slate-200 bg-slate-50 text-slate-700 text-sm p-3">Bạn đã hủy phiên thanh toán SePay' + orderLabel + '. Đơn vẫn ở trạng thái chờ thanh toán cho đến khi timeout hoặc thanh toán thành công.</div>';
        } else {
            paymentNoticeEl.innerHTML = '';
        }
    }

    async function pollSepayStatus(orderCode) {
        const maxAttempts = 8;     // 8 × 2.5s ≈ 20s
        const intervalMs = 2500;
        const escapedCode = escapeHtml(orderCode);
        for (let attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                const resp = await fetch('/api/v1/payments/sepay/check/' + encodeURIComponent(orderCode), {
                    method: 'POST',
                    headers: buildJsonHeaders()
                });
                if (resp.ok) {
                    const data = await resp.json();
                    if (data && data.paid) {
                        paymentNoticeEl.innerHTML = '<div class="rounded-lg border border-emerald-200 bg-emerald-50 text-emerald-700 text-sm p-3 flex items-center gap-2">' +
                            '<i class="fa-solid fa-circle-check"></i>' +
                            '<span>Đã ghi nhận thanh toán SePay cho đơn ' + escapedCode + '.</span>' +
                            '</div>';
                        await loadOrders();
                        return;
                    }
                }
            } catch (err) {
                // ignore — sẽ thử lại lần sau
            }
            if (attempt < maxAttempts) {
                await new Promise(function (resolve) { setTimeout(resolve, intervalMs); });
            }
        }
        paymentNoticeEl.innerHTML = '<div class="rounded-lg border border-amber-200 bg-amber-50 text-amber-700 text-sm p-3">' +
            'Hệ thống chưa nhận được xác nhận thanh toán cho đơn ' + escapedCode + '. Nếu bạn đã thanh toán thành công, hãy đợi vài phút rồi tải lại trang — hoặc dùng nút <strong>Kiểm tra thanh toán</strong> trong đơn để đối soát thủ công.' +
            '</div>';
    }

    // ─────────────────────────────────────────────
    // LOAD ORDERS
    // ─────────────────────────────────────────────
    async function loadOrders() {
        setLoading(true);
        clearSummaryError();
        try {
            const response = await fetch('/api/v1/orders/my', {
                method: 'GET',
                headers: buildJsonHeaders()
            });
            if (response.status === 401 || response.status === 403) {
                window.location.href = '/login';
                return;
            }
            const data = await response.json();
            if (!response.ok) {
                throw new Error((data && data.message) || 'Không tải được danh sách đơn hàng.');
            }
            renderOrders(Array.isArray(data) ? data : []);
        } catch (err) {
            showSummaryError(err.message || 'Có lỗi hệ thống.');
        } finally {
            setLoading(false);
        }
    }

    // ─────────────────────────────────────────────
    // RENDER
    // ─────────────────────────────────────────────
    function renderOrders(orders) {
        listEl.innerHTML = '';
        if (!orders.length) {
            emptyEl.classList.remove('hidden');
            return;
        }
        emptyEl.classList.add('hidden');
        orders.forEach(function (order) {
            const card = document.createElement('article');
            card.className = 'bg-white rounded-xl border border-slate-200 p-4';
            card.innerHTML = buildOrderCard(order);
            listEl.appendChild(card);
            bindOrderActions(card, order);
        });
    }

    function statusLabel(code) {
        const m = {
            PENDING_PAYMENT: 'Chờ thanh toán',
            PENDING_CONFIRMATION: 'Chờ xác nhận',
            PROCESSING: 'Đang xử lý',
            SHIPPING: 'Đang giao hàng',
            DELIVERED: 'Đã giao hàng',
            REFUND_REQUESTED: 'Yêu cầu hoàn tiền',
            REFUND_REJECTED: 'Từ chối hoàn tiền',
            CANCELLED: 'Đã hủy',
            RETURN_REFUND: 'Đã duyệt hoàn tiền',
            REFUND_COMPLETED: 'Đã hoàn tiền'
        };
        return m[code] || code || '';
    }

    function statusColor(code) {
        const m = {
            PENDING_PAYMENT: 'text-amber-600',
            PENDING_CONFIRMATION: 'text-blue-600',
            PROCESSING: 'text-indigo-600',
            SHIPPING: 'text-purple-600',
            DELIVERED: 'text-emerald-600',
            REFUND_REQUESTED: 'text-orange-600',
            REFUND_REJECTED: 'text-red-600',
            CANCELLED: 'text-slate-400',
            RETURN_REFUND: 'text-teal-600',
            REFUND_COMPLETED: 'text-emerald-600'
        };
        return m[code] || 'text-slate-600';
    }

    function paymentLabel(m) {
        if (m === 'COD') return 'COD (thanh toán khi nhận)';
        if (m === 'SEPAY') return 'SePay';
        return m || '';
    }

    function buildPricingSection(order) {
        const subtotal = Number(order.subtotal != null ? order.subtotal : 0);
        const discount = Number(order.discount != null ? order.discount : 0);
        const ship = Number(order.shippingFee != null ? order.shippingFee : 0);
        const total = Number(order.total || 0);
        const vCode = order.voucher && order.voucher.code ? order.voucher.code : '';
        let html = '<div class="mt-3 rounded-lg border border-slate-100 bg-slate-50/80 p-3 space-y-1 text-sm">';
        html += '<div class="flex justify-between"><span class="text-slate-600">Tạm tính</span><span>' + subtotal.toLocaleString('vi-VN') + 'đ</span></div>';
        if (discount > 0) {
            html += '<div class="flex justify-between text-emerald-700"><span>Giảm giá' + (vCode ? ' (' + escapeHtml(vCode) + ')' : '') + '</span><span>−' + discount.toLocaleString('vi-VN') + 'đ</span></div>';
        }
        if (ship > 0) {
            html += '<div class="flex justify-between"><span class="text-slate-600">Phí vận chuyển</span><span>' + ship.toLocaleString('vi-VN') + 'đ</span></div>';
        }
        html += '<div class="flex justify-between font-semibold text-slate-900 pt-1 mt-1 border-t border-slate-200"><span>Tổng thanh toán</span><span>' + total.toLocaleString('vi-VN') + 'đ</span></div>';
        html += '</div>';
        return html;
    }

    function buildOrderDetailContent(order) {
        const lines = [];
        if (order.createdAt) {
            try {
                const d = new Date(order.createdAt);
                if (!isNaN(d.getTime())) {
                    lines.push('<div><span class="text-slate-500">Đặt lúc:</span> ' + escapeHtml(d.toLocaleString('vi-VN')) + '</div>');
                }
            } catch (e) { /* ignore */ }
        }
        if (order.recipientName) lines.push('<div><span class="text-slate-500">Người nhận:</span> ' + escapeHtml(order.recipientName) + '</div>');
        if (order.recipientPhone) lines.push('<div><span class="text-slate-500">ĐT:</span> ' + escapeHtml(order.recipientPhone) + '</div>');
        if (order.recipientEmail) lines.push('<div><span class="text-slate-500">Email:</span> ' + escapeHtml(order.recipientEmail) + '</div>');
        if (order.shippingAddress) lines.push('<div><span class="text-slate-500">Địa chỉ:</span> ' + escapeHtml(order.shippingAddress) + '</div>');
        if (order.note) lines.push('<div><span class="text-slate-500">Ghi chú:</span> ' + escapeHtml(order.note) + '</div>');
        if (order.trackingNumber) lines.push('<div><span class="text-slate-500">Vận đơn:</span> ' + escapeHtml(order.trackingNumber) + '</div>');
        if (order.cancelReason) lines.push('<div><span class="text-slate-500">Lý do hủy:</span> <span class="text-red-600">' + escapeHtml(order.cancelReason) + '</span></div>');
        if (order.refundReason) lines.push('<div><span class="text-slate-500">Lý do hoàn tiền:</span> <span class="text-orange-600">' + escapeHtml(order.refundReason) + '</span></div>');
        if (order.refundRejectNote) lines.push('<div><span class="text-slate-500">Lý do từ chối:</span> <span class="text-red-600">' + escapeHtml(order.refundRejectNote) + '</span></div>');
        return lines.length ? lines.join('') : '<div class="text-slate-400">Không có thêm thông tin.</div>';
    }

    function buildOrderCard(order) {
        const items = Array.isArray(order.items) ? order.items : [];
        const isDelivered = order.status === 'DELIVERED';
        const itemsHtml = items.map(function (item) {
            const productId = item.product && item.product.id ? item.product.id : null;
            const slug = item.product && item.product.slug ? item.product.slug : null;
            const name = item.productName || (item.product && item.product.name) || ('Sản phẩm #' + (productId || ''));
            const qty = item.quantity || 0;
            const lineTotal = item.lineTotal != null ? Number(item.lineTotal) : 0;
            const wMonths = item.warrantyMonths != null ? item.warrantyMonths : (item.product && item.product.warrantyMonths);
            let warrantyLine = '';
            if (wMonths != null && wMonths > 0) {
                if (isDelivered && order.deliveredAt) {
                    try {
                        const end = new Date(order.deliveredAt);
                        if (!isNaN(end.getTime())) {
                            const e2 = new Date(end);
                            e2.setMonth(e2.getMonth() + Number(wMonths));
                            warrantyLine = '<div class="text-xs text-slate-500 mt-1">Bảo hành ' + wMonths + ' tháng - hạn dự kiến: ' + e2.toLocaleDateString('vi-VN') + '</div>';
                        } else {
                            warrantyLine = '<div class="text-xs text-slate-500 mt-1">Bảo hành ' + wMonths + ' tháng (kể từ ngày giao)</div>';
                        }
                    } catch (e) {
                        warrantyLine = '<div class="text-xs text-slate-500 mt-1">Bảo hành ' + wMonths + ' tháng</div>';
                    }
                } else {
                    warrantyLine = '<div class="text-xs text-slate-500 mt-1">Bảo hành ' + wMonths + ' tháng kể từ khi giao hàng</div>';
                }
            }
            let refundImagesHtml = '';
            if (order.status === 'REFUND_REQUESTED' && order.refundEvidenceUrls) {
                const urls = order.refundEvidenceUrls.split(',').filter(Boolean);
                if (urls.length > 0) {
                    refundImagesHtml = '<div class="flex gap-1 mt-1">';
                    urls.forEach(function (url) {
                        refundImagesHtml += '<img src="' + escapeHtml(url.trim()) + '" class="w-12 h-12 object-cover rounded border border-slate-200 cursor-pointer hover:opacity-75" onclick="window.open(\'' + escapeHtml(url.trim()) + '\', \'_blank\')"/>';
                    });
                    refundImagesHtml += '</div>';
                }
            }
            let itemActionsHtml = '';
            if (isDelivered && productId) {
                itemActionsHtml = buildItemReviewActions(order.id, productId, name, slug);
            }
            return '<div class="flex items-center justify-between text-sm py-2 border-b border-slate-100 last:border-b-0 gap-3">' +
                '<div class="min-w-0 flex-1"><span>' + escapeHtml(name) + ' x' + qty + '</span>' + warrantyLine + refundImagesHtml + itemActionsHtml + '</div>' +
                '<span class="font-medium shrink-0">' + lineTotal.toLocaleString('vi-VN') + 'đ</span>' +
                '</div>';
        }).join('');

        const total = Number(order.total || 0);
        return '' +
            '<div class="flex items-start justify-between gap-3">' +
            '  <div>' +
            '    <h2 class="font-semibold text-lg">#' + escapeHtml(order.orderCode || '') + '</h2>' +
            '    <p class="text-sm text-slate-500">Trạng thái: <span class="font-medium ' + statusColor(order.status) + '">' + escapeHtml(statusLabel(order.status)) + '</span></p>' +
            '    <p class="text-sm text-slate-500">Thanh toán: ' + escapeHtml(paymentLabel(order.paymentMethod)) + '</p>' +
            '  </div>' +
            '</div>' +
            buildPricingSection(order) +
            buildSepayBlock(order, total) +
            buildRefundBankBlock(order) +
            '<div class="mt-3 rounded-lg border border-slate-200 p-3">' + itemsHtml + '</div>' +
            '<div class="js-order-detail hidden mt-3 rounded-lg border border-dashed border-slate-200 bg-slate-50/80 p-3 text-sm space-y-1">' +
            buildOrderDetailContent(order) +
            '</div>' +
            '<div class="mt-3 flex flex-wrap gap-2">' +
            buildActionButtons(order) +
            '</div>';
    }

    function buildItemReviewActions(orderId, productId, productName, slug) {
        return '<div class="mt-1 flex gap-2 flex-wrap">' +
            (slug ? ('<a href="/products/' + encodeURIComponent(slug) + '" class="text-xs text-brand-600 hover:underline">Xem sản phẩm</a>') : '') +
            '<button type="button" data-action="reviewItem" data-product-id="' + productId + '" data-product-name="' + escapeHtml(productName) + '" data-order-id="' + orderId + '" class="text-xs px-2 py-1 rounded border border-amber-300 text-amber-700 hover:bg-amber-50 transition">Đánh giá</button>' +
            '</div>';
    }

    function buildSepayBlock(order, total) {
        if (!(order.paymentMethod === 'SEPAY' && order.status === 'PENDING_PAYMENT')) {
            return '';
        }
        const transferContent = order.sepayTransferContent || ('Thanh toán ' + (order.orderCode || ''));
        const checkoutAction = order.sepayCheckoutActionUrl || '';
        const checkoutFields = order.sepayCheckoutFields || {};
        const orderCode = order.orderCode || '';
        const gatewayFormHtml = (checkoutAction && checkoutFields && Object.keys(checkoutFields).length)
            ? '<form class="inline-block" method="POST" action="' + escapeHtml(checkoutAction) + '">' +
              buildSepayHiddenInputs(checkoutFields) +
              '<button type="submit" class="px-3 py-2 rounded-lg bg-emerald-600 hover:bg-emerald-700 text-white text-sm font-semibold">Thanh toán với SePay</button>' +
              '</form>'
            : '<div class="text-xs text-amber-700">Chưa cấu hình merchant-id/secret-key SePay để mở cổng thanh toán.</div>';
        const recheckBtn = orderCode
            ? '<button type="button" data-action="recheckSepay" data-order-code="' + escapeHtml(orderCode) + '" class="px-3 py-2 rounded-lg border border-emerald-300 text-emerald-700 hover:bg-emerald-100 text-sm font-medium transition inline-flex items-center gap-1.5">' +
              '<i class="fa-solid fa-rotate"></i><span>Kiểm tra thanh toán</span>' +
              '</button>'
            : '';
        return '' +
            '<div class="mt-3 rounded-lg border border-emerald-200 bg-emerald-50 p-3">' +
            '  <div class="font-semibold text-emerald-700 mb-1">Thanh toán SePay</div>' +
            '  <div class="text-sm text-slate-700">Số tiền cần chuyển: <strong>' + total.toLocaleString('vi-VN') + 'đ</strong></div>' +
            '  <div class="text-sm text-slate-700">Nội dung chuyển khoản: <code>' + escapeHtml(transferContent) + '</code></div>' +
            '  <div class="mt-3 flex flex-wrap gap-2">' + gatewayFormHtml + recheckBtn + '</div>' +
            '  <div class="text-xs text-slate-500 mt-2">Đã thanh toán nhưng đơn chưa cập nhật? Bấm <strong>Kiểm tra thanh toán</strong> để đối soát ngay. Đơn sẽ tự hủy sau 30 phút nếu chưa thanh toán.</div>' +
            '</div>';
    }

    function buildSepayHiddenInputs(fields) {
        return Object.keys(fields).map(function (key) {
            return '<input type="hidden" name="' + escapeHtml(key) + '" value="' + escapeHtml(String(fields[key] || '')) + '"/>';
        }).join('');
    }

    function buildRefundBankBlock(order) {
        if (order.status !== 'RETURN_REFUND' && order.status !== 'REFUND_COMPLETED') return '';
        const total = Number(order.total || 0);

        // ── REFUND_COMPLETED — emerald success block ──────────────
        if (order.status === 'REFUND_COMPLETED') {
            const completedDate = formatDateTime(order.refundCompletedAt);
            const completedNote = order.refundCompletedNote
                ? '<div class="text-sm text-slate-700 mt-2"><span class="text-slate-500">Ghi chú từ CSKH:</span> ' + escapeHtml(order.refundCompletedNote) + '</div>'
                : '';
            const bankBlock = order.refundBankAccountNumber ? (
                '  <div class="grid sm:grid-cols-2 gap-x-4 gap-y-1 text-sm text-slate-700 mt-3 pt-3 border-t border-emerald-200">' +
                '    <div><span class="text-slate-500">Ngân hàng:</span> <span class="font-medium">' + escapeHtml(order.refundBankName || '') + '</span></div>' +
                '    <div><span class="text-slate-500">Số TK:</span> <span class="font-mono font-medium">' + escapeHtml(order.refundBankAccountNumber || '') + '</span></div>' +
                '    <div class="sm:col-span-2"><span class="text-slate-500">Chủ TK:</span> <span class="font-medium uppercase">' + escapeHtml(order.refundBankAccountHolder || '') + '</span></div>' +
                '  </div>'
            ) : '';
            return '' +
                '<div class="mt-3 rounded-lg border border-emerald-200 bg-emerald-50 p-3">' +
                '  <div class="font-semibold text-emerald-800 mb-1"><i class="fa-solid fa-circle-check mr-1"></i>Đã hoàn tiền thành công</div>' +
                '  <div class="text-sm text-slate-700">Số tiền hoàn: <strong class="text-emerald-700">' + total.toLocaleString('vi-VN') + 'đ</strong></div>' +
                (completedDate ? ('  <div class="text-sm text-slate-700">Hoàn lúc: <strong>' + escapeHtml(completedDate) + '</strong></div>') : '') +
                completedNote +
                bankBlock +
                '  <div class="text-xs text-slate-500 mt-3">Vui lòng kiểm tra số dư tài khoản ngân hàng. Nếu sau 24h chưa nhận được, hãy liên hệ CSKH.</div>' +
                '</div>';
        }

        // ── RETURN_REFUND — teal block (đã duyệt, chờ chuyển khoản) ──
        const submitted = !!order.refundBankSubmittedAt;
        const totalLine = '<div class="text-sm text-slate-700">Số tiền hoàn: <strong class="text-teal-700">' + total.toLocaleString('vi-VN') + 'đ</strong></div>';
        if (submitted) {
            const submittedDate = formatDateTime(order.refundBankSubmittedAt);
            return '' +
                '<div class="mt-3 rounded-lg border border-teal-200 bg-teal-50 p-3">' +
                '  <div class="font-semibold text-teal-800 mb-1"><i class="fa-solid fa-circle-check mr-1"></i>Đã gửi thông tin tài khoản nhận hoàn tiền</div>' +
                totalLine +
                '  <div class="grid sm:grid-cols-2 gap-x-4 gap-y-1 text-sm text-slate-700 mt-2">' +
                '    <div><span class="text-slate-500">Ngân hàng:</span> <span class="font-medium">' + escapeHtml(order.refundBankName || '') + '</span></div>' +
                '    <div><span class="text-slate-500">Số TK:</span> <span class="font-mono font-medium">' + escapeHtml(order.refundBankAccountNumber || '') + '</span></div>' +
                '    <div class="sm:col-span-2"><span class="text-slate-500">Chủ TK:</span> <span class="font-medium uppercase">' + escapeHtml(order.refundBankAccountHolder || '') + '</span></div>' +
                (order.refundBankNote ? ('    <div class="sm:col-span-2"><span class="text-slate-500">Ghi chú:</span> ' + escapeHtml(order.refundBankNote) + '</div>') : '') +
                '  </div>' +
                '  <div class="mt-3 flex flex-wrap gap-2">' +
                '    <button type="button" data-action="editRefundBank" class="px-3 py-2 rounded-lg border border-teal-300 text-teal-700 hover:bg-teal-100 text-sm font-medium transition inline-flex items-center gap-1.5"><i class="fa-solid fa-pen-to-square"></i><span>Sửa thông tin</span></button>' +
                '  </div>' +
                '  <div class="text-xs text-slate-500 mt-2">Đã gửi lúc ' + escapeHtml(submittedDate) + '. CSKH sẽ chuyển khoản trong thời gian sớm nhất.</div>' +
                '</div>';
        }
        return '' +
            '<div class="mt-3 rounded-lg border border-teal-200 bg-teal-50 p-3">' +
            '  <div class="font-semibold text-teal-800 mb-1"><i class="fa-solid fa-money-bill-transfer mr-1"></i>Yêu cầu hoàn tiền đã được duyệt</div>' +
            totalLine +
            '  <div class="text-sm text-slate-700 mt-1">Vui lòng cung cấp thông tin tài khoản ngân hàng để CSKH chuyển khoản hoàn tiền cho bạn.</div>' +
            '  <div class="mt-3 flex flex-wrap gap-2">' +
            '    <button type="button" data-action="submitRefundBank" class="px-3 py-2 rounded-lg bg-teal-600 hover:bg-teal-700 text-white text-sm font-semibold transition inline-flex items-center gap-1.5"><i class="fa-solid fa-building-columns"></i><span>Điền thông tin tài khoản</span></button>' +
            '  </div>' +
            '</div>';
    }

    function formatDateTime(value) {
        if (!value) return '';
        try {
            const d = new Date(value);
            if (isNaN(d.getTime())) return '';
            return d.toLocaleString('vi-VN', { day: '2-digit', month: '2-digit', year: 'numeric', hour: '2-digit', minute: '2-digit' });
        } catch (e) { return ''; }
    }

    function buildActionButtons(order) {
        const status = order.status;
        const buttons = [];

        // Chi tiết đơn — luôn có
        buttons.push('<button type="button" data-action="toggleDetail" class="px-3 py-2 rounded-lg border border-slate-300 hover:bg-slate-50 text-sm transition">Chi tiết đơn</button>');

        // Hủy đơn — PENDING_PAYMENT, PENDING_CONFIRMATION, PROCESSING
        if (['PENDING_PAYMENT', 'PENDING_CONFIRMATION', 'PROCESSING'].indexOf(status) >= 0) {
            buttons.push('<button type="button" data-action="cancel" class="px-3 py-2 rounded-lg border border-red-300 text-red-600 hover:bg-red-50 text-sm transition">Hủy đơn</button>');
        }

        // SHIPPING — chỉ có "Đã nhận hàng". Trả hàng/Hoàn tiền chỉ mở sau khi DELIVERED.
        if (status === 'SHIPPING') {
            buttons.push('<button type="button" data-action="markDelivered" class="px-3 py-2 rounded-lg border border-emerald-300 text-emerald-700 hover:bg-emerald-50 text-sm font-medium transition">Đã nhận hàng</button>');
        }

        // DELIVERED — Trả hàng/Hoàn tiền + Mua lại
        if (status === 'DELIVERED') {
            buttons.push('<button type="button" data-action="requestRefund" class="px-3 py-2 rounded-lg border border-orange-300 text-orange-600 hover:bg-orange-50 text-sm transition">Trả hàng/Hoàn tiền</button>');
            buttons.push('<button type="button" data-action="rebuy" class="px-3 py-2 rounded-lg border border-slate-300 hover:bg-slate-50 text-sm transition">Mua lại</button>');
        }

        // REFUND_REJECTED — Yêu cầu hoàn tiền lại
        if (status === 'REFUND_REJECTED') {
            buttons.push('<button type="button" data-action="requestRefund" class="px-3 py-2 rounded-lg border border-orange-300 text-orange-600 hover:bg-orange-50 text-sm transition">Yêu cầu hoàn tiền lại</button>');
        }

        return buttons.join('');
    }

    // ─────────────────────────────────────────────
    // BIND ORDER ACTIONS
    // ─────────────────────────────────────────────
    function bindOrderActions(card, order) {
        // Toggle detail
        const detailBtn = card.querySelector('[data-action="toggleDetail"]');
        const detailPane = card.querySelector('.js-order-detail');
        if (detailBtn && detailPane) {
            detailBtn.addEventListener('click', function () {
                detailPane.classList.toggle('hidden');
                detailBtn.textContent = detailPane.classList.contains('hidden') ? 'Chi tiết đơn' : 'Ẩn chi tiết';
            });
        }

        // Cancel
        const cancelBtn = card.querySelector('[data-action="cancel"]');
        if (cancelBtn) {
            cancelBtn.addEventListener('click', function () {
                openCancelModal(order.id);
            });
        }

        // Submit / edit refund bank info (RETURN_REFUND only)
        const submitBankBtn = card.querySelector('[data-action="submitRefundBank"]');
        if (submitBankBtn) {
            submitBankBtn.addEventListener('click', function () {
                openRefundBankModal(order);
            });
        }
        const editBankBtn = card.querySelector('[data-action="editRefundBank"]');
        if (editBankBtn) {
            editBankBtn.addEventListener('click', function () {
                openRefundBankModal(order);
            });
        }

        // Recheck SePay payment status (PENDING_PAYMENT only)
        const recheckBtn = card.querySelector('[data-action="recheckSepay"]');
        if (recheckBtn) {
            recheckBtn.addEventListener('click', async function () {
                const code = recheckBtn.getAttribute('data-order-code');
                if (!code) return;
                const original = recheckBtn.innerHTML;
                recheckBtn.disabled = true;
                recheckBtn.innerHTML = '<i class="fa-solid fa-spinner fa-spin"></i><span class="ml-1.5">Đang kiểm tra…</span>';
                try {
                    const resp = await fetch('/api/v1/payments/sepay/check/' + encodeURIComponent(code), {
                        method: 'POST',
                        headers: buildJsonHeaders()
                    });
                    const data = resp.ok ? await resp.json() : null;
                    if (data && data.paid) {
                        showSummarySuccess('Đã ghi nhận thanh toán cho đơn ' + code + '.');
                        await loadOrders();
                    } else {
                        let msg = 'Chưa ghi nhận giao dịch cho đơn ' + code + '. ';
                        if (data && data.pollingAvailable === false) {
                            msg += 'Server chưa cấu hình SEPAY_API_TOKEN — không thể tự đối soát qua API. Nhân viên cần xác nhận thủ công, hoặc đặt env var SEPAY_API_TOKEN trong run config.';
                        } else if (data && data.pollError) {
                            msg += 'Polling lỗi: ' + data.pollError;
                        } else {
                            msg += 'Giao dịch có thể chưa kịp về (delay 10–60s). Hãy đợi rồi thử lại.';
                        }
                        showSummaryError(msg);
                        recheckBtn.disabled = false;
                        recheckBtn.innerHTML = original;
                    }
                } catch (err) {
                    showSummaryError(err.message || 'Không thể kiểm tra trạng thái thanh toán.');
                    recheckBtn.disabled = false;
                    recheckBtn.innerHTML = original;
                }
            });
        }

        // Mark delivered (SHIPPING -> DELIVERED) — dùng endpoint customer-only, không qua /status (STAFF-only)
        const deliveredBtn = card.querySelector('[data-action="markDelivered"]');
        if (deliveredBtn) {
            deliveredBtn.addEventListener('click', async function () {
                if (!confirm('Bạn xác nhận đã nhận được đơn hàng này?')) return;
                try {
                    await postJson('/api/v1/orders/' + order.id + '/mark-delivered', {});
                    showSummarySuccess('Xác nhận đã nhận hàng thành công.');
                    await loadOrders();
                } catch (err) {
                    showSummaryError(err.message || 'Xác nhận thất bại.');
                }
            });
        }

        // Request refund (SHIPPING/DELIVERED -> REFUND_REQUESTED)
        const refundBtn = card.querySelector('[data-action="requestRefund"]');
        if (refundBtn) {
            refundBtn.addEventListener('click', function () {
                openRefundModal(order.id);
            });
        }

        // Rebuy
        const rebuyBtn = card.querySelector('[data-action="rebuy"]');
        if (rebuyBtn) {
            rebuyBtn.addEventListener('click', function () {
                const items = Array.isArray(order.items) ? order.items : [];
                // Mỗi SP từ đơn cũ → cộng +1 vào giỏ (không nhân theo qty đơn cũ).
                // Khách có thể tự tăng số lượng hoặc untick SP không muốn mua trong giỏ.
                const localItems = items.map(function (item) {
                    return {
                        productId: item.product && item.product.id ? item.product.id : null,
                        quantity: 1,
                        selected: true
                    };
                }).filter(function (x) { return x.productId != null; });
                if (!localItems.length) {
                    showSummaryError('Không thể mua lại vì đơn không có sản phẩm hợp lệ.');
                    return;
                }
                localStorage.setItem('local_cart_items', JSON.stringify(localItems));
                window.location.href = '/cart';
            });
        }

        // Review per item (DELIVERED orders)
        card.querySelectorAll('[data-action="reviewItem"]').forEach(function (btn) {
            btn.addEventListener('click', function () {
                const productId = parseInt(btn.dataset.productId);
                const productName = btn.dataset.productName;
                const orderId = parseInt(btn.dataset.orderId);
                loadAndOpenReview(orderId, productId, productName);
            });
        });
    }

    async function loadAndOpenReview(orderId, productId, productName) {
        // Try to load existing review for this order + product
        try {
            const response = await fetch(
                '/api/v1/products/' + productId + '/reviews/me?orderId=' + orderId,
                { method: 'GET', headers: buildJsonHeaders() }
            );
            let existingReview = null;
            if (response.ok) {
                existingReview = await response.json();
            }
            openReviewModal(orderId, productId, productName, existingReview);
        } catch (e) {
            openReviewModal(orderId, productId, productName, null);
        }
    }

    // ─────────────────────────────────────────────
    // API HELPERS
    // ─────────────────────────────────────────────
    async function postJson(url, body) {
        clearSummaryError();
        const response = await fetch(url, {
            method: 'POST',
            headers: buildJsonHeaders(),
            body: JSON.stringify(body)
        });
        const data = await response.json();
        if (!response.ok) {
            throw new Error((data && data.message) || 'Yêu cầu thất bại.');
        }
        return data;
    }

    async function putJson(url, body) {
        clearSummaryError();
        const response = await fetch(url, {
            method: 'PUT',
            headers: buildJsonHeaders(),
            body: JSON.stringify(body)
        });
        const data = await response.json();
        if (!response.ok) {
            throw new Error((data && data.message) || 'Yêu cầu thất bại.');
        }
        return data;
    }

    async function postRefundWithUpload(orderId, reason, files) {
        clearSummaryError();
        const formData = new FormData();
        formData.append('reason', reason);
        (files || []).forEach(function (file) {
            formData.append('evidenceImages', file);
        });
        const response = await fetch('/api/v1/orders/' + orderId + '/refund/upload', {
            method: 'POST',
            headers: buildMultipartHeaders(),
            body: formData
        });
        const data = await response.json();
        if (!response.ok) {
            throw new Error((data && data.message) || 'Yêu cầu thất bại.');
        }
        return data;
    }

    // ─────────────────────────────────────────────
    // UI HELPERS
    // ─────────────────────────────────────────────
    function setLoading(value) {
        if (!loadingEl) return;
        loadingEl.classList.toggle('hidden', !value);
    }

    function showSummaryError(message) {
        if (!summaryErrorEl) return;
        summaryErrorEl.innerHTML = '<div class="rounded-lg border border-red-200 bg-red-50 text-red-700 text-sm p-3">' + escapeHtml(message) + '</div>';
    }

    function showSummarySuccess(message) {
        if (!summaryErrorEl) return;
        summaryErrorEl.innerHTML = '<div class="rounded-lg border border-emerald-200 bg-emerald-50 text-emerald-700 text-sm p-3">' + escapeHtml(message) + '</div>';
        setTimeout(function () { clearSummaryError(); }, 4000);
    }

    function clearSummaryError() {
        if (!summaryErrorEl) return;
        summaryErrorEl.innerHTML = '';
    }

    function escapeHtml(text) {
        return String(text || '')
            .replace(/&/g, '&amp;')
            .replace(/</g, '&lt;')
            .replace(/>/g, '&gt;')
            .replace(/"/g, '&quot;')
            .replace(/'/g, '&#039;');
    }
})();
