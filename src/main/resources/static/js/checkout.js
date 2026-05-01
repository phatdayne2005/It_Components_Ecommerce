(function () {
    'use strict';

    const LOCAL_CART_KEY = 'local_cart_items';
    const MERGE_WARNING_KEY = 'cart_merge_warnings_persistent';
    const form = document.getElementById('checkoutForm');
    const submitButton = document.getElementById('checkoutSubmitButton');
    const checkoutItemsList = document.getElementById('checkoutItemsList');
    const checkoutItemsEmpty = document.getElementById('checkoutItemsEmpty');
    const checkoutItemCount = document.getElementById('checkoutItemCount');
    const checkoutTotal = document.getElementById('checkoutTotal');
    let serverItems = []; // raw server cart items

    if (!form) return;

    init();
    hydrateMergeWarningsFromSession();

    async function init() {
        if (isLoggedIn()) {
            await mergeLocalCartIfAny();
            await loadServerCartItems();
        } else {
            renderCheckoutItems();
            syncHeaderCartBadgeFromLocal();
        }
    }

    form.addEventListener('submit', async function (event) {
        event.preventDefault();
        if (!form.checkValidity()) { form.reportValidity(); return; }

        clearErrors();
        submitButton.disabled = true;
        const originalButtonText = submitButton.textContent;
        submitButton.textContent = 'Đang xử lý...';

        try {
            if (!isLoggedIn()) {
                showSummaryError('Vui lòng đăng nhập để thanh toán.');
                window.location.href = '/login';
                return;
            }
            await loadServerCartItems();

            const payload = buildCheckoutPayload();
            if (!payload.items.length) {
                showSummaryError('Giỏ hàng trống. Vui lòng chọn sản phẩm.');
                return;
            }

            const response = await fetch('/api/v1/orders/checkout', {
                method: 'POST',
                headers: buildJsonHeaders(),
                body: JSON.stringify(payload)
            });

            if (response.status === 401 || response.status === 403) {
                window.location.href = '/login';
                return;
            }

            if (!response.headers.get('content-type')?.includes('application/json')) throw new Error('Server Error');
            const data = await response.json();

            if (!response.ok) {
                if (Array.isArray(data)) {
                    renderFieldErrors(data);
                    return;
                }
                throw new Error(data.message || 'Checkout thất bại');
            }

            // Server đã tự xóa các item đã thanh toán khỏi giỏ.
            if (data && data.orderCode) {
                window.location.href = '/?checkoutSuccess=' + encodeURIComponent(data.orderCode);
            } else {
                window.location.href = '/';
            }
        } catch (error) {
            showSummaryError(error.message || 'Có lỗi hệ thống.');
        } finally {
            submitButton.disabled = false;
            submitButton.textContent = originalButtonText;
        }
    });

    function buildCheckoutPayload() {
        const items = isLoggedIn()
            ? serverItems
                  .filter(function (item) { return item && item.selected === true && item.product && item.product.id; })
                  .map(function (item) { return { productId: item.product.id, quantity: item.quantity }; })
            : JSON.parse(localStorage.getItem(LOCAL_CART_KEY) || '[]')
                  .filter(function (item) { return item && item.selected === true; })
                  .map(function (item) { return { productId: item.productId, quantity: item.quantity }; });

        return {
            phone: document.getElementById('phone').value.trim(),
            email: document.getElementById('email').value.trim(),
            address: document.getElementById('address').value.trim(),
            note: document.getElementById('note').value.trim(),
            paymentMethod: document.getElementById('paymentMethod').value,
            items: items
        };
    }

    async function mergeLocalCartIfAny() {
        const items = JSON.parse(localStorage.getItem(LOCAL_CART_KEY) || '[]');
        if (!items.length) return;

        const response = await fetch('/api/v1/carts/merge', {
            method: 'POST',
            headers: buildJsonHeaders(),
            body: JSON.stringify({
                items: items.map(i => ({ productId: i.productId, quantity: i.quantity, selected: i.selected !== false }))
            })
        });
        if (response.status === 401 || response.status === 403) {
            window.location.href = '/login';
            return;
        }
        if (response.ok && response.headers.get('content-type')?.includes('application/json')) {
            const data = await response.json();
            if (data && data.hasWarning && Array.isArray(data.warnings)) {
                saveMergeWarnings(data.warnings);
                showMergeWarnings(data.warnings);
            }
        }
        localStorage.removeItem(LOCAL_CART_KEY);
    }

    async function loadServerCartItems() {
        const response = await fetch('/api/v1/carts', {
            method: 'GET',
            headers: buildJsonHeaders(true)
        });
        if (response.status === 401 || response.status === 403) {
            window.location.href = '/login';
            return;
        }
        if (!response.headers.get('content-type')?.includes('application/json')) throw new Error('Server Error');
        if (!response.ok) throw new Error('Cannot load cart');

        const cartData = await response.json();
        serverItems = (cartData && Array.isArray(cartData.items))
            ? cartData.items.filter(function (item) { return item && item.product && item.product.id; })
            : [];
        renderCheckoutItems();
        syncHeaderCartBadgeFromServerItems();
    }

    async function changeQty(productId, newQty) {
        if (newQty <= 0) {
            return removeItem(productId);
        }
        if (isLoggedIn()) {
            try {
                const res = await fetch('/api/v1/carts/items/' + productId + '/quantity?quantity=' + newQty, {
                    method: 'PATCH',
                    headers: buildJsonHeaders()
                });
                if (res.status === 401 || res.status === 403) { window.location.href = '/login'; return; }
                if (!res.ok) {
                    const err = await safeJson(res);
                    showSummaryError(err && err.message ? err.message : 'Không cập nhật được số lượng (có thể vượt tồn kho).');
                    return;
                }
                await loadServerCartItems();
            } catch (e) {
                showSummaryError('Lỗi kết nối khi cập nhật số lượng.');
            }
        } else {
            const local = JSON.parse(localStorage.getItem(LOCAL_CART_KEY) || '[]');
            const item = local.find(i => i.productId === productId);
            if (item) {
                item.quantity = newQty;
                localStorage.setItem(LOCAL_CART_KEY, JSON.stringify(local));
            }
            renderCheckoutItems();
            syncHeaderCartBadgeFromLocal();
        }
    }

    async function removeItem(productId) {
        if (isLoggedIn()) {
            try {
                const res = await fetch('/api/v1/carts/items/' + productId, {
                    method: 'DELETE',
                    headers: buildJsonHeaders()
                });
                if (res.status === 401 || res.status === 403) { window.location.href = '/login'; return; }
                if (!res.ok) {
                    showSummaryError('Không xóa được sản phẩm.');
                    return;
                }
                await loadServerCartItems();
            } catch (e) {
                showSummaryError('Lỗi kết nối khi xóa sản phẩm.');
            }
        } else {
            const local = JSON.parse(localStorage.getItem(LOCAL_CART_KEY) || '[]')
                .filter(i => i.productId !== productId);
            localStorage.setItem(LOCAL_CART_KEY, JSON.stringify(local));
            renderCheckoutItems();
            syncHeaderCartBadgeFromLocal();
        }
    }

    async function safeJson(res) {
        try { return await res.json(); } catch (e) { return null; }
    }

    function readCsrfTokenFromCookie() {
        const cookies = document.cookie ? document.cookie.split('; ') : [];
        for (let i = 0; i < cookies.length; i++) {
            const parts = cookies[i].split('=');
            if (parts[0] === 'XSRF-TOKEN') {
                return decodeURIComponent(parts.slice(1).join('='));
            }
        }
        return '';
    }

    function getAuthToken() {
        try {
            const auth = JSON.parse(localStorage.getItem('auth') || '{}');
            return auth && auth.token ? auth.token : '';
        } catch (e) { return ''; }
    }

    function isLoggedIn() { return !!getAuthToken(); }

    function buildJsonHeaders(isGet) {
        const headers = { 'Accept': 'application/json' };
        if (!isGet) {
            headers['Content-Type'] = 'application/json';
            headers['X-XSRF-TOKEN'] = readCsrfTokenFromCookie();
        }
        const token = getAuthToken();
        if (token) headers['Authorization'] = 'Bearer ' + token;
        return headers;
    }

    function clearErrors() {
        ['phone', 'email', 'address', 'paymentMethod', 'items'].forEach(function (field) {
            const holder = document.getElementById('error-' + field);
            if (holder) holder.textContent = '';
        });
        const summary = document.getElementById('error-summary');
        if (summary) summary.textContent = '';
    }

    function renderFieldErrors(errors) {
        errors.forEach(function (errorObj) {
            const holder = document.getElementById('error-' + errorObj.fieldId);
            if (!holder) return;
            const div = document.createElement('div');
            div.className = 'text-red-600 text-sm';
            div.textContent = errorObj.errorMessage;
            holder.appendChild(div);
        });
    }

    function showSummaryError(message) {
        const summary = document.getElementById('error-summary');
        if (!summary) return;
        const div = document.createElement('div');
        div.className = 'text-red-600 text-sm';
        div.textContent = message;
        summary.innerHTML = '';
        summary.appendChild(div);
    }

    function showMergeWarnings(warnings) {
        if (!Array.isArray(warnings) || !warnings.length) return;
        const warningHolder = document.getElementById('merge-warning');
        if (!warningHolder) return;
        warningHolder.innerHTML = '';

        const box = document.createElement('div');
        box.className = 'p-3 rounded-lg bg-amber-50 border border-amber-200';
        const header = document.createElement('div');
        header.className = 'flex items-start justify-between gap-2 mb-1';
        const title = document.createElement('div');
        title.className = 'font-semibold text-amber-800 text-sm';
        title.innerHTML = '<i class="fa-solid fa-triangle-exclamation mr-1"></i> Thông báo về giỏ hàng';
        const closeBtn = document.createElement('button');
        closeBtn.type = 'button';
        closeBtn.className = 'text-amber-700 hover:text-amber-900';
        closeBtn.innerHTML = '<i class="fa-solid fa-xmark"></i>';
        closeBtn.addEventListener('click', function () {
            warningHolder.innerHTML = '';
            localStorage.removeItem(MERGE_WARNING_KEY);
        });
        header.appendChild(title);
        header.appendChild(closeBtn);
        box.appendChild(header);

        warnings.forEach(function (message) {
            const p = document.createElement('div');
            p.className = 'text-amber-700 text-sm';
            p.textContent = '• ' + message;
            box.appendChild(p);
        });
        warningHolder.appendChild(box);
    }

    function syncHeaderCartBadgeFromLocal() {
        const cartCountEl = document.getElementById('cartCount');
        if (!cartCountEl) return;
        const localItems = JSON.parse(localStorage.getItem(LOCAL_CART_KEY) || '[]');
        const total = localItems.reduce((s, i) => s + (i.quantity || 0), 0);
        cartCountEl.textContent = String(total);
    }

    function syncHeaderCartBadgeFromServerItems() {
        const cartCountEl = document.getElementById('cartCount');
        if (!cartCountEl) return;
        const total = serverItems.reduce((s, i) => s + (i.quantity || 0), 0);
        cartCountEl.textContent = String(total);
    }

    function hydrateMergeWarningsFromSession() {
        const raw = localStorage.getItem(MERGE_WARNING_KEY);
        if (!raw) return;
        try { showMergeWarnings(JSON.parse(raw)); } catch (e) { /* ignore */ }
    }

    function saveMergeWarnings(warnings) {
        localStorage.setItem(MERGE_WARNING_KEY, JSON.stringify(warnings));
    }

    function renderCheckoutItems() {
        if (!checkoutItemsList || !checkoutItemsEmpty || !checkoutItemCount || !checkoutTotal) return;

        let items = [];
        if (isLoggedIn()) {
            items = serverItems
                .filter(function (it) { return it.selected === true; })
                .map(function (it) {
                    return {
                        productId: it.product.id,
                        name: it.product.name || ('Sản phẩm #' + it.product.id),
                        price: Number(it.product.price || 0),
                        quantity: Number(it.quantity || 0),
                        stock: Number(it.product.stock || 0),
                        image: it.product.imageUrl || ''
                    };
                });
        } else {
            const local = JSON.parse(localStorage.getItem(LOCAL_CART_KEY) || '[]')
                .filter(function (i) { return i && i.selected === true; });
            items = local.map(function (i) {
                return {
                    productId: i.productId,
                    name: i.name || ('Sản phẩm #' + i.productId),
                    price: Number(i.price || 0),
                    quantity: Number(i.quantity || 0),
                    stock: 0,
                    image: ''
                };
            });
        }

        checkoutItemsList.innerHTML = '';
        if (!items.length) {
            checkoutItemsEmpty.classList.remove('hidden');
            checkoutItemCount.textContent = '0 sản phẩm';
            checkoutTotal.textContent = '0đ';
            return;
        }
        checkoutItemsEmpty.classList.add('hidden');

        const totalQuantity = items.reduce((s, i) => s + i.quantity, 0);
        const totalPrice = items.reduce((s, i) => s + (i.price * i.quantity), 0);
        checkoutItemCount.textContent = totalQuantity + ' sản phẩm';
        checkoutTotal.textContent = Number(totalPrice).toLocaleString('vi-VN') + 'đ';

        items.forEach(function (item) {
            const row = document.createElement('div');
            row.className = 'flex items-center gap-3 bg-white rounded-lg border border-slate-200 p-3';
            row.innerHTML =
                '<div class="flex-1 min-w-0">' +
                '  <div class="font-medium text-sm truncate">' + escapeHtml(item.name) + '</div>' +
                '  <div class="text-xs text-slate-500 mt-0.5">' + Number(item.price).toLocaleString('vi-VN') + 'đ / sp' +
                       (item.stock ? ' • Tồn: ' + item.stock : '') + '</div>' +
                '</div>' +
                '<div class="flex items-center border border-slate-300 rounded-lg">' +
                '  <button type="button" class="qty-dec w-8 h-8 grid place-items-center hover:bg-slate-100 text-slate-700"><i class="fa-solid fa-minus text-xs"></i></button>' +
                '  <input type="number" min="1" class="qty-input w-12 text-center border-0 focus:ring-0 text-sm" value="' + item.quantity + '"' + (item.stock ? ' max="' + item.stock + '"' : '') + ' />' +
                '  <button type="button" class="qty-inc w-8 h-8 grid place-items-center hover:bg-slate-100 text-slate-700"><i class="fa-solid fa-plus text-xs"></i></button>' +
                '</div>' +
                '<div class="text-sm font-semibold w-24 text-right">' + Number(item.price * item.quantity).toLocaleString('vi-VN') + 'đ</div>' +
                '<button type="button" class="qty-del w-8 h-8 grid place-items-center text-red-500 hover:bg-red-50 rounded"><i class="fa-solid fa-trash"></i></button>';

            row.querySelector('.qty-dec').addEventListener('click', () => changeQty(item.productId, item.quantity - 1));
            row.querySelector('.qty-inc').addEventListener('click', () => changeQty(item.productId, item.quantity + 1));
            row.querySelector('.qty-del').addEventListener('click', () => removeItem(item.productId));
            const qtyInput = row.querySelector('.qty-input');
            qtyInput.addEventListener('change', () => {
                const v = parseInt(qtyInput.value, 10);
                if (Number.isNaN(v) || v <= 0) {
                    qtyInput.value = item.quantity;
                    return;
                }
                changeQty(item.productId, v);
            });

            checkoutItemsList.appendChild(row);
        });
    }

    function escapeHtml(text) {
        return String(text || '')
            .replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;')
            .replace(/"/g, '&quot;').replace(/'/g, '&#039;');
    }
})();
