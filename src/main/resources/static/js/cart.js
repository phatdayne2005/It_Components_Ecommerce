(function () {
    'use strict';

    const LOCAL_CART_KEY = 'local_cart_items';
    const MERGE_WARNING_KEY = 'cart_merge_warnings_persistent';
    const cartEmpty = document.getElementById('cartEmpty');
    const cartContent = document.getElementById('cartContent');
    const cartItemsList = document.getElementById('cartItemsList');
    const cartStickyBar = document.getElementById('cartStickyBar');
    const cartSelectAll = document.getElementById('cartSelectAll');
    const cartSelectedCount = document.getElementById('cartSelectedCount');
    const cartItemTypeCount = document.getElementById('cartItemTypeCount');
    const cartSelectedSubtotal = document.getElementById('cartSelectedSubtotal');
    const cartCheckoutBtn = document.getElementById('cartCheckoutBtn');
    const cartDeleteSelected = document.getElementById('cartDeleteSelected');
    const cartGuestHint = document.getElementById('cartGuestHint');

    let serverItems = [];

    if (!cartItemsList) return;

    init();
    hydrateMergeWarningsFromSession();

    if (cartSelectAll) {
        cartSelectAll.addEventListener('change', function () {
            toggleSelectAll(cartSelectAll.checked);
        });
    }
    if (cartCheckoutBtn) {
        cartCheckoutBtn.addEventListener('click', goCheckout);
    }
    if (cartDeleteSelected) {
        cartDeleteSelected.addEventListener('click', deleteSelectedItems);
    }

    async function init() {
        if (isLoggedIn()) {
            cartGuestHint.classList.add('hidden');
            await mergeLocalCartIfAny();
            await loadServerCartItems();
        } else {
            cartGuestHint.classList.remove('hidden');
            renderLocalCart();
            syncHeaderCartBadgeFromLocal();
        }
    }

    function goCheckout() {
        const selected = getSelectedItemsSnapshot();
        if (!selected.length) {
            showToast('Vui lòng chọn ít nhất một sản phẩm.');
            return;
        }
        if (!isLoggedIn()) {
            window.location.href = '/login?next=' + encodeURIComponent('/checkout');
            return;
        }
        window.location.href = '/checkout';
    }

    function getSelectedItemsSnapshot() {
        if (isLoggedIn()) {
            return serverItems.filter(function (it) { return it && it.selected === true; });
        }
        const local = JSON.parse(localStorage.getItem(LOCAL_CART_KEY) || '[]');
        return local.filter(function (i) { return i && i.selected !== false; });
    }

    async function mergeLocalCartIfAny() {
        const items = JSON.parse(localStorage.getItem(LOCAL_CART_KEY) || '[]');
        if (!items.length) return;

        const response = await fetch('/api/v1/carts/merge', {
            method: 'POST',
            headers: buildJsonHeaders(),
            body: JSON.stringify({
                items: items.map(function (i) {
                    return { productId: i.productId, quantity: i.quantity, selected: i.selected !== false };
                })
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
        if (!response.headers.get('content-type')?.includes('application/json')) return;
        if (!response.ok) return;
        const cartData = await response.json();
        serverItems = (cartData && Array.isArray(cartData.items))
            ? cartData.items.filter(function (item) { return item && item.product && item.product.id; })
            : [];
        renderServerCart();
        syncHeaderCartBadgeFromServerItems();
    }

    function renderLocalCart() {
        const local = JSON.parse(localStorage.getItem(LOCAL_CART_KEY) || '[]');
        serverItems = [];
        cartItemsList.innerHTML = '';
        if (!local.length) {
            showEmptyState();
            return;
        }
        cartEmpty.classList.add('hidden');
        cartContent.classList.remove('hidden');
        cartStickyBar.classList.remove('hidden');

        local.forEach(function (item) {
            cartItemsList.appendChild(buildRow({
                productId: item.productId,
                name: item.name || ('Sản phẩm #' + item.productId),
                slug: '',
                price: Number(item.price || 0),
                quantity: Number(item.quantity || 0),
                stock: 0,
                image: item.image || '',
                selected: item.selected !== false
            }, true));
        });
        updateFooter();
        syncSelectAllCheckbox();
    }

    function renderServerCart() {
        cartItemsList.innerHTML = '';
        if (!serverItems.length) {
            showEmptyState();
            return;
        }
        cartEmpty.classList.add('hidden');
        cartContent.classList.remove('hidden');
        cartStickyBar.classList.remove('hidden');

        serverItems.forEach(function (it) {
            const p = it.product;
            cartItemsList.appendChild(buildRow({
                productId: p.id,
                name: p.name || ('Sản phẩm #' + p.id),
                slug: p.slug || '',
                price: Number(p.price || 0),
                quantity: Number(it.quantity || 0),
                stock: Number(p.stock || 0),
                image: p.imageUrl || '',
                selected: it.selected === true
            }, false));
        });
        updateFooter();
        syncSelectAllCheckbox();
    }

    function showEmptyState() {
        cartEmpty.classList.remove('hidden');
        cartContent.classList.add('hidden');
        cartStickyBar.classList.add('hidden');
    }

    function buildRow(item, isGuest) {
        const row = document.createElement('div');
        row.className = 'flex gap-3 px-3 md:px-4 py-4 border-b border-slate-100 items-start';
        const lineTotal = item.price * item.quantity;
        const href = item.slug ? ('/products/' + encodeURIComponent(item.slug)) : '/products';
        const imgUrl = normalizeImage(item.image);
        const pid = item.productId;

        const imgBlock = imgUrl
            ? '<img src="' + escapeHtml(imgUrl) + '" alt="" class="w-full h-full object-cover" loading="lazy" />'
            : '<div class="w-full h-full flex items-center justify-center bg-slate-50 text-slate-300"><i class="fa-solid fa-image"></i></div>';

        row.innerHTML =
            '<label class="shrink-0 pt-1 cursor-pointer">' +
            '  <input type="checkbox" class="cart-line-select w-4 h-4 rounded border-slate-300 text-orange-600 focus:ring-orange-500" data-product-id="' + pid + '"' + (item.selected ? ' checked' : '') + ' />' +
            '</label>' +
            '<a href="' + href + '" class="shrink-0 w-[72px] h-[72px] md:w-20 md:h-20 border border-slate-100 rounded-sm overflow-hidden bg-white block">' + imgBlock + '</a>' +
            '<div class="flex-1 min-w-0">' +
            '  <a href="' + href + '" class="text-sm text-slate-900 hover:text-orange-600 line-clamp-2 font-medium">' + escapeHtml(item.name) + '</a>' +
            '  <div class="mt-1 flex flex-wrap items-center gap-x-2 gap-y-0.5 text-xs text-slate-500">' +
            '    <span class="text-slate-800 font-medium">' + formatMoney(item.price) + '</span>' +
            (item.stock ? '<span>• Còn ' + item.stock + '</span>' : '') +
            '  </div>' +
            '  <div class="mt-3 flex flex-wrap items-center justify-between gap-2">' +
            '    <div class="inline-flex items-center border border-slate-200 rounded-sm">' +
            '      <button type="button" class="qty-dec w-9 h-9 grid place-items-center hover:bg-slate-50 text-slate-600 text-xs" aria-label="Giảm"><i class="fa-solid fa-minus"></i></button>' +
            '      <input type="number" min="1" class="qty-input w-11 text-center border-0 border-x border-slate-200 py-2 text-sm focus:ring-0 tabular-nums" value="' + item.quantity + '"' + (item.stock ? ' max="' + item.stock + '"' : '') + ' />' +
            '      <button type="button" class="qty-inc w-9 h-9 grid place-items-center hover:bg-slate-50 text-slate-600 text-xs" aria-label="Tăng"><i class="fa-solid fa-plus"></i></button>' +
            '    </div>' +
            '    <div class="flex items-center gap-2 sm:gap-3">' +
            '      <span class="text-sm font-semibold text-orange-600 tabular-nums">' + formatMoney(lineTotal) + '</span>' +
            '      <button type="button" class="cart-del w-9 h-9 rounded-sm text-slate-400 hover:text-red-500 hover:bg-red-50" aria-label="Xóa"><i class="fa-solid fa-trash-can text-sm"></i></button>' +
            '    </div>' +
            '  </div>' +
            '</div>';

        const cb = row.querySelector('.cart-line-select');
        cb.addEventListener('change', function () {
            setLineSelected(item.productId, cb.checked, isGuest);
        });
        row.querySelector('.qty-dec').addEventListener('click', function () { changeQty(item.productId, item.quantity - 1, isGuest); });
        row.querySelector('.qty-inc').addEventListener('click', function () { changeQty(item.productId, item.quantity + 1, isGuest); });
        const qtyInput = row.querySelector('.qty-input');
        qtyInput.addEventListener('change', function () {
            const v = parseInt(qtyInput.value, 10);
            if (Number.isNaN(v) || v <= 0) {
                qtyInput.value = item.quantity;
                return;
            }
            changeQty(item.productId, v, isGuest);
        });
        row.querySelector('.cart-del').addEventListener('click', function () { removeItem(item.productId, isGuest); });
        return row;
    }

    async function setLineSelected(productId, selected, isGuest) {
        if (isGuest) {
            const local = JSON.parse(localStorage.getItem(LOCAL_CART_KEY) || '[]');
            const line = local.find(function (i) { return i.productId === productId; });
            if (line) {
                line.selected = selected;
                localStorage.setItem(LOCAL_CART_KEY, JSON.stringify(local));
            }
            updateFooter();
            syncSelectAllCheckbox();
            return;
        }
        try {
            const res = await fetch('/api/v1/carts/items/' + productId + '/select?selected=' + (selected ? 'true' : 'false'), {
                method: 'PATCH',
                headers: buildJsonHeaders()
            });
            if (res.status === 401 || res.status === 403) { window.location.href = '/login'; return; }
            await loadServerCartItems();
        } catch (e) { /* ignore */ }
    }

    async function toggleSelectAll(selected) {
        if (isLoggedIn()) {
            try {
                await Promise.all(serverItems.map(function (it) {
                    return fetch('/api/v1/carts/items/' + it.product.id + '/select?selected=' + (selected ? 'true' : 'false'), {
                        method: 'PATCH',
                        headers: buildJsonHeaders()
                    });
                }));
                await loadServerCartItems();
            } catch (e) { /* ignore */ }
            return;
        }
        const local = JSON.parse(localStorage.getItem(LOCAL_CART_KEY) || '[]');
        local.forEach(function (i) { i.selected = selected; });
        localStorage.setItem(LOCAL_CART_KEY, JSON.stringify(local));
        renderLocalCart();
    }

    async function deleteSelectedItems() {
        let targets = [];
        if (isLoggedIn()) {
            targets = serverItems
                .filter(function (it) { return it.selected === true; })
                .map(function (it) { return it.product.id; });
        } else {
            const local = JSON.parse(localStorage.getItem(LOCAL_CART_KEY) || '[]');
            targets = local
                .filter(function (i) { return i.selected !== false; })
                .map(function (i) { return i.productId; });
        }
        if (!targets.length) {
            showToast('Chưa chọn sản phẩm nào.');
            return;
        }
        if (isLoggedIn()) {
            for (let i = 0; i < targets.length; i++) {
                await fetch('/api/v1/carts/items/' + targets[i], { method: 'DELETE', headers: buildJsonHeaders() });
            }
            await loadServerCartItems();
            return;
        }
        const local = JSON.parse(localStorage.getItem(LOCAL_CART_KEY) || '[]')
            .filter(function (item) { return targets.indexOf(item.productId) === -1; });
        localStorage.setItem(LOCAL_CART_KEY, JSON.stringify(local));
        renderLocalCart();
        syncHeaderCartBadgeFromLocal();
    }

    async function changeQty(productId, newQty, isGuest) {
        if (newQty <= 0) return removeItem(productId, isGuest);
        if (isGuest) {
            const local = JSON.parse(localStorage.getItem(LOCAL_CART_KEY) || '[]');
            const line = local.find(function (i) { return i.productId === productId; });
            if (line) {
                line.quantity = newQty;
                localStorage.setItem(LOCAL_CART_KEY, JSON.stringify(local));
            }
            renderLocalCart();
            syncHeaderCartBadgeFromLocal();
            return;
        }
        try {
            const res = await fetch('/api/v1/carts/items/' + productId + '/quantity?quantity=' + newQty, {
                method: 'PATCH',
                headers: buildJsonHeaders()
            });
            if (res.status === 401 || res.status === 403) { window.location.href = '/login'; return; }
            if (!res.ok) {
                const err = await safeJson(res);
                showToast(err && err.message ? err.message : 'Không cập nhật được số lượng.');
                return;
            }
            await loadServerCartItems();
        } catch (e) {
            showToast('Lỗi kết nối.');
        }
    }

    async function removeItem(productId, isGuest) {
        if (isGuest) {
            const local = JSON.parse(localStorage.getItem(LOCAL_CART_KEY) || '[]')
                .filter(function (i) { return i.productId !== productId; });
            localStorage.setItem(LOCAL_CART_KEY, JSON.stringify(local));
            renderLocalCart();
            syncHeaderCartBadgeFromLocal();
            return;
        }
        try {
            const res = await fetch('/api/v1/carts/items/' + productId, {
                method: 'DELETE',
                headers: buildJsonHeaders()
            });
            if (res.status === 401 || res.status === 403) { window.location.href = '/login'; return; }
            await loadServerCartItems();
        } catch (e) { /* ignore */ }
    }

    function updateFooter() {
        let rows = [];
        if (isLoggedIn()) {
            rows = serverItems.map(function (it) {
                return {
                    selected: it.selected === true,
                    qty: Number(it.quantity || 0),
                    price: Number(it.product.price || 0)
                };
            });
        } else {
            const local = JSON.parse(localStorage.getItem(LOCAL_CART_KEY) || '[]');
            rows = local.map(function (i) {
                return {
                    selected: i.selected !== false,
                    qty: Number(i.quantity || 0),
                    price: Number(i.price || 0)
                };
            });
        }
        const selectedLines = rows.filter(function (r) { return r.selected; });
        const totalQty = selectedLines.reduce(function (s, r) { return s + r.qty; }, 0);
        const subtotal = selectedLines.reduce(function (s, r) { return s + r.price * r.qty; }, 0);
        const typeCount = rows.length;

        if (cartSelectedCount) cartSelectedCount.textContent = String(totalQty);
        if (cartItemTypeCount) cartItemTypeCount.textContent = String(typeCount);
        if (cartSelectedSubtotal) cartSelectedSubtotal.textContent = formatMoney(subtotal);
        if (cartCheckoutBtn) cartCheckoutBtn.disabled = totalQty === 0;
    }

    function syncSelectAllCheckbox() {
        if (!cartSelectAll) return;
        let rows = [];
        if (isLoggedIn()) {
            if (!serverItems.length) {
                cartSelectAll.checked = false;
                cartSelectAll.indeterminate = false;
                return;
            }
            const allOn = serverItems.every(function (it) { return it.selected === true; });
            const some = serverItems.some(function (it) { return it.selected === true; });
            cartSelectAll.checked = allOn;
            cartSelectAll.indeterminate = some && !allOn;
            return;
        }
        const local = JSON.parse(localStorage.getItem(LOCAL_CART_KEY) || '[]');
        if (!local.length) {
            cartSelectAll.checked = false;
            cartSelectAll.indeterminate = false;
            return;
        }
        const allOn = local.every(function (i) { return i.selected !== false; });
        const some = local.some(function (i) { return i.selected !== false; });
        cartSelectAll.checked = allOn;
        cartSelectAll.indeterminate = some && !allOn;
    }

    function syncHeaderCartBadgeFromLocal() {
        const cartCountEl = document.getElementById('cartCount');
        if (!cartCountEl) return;
        const localItems = JSON.parse(localStorage.getItem(LOCAL_CART_KEY) || '[]');
        const total = localItems.reduce(function (s, i) { return s + (i.quantity || 0); }, 0);
        cartCountEl.textContent = String(total);
    }

    function syncHeaderCartBadgeFromServerItems() {
        const cartCountEl = document.getElementById('cartCount');
        if (!cartCountEl) return;
        const total = serverItems.reduce(function (s, i) { return s + (i.quantity || 0); }, 0);
        cartCountEl.textContent = String(total);
    }

    function showMergeWarnings(warnings) {
        if (!Array.isArray(warnings) || !warnings.length) return;
        const warningHolder = document.getElementById('merge-warning');
        if (!warningHolder) return;
        warningHolder.innerHTML = '';
        const box = document.createElement('div');
        box.className = 'p-3 rounded-sm bg-amber-50 border border-amber-200';
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

    function hydrateMergeWarningsFromSession() {
        const raw = localStorage.getItem(MERGE_WARNING_KEY);
        if (!raw) return;
        try { showMergeWarnings(JSON.parse(raw)); } catch (e) { /* ignore */ }
    }

    function saveMergeWarnings(warnings) {
        localStorage.setItem(MERGE_WARNING_KEY, JSON.stringify(warnings));
    }

    function formatMoney(n) {
        return Number(n || 0).toLocaleString('vi-VN') + 'đ';
    }

    function normalizeImage(url) {
        if (!url) return '';
        const u = String(url);
        if (u.startsWith('http://') || u.startsWith('https://')) return u;
        return u.startsWith('/') ? u : '/' + u;
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
        if (!isGet) headers['Content-Type'] = 'application/json';
        const token = getAuthToken();
        if (token) headers['Authorization'] = 'Bearer ' + token;
        return headers;
    }

    function showToast(msg) {
        const toast = document.getElementById('toast');
        const toastMsg = document.getElementById('toastMsg');
        if (!toast || !toastMsg) {
            alert(msg);
            return;
        }
        toastMsg.textContent = msg;
        toast.classList.add('show');
        clearTimeout(showToast._t);
        showToast._t = setTimeout(function () { toast.classList.remove('show'); }, 2800);
    }

    function escapeHtml(text) {
        return String(text || '')
            .replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;')
            .replace(/"/g, '&quot;').replace(/'/g, '&#039;');
    }
})();
