(function () {
    'use strict';

    const LOCAL_CART_KEY = 'local_cart_items';
    let cart = JSON.parse(localStorage.getItem(LOCAL_CART_KEY) || '[]');
    const cartCountEl = document.getElementById('cartCount');
    let pendingAdd = null;

    function setCartBadge(total) {
        if (!cartCountEl) return;
        cartCountEl.textContent = String(total || 0);
        cartCountEl.classList.remove('cart-bump');
        void cartCountEl.offsetWidth;
        cartCountEl.classList.add('cart-bump');
    }

    function updateCartBadgeFromLocal() {
        const total = cart.reduce((sum, item) => sum + (item.quantity || 0), 0);
        setCartBadge(total);
    }

    async function refreshCartBadgeFromServer() {
        try {
            const response = await fetch('/api/v1/carts/summary', {
                method: 'GET',
                headers: buildJsonHeaders(true)
            });
            if (!response.ok) return;
            if (!response.headers.get('content-type')?.includes('application/json')) return;
            const summary = await response.json();
            setCartBadge(summary.totalQuantity || 0);
        } catch (e) {
            // ignore
        }
    }

    const modal = createCartModal();
    const modalNameEl = document.getElementById('cartModalName');
    const modalPriceEl = document.getElementById('cartModalPrice');
    const modalQtyEl = document.getElementById('cartModalQty');
    const modalStockEl = document.getElementById('cartModalStock');
    const modalConfirmBtn = document.getElementById('cartModalConfirm');
    const modalCancelBtn = document.getElementById('cartModalCancel');
    const modalCloseBtn = document.getElementById('cartModalClose');

    window.addToCart = function (btn) {
        const productId = Number(btn.getAttribute('data-product-id'));
        const name = btn.getAttribute('data-name');
        const price = parseInt(btn.getAttribute('data-price'), 10);
        const stockRaw = btn.getAttribute('data-stock');
        const maxStock = stockRaw ? parseInt(stockRaw, 10) : null;
        const maxAllowed = Number.isNaN(maxStock) || maxStock === null || maxStock <= 0 ? 999 : maxStock;

        pendingAdd = { productId, name, price, maxAllowed };
        modalNameEl.textContent = name;
        modalPriceEl.textContent = Number(price || 0).toLocaleString('vi-VN') + 'đ';
        modalQtyEl.value = '1';
        modalQtyEl.max = String(maxAllowed);
        if (maxAllowed >= 999) {
            modalStockEl.textContent = 'Số lượng tối đa: không giới hạn';
        } else {
            modalStockEl.textContent = 'Tồn kho hiện tại: ' + maxAllowed;
        }
        openCartModal();
    };

    modalConfirmBtn.addEventListener('click', async () => {
        if (!pendingAdd) return;
        const qty = parseInt(modalQtyEl.value, 10);
        if (Number.isNaN(qty) || qty <= 0) {
            alert('Số lượng không hợp lệ. Vui lòng nhập số nguyên lớn hơn 0.');
            return;
        }
        if (qty > pendingAdd.maxAllowed) {
            alert('Số lượng vượt quá tồn kho hiện tại (' + pendingAdd.maxAllowed + ').');
            return;
        }

        if (isLoggedIn()) {
            // Logged-in: send only the delta (qty being added now), do NOT touch local cart.
            // Server adds delta to existing dbItem.quantity. Otherwise we'd double-count.
            try {
                const res = await fetch('/api/v1/carts/merge', {
                    method: 'POST',
                    headers: buildJsonHeaders(),
                    body: JSON.stringify({
                        items: [{ productId: pendingAdd.productId, quantity: qty, selected: true }]
                    })
                });
                if (res.status === 401 || res.status === 403) {
                    window.location.href = '/login';
                    return;
                }
                if (!res.ok) throw new Error('Server error');
                const data = await res.json();
                if (data && data.hasWarning && Array.isArray(data.warnings)) {
                    localStorage.setItem('cart_merge_warnings_persistent', JSON.stringify(data.warnings));
                    showToast(data.warnings[0]);
                } else {
                    localStorage.removeItem('cart_merge_warnings_persistent');
                    showToast('Đã thêm "' + pendingAdd.name + '" x' + qty + ' vào giỏ hàng');
                }
                await refreshCartBadgeFromServer();
            } catch (err) {
                showToast('Không đồng bộ được giỏ hàng. Vui lòng thử lại.');
                return;
            }
        } else {
            const existing = cart.find(i => i.productId === pendingAdd.productId);
            if (existing) {
                existing.quantity += qty;
            } else {
                cart.push({
                    productId: pendingAdd.productId,
                    name: pendingAdd.name,
                    price: pendingAdd.price,
                    quantity: qty,
                    selected: true
                });
            }
            localStorage.setItem(LOCAL_CART_KEY, JSON.stringify(cart));
            updateCartBadgeFromLocal();
            showToast('Đã thêm "' + pendingAdd.name + '" x' + qty + ' vào giỏ hàng');
        }
        closeCartModal();
    });

    modalCancelBtn.addEventListener('click', closeCartModal);
    modalCloseBtn.addEventListener('click', closeCartModal);
    modal.addEventListener('click', (e) => {
        if (e.target === modal) closeCartModal();
    });
    document.addEventListener('keydown', (e) => {
        if (e.key === 'Escape' && !modal.classList.contains('hidden')) {
            closeCartModal();
        }
    });

    window.handleSearch = function (e) {
        e.preventDefault();
        const q = document.getElementById('searchInput').value.trim();
        if (!q) return false;
        showToast('Tìm kiếm: "' + q + '" (chức năng đang phát triển)');
        return false;
    };

    window.handleSubscribe = function (e) {
        e.preventDefault();
        const email = document.getElementById('newsletterEmail').value.trim();
        if (!email) return false;
        showToast('Cảm ơn bạn đã đăng ký nhận tin!');
        document.getElementById('newsletterEmail').value = '';
        return false;
    };

    function showToast(message) {
        const toast = document.getElementById('toast');
        const msg = document.getElementById('toastMsg');
        if (!toast || !msg) return;
        msg.textContent = message;
        toast.classList.add('show');
        clearTimeout(showToast._timer);
        showToast._timer = setTimeout(() => toast.classList.remove('show'), 2200);
    }

    const backToTopBtn = document.getElementById('backToTop');
    window.scrollToTop = function () {
        window.scrollTo({ top: 0, behavior: 'smooth' });
    };

    window.addEventListener('scroll', () => {
        if (!backToTopBtn) return;
        if (window.scrollY > 400) backToTopBtn.classList.remove('hidden');
        else backToTopBtn.classList.add('hidden');
    });

    async function migrateLocalCartOnLogin() {
        const items = JSON.parse(localStorage.getItem(LOCAL_CART_KEY) || '[]');
        if (!items.length) return;
        try {
            const response = await fetch('/api/v1/carts/merge', {
                method: 'POST',
                headers: buildJsonHeaders(),
                body: JSON.stringify({
                    items: items.map(i => ({
                        productId: i.productId,
                        quantity: i.quantity,
                        selected: i.selected !== false
                    }))
                })
            });
            if (response.status === 401 || response.status === 403) return;
            if (!response.ok) return;
            const data = await response.json();
            if (data && data.hasWarning && Array.isArray(data.warnings)) {
                localStorage.setItem('cart_merge_warnings_persistent', JSON.stringify(data.warnings));
            }
            // local cart now lives on the server
            localStorage.removeItem(LOCAL_CART_KEY);
            cart = [];
        } catch (e) {
            // ignore
        }
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
        } catch (e) {
            return '';
        }
    }

    function isLoggedIn() {
        return !!getAuthToken();
    }

    function buildJsonHeaders(isGet) {
        const headers = { 'Accept': 'application/json' };
        if (!isGet) {
            headers['Content-Type'] = 'application/json';
            headers['X-XSRF-TOKEN'] = readCsrfTokenFromCookie();
        }
        const token = getAuthToken();
        if (token) headers.Authorization = 'Bearer ' + token;
        return headers;
    }

    // === User menu (login state) ===
    function renderUserMenu() {
        const el = document.getElementById('userMenu');
        if (!el) return;
        el.innerHTML = '';
        if (isLoggedIn()) {
            let displayName = 'Tài khoản';
            try {
                const auth = JSON.parse(localStorage.getItem('auth') || '{}');
                displayName = auth.fullName || auth.username || 'Tài khoản';
            } catch (e) { /* keep default */ }

            const wrap = document.createElement('div');
            wrap.className = 'relative';
            wrap.innerHTML =
                '<button id="userMenuBtn" type="button" class="flex flex-col items-center text-xs hover:text-brand-600">' +
                '  <i class="fa-solid fa-circle-user text-lg"></i>' +
                '  <span class="mt-1 max-w-[100px] truncate">' + escapeHtml(displayName) + '</span>' +
                '</button>' +
                '<div id="userMenuDropdown" class="hidden absolute right-0 top-full mt-2 w-48 bg-white border border-slate-200 rounded-lg shadow-lg z-50">' +
                '  <a href="/orders/my" class="block px-4 py-2 text-sm text-slate-700 hover:bg-slate-50"><i class="fa-solid fa-box-open mr-2 text-slate-400"></i> Đơn hàng của tôi</a>' +
                '  <button type="button" id="logoutBtn" class="w-full text-left block px-4 py-2 text-sm text-red-600 hover:bg-red-50"><i class="fa-solid fa-right-from-bracket mr-2"></i> Đăng xuất</button>' +
                '</div>';
            el.appendChild(wrap);

            const btn = wrap.querySelector('#userMenuBtn');
            const dropdown = wrap.querySelector('#userMenuDropdown');
            btn.addEventListener('click', (e) => {
                e.stopPropagation();
                dropdown.classList.toggle('hidden');
            });
            document.addEventListener('click', (e) => {
                if (!wrap.contains(e.target)) dropdown.classList.add('hidden');
            });
            wrap.querySelector('#logoutBtn').addEventListener('click', () => {
                localStorage.removeItem('auth');
                localStorage.removeItem(LOCAL_CART_KEY);
                localStorage.removeItem('cart_merge_warnings_persistent');
                window.location.href = '/';
            });
        } else {
            const wrap = document.createElement('div');
            wrap.className = 'flex items-center gap-3 text-xs';
            wrap.innerHTML =
                '<a href="/login" class="flex flex-col items-center hover:text-brand-600">' +
                '  <i class="fa-regular fa-user text-lg"></i>' +
                '  <span class="mt-1">Đăng nhập</span>' +
                '</a>' +
                '<a href="/register" class="hidden md:flex flex-col items-center hover:text-brand-600">' +
                '  <i class="fa-solid fa-user-plus text-lg"></i>' +
                '  <span class="mt-1">Đăng ký</span>' +
                '</a>';
            el.appendChild(wrap);
        }
    }

    function escapeHtml(text) {
        return String(text || '')
            .replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;')
            .replace(/"/g, '&quot;').replace(/'/g, '&#039;');
    }

    renderUserMenu();

    if (isLoggedIn()) {
        // If guest cart still has items (just logged in), migrate first then refresh badge.
        const localItems = JSON.parse(localStorage.getItem(LOCAL_CART_KEY) || '[]');
        if (localItems.length > 0) {
            migrateLocalCartOnLogin().then(refreshCartBadgeFromServer);
        } else {
            refreshCartBadgeFromServer();
        }
    } else {
        updateCartBadgeFromLocal();
    }

    function createCartModal() {
        let el = document.getElementById('cartQtyModal');
        if (el) return el;

        el = document.createElement('div');
        el.id = 'cartQtyModal';
        el.className = 'fixed inset-0 z-50 hidden bg-black/50 p-4';
        el.innerHTML = '' +
            '<div class="min-h-full flex items-center justify-center">' +
            '  <div class="w-full max-w-md bg-white rounded-2xl shadow-2xl overflow-hidden">' +
            '    <div class="px-5 py-4 border-b border-slate-200 flex items-center justify-between">' +
            '      <h3 class="text-lg font-bold text-slate-900">Thêm vào giỏ hàng</h3>' +
            '      <button id="cartModalClose" class="w-8 h-8 rounded-full hover:bg-slate-100 text-slate-500"><i class="fa-solid fa-xmark"></i></button>' +
            '    </div>' +
            '    <div class="px-5 py-4 space-y-3">' +
            '      <p class="text-sm text-slate-500">Sản phẩm</p>' +
            '      <p id="cartModalName" class="font-semibold text-slate-900"></p>' +
            '      <p id="cartModalPrice" class="text-red-600 font-bold"></p>' +
            '      <p id="cartModalStock" class="text-xs text-slate-500"></p>' +
            '      <div>' +
            '        <label for="cartModalQty" class="block text-sm text-slate-600 mb-1">Số lượng</label>' +
            '        <input id="cartModalQty" type="number" min="1" value="1" class="w-28 px-3 py-2 border border-slate-300 rounded-lg" />' +
            '      </div>' +
            '    </div>' +
            '    <div class="px-5 py-4 border-t border-slate-200 flex items-center justify-end gap-2">' +
            '      <button id="cartModalCancel" class="px-4 py-2 rounded-lg border border-slate-300 hover:bg-slate-50">Hủy</button>' +
            '      <button id="cartModalConfirm" class="px-4 py-2 rounded-lg bg-brand-600 text-white hover:bg-brand-700">Xác nhận thêm</button>' +
            '    </div>' +
            '  </div>' +
            '</div>';
        document.body.appendChild(el);
        return el;
    }

    function openCartModal() {
        modal.classList.remove('hidden');
        modalQtyEl.focus();
        modalQtyEl.select();
    }

    function closeCartModal() {
        modal.classList.add('hidden');
        pendingAdd = null;
    }
})();
