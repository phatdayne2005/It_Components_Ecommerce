(function () {
    'use strict';

    const auth = JSON.parse(localStorage.getItem('auth') || 'null');
    if (!auth || !(auth.roles || []).some(function (r) { return r === 'ROLE_STAFF' || r === 'ROLE_ADMIN'; })) {
        alert('Bạn cần đăng nhập với tài khoản STAFF hoặc ADMIN.');
        window.location.href = '/login';
        return;
    }

    document.getElementById('loadingGate').classList.add('hidden');
    const badge = document.getElementById('userBadge');
    const badgeName = document.getElementById('userBadgeName');
    if (badge && badgeName) {
        badge.classList.remove('hidden');
        badgeName.textContent = auth.username;
    }

    document.getElementById('logoutBtn').addEventListener('click', function () {
        localStorage.removeItem('auth');
        window.location.href = '/login';
    });

    var currentFilters = { status: '', keyword: '', dateFrom: '', dateTo: '' };
    var allOrders = [];

    function headers() {
        return {
            'Content-Type': 'application/json',
            'Authorization': 'Bearer ' + auth.token
        };
    }

    async function api(method, url, body) {
        var opts = { method: method, headers: headers() };
        if (body) opts.body = JSON.stringify(body);
        var res = await fetch(url, opts);
        if (res.status === 401 || res.status === 403) {
            localStorage.removeItem('auth');
            window.location.href = '/login';
            throw new Error('Hết phiên');
        }
        var data = await res.json();
        if (!res.ok) {
            throw new Error((data && data.message) || 'Lỗi hệ thống');
        }
        return data;
    }

    function showToast(msg) {
        var t = document.getElementById('toast');
        document.getElementById('toastMsg').textContent = msg;
        t.classList.add('show');
        t.classList.remove('opacity-0');
        setTimeout(function () {
            t.classList.remove('show');
            t.classList.add('opacity-0');
        }, 3000);
    }

    // ─── Tab Navigation ───────────────────────────
    document.querySelectorAll('.tab-btn').forEach(function (btn) {
        btn.addEventListener('click', function () {
            document.querySelectorAll('.tab-btn').forEach(function (b) {
                b.classList.remove('border-brand-600', 'text-brand-600');
                b.classList.add('border-transparent', 'text-slate-500');
            });
            btn.classList.add('border-brand-600', 'text-brand-600');
            btn.classList.remove('border-transparent', 'text-slate-500');
        });
    });

    // ─── Load Orders ──────────────────────────────
    // Status được filter ở client để click stat card / đổi dropdown phản hồi ngay,
    // và để stat luôn đếm trên TOÀN BỘ đơn (không bị méo bởi filter status hiện tại).
    // Date/keyword vẫn filter ở server vì có thể trả ít data hơn.
    async function loadOrders() {
        document.getElementById('ordersLoading').classList.remove('hidden');
        document.getElementById('ordersEmpty').classList.add('hidden');
        document.getElementById('ordersList').innerHTML = '';
        try {
            var params = new URLSearchParams();
            if (currentFilters.keyword) params.set('keyword', currentFilters.keyword);
            if (currentFilters.dateFrom) params.set('dateFrom', currentFilters.dateFrom);
            if (currentFilters.dateTo) params.set('dateTo', currentFilters.dateTo);
            var qs = params.toString();
            var url = '/api/v1/orders/admin/list' + (qs ? ('?' + qs) : '');
            var orders = await api('GET', url);
            allOrders = Array.isArray(orders) ? orders : [];
            applyClientFilters();
        } catch (err) {
            showToast(err.message || 'Không tải được danh sách đơn hàng');
        } finally {
            document.getElementById('ordersLoading').classList.add('hidden');
        }
    }

    function applyClientFilters() {
        var visible = currentFilters.status
            ? allOrders.filter(function (o) { return o.status === currentFilters.status; })
            : allOrders.slice();
        updateStats(allOrders);
        renderOrders(visible);
    }

    function updateStats(orders) {
        // Mỗi card = 1 status — click để filter trực tiếp.
        var counts = { PENDING_CONFIRMATION: 0, PROCESSING: 0, SHIPPING: 0, REFUND_REQUESTED: 0 };
        orders.forEach(function (o) {
            if (counts.hasOwnProperty(o.status)) counts[o.status]++;
        });
        var cards = [
            { key: 'PENDING_CONFIRMATION', label: 'Chờ xác nhận',     color: 'blue',   icon: 'fa-clock' },
            { key: 'PROCESSING',           label: 'Đang xử lý',       color: 'indigo', icon: 'fa-gear' },
            { key: 'SHIPPING',             label: 'Đang giao',        color: 'purple', icon: 'fa-truck-fast' },
            { key: 'REFUND_REQUESTED',     label: 'Yêu cầu hoàn tiền', color: 'orange', icon: 'fa-money-bill-transfer' }
        ];
        var activeKey = currentFilters.status;
        var html = cards.map(function (c) {
            var isActive = activeKey === c.key;
            var ringCls = isActive ? ' border-' + c.color + '-500 ring-2 ring-' + c.color + '-200' : ' border-slate-200';
            return '<button type="button" data-stat-key="' + c.key + '" ' +
                'class="text-left bg-white rounded-xl border p-4 flex items-center gap-3 hover:shadow-md transition' + ringCls + '">' +
                '<i class="fa-solid ' + c.icon + ' text-2xl text-' + c.color + '-600"></i>' +
                '<div class="min-w-0"><div class="text-2xl font-bold text-' + c.color + '-600">' + counts[c.key] + '</div>' +
                '<div class="text-xs text-slate-500">' + c.label + '</div></div></button>';
        }).join('');
        document.getElementById('statsRow').innerHTML = html;

        // Bind clicks: click 1 card → filter status; click lại card đang active → clear filter
        document.querySelectorAll('[data-stat-key]').forEach(function (el) {
            el.addEventListener('click', function () {
                var key = el.getAttribute('data-stat-key');
                var newStatus = currentFilters.status === key ? '' : key;
                currentFilters.status = newStatus;
                var dropdown = document.getElementById('filterStatus');
                if (dropdown) dropdown.value = newStatus;
                applyClientFilters();
            });
        });
    }

    function renderOrders(orders) {
        var listEl = document.getElementById('ordersList');
        listEl.innerHTML = '';

        // Filter indicator
        var hintEl = document.getElementById('filterHint');
        if (!hintEl) {
            hintEl = document.createElement('div');
            hintEl.id = 'filterHint';
            hintEl.className = 'mb-3';
            listEl.parentNode.insertBefore(hintEl, listEl);
        }
        if (currentFilters.status) {
            hintEl.innerHTML = '<div class="inline-flex items-center gap-2 px-3 py-1.5 rounded-full bg-brand-50 border border-brand-200 text-brand-700 text-xs font-medium">' +
                '<i class="fa-solid fa-filter"></i>' +
                '<span>Đang lọc: <strong>' + escapeHtml(statusLabel(currentFilters.status)) + '</strong> (' + orders.length + ' đơn)</span>' +
                '<button type="button" id="clearStatusFilter" class="ml-1 text-brand-700 hover:text-brand-900" title="Bỏ lọc"><i class="fa-solid fa-xmark"></i></button>' +
                '</div>';
            var clearBtn = document.getElementById('clearStatusFilter');
            if (clearBtn) {
                clearBtn.addEventListener('click', function () {
                    currentFilters.status = '';
                    var dd = document.getElementById('filterStatus');
                    if (dd) dd.value = '';
                    applyClientFilters();
                });
            }
        } else {
            hintEl.innerHTML = '';
        }

        if (!orders.length) {
            document.getElementById('ordersEmpty').classList.remove('hidden');
            return;
        }
        document.getElementById('ordersEmpty').classList.add('hidden');
        orders.forEach(function (order) {
            var card = document.createElement('div');
            card.className = 'bg-white rounded-xl border border-slate-200 p-4';
            card.innerHTML = buildOrderCard(order);
            listEl.appendChild(card);
            bindOrderCard(card, order);
        });
    }

    function statusLabel(code) {
        var m = {
            PENDING_PAYMENT: 'Chờ thanh toán',
            PENDING_CONFIRMATION: 'Chờ xác nhận',
            PROCESSING: 'Đang xử lý',
            SHIPPING: 'Đang giao',
            DELIVERED: 'Đã giao',
            REFUND_REQUESTED: 'Yêu cầu hoàn tiền',
            REFUND_REJECTED: 'Từ chối hoàn tiền',
            CANCELLED: 'Đã hủy',
            RETURN_REFUND: 'Đã duyệt hoàn tiền',
            REFUND_COMPLETED: 'Đã hoàn tiền'
        };
        return m[code] || code || '';
    }

    function statusColor(code) {
        var m = {
            PENDING_PAYMENT: 'bg-amber-100 text-amber-700',
            PENDING_CONFIRMATION: 'bg-blue-100 text-blue-700',
            PROCESSING: 'bg-indigo-100 text-indigo-700',
            SHIPPING: 'bg-purple-100 text-purple-700',
            DELIVERED: 'bg-emerald-100 text-emerald-700',
            REFUND_REQUESTED: 'bg-orange-100 text-orange-700',
            REFUND_REJECTED: 'bg-red-100 text-red-700',
            CANCELLED: 'bg-slate-100 text-slate-500',
            RETURN_REFUND: 'bg-teal-100 text-teal-700',
            REFUND_COMPLETED: 'bg-emerald-100 text-emerald-700'
        };
        return m[code] || 'bg-slate-100 text-slate-600';
    }

    function buildOrderCard(order) {
        var items = Array.isArray(order.items) ? order.items : [];
        var itemsHtml = items.map(function (item) {
            var name = item.productName || (item.product && item.product.name) || 'Sản phẩm';
            return '<div class="flex justify-between text-sm py-1 border-b border-slate-100 last:border-b-0">' +
                '<span class="text-slate-700">' + escapeHtml(name) + ' x' + (item.quantity || 0) + '</span>' +
                '<span class="font-medium">' + Number(item.lineTotal || 0).toLocaleString('vi-VN') + 'đ</span></div>';
        }).join('');
        var userInfo = order.userUsername ? ('<span>' + escapeHtml(order.userUsername) + '</span>') : '<span class="text-slate-400">Khách</span>';
        var createdAt = '';
        if (order.createdAt) {
            try {
                var d = new Date(order.createdAt);
                if (!isNaN(d.getTime())) createdAt = d.toLocaleDateString('vi-VN');
            } catch (e) {}
        }
        var refundInfo = '';
        if (order.status === 'REFUND_REQUESTED' && order.refundReason) {
            refundInfo = '<div class="mt-2 p-3 bg-orange-50 rounded-lg border border-orange-200">' +
                '<div class="text-xs font-medium text-orange-700 mb-1">Lý do hoàn tiền:</div>' +
                '<div class="text-sm text-orange-800">' + escapeHtml(order.refundReason) + '</div>';
            if (order.refundEvidenceUrls) {
                var urls = order.refundEvidenceUrls.split(',').filter(Boolean);
                if (urls.length > 0) {
                    refundInfo += '<div class="flex gap-2 mt-2">';
                    urls.forEach(function (url) {
                        refundInfo += '<img src="' + escapeHtml(url.trim()) + '" class="w-16 h-16 object-cover rounded border border-orange-200 cursor-pointer hover:opacity-75" onclick="window.open(\'' + escapeHtml(url.trim()) + '\', \'_blank\')"/>';
                    });
                    refundInfo += '</div>';
                }
            }
            refundInfo += '</div>';
        }
        return '' +
            '<div class="flex items-start justify-between gap-3 mb-3">' +
            '  <div>' +
            '    <div class="flex items-center gap-2 mb-1">' +
            '      <h3 class="font-semibold text-lg">#' + escapeHtml(order.orderCode || '') + '</h3>' +
            '      <span class="px-2 py-0.5 rounded-full text-xs font-medium ' + statusColor(order.status) + '">' + escapeHtml(statusLabel(order.status)) + '</span>' +
            '    </div>' +
            '    <div class="text-sm text-slate-500">' +
            '      <span>Khách: ' + userInfo + '</span>' +
            '      <span class="mx-1">|</span>' +
            '      <span>' + createdAt + '</span>' +
            '      <span class="mx-1">|</span>' +
            '      <span>' + escapeHtml(order.paymentMethod || '') + '</span>' +
            '    </div>' +
            '  </div>' +
            '  <div class="text-right">' +
            '    <div class="font-bold text-lg text-slate-900">' + Number(order.total || 0).toLocaleString('vi-VN') + ' đ</div>' +
            '  </div>' +
            '</div>' +
            refundInfo +
            '<div class="border border-slate-200 rounded-lg p-2 mb-3">' + itemsHtml + '</div>' +
            '<div class="flex flex-wrap gap-2">' +
            '  <button data-action="viewDetail" class="px-3 py-1.5 rounded-lg border border-slate-300 text-sm hover:bg-slate-50 transition">Chi tiết</button>' +
            buildStaffActionButtons(order) +
            '</div>';
    }

    function buildStaffActionButtons(order) {
        var html = '';
        if (order.status === 'PENDING_CONFIRMATION') {
            html += '<button data-action="confirm" class="px-3 py-1.5 rounded-lg bg-blue-600 text-white text-sm hover:bg-blue-700 transition">Xác nhận</button>';
        }
        if (order.status === 'PROCESSING') {
            html += '<button data-action="ship" class="px-3 py-1.5 rounded-lg bg-indigo-600 text-white text-sm hover:bg-indigo-700 transition">Chuyển giao</button>';
        }
        if (order.status === 'SHIPPING') {
            html += '<button data-action="deliver" class="px-3 py-1.5 rounded-lg bg-purple-600 text-white text-sm hover:bg-purple-700 transition">Đã giao</button>';
        }
        if (order.status === 'REFUND_REQUESTED') {
            html += '<button data-action="approveRefund" class="px-3 py-1.5 rounded-lg bg-emerald-600 text-white text-sm hover:bg-emerald-700 transition">Chấp nhận hoàn tiền</button>';
            html += '<button data-action="rejectRefund" class="px-3 py-1.5 rounded-lg bg-red-600 text-white text-sm hover:bg-red-700 transition">Từ chối</button>';
        }
        if (order.status === 'RETURN_REFUND') {
            if (order.refundBankSubmittedAt) {
                html += '<button data-action="confirmRefund" class="px-3 py-1.5 rounded-lg bg-emerald-600 text-white text-sm font-medium hover:bg-emerald-700 transition inline-flex items-center gap-1.5"><i class="fa-solid fa-circle-check"></i><span>Xác nhận đã hoàn tiền</span></button>';
            } else {
                html += '<span class="px-3 py-1.5 rounded-lg bg-amber-50 text-amber-700 text-xs border border-amber-200 inline-flex items-center gap-1.5"><i class="fa-solid fa-clock"></i><span>Chờ khách điền TK</span></span>';
            }
        }
        return html;
    }

    function bindOrderCard(card, order) {
        card.querySelector('[data-action="viewDetail"]').addEventListener('click', function () {
            openDetailModal(order);
        });
        var confirmBtn = card.querySelector('[data-action="confirm"]');
        if (confirmBtn) {
            confirmBtn.addEventListener('click', function () {
                doAction(order.id, 'PROCESSING', 'Xác nhận đơn hàng');
            });
        }
        var shipBtn = card.querySelector('[data-action="ship"]');
        if (shipBtn) {
            shipBtn.addEventListener('click', function () {
                var tracking = prompt('Nhập số vận đơn (tracking number):');
                if (tracking === null) return;
                if (!tracking.trim()) { showToast('Số vận đơn không được để trống'); return; }
                doActionWithTracking(order.id, 'SHIPPING', tracking.trim());
            });
        }
        var deliverBtn = card.querySelector('[data-action="deliver"]');
        if (deliverBtn) {
            deliverBtn.addEventListener('click', function () {
                doAction(order.id, 'DELIVERED', 'Xác nhận đã giao');
            });
        }
        var approveBtn = card.querySelector('[data-action="approveRefund"]');
        if (approveBtn) {
            approveBtn.addEventListener('click', function () {
                if (!confirm('Bạn có chắc chắn muốn chấp nhận yêu cầu hoàn tiền cho đơn #' + order.orderCode + '?')) return;
                doAction(order.id, 'RETURN_REFUND', 'Chấp nhận hoàn tiền');
            });
        }
        var rejectBtn = card.querySelector('[data-action="rejectRefund"]');
        if (rejectBtn) {
            rejectBtn.addEventListener('click', function () {
                openRejectModal(order.id, order.orderCode);
            });
        }
        var confirmRefundBtn = card.querySelector('[data-action="confirmRefund"]');
        if (confirmRefundBtn) {
            confirmRefundBtn.addEventListener('click', function () {
                openConfirmRefundModal(order);
            });
        }
    }

    async function doAction(orderId, newStatus, successMsg) {
        try {
            await api('PUT', '/api/v1/orders/' + orderId + '/status', { status: newStatus });
            showToast(successMsg + ' thành công');
            await loadOrders();
        } catch (err) {
            showToast(err.message || successMsg + ' thất bại');
        }
    }

    async function doActionWithTracking(orderId, newStatus, trackingNumber) {
        try {
            await api('PUT', '/api/v1/orders/' + orderId + '/status', { status: newStatus, trackingNumber: trackingNumber });
            showToast('Chuyển giao thành công');
            await loadOrders();
        } catch (err) {
            showToast(err.message || 'Chuyển giao thất bại');
        }
    }

    // ─── Order Detail Modal ───────────────────────
    var pendingRejectOrderId = null;
    var pendingRejectCode = null;

    function openDetailModal(order) {
        var items = Array.isArray(order.items) ? order.items : [];
        var itemsHtml = items.map(function (item) {
            var name = item.productName || 'Sản phẩm';
            return '<tr class="border-b border-slate-100"><td class="py-2 pr-4 text-sm">' + escapeHtml(name) + '</td>' +
                '<td class="py-2 pr-4 text-center text-sm">' + (item.quantity || 0) + ' x ' + Number(item.unitPrice || 0).toLocaleString('vi-VN') + '</td>' +
                '<td class="py-2 text-right text-sm font-medium">' + Number(item.lineTotal || 0).toLocaleString('vi-VN') + '</td></tr>';
        }).join('');
        var detailHtml = '' +
            '<div class="grid grid-cols-2 gap-4 mb-4">' +
            '<div><span class="text-xs text-slate-500">Mã đơn</span><div class="font-semibold">' + escapeHtml(order.orderCode || '') + '</div></div>' +
            '<div><span class="text-xs text-slate-500">Trạng thái</span><div class="font-semibold ' + statusColor(order.status) + ' px-2 py-0.5 rounded inline-block mt-1">' + escapeHtml(statusLabel(order.status)) + '</div></div>' +
            '<div><span class="text-xs text-slate-500">Khách hàng</span><div class="font-medium">' + (order.userUsername ? escapeHtml(order.userUsername) : '<span class="text-slate-400">Khách vãng lai</span>') + '</div></div>' +
            '<div><span class="text-xs text-slate-500">Phương thức</span><div>' + escapeHtml(order.paymentMethod || '') + '</div></div>' +
            '<div><span class="text-xs text-slate-500">Người nhận</span><div>' + escapeHtml(order.recipientName || '') + '</div></div>' +
            '<div><span class="text-xs text-slate-500">SĐT</span><div>' + escapeHtml(order.recipientPhone || '') + '</div></div>' +
            '<div><span class="text-xs text-slate-500">Email</span><div>' + escapeHtml(order.recipientEmail || '') + '</div></div>' +
            '<div><span class="text-xs text-slate-500">Địa chỉ</span><div class="col-span-2">' + escapeHtml(order.shippingAddress || '') + '</div></div>' +
            '</div>';
        if (order.trackingNumber) {
            detailHtml += '<div class="mb-4 p-3 bg-slate-50 rounded-lg border border-slate-200"><span class="text-xs text-slate-500">Vận đơn:</span> <span class="font-medium">' + escapeHtml(order.trackingNumber) + '</span></div>';
        }
        if (order.cancelReason) {
            detailHtml += '<div class="mb-4 p-3 bg-red-50 rounded-lg border border-red-200"><span class="text-xs text-red-600">Lý do hủy:</span> <span class="text-red-800">' + escapeHtml(order.cancelReason) + '</span></div>';
        }
        if (order.refundRejectNote) {
            detailHtml += '<div class="mb-4 p-3 bg-orange-50 rounded-lg border border-orange-200"><span class="text-xs text-orange-600">Lý do từ chối hoàn tiền:</span> <span class="text-orange-800">' + escapeHtml(order.refundRejectNote) + '</span></div>';
        }
        if (order.status === 'RETURN_REFUND' || order.status === 'REFUND_COMPLETED') {
            if (order.refundBankSubmittedAt) {
                var bankSubmittedDate = '';
                try {
                    var bd = new Date(order.refundBankSubmittedAt);
                    if (!isNaN(bd.getTime())) bankSubmittedDate = bd.toLocaleString('vi-VN', { day: '2-digit', month: '2-digit', year: 'numeric', hour: '2-digit', minute: '2-digit' });
                } catch (e) {}
                detailHtml += '<div class="mb-4 p-3 bg-teal-50 rounded-lg border border-teal-200">' +
                    '<div class="text-xs text-teal-700 font-semibold mb-2"><i class="fa-solid fa-building-columns mr-1"></i>Thông tin tài khoản nhận hoàn tiền</div>' +
                    '<div class="grid grid-cols-2 gap-x-4 gap-y-1 text-sm">' +
                    '<div><span class="text-xs text-slate-500">Ngân hàng</span><div class="font-medium">' + escapeHtml(order.refundBankName || '—') + '</div></div>' +
                    '<div><span class="text-xs text-slate-500">Số tài khoản</span><div class="font-mono font-medium">' + escapeHtml(order.refundBankAccountNumber || '—') + '</div></div>' +
                    '<div class="col-span-2"><span class="text-xs text-slate-500">Chủ tài khoản</span><div class="font-medium uppercase">' + escapeHtml(order.refundBankAccountHolder || '—') + '</div></div>' +
                    (order.refundBankNote ? ('<div class="col-span-2"><span class="text-xs text-slate-500">Ghi chú khách</span><div>' + escapeHtml(order.refundBankNote) + '</div></div>') : '') +
                    '</div>' +
                    (bankSubmittedDate ? ('<div class="text-xs text-slate-500 mt-2">Khách đã gửi lúc ' + escapeHtml(bankSubmittedDate) + '</div>') : '') +
                    '</div>';
            } else if (order.status === 'RETURN_REFUND') {
                detailHtml += '<div class="mb-4 p-3 bg-amber-50 rounded-lg border border-amber-200">' +
                    '<div class="text-xs text-amber-700 font-semibold"><i class="fa-solid fa-circle-exclamation mr-1"></i>Khách chưa cung cấp thông tin tài khoản nhận hoàn tiền</div>' +
                    '<div class="text-sm text-amber-800 mt-1">Đơn đang chờ khách điền thông tin TK ngân hàng. Sau khi nhận thông tin, CSKH chuyển khoản thủ công.</div>' +
                    '</div>';
            }
        }
        if (order.status === 'REFUND_COMPLETED' && order.refundCompletedAt) {
            var completedDate = '';
            try {
                var cd = new Date(order.refundCompletedAt);
                if (!isNaN(cd.getTime())) completedDate = cd.toLocaleString('vi-VN', { day: '2-digit', month: '2-digit', year: 'numeric', hour: '2-digit', minute: '2-digit' });
            } catch (e) {}
            detailHtml += '<div class="mb-4 p-3 bg-emerald-50 rounded-lg border border-emerald-200">' +
                '<div class="text-xs text-emerald-700 font-semibold mb-1"><i class="fa-solid fa-circle-check mr-1"></i>Đã hoàn tiền</div>' +
                '<div class="text-sm text-emerald-900">' +
                'Số tiền <strong>' + Number(order.total || 0).toLocaleString('vi-VN') + 'đ</strong> đã được CSKH chuyển khoản' +
                (completedDate ? (' lúc <strong>' + escapeHtml(completedDate) + '</strong>') : '') +
                (order.refundCompletedBy ? (' bởi <strong>' + escapeHtml(order.refundCompletedBy) + '</strong>') : '') + '.' +
                '</div>' +
                (order.refundCompletedNote ? ('<div class="text-sm text-emerald-800 mt-1"><span class="text-xs text-slate-500">Ghi chú:</span> ' + escapeHtml(order.refundCompletedNote) + '</div>') : '') +
                '</div>';
        }
        detailHtml += '' +
            '<table class="w-full text-left"><thead><tr class="text-xs text-slate-500 border-b border-slate-200"><th class="pb-1 text-left">Sản phẩm</th><th class="pb-1 text-center">Số lượng / Đơn giá</th><th class="pb-1 text-right">Thành tiền</th></tr></thead><tbody>' + itemsHtml + '</tbody></table>' +
            '<div class="mt-4 pt-3 border-t border-slate-200 space-y-1 text-sm">' +
            '<div class="flex justify-between"><span class="text-slate-500">Tạm tính</span><span>' + Number(order.subtotal || 0).toLocaleString('vi-VN') + ' đ</span></div>' +
            (order.discount > 0 ? '<div class="flex justify-between text-emerald-600"><span>Giảm giá</span><span> -' + Number(order.discount).toLocaleString('vi-VN') + ' đ</span></div>' : '') +
            '<div class="flex justify-between font-semibold text-base pt-1 border-t border-slate-200"><span>Tổng</span><span>' + Number(order.total || 0).toLocaleString('vi-VN') + ' đ</span></div>' +
            '</div>';

        document.getElementById('orderDetailContent').innerHTML = detailHtml;
        document.getElementById('orderDetailActions').innerHTML = '<button onclick="document.getElementById(\'orderDetailModal\').classList.add(\'hidden\')" class="px-4 py-2 rounded-lg border border-slate-300 text-sm hover:bg-slate-100 transition">Đóng</button>';
        document.getElementById('orderDetailModal').classList.remove('hidden');
        document.getElementById('orderDetailModal').classList.add('flex');
    }

    document.getElementById('closeOrderDetail').addEventListener('click', function () {
        document.getElementById('orderDetailModal').classList.add('hidden');
        document.getElementById('orderDetailModal').classList.remove('flex');
    });

    // ─── Reject Refund Modal ─────────────────────
    function openRejectModal(orderId, orderCode) {
        pendingRejectOrderId = orderId;
        pendingRejectCode = orderCode;
        document.getElementById('rejectNoteInput').value = '';
        document.getElementById('rejectNoteError').classList.add('hidden');
        document.getElementById('rejectModal').classList.remove('hidden');
        document.getElementById('rejectModal').classList.add('flex');
        document.getElementById('rejectNoteInput').focus();
    }

    document.getElementById('closeRejectModal').addEventListener('click', closeRejectModal);
    document.getElementById('cancelRejectModal').addEventListener('click', closeRejectModal);

    function closeRejectModal() {
        document.getElementById('rejectModal').classList.add('hidden');
        document.getElementById('rejectModal').classList.remove('flex');
        pendingRejectOrderId = null;
        pendingRejectCode = null;
    }

    document.getElementById('rejectNoteInput').addEventListener('input', function () {
        document.getElementById('rejectNoteError').classList.add('hidden');
    });

    document.getElementById('confirmRejectModal').addEventListener('click', async function () {
        var note = document.getElementById('rejectNoteInput').value.trim();
        if (!note) {
            document.getElementById('rejectNoteError').classList.remove('hidden');
            document.getElementById('rejectNoteInput').focus();
            return;
        }
        // Snapshot orderId TRƯỚC khi closeRejectModal() (close sẽ set = null)
        var orderIdSnapshot = pendingRejectOrderId;
        if (orderIdSnapshot == null) {
            showToast('Không xác định được đơn để từ chối');
            return;
        }
        closeRejectModal();
        try {
            await api('PUT', '/api/v1/orders/' + orderIdSnapshot + '/refund/reject', { rejectNote: note });
            showToast('Từ chối hoàn tiền thành công');
            await loadOrders();
        } catch (err) {
            showToast(err.message || 'Từ chối thất bại');
        }
    });

    // ─── Confirm Refund Modal (RETURN_REFUND -> REFUND_COMPLETED) ─
    var pendingConfirmRefundOrderId = null;
    var pendingConfirmRefundOrderCode = null;

    function openConfirmRefundModal(order) {
        pendingConfirmRefundOrderId = order.id;
        pendingConfirmRefundOrderCode = order.orderCode;
        var orderInfoEl = document.getElementById('confirmRefundOrderInfo');
        var bankInfoEl = document.getElementById('confirmRefundBankInfo');
        if (orderInfoEl) {
            orderInfoEl.innerHTML = '<strong>Đơn:</strong> #' + escapeHtml(order.orderCode || '') +
                ' • <strong>Số tiền:</strong> ' + Number(order.total || 0).toLocaleString('vi-VN') + 'đ';
        }
        if (bankInfoEl) {
            bankInfoEl.innerHTML = '' +
                '<div><span class="text-slate-500">Ngân hàng:</span> <span class="font-medium">' + escapeHtml(order.refundBankName || '—') + '</span></div>' +
                '<div><span class="text-slate-500">Số TK:</span> <span class="font-mono font-medium">' + escapeHtml(order.refundBankAccountNumber || '—') + '</span></div>' +
                '<div><span class="text-slate-500">Chủ TK:</span> <span class="font-medium uppercase">' + escapeHtml(order.refundBankAccountHolder || '—') + '</span></div>' +
                (order.refundBankNote ? ('<div class="col-span-2"><span class="text-slate-500">Ghi chú khách:</span> ' + escapeHtml(order.refundBankNote) + '</div>') : '');
        }
        document.getElementById('confirmRefundNoteInput').value = '';
        document.getElementById('confirmRefundModal').classList.remove('hidden');
        document.getElementById('confirmRefundModal').classList.add('flex');
        document.getElementById('confirmRefundNoteInput').focus();
    }

    function closeConfirmRefundModal() {
        document.getElementById('confirmRefundModal').classList.add('hidden');
        document.getElementById('confirmRefundModal').classList.remove('flex');
        pendingConfirmRefundOrderId = null;
        pendingConfirmRefundOrderCode = null;
    }

    var closeConfirmRefundBtn = document.getElementById('closeConfirmRefundModal');
    var cancelConfirmRefundBtn = document.getElementById('cancelConfirmRefundModal');
    var doConfirmRefundBtn = document.getElementById('doConfirmRefundModal');
    if (closeConfirmRefundBtn) closeConfirmRefundBtn.addEventListener('click', closeConfirmRefundModal);
    if (cancelConfirmRefundBtn) cancelConfirmRefundBtn.addEventListener('click', closeConfirmRefundModal);
    if (doConfirmRefundBtn) {
        doConfirmRefundBtn.addEventListener('click', async function () {
            var note = document.getElementById('confirmRefundNoteInput').value.trim();
            var idSnapshot = pendingConfirmRefundOrderId;
            var codeSnapshot = pendingConfirmRefundOrderCode;
            if (idSnapshot == null) {
                showToast('Không xác định được đơn để xác nhận hoàn tiền');
                return;
            }
            doConfirmRefundBtn.disabled = true;
            try {
                await api('PUT', '/api/v1/orders/' + idSnapshot + '/refund/confirm', { note: note || null });
                closeConfirmRefundModal();
                showToast('Đã xác nhận hoàn tiền cho đơn #' + (codeSnapshot || idSnapshot));
                await loadOrders();
            } catch (err) {
                showToast(err.message || 'Xác nhận hoàn tiền thất bại');
            } finally {
                doConfirmRefundBtn.disabled = false;
            }
        });
    }

    // ─── Filters ────────────────────────────────
    // Status đổi ngay không cần bấm "Tìm kiếm" — filter client-side
    document.getElementById('filterStatus').addEventListener('change', function () {
        currentFilters.status = this.value;
        applyClientFilters();
    });

    // Date/keyword cần gọi server → bấm "Tìm kiếm" để áp dụng
    document.getElementById('btnApplyFilter').addEventListener('click', function () {
        currentFilters.status = document.getElementById('filterStatus').value;
        currentFilters.keyword = document.getElementById('filterKeyword').value.trim();
        currentFilters.dateFrom = document.getElementById('filterDateFrom').value;
        currentFilters.dateTo = document.getElementById('filterDateTo').value;
        loadOrders();
    });

    document.getElementById('btnResetFilter').addEventListener('click', function () {
        document.getElementById('filterStatus').value = '';
        document.getElementById('filterKeyword').value = '';
        document.getElementById('filterDateFrom').value = '';
        document.getElementById('filterDateTo').value = '';
        currentFilters = { status: '', keyword: '', dateFrom: '', dateTo: '' };
        loadOrders();
    });

    function escapeHtml(text) {
        return String(text || '')
            .replace(/&/g, '&amp;')
            .replace(/</g, '&lt;')
            .replace(/>/g, '&gt;')
            .replace(/"/g, '&quot;')
            .replace(/'/g, '&#039;');
    }

    // ─── Boot ────────────────────────────────────
    loadOrders();
})();
