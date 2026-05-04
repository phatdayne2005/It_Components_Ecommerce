(function () {
    'use strict';

    const auth = JSON.parse(localStorage.getItem('auth') || 'null');
    if (!auth || !(auth.roles || []).some(function (r) { return r === 'ROLE_STAFF' || r === 'ROLE_ADMIN'; })) {
        alert('Ban can dang nhap voi tai khoan STAFF hoac ADMIN.');
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
            throw new Error('Het phien');
        }
        var data = await res.json();
        if (!res.ok) {
            throw new Error((data && data.message) || 'Loi he thong');
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
    async function loadOrders() {
        document.getElementById('ordersLoading').classList.remove('hidden');
        document.getElementById('ordersEmpty').classList.add('hidden');
        document.getElementById('ordersList').innerHTML = '';
        try {
            var params = new URLSearchParams();
            if (currentFilters.status) params.set('status', currentFilters.status);
            if (currentFilters.keyword) params.set('keyword', currentFilters.keyword);
            if (currentFilters.dateFrom) params.set('dateFrom', currentFilters.dateFrom);
            if (currentFilters.dateTo) params.set('dateTo', currentFilters.dateTo);
            var url = '/api/v1/orders/admin/list?' + params.toString();
            var orders = await api('GET', url);
            allOrders = Array.isArray(orders) ? orders : [];
            updateStats(allOrders);
            renderOrders(allOrders);
        } catch (err) {
            showToast(err.message || 'Khong tai duoc danh sach don hang');
        } finally {
            document.getElementById('ordersLoading').classList.add('hidden');
        }
    }

    function updateStats(orders) {
        var stats = {
            PENDING_CONFIRMATION: 0,
            PROCESSING: 0,
            SHIPPING: 0,
            REFUND_REQUESTED: 0
        };
        orders.forEach(function (o) {
            if (stats.hasOwnProperty(o.status)) stats[o.status]++;
        });
        var html = '';
        var labels = {
            PENDING_CONFIRMATION: { label: 'Cho xac nhan', color: 'text-blue-600', icon: 'fa-clock' },
            PROCESSING: { label: 'Dang xu ly', color: 'text-indigo-600', icon: 'fa-gear' },
            SHIPPING: { label: 'Dang giao', color: 'text-purple-600', icon: 'fa-truck-fast' },
            REFUND_REQUESTED: { label: 'Hoan tien', color: 'text-orange-600', icon: 'fa-money-bill-transfer' }
        };
        Object.keys(labels).forEach(function (key) {
            var info = labels[key];
            html += '<div class="bg-white rounded-xl border border-slate-200 p-4 flex items-center gap-3">' +
                '<i class="fa-solid ' + info.icon + ' text-2xl ' + info.color + '"></i>' +
                '<div><div class="text-2xl font-bold ' + info.color + '">' + stats[key] + '</div>' +
                '<div class="text-xs text-slate-500">' + info.label + '</div></div></div>';
        });
        document.getElementById('statsRow').innerHTML = html;
    }

    function renderOrders(orders) {
        var listEl = document.getElementById('ordersList');
        listEl.innerHTML = '';
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
            PENDING_PAYMENT: 'Cho thanh toan',
            PENDING_CONFIRMATION: 'Cho xac nhan',
            PROCESSING: 'Dang xu ly',
            SHIPPING: 'Dang giao',
            DELIVERED: 'Da giao',
            REFUND_REQUESTED: 'Yeu cau hoan tien',
            REFUND_REJECTED: 'Tu choi hoan tien',
            CANCELLED: 'Da huy',
            RETURN_REFUND: 'Hoan tien'
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
            RETURN_REFUND: 'bg-teal-100 text-teal-700'
        };
        return m[code] || 'bg-slate-100 text-slate-600';
    }

    function buildOrderCard(order) {
        var items = Array.isArray(order.items) ? order.items : [];
        var itemsHtml = items.map(function (item) {
            var name = item.productName || (item.product && item.product.name) || 'San pham';
            return '<div class="flex justify-between text-sm py-1 border-b border-slate-100 last:border-b-0">' +
                '<span class="text-slate-700">' + escapeHtml(name) + ' x' + (item.quantity || 0) + '</span>' +
                '<span class="font-medium">' + Number(item.lineTotal || 0).toLocaleString('vi-VN') + 'dong</span></div>';
        }).join('');
        var userInfo = order.user ? ('<span>' + escapeHtml(order.user.username || '') + '</span>') : '<span class="text-slate-400">Khach</span>';
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
                '<div class="text-xs font-medium text-orange-700 mb-1">Ly do hoan tien:</div>' +
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
            '      <span>Khach: ' + userInfo + '</span>' +
            '      <span class="mx-1">|</span>' +
            '      <span>' + createdAt + '</span>' +
            '      <span class="mx-1">|</span>' +
            '      <span>' + escapeHtml(order.paymentMethod || '') + '</span>' +
            '    </div>' +
            '  </div>' +
            '  <div class="text-right">' +
            '    <div class="font-bold text-lg text-slate-900">' + Number(order.total || 0).toLocaleString('vi-VN') + ' dong</div>' +
            '  </div>' +
            '</div>' +
            refundInfo +
            '<div class="border border-slate-200 rounded-lg p-2 mb-3">' + itemsHtml + '</div>' +
            '<div class="flex flex-wrap gap-2">' +
            '  <button data-action="viewDetail" class="px-3 py-1.5 rounded-lg border border-slate-300 text-sm hover:bg-slate-50 transition">Chi tiet</button>' +
            '  <button data-action="viewUser" class="px-3 py-1.5 rounded-lg border border-slate-300 text-sm hover:bg-slate-50 transition">Khach hang</button>' +
            buildStaffActionButtons(order) +
            '</div>';
    }

    function buildStaffActionButtons(order) {
        var html = '';
        if (order.status === 'PENDING_CONFIRMATION') {
            html += '<button data-action="confirm" class="px-3 py-1.5 rounded-lg bg-blue-600 text-white text-sm hover:bg-blue-700 transition">Xac nhan</button>';
        }
        if (order.status === 'PROCESSING') {
            html += '<button data-action="ship" class="px-3 py-1.5 rounded-lg bg-indigo-600 text-white text-sm hover:bg-indigo-700 transition">Chuyen giao</button>';
        }
        if (order.status === 'SHIPPING') {
            html += '<button data-action="deliver" class="px-3 py-1.5 rounded-lg bg-purple-600 text-white text-sm hover:bg-purple-700 transition">Da giao</button>';
        }
        if (order.status === 'REFUND_REQUESTED') {
            html += '<button data-action="approveRefund" class="px-3 py-1.5 rounded-lg bg-emerald-600 text-white text-sm hover:bg-emerald-700 transition">Chap nhan hoan tien</button>';
            html += '<button data-action="rejectRefund" class="px-3 py-1.5 rounded-lg bg-red-600 text-white text-sm hover:bg-red-700 transition">Tu choi</button>';
        }
        return html;
    }

    function bindOrderCard(card, order) {
        card.querySelector('[data-action="viewDetail"]').addEventListener('click', function () {
            openDetailModal(order);
        });
        var viewUser = card.querySelector('[data-action="viewUser"]');
        if (viewUser) {
            viewUser.addEventListener('click', function () {
                showToast('Khach hang: ' + (order.user ? order.user.username : 'Khong ro'));
            });
        }
        var confirmBtn = card.querySelector('[data-action="confirm"]');
        if (confirmBtn) {
            confirmBtn.addEventListener('click', function () {
                doAction(order.id, 'PROCESSING', 'Xac nhan don hang');
            });
        }
        var shipBtn = card.querySelector('[data-action="ship"]');
        if (shipBtn) {
            shipBtn.addEventListener('click', function () {
                var tracking = prompt('Nhap so van don (tracking number):');
                if (tracking === null) return;
                if (!tracking.trim()) { showToast('So van don khong duoc de trong'); return; }
                doActionWithTracking(order.id, 'SHIPPING', tracking.trim());
            });
        }
        var deliverBtn = card.querySelector('[data-action="deliver"]');
        if (deliverBtn) {
            deliverBtn.addEventListener('click', function () {
                doAction(order.id, 'DELIVERED', 'Xac nhan da giao');
            });
        }
        var approveBtn = card.querySelector('[data-action="approveRefund"]');
        if (approveBtn) {
            approveBtn.addEventListener('click', function () {
                if (!confirm('Ban co chan chan muon chap nhan yeu cau hoan tien cho don #' + order.orderCode + '?')) return;
                doAction(order.id, 'RETURN_REFUND', 'Chap nhan hoan tien');
            });
        }
        var rejectBtn = card.querySelector('[data-action="rejectRefund"]');
        if (rejectBtn) {
            rejectBtn.addEventListener('click', function () {
                openRejectModal(order.id, order.orderCode);
            });
        }
    }

    async function doAction(orderId, newStatus, successMsg) {
        try {
            await api('PUT', '/api/v1/orders/' + orderId + '/status', { status: newStatus });
            showToast(successMsg + ' thanh cong');
            await loadOrders();
        } catch (err) {
            showToast(err.message || successMsg + ' that bai');
        }
    }

    async function doActionWithTracking(orderId, newStatus, trackingNumber) {
        try {
            await api('PUT', '/api/v1/orders/' + orderId + '/status', { status: newStatus, trackingNumber: trackingNumber });
            showToast('Chuyen giao thanh cong');
            await loadOrders();
        } catch (err) {
            showToast(err.message || 'Chuyen giao that bai');
        }
    }

    // ─── Order Detail Modal ───────────────────────
    var pendingRejectOrderId = null;
    var pendingRejectCode = null;

    function openDetailModal(order) {
        var items = Array.isArray(order.items) ? order.items : [];
        var itemsHtml = items.map(function (item) {
            var name = item.productName || 'San pham';
            return '<tr class="border-b border-slate-100"><td class="py-2 pr-4 text-sm">' + escapeHtml(name) + '</td>' +
                '<td class="py-2 pr-4 text-center text-sm">' + (item.quantity || 0) + ' x ' + Number(item.unitPrice || 0).toLocaleString('vi-VN') + '</td>' +
                '<td class="py-2 text-right text-sm font-medium">' + Number(item.lineTotal || 0).toLocaleString('vi-VN') + '</td></tr>';
        }).join('');
        var detailHtml = '' +
            '<div class="grid grid-cols-2 gap-4 mb-4">' +
            '<div><span class="text-xs text-slate-500">Ma don</span><div class="font-semibold">' + escapeHtml(order.orderCode || '') + '</div></div>' +
            '<div><span class="text-xs text-slate-500">Trang thai</span><div class="font-semibold ' + statusColor(order.status) + ' px-2 py-0.5 rounded inline-block mt-1">' + escapeHtml(statusLabel(order.status)) + '</div></div>' +
            '<div><span class="text-xs text-slate-500">Khach hang</span><div class="font-medium">' + (order.user ? escapeHtml(order.user.username || '') : 'Khong ro') + '</div></div>' +
            '<div><span class="text-xs text-slate-500">Phuong thuc</span><div>' + escapeHtml(order.paymentMethod || '') + '</div></div>' +
            '<div><span class="text-xs text-slate-500">Nguoi nhan</span><div>' + escapeHtml(order.recipientName || '') + '</div></div>' +
            '<div><span class="text-xs text-slate-500">SDT</span><div>' + escapeHtml(order.recipientPhone || '') + '</div></div>' +
            '<div><span class="text-xs text-slate-500">Email</span><div>' + escapeHtml(order.recipientEmail || '') + '</div></div>' +
            '<div><span class="text-xs text-slate-500">Dia chi</span><div class="col-span-2">' + escapeHtml(order.shippingAddress || '') + '</div></div>' +
            '</div>';
        if (order.trackingNumber) {
            detailHtml += '<div class="mb-4 p-3 bg-slate-50 rounded-lg border border-slate-200"><span class="text-xs text-slate-500">Van don:</span> <span class="font-medium">' + escapeHtml(order.trackingNumber) + '</span></div>';
        }
        if (order.cancelReason) {
            detailHtml += '<div class="mb-4 p-3 bg-red-50 rounded-lg border border-red-200"><span class="text-xs text-red-600">Ly do huy:</span> <span class="text-red-800">' + escapeHtml(order.cancelReason) + '</span></div>';
        }
        if (order.refundRejectNote) {
            detailHtml += '<div class="mb-4 p-3 bg-orange-50 rounded-lg border border-orange-200"><span class="text-xs text-orange-600">Ly do tu choi hoan tien:</span> <span class="text-orange-800">' + escapeHtml(order.refundRejectNote) + '</span></div>';
        }
        detailHtml += '' +
            '<table class="w-full text-left"><thead><tr class="text-xs text-slate-500 border-b border-slate-200"><th class="pb-1 text-left">San pham</th><th class="pb-1 text-center">So luong / Don gia</th><th class="pb-1 text-right">Thanh tien</th></tr></thead><tbody>' + itemsHtml + '</tbody></table>' +
            '<div class="mt-4 pt-3 border-t border-slate-200 space-y-1 text-sm">' +
            '<div class="flex justify-between"><span class="text-slate-500">Tam tinh</span><span>' + Number(order.subtotal || 0).toLocaleString('vi-VN') + ' dong</span></div>' +
            (order.discount > 0 ? '<div class="flex justify-between text-emerald-600"><span>Giam gia</span><span> -' + Number(order.discount).toLocaleString('vi-VN') + ' dong</span></div>' : '') +
            '<div class="flex justify-between font-semibold text-base pt-1 border-t border-slate-200"><span>Tong</span><span>' + Number(order.total || 0).toLocaleString('vi-VN') + ' dong</span></div>' +
            '</div>';

        document.getElementById('orderDetailContent').innerHTML = detailHtml;
        document.getElementById('orderDetailActions').innerHTML = '<button onclick="document.getElementById(\'orderDetailModal\').classList.add(\'hidden\')" class="px-4 py-2 rounded-lg border border-slate-300 text-sm hover:bg-slate-100 transition">Dong</button>';
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
        closeRejectModal();
        try {
            await api('PUT', '/api/v1/orders/' + pendingRejectOrderId + '/refund/reject', { rejectNote: note });
            showToast('Tu choi hoan tien thanh cong');
            await loadOrders();
        } catch (err) {
            showToast(err.message || 'Tu choi that bai');
        }
    });

    // ─── Filters ────────────────────────────────
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
