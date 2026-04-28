(function () {
    'use strict';

    const LOCAL_CART_KEY = 'local_cart_items';
    const cart = JSON.parse(localStorage.getItem(LOCAL_CART_KEY) || '[]');
    const cartCountEl = document.getElementById('cartCount');
    let pendingAdd = null;

    function updateCartBadge() {
        if (!cartCountEl) return;
        const total = cart.reduce((sum, item) => sum + (item.quantity || 0), 0);
        cartCountEl.textContent = total;
        cartCountEl.classList.remove('cart-bump');
        void cartCountEl.offsetWidth;
        cartCountEl.classList.add('cart-bump');
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

        const existing = cart.find(i => i.productId === pendingAdd.productId);
        if (existing) {
            existing.quantity += qty;
        } else {
            cart.push({ productId: pendingAdd.productId, quantity: qty, selected: true });
        }
        localStorage.setItem(LOCAL_CART_KEY, JSON.stringify(cart));
        if (isLoggedIn()) {
            try {
                await mergeLocalCartToDatabase();
            } catch (err) {
                showToast('Khong dong bo duoc gio hang. Vui long thu lai.');
                return;
            }
        }
        updateCartBadge();
        showToast('Đã thêm "' + pendingAdd.name + '" x' + qty + ' vào giỏ hàng');
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

    const cartBtn = document.getElementById('cartBtn');
    if (cartBtn && !isLoggedIn() && !cart.length) {
        cartBtn.addEventListener('click', () => showToast('Giỏ hàng đang trống'));
    }

    async function mergeLocalCartToDatabase() {
        const items = JSON.parse(localStorage.getItem(LOCAL_CART_KEY) || '[]');
        if (!items.length) return;
        const response = await fetch('/api/v1/carts/merge', {
            method: 'POST',
            headers: buildJsonHeaders(),
            body: JSON.stringify({ items })
        });
        if (response.status === 401 || response.status === 403) {
            window.location.href = '/login';
            return;
        }
        if (!response.headers.get('content-type')?.includes('application/json')) throw new Error('Server Error');
        if (response.ok) {
            const mergeData = await response.json();
            localStorage.removeItem(LOCAL_CART_KEY);
            cart.length = 0;
            if (mergeData && mergeData.cart && Array.isArray(mergeData.cart.items)) {
                mergeData.cart.items.forEach(function (item) {
                    if (item && item.product && item.product.id) {
                        cart.push({
                            productId: item.product.id,
                            quantity: item.quantity || 0,
                            selected: item.selected === true
                        });
                    }
                });
            }
            if (mergeData && mergeData.hasWarning === true && Array.isArray(mergeData.warnings)) {
                localStorage.setItem('cart_merge_warnings_persistent', JSON.stringify(mergeData.warnings));
                showToast('Gio hang da duoc dong bo voi mot so canh bao.');
            } else {
                localStorage.removeItem('cart_merge_warnings_persistent');
            }
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

    function buildJsonHeaders() {
        const headers = {
            'Content-Type': 'application/json',
            'X-XSRF-TOKEN': readCsrfTokenFromCookie(),
            'Accept': 'application/json'
        };
        const token = getAuthToken();
        if (token) {
            headers.Authorization = 'Bearer ' + token;
        }
        return headers;
    }

    updateCartBadge();
    if (isLoggedIn() && cart.length > 0) {
        mergeLocalCartToDatabase()
            .then(() => updateCartBadge())
            .catch(() => {});
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
