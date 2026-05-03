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
    const checkoutSubtotal = document.getElementById('checkoutSubtotal');
    const checkoutDiscountRow = document.getElementById('checkoutDiscountRow');
    const checkoutDiscount = document.getElementById('checkoutDiscount');
    const voucherCodeInput = document.getElementById('voucherCode');
    const voucherApplyBtn = document.getElementById('voucherApplyBtn');
    const voucherHint = document.getElementById('voucherHint');
    let serverItems = []; // raw server cart items
    let appliedVoucherCode = '';
    let previewDiscount = 0;

    if (!form) return;

    init();
    hydrateMergeWarningsFromSession();

    if (voucherApplyBtn) {
        voucherApplyBtn.addEventListener('click', applyVoucherPreview);
    }
    if (voucherCodeInput) {
        voucherCodeInput.addEventListener('input', function () {
            if (appliedVoucherCode && voucherCodeInput.value.trim().toUpperCase() !== appliedVoucherCode) {
                appliedVoucherCode = '';
                previewDiscount = 0;
                renderCheckoutItems();
            }
        });
    }

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
            if (data && data.sepayCheckoutActionUrl && data.sepayCheckoutFields) {
                const redirectForm = document.createElement('form');
                redirectForm.method = 'POST';
                redirectForm.action = data.sepayCheckoutActionUrl;
                
                for (const key in data.sepayCheckoutFields) {
                    if (data.sepayCheckoutFields.hasOwnProperty(key)) {
                        const hiddenField = document.createElement('input');
                        hiddenField.type = 'hidden';
                        hiddenField.name = key;
                        hiddenField.value = data.sepayCheckoutFields[key];
                        redirectForm.appendChild(hiddenField);
                    }
                }
                document.body.appendChild(redirectForm);
                redirectForm.submit();
            } else if (data && data.orderCode) {
                window.location.href = '/payment/success?orderCode=' + encodeURIComponent(data.orderCode);
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
            voucherCode: appliedVoucherCode || null,
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

    async function safeJson(res) {
        try { return await res.json(); } catch (e) { return null; }
    }

    function api() { return window.TechPartsApi; }
    function getAuthToken() {
        const a = api();
        return a && a.getAuthToken ? a.getAuthToken() : '';
    }
    function isLoggedIn() {
        const a = api();
        return a && a.isLoggedIn ? a.isLoggedIn() : !!getAuthToken();
    }
    function buildJsonHeaders(isGet) {
        const a = api();
        if (a && a.buildJsonHeaders) return a.buildJsonHeaders(isGet);
        const headers = { 'Accept': 'application/json' };
        if (!isGet) {
            headers['Content-Type'] = 'application/json';
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
                        slug: it.product.slug || '',
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
                    slug: '',
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
            setTotals(0, 0, 0);
            return;
        }
        checkoutItemsEmpty.classList.add('hidden');

        const totalQuantity = items.reduce((s, i) => s + i.quantity, 0);
        const totalPrice = items.reduce((s, i) => s + (i.price * i.quantity), 0);
        checkoutItemCount.textContent = totalQuantity + ' sản phẩm';
        const discount = (appliedVoucherCode && previewDiscount > 0) ? previewDiscount : 0;
        setTotals(totalPrice, discount, Math.max(0, totalPrice - discount));

        items.forEach(function (item) {
            const row = document.createElement('div');
            row.className = 'flex gap-3 py-3';
            const href = item.slug ? ('/products/' + encodeURIComponent(item.slug)) : '/products';
            const imgUrl = normalizeCheckoutImage(item.image);
            const imgHtml = imgUrl
                ? '<img src="' + escapeHtml(imgUrl) + '" alt="" class="object-cover rounded-sm border border-slate-100 bg-white" loading="lazy" style="width: 56px; height: 56px; flex-shrink: 0;" />'
                : '<div class="rounded-sm border border-slate-100 bg-slate-50 flex items-center justify-center text-slate-300 text-sm" style="width: 56px; height: 56px; flex-shrink: 0;"><i class="fa-solid fa-image"></i></div>';

            row.innerHTML =
                '<a href="' + href + '" class="shrink-0" style="flex-shrink: 0;">' + imgHtml + '</a>' +
                '<div class="flex-1 min-w-0 pr-2" style="flex: 1; min-width: 0;">' +
                '  <a href="' + href + '" class="text-sm font-medium text-slate-900 hover:text-brand-600 line-clamp-2">' + escapeHtml(item.name) + '</a>' +
                '  <div class="text-xs text-slate-500 mt-1">SL: x' + item.quantity + '</div>' +
                '</div>' +
                '<div class="text-sm font-semibold text-brand-600 tabular-nums shrink-0 self-start" style="flex-shrink: 0;">' +
                Number(item.price * item.quantity).toLocaleString('vi-VN') + 'đ</div>';

            checkoutItemsList.appendChild(row);
        });
    }

    function setTotals(subtotal, discount, grand) {
        if (checkoutSubtotal) checkoutSubtotal.textContent = Number(subtotal).toLocaleString('vi-VN') + 'đ';
        if (checkoutDiscountRow && checkoutDiscount) {
            if (discount > 0) {
                checkoutDiscountRow.classList.remove('hidden');
                checkoutDiscount.textContent = '−' + Number(discount).toLocaleString('vi-VN') + 'đ';
            } else {
                checkoutDiscountRow.classList.add('hidden');
            }
        }
        if (checkoutTotal) checkoutTotal.textContent = Number(grand).toLocaleString('vi-VN') + 'đ';
    }

    async function applyVoucherPreview() {
        if (!voucherHint || !voucherCodeInput) return;
        voucherHint.textContent = '';
        const code = voucherCodeInput.value.trim();
        if (!code) {
            appliedVoucherCode = '';
            previewDiscount = 0;
            renderCheckoutItems();
            return;
        }
        let items = [];
        if (isLoggedIn()) {
            items = serverItems.filter(function (it) { return it.selected === true; })
                .map(function (it) {
                    return { price: Number(it.product.price || 0), quantity: Number(it.quantity || 0) };
                });
        } else {
            const local = JSON.parse(localStorage.getItem(LOCAL_CART_KEY) || '[]')
                .filter(function (i) { return i && i.selected === true; });
            items = local.map(function (i) {
                return { price: Number(i.price || 0), quantity: Number(i.quantity || 0) };
            });
        }
        const subtotal = items.reduce(function (s, i) { return s + i.price * i.quantity; }, 0);
        if (subtotal <= 0) {
            voucherHint.textContent = 'Chưa có sản phẩm để áp dụng voucher.';
            return;
        }
        try {
            const res = await fetch('/api/v1/vouchers/preview', {
                method: 'POST',
                headers: buildJsonHeaders(false),
                body: JSON.stringify({ code: code, subtotal: subtotal })
            });
            const data = await res.json();
            if (!res.ok) {
                appliedVoucherCode = '';
                previewDiscount = 0;
                renderCheckoutItems();
                voucherHint.textContent = (data && data.message) ? data.message : 'Không áp dụng được mã.';
                return;
            }
            appliedVoucherCode = data.code || code.toUpperCase();
            previewDiscount = Number(data.discount || 0);
            voucherHint.textContent = 'Đã áp dụng: ' + (data.name || '') + ' — giảm ' + previewDiscount.toLocaleString('vi-VN') + 'đ';
            if (voucherCodeInput) voucherCodeInput.value = appliedVoucherCode;
            renderCheckoutItems();
        } catch (e) {
            voucherHint.textContent = 'Lỗi kiểm tra mã voucher.';
        }
    }

    function normalizeCheckoutImage(url) {
        if (!url) return '';
        const u = String(url);
        if (u.startsWith('http://') || u.startsWith('https://')) return u;
        return u.startsWith('/') ? u : '/' + u;
    }

    function escapeHtml(text) {
        return String(text || '')
            .replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;')
            .replace(/"/g, '&quot;').replace(/'/g, '&#039;');
    }
})();
