(function () {
    'use strict';

    const listEl = document.getElementById('ordersList');
    const loadingEl = document.getElementById('ordersLoading');
    const emptyEl = document.getElementById('ordersEmpty');
    const paymentNoticeEl = document.getElementById('orderPaymentNotice');
    const summaryErrorEl = document.getElementById('orderSummaryError');

    function api() { return window.TechPartsApi; }

    function buildJsonHeaders(isGet) {
        const a = api();
        return a && a.buildJsonHeaders ? a.buildJsonHeaders(isGet) : { 'Accept': 'application/json' };
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
        await loadOrders();
    }

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
            paymentNoticeEl.innerHTML = '<div class="rounded-lg border border-amber-200 bg-amber-50 text-amber-700 text-sm p-3">Bạn vừa hoàn tất thanh toán SePay' + orderLabel + '. Hệ thống đang chờ IPN xác nhận, vui lòng đợi vài giây và tải lại nếu cần.</div>';
        } else if (payment === 'error') {
            paymentNoticeEl.innerHTML = '<div class="rounded-lg border border-red-200 bg-red-50 text-red-700 text-sm p-3">Thanh toán SePay thất bại' + orderLabel + '. Bạn có thể thử lại hoặc chọn phương thức khác.</div>';
        } else if (payment === 'cancel') {
            paymentNoticeEl.innerHTML = '<div class="rounded-lg border border-slate-200 bg-slate-50 text-slate-700 text-sm p-3">Bạn đã hủy phiên thanh toán SePay' + orderLabel + '. Đơn vẫn ở trạng thái chờ thanh toán cho đến khi timeout hoặc thanh toán thành công.</div>';
        } else {
            paymentNoticeEl.innerHTML = '';
        }
    }

    async function loadOrders() {
        setLoading(true);
        clearSummaryError();
        try {
            const response = await fetch('/api/v1/orders/my', {
                method: 'GET',
                headers: buildJsonHeaders(true)
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
            RETURN_REFUND: 'Trả hàng / hoàn tiền'
        };
        return m[code] || code || '';
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
        if (order.recipientPhone) lines.push('<div><span class="text-slate-500">SĐT:</span> ' + escapeHtml(order.recipientPhone) + '</div>');
        if (order.recipientEmail) lines.push('<div><span class="text-slate-500">Email:</span> ' + escapeHtml(order.recipientEmail) + '</div>');
        if (order.shippingAddress) lines.push('<div><span class="text-slate-500">Địa chỉ:</span> ' + escapeHtml(order.shippingAddress) + '</div>');
        if (order.note) lines.push('<div><span class="text-slate-500">Ghi chú:</span> ' + escapeHtml(order.note) + '</div>');
        if (order.trackingNumber) lines.push('<div><span class="text-slate-500">Vận đơn:</span> ' + escapeHtml(order.trackingNumber) + '</div>');
        return lines.length ? lines.join('') : '<div class="text-slate-400">Không có thêm thông tin.</div>';
    }

    function buildOrderCard(order) {
        const items = Array.isArray(order.items) ? order.items : [];
        const itemsHtml = items.map(function (item) {
            const name = item.productName || (item.product && item.product.name) || ('Sản phẩm #' + (item.product && item.product.id ? item.product.id : ''));
            const qty = item.quantity || 0;
            const lineTotal = item.lineTotal != null ? Number(item.lineTotal) : 0;
            const wMonths = item.warrantyMonths != null ? item.warrantyMonths : (item.product && item.product.warrantyMonths);
            let warrantyLine = '';
            if (wMonths != null && wMonths > 0) {
                if (order.status === 'DELIVERED' && order.deliveredAt) {
                    try {
                        const end = new Date(order.deliveredAt);
                        if (!isNaN(end.getTime())) {
                            const e2 = new Date(end);
                            e2.setMonth(e2.getMonth() + Number(wMonths));
                            warrantyLine = '<div class="text-xs text-slate-500 mt-1">Bảo hành ' + wMonths + ' tháng — hạn dự kiến: ' + e2.toLocaleDateString('vi-VN') + '</div>';
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
            let reviewLine = '';
            const slug = item.product && item.product.slug;
            if (order.status === 'DELIVERED' && slug) {
                reviewLine = '<div class="mt-0.5 flex flex-wrap gap-x-2 gap-y-0.5">' +
                    '<a href="/products/' + encodeURIComponent(slug) + '#danhGiaChiTiet" class="text-xs text-brand-600 hover:underline">Bài viết chi tiết</a>' +
                    '<a href="/products/' + encodeURIComponent(slug) + '#danhGiaKhachHang" class="text-xs text-brand-600 hover:underline">Nhận xét khách</a>' +
                    '</div>';
            }
            return '<div class="flex items-center justify-between text-sm py-1 border-b border-slate-100 last:border-b-0">' +
                '<div class="min-w-0"><span>' + escapeHtml(name) + ' x' + qty + '</span>' + warrantyLine + reviewLine + '</div>' +
                '<span class="font-medium shrink-0">' + lineTotal.toLocaleString('vi-VN') + 'đ</span>' +
                '</div>';
        }).join('');
        const total = Number(order.total || 0);
        return '' +
            '<div class="flex items-start justify-between gap-3">' +
            '  <div>' +
            '    <h2 class="font-semibold text-lg">#' + escapeHtml(order.orderCode || '') + '</h2>' +
            '    <p class="text-sm text-slate-500">Trạng thái: <span class="font-medium text-slate-700">' + escapeHtml(statusLabel(order.status)) + '</span></p>' +
            '    <p class="text-sm text-slate-500">Thanh toán: ' + escapeHtml(paymentLabel(order.paymentMethod)) + '</p>' +
            '  </div>' +
            '</div>' +
            buildPricingSection(order) +
            buildSepayBlock(order, total) +
            '<div class="mt-3 rounded-lg border border-slate-200 p-3">' + itemsHtml + '</div>' +
            '<div class="js-order-detail hidden mt-3 rounded-lg border border-dashed border-slate-200 bg-slate-50/80 p-3 text-sm space-y-1">' +
            buildOrderDetailContent(order) +
            '</div>' +
            '<div class="mt-3 flex flex-wrap gap-2">' +
            buildActionButtons(order) +
            '</div>';
    }

    function buildSepayBlock(order, total) {
        if (!(order.paymentMethod === 'SEPAY' && order.status === 'PENDING_PAYMENT')) {
            return '';
        }
        const transferContent = order.sepayTransferContent || ('Thanh toan ' + (order.orderCode || ''));
        const checkoutAction = order.sepayCheckoutActionUrl || '';
        const checkoutFields = order.sepayCheckoutFields || {};
        const gatewayFormHtml = (checkoutAction && checkoutFields && Object.keys(checkoutFields).length)
            ? '<form class="mt-3" method="POST" action="' + escapeHtml(checkoutAction) + '">' +
              buildSepayHiddenInputs(checkoutFields) +
              '<button type="submit" class="px-3 py-2 rounded-lg bg-emerald-600 hover:bg-emerald-700 text-white text-sm font-semibold">Thanh toán với SePay</button>' +
              '</form>'
            : '<div class="text-xs text-amber-700 mt-3">Chưa cấu hình merchant-id/secret-key SePay để mở cổng thanh toán.</div>';
        return '' +
            '<div class="mt-3 rounded-lg border border-emerald-200 bg-emerald-50 p-3">' +
            '  <div class="font-semibold text-emerald-700 mb-1">Thanh toán SePay</div>' +
            '  <div class="text-sm text-slate-700">Số tiền cần chuyển: <strong>' + total.toLocaleString('vi-VN') + 'đ</strong></div>' +
            '  <div class="text-sm text-slate-700">Nội dung chuyển khoản: <code>' + escapeHtml(transferContent) + '</code></div>' +
            gatewayFormHtml +
            '  <div class="text-xs text-slate-500 mt-1">Đơn sẽ tự hủy sau 30 phút nếu chưa thanh toán.</div>' +
            '</div>';
    }

    function buildSepayHiddenInputs(fields) {
        return Object.keys(fields).map(function (key) {
            return '<input type="hidden" name="' + escapeHtml(key) + '" value="' + escapeHtml(String(fields[key] || '')) + '"/>';
        }).join('');
    }

    function buildActionButtons(order) {
        const status = order.status;
        const buttons = [];
        buttons.push('<button type="button" data-action="toggleDetail" class="px-3 py-2 rounded-lg border border-slate-300 hover:bg-slate-50 text-sm">Chi tiết đơn</button>');
        const canCancel = ['PENDING_PAYMENT', 'PENDING_CONFIRMATION', 'PROCESSING'].indexOf(status) >= 0;
        if (canCancel) {
            buttons.push('<button data-action="cancel" class="px-3 py-2 rounded-lg border border-red-300 text-red-600 hover:bg-red-50 text-sm">Hủy đơn</button>');
        }
        if (status === 'SHIPPING') {
            buttons.push('<button data-action="markDelivered" class="px-3 py-2 rounded-lg border border-slate-300 hover:bg-slate-50 text-sm">Đã nhận hàng</button>');
        }
        if (status === 'DELIVERED') {
            buttons.push('<button data-action="refund" class="px-3 py-2 rounded-lg border border-slate-300 hover:bg-slate-50 text-sm">Trả hàng/Hoàn tiền</button>');
            buttons.push('<button data-action="rebuy" class="px-3 py-2 rounded-lg border border-slate-300 hover:bg-slate-50 text-sm">Mua lại</button>');
        }
        if (buttons.length === 1) {
            buttons.push('<span class="text-sm text-slate-400">Chưa có thao tác khác cho trạng thái này</span>');
        }
        return buttons.join('');
    }

    function bindOrderActions(card, order) {
        const detailBtn = card.querySelector('[data-action="toggleDetail"]');
        const detailPane = card.querySelector('.js-order-detail');
        if (detailBtn && detailPane) {
            detailBtn.addEventListener('click', function () {
                if (detailPane.classList.contains('hidden')) {
                    detailPane.classList.remove('hidden');
                    detailBtn.textContent = 'Ẩn chi tiết';
                } else {
                    detailPane.classList.add('hidden');
                    detailBtn.textContent = 'Chi tiết đơn';
                }
            });
        }

        const cancelBtn = card.querySelector('[data-action="cancel"]');
        if (cancelBtn) {
            cancelBtn.addEventListener('click', async function () {
                const reason = window.prompt('Nhập lý do hủy đơn:');
                if (!reason) return;
                await postJson('/api/v1/orders/' + order.id + '/cancel', { reason: reason });
                await loadOrders();
            });
        }

        const deliveredBtn = card.querySelector('[data-action="markDelivered"]');
        if (deliveredBtn) {
            deliveredBtn.addEventListener('click', async function () {
                await putJson('/api/v1/orders/' + order.id + '/status', { status: 'DELIVERED' });
                await loadOrders();
            });
        }

        const refundBtn = card.querySelector('[data-action="refund"]');
        if (refundBtn) {
            refundBtn.addEventListener('click', async function () {
                const reason = window.prompt('Nhập lý do trả hàng/hoàn tiền:');
                if (!reason) return;
                const files = await pickEvidenceImages();
                await postRefundWithUpload(order.id, reason, files);
                await loadOrders();
            });
        }

        const rebuyBtn = card.querySelector('[data-action="rebuy"]');
        if (rebuyBtn) {
            rebuyBtn.addEventListener('click', function () {
                const items = Array.isArray(order.items) ? order.items : [];
                const localItems = items.map(function (item) {
                    return {
                        productId: item.product && item.product.id ? item.product.id : null,
                        quantity: item.quantity || 1,
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
    }

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

    function pickEvidenceImages() {
        return new Promise(function (resolve) {
            const input = document.createElement('input');
            input.type = 'file';
            input.accept = '.jpg,.jpeg,.png,.webp,image/jpeg,image/png,image/webp';
            input.multiple = true;
            input.addEventListener('change', function () {
                const files = Array.from(input.files || []);
                resolve(files.slice(0, 3));
            });
            input.click();
        });
    }

    function setLoading(value) {
        if (!loadingEl) return;
        loadingEl.classList.toggle('hidden', !value);
    }

    function showSummaryError(message) {
        if (!summaryErrorEl) return;
        summaryErrorEl.innerHTML = '<div class="text-red-600 text-sm">' + escapeHtml(message) + '</div>';
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
