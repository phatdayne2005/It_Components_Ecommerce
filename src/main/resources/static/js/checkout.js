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
    let mergedServerItems = [];

    if (!form) {
        return;
    }

    if (isLoggedIn()) {
        mergeLocalCartToDatabase()
            .then(loadServerCartItems)
            .then(syncHeaderCartBadgeFromServer)
            .then(renderCheckoutItems)
            .catch(function () {});
    } else {
        syncHeaderCartBadge();
        renderCheckoutItems();
    }
    hydrateMergeWarningsFromSession();

    form.addEventListener('submit', async function (event) {
        event.preventDefault();
        if (!form.checkValidity()) { form.reportValidity(); return; }

        clearErrors();
        submitButton.disabled = true;
        const originalButtonText = submitButton.textContent;
        submitButton.textContent = 'Đang xử lý...';

        try {
            if (!isLoggedIn()) {
                showSummaryError('Vui long dang nhap de thanh toan.');
                window.location.href = '/login';
                return;
            }
            if (!mergedServerItems.length) {
                await loadServerCartItems();
                await syncHeaderCartBadgeFromServer();
            }
            renderCheckoutItems();

            const payload = buildCheckoutPayload();
            if (!payload.items.length) {
                window.location.href = '/cart?checkoutError=cart_empty';
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
                throw new Error(data.message || 'Checkout failed');
            }

            localStorage.removeItem(LOCAL_CART_KEY);
            if (data && data.orderCode) {
                window.location.href = '/?checkoutSuccess=' + encodeURIComponent(data.orderCode);
            } else {
                window.location.href = '/';
            }
        } catch (error) {
            showSummaryError(error.message || 'Co loi he thong.');
        } finally {
            submitButton.disabled = false;
            submitButton.textContent = originalButtonText;
        }
    });

    function buildCheckoutPayload() {
        const items = isLoggedIn()
            ? mergedServerItems.map(function (item) { return { productId: item.product.id, quantity: item.quantity }; })
            : JSON.parse(localStorage.getItem(LOCAL_CART_KEY) || '[]').filter(function (item) {
                return item && item.selected === true;
            }).map(function (item) {
                return {
                    productId: item.productId,
                    quantity: item.quantity
                };
            });

        return {
            phone: document.getElementById('phone').value.trim(),
            email: document.getElementById('email').value.trim(),
            address: document.getElementById('address').value.trim(),
            note: document.getElementById('note').value.trim(),
            paymentMethod: document.getElementById('paymentMethod').value,
            items: items
        };
    }

    async function mergeLocalCartToDatabase() {
        const items = JSON.parse(localStorage.getItem(LOCAL_CART_KEY) || '[]');
        if (!items.length) {
            return;
        }

        const response = await fetch('/api/v1/carts/merge', {
            method: 'POST',
            headers: buildJsonHeaders(),
            body: JSON.stringify({ items: items })
        });

        if (response.status === 401 || response.status === 403) {
            window.location.href = '/login';
            return;
        }

        if (!response.headers.get('content-type')?.includes('application/json')) throw new Error('Server Error');
        if (!response.ok) {
            throw new Error('Cannot merge local cart');
        }

        const mergeData = await response.json();
        if (mergeData && mergeData.hasWarning === true && Array.isArray(mergeData.warnings)) {
            showMergeWarnings(mergeData.warnings);
            saveMergeWarnings(mergeData.warnings);
        }
        if (mergeData && mergeData.cart && Array.isArray(mergeData.cart.items)) {
            mergedServerItems = mergeData.cart.items.filter(function (item) {
                return item && item.selected === true && item.product && item.product.id;
            });
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
        if (!response.ok) {
            throw new Error('Cannot load cart');
        }

        const cartData = await response.json();
        if (cartData && Array.isArray(cartData.items)) {
            mergedServerItems = cartData.items.filter(function (item) {
                return item && item.selected === true && item.product && item.product.id;
            });
        }
        renderCheckoutItems();
        await syncHeaderCartBadgeFromServer();
    }

    function readCsrfTokenFromCookie() {
        const cookies = document.cookie ? document.cookie.split('; ') : [];
        for (let i = 0; i < cookies.length; i++) {
            const parts = cookies[i].split('=');
            const key = parts[0];
            if (key === 'XSRF-TOKEN') {
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
        const headers = {
            'Accept': 'application/json'
        };
        if (!isGet) {
            headers['Content-Type'] = 'application/json';
            headers['X-XSRF-TOKEN'] = readCsrfTokenFromCookie();
        }
        const token = getAuthToken();
        if (token) {
            headers['Authorization'] = 'Bearer ' + token;
        }
        return headers;
    }

    function clearErrors() {
        const fields = ['phone', 'email', 'address', 'paymentMethod', 'items'];
        fields.forEach(function (field) {
            const holder = document.getElementById('error-' + field);
            if (holder) {
                holder.textContent = '';
            }
        });
        const summary = document.getElementById('error-summary');
        if (summary) {
            summary.textContent = '';
        }
        const warning = document.getElementById('merge-warning');
        if (warning) {
            warning.textContent = '';
        }
    }

    function renderFieldErrors(errors) {
        errors.forEach(function (errorObj) {
            const fieldId = errorObj.fieldId;
            const errorMessage = errorObj.errorMessage;
            const holder = document.getElementById('error-' + fieldId);
            if (!holder) {
                return;
            }
            const div = document.createElement('div');
            div.className = 'text-red-600 text-sm';
            div.textContent = errorMessage;
            holder.appendChild(div);
        });
    }

    function showSummaryError(message) {
        const summary = document.getElementById('error-summary');
        if (!summary) {
            return;
        }
        const div = document.createElement('div');
        div.className = 'text-red-600 text-sm';
        div.textContent = message;
        summary.innerHTML = '';
        summary.appendChild(div);
    }

    function showMergeWarnings(warnings) {
        if (!Array.isArray(warnings) || !warnings.length) {
            return;
        }
        const warningHolder = document.getElementById('merge-warning');
        if (!warningHolder) {
            return;
        }
        warningHolder.innerHTML = '';
        const closeBtn = document.createElement('button');
        closeBtn.type = 'button';
        closeBtn.className = 'text-xs text-slate-500 hover:text-slate-700 mb-1 underline';
        closeBtn.textContent = 'Đóng cảnh báo';
        closeBtn.addEventListener('click', function () {
            warningHolder.innerHTML = '';
            localStorage.removeItem(MERGE_WARNING_KEY);
        });
        warningHolder.appendChild(closeBtn);
        warnings.forEach(function (message) {
            const div = document.createElement('div');
            div.className = 'text-amber-700 text-sm';
            div.textContent = message;
            warningHolder.appendChild(div);
        });
    }

    function syncHeaderCartBadge() {
        const cartCountEl = document.getElementById('cartCount');
        if (!cartCountEl) {
            return;
        }
        let total = 0;
        if (isLoggedIn()) {
            total = mergedServerItems.reduce(function (sum, item) {
                return sum + (item.quantity || 0);
            }, 0);
        } else {
            const localItems = JSON.parse(localStorage.getItem(LOCAL_CART_KEY) || '[]');
            total = localItems.reduce(function (sum, item) {
                return sum + (item.quantity || 0);
            }, 0);
        }
        cartCountEl.textContent = String(total);
    }

    async function syncHeaderCartBadgeFromServer() {
        const cartCountEl = document.getElementById('cartCount');
        if (!cartCountEl) {
            return;
        }
        if (!isLoggedIn()) {
            syncHeaderCartBadge();
            return;
        }
        try {
            const response = await fetch('/api/v1/carts/summary', {
                method: 'GET',
                headers: buildJsonHeaders(true)
            });
            if (response.ok && response.headers.get('content-type')?.includes('application/json')) {
                const summary = await response.json();
                cartCountEl.textContent = String(summary.totalQuantity || 0);
                return;
            }
        } catch (error) {
            // fallback to local rendering
        }
        syncHeaderCartBadge();
    }

    function hydrateMergeWarningsFromSession() {
        const raw = localStorage.getItem(MERGE_WARNING_KEY) || sessionStorage.getItem('cart_merge_warnings');
        if (!raw) {
            return;
        }
        try {
            const warnings = JSON.parse(raw);
            showMergeWarnings(warnings);
        } catch (error) {
            // ignore malformed cache
        }
    }

    function saveMergeWarnings(warnings) {
        localStorage.setItem(MERGE_WARNING_KEY, JSON.stringify(warnings));
    }

    function getSelectedLocalItems() {
        const localItems = JSON.parse(localStorage.getItem(LOCAL_CART_KEY) || '[]');
        return localItems.filter(function (item) {
            return item && item.selected === true;
        });
    }

    function renderCheckoutItems() {
        if (!checkoutItemsList || !checkoutItemsEmpty || !checkoutItemCount || !checkoutTotal) {
            return;
        }

        let items = [];
        if (isLoggedIn()) {
            items = mergedServerItems.map(function (item) {
                return {
                    name: item.product && item.product.name ? item.product.name : ('San pham #' + item.product.id),
                    price: Number(item.product && item.product.price ? item.product.price : 0),
                    quantity: Number(item.quantity || 0)
                };
            });
        } else {
            items = getSelectedLocalItems().map(function (item) {
                return {
                    name: item.name || ('San pham #' + item.productId),
                    price: Number(item.price || 0),
                    quantity: Number(item.quantity || 0)
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
        const totalQuantity = items.reduce(function (sum, item) {
            return sum + item.quantity;
        }, 0);
        const totalPrice = items.reduce(function (sum, item) {
            return sum + (item.price * item.quantity);
        }, 0);

        checkoutItemCount.textContent = totalQuantity + ' sản phẩm';
        checkoutTotal.textContent = Number(totalPrice).toLocaleString('vi-VN') + 'đ';

        items.forEach(function (item) {
            const row = document.createElement('div');
            row.className = 'flex items-center justify-between bg-white rounded-lg border border-slate-200 p-3';
            row.innerHTML =
                '<div>' +
                '  <div class="font-medium text-sm">' + escapeHtml(item.name) + '</div>' +
                '  <div class="text-xs text-slate-500">SL: ' + item.quantity + '</div>' +
                '</div>' +
                '<div class="text-sm font-semibold">' + Number(item.price * item.quantity).toLocaleString('vi-VN') + 'đ</div>';
            checkoutItemsList.appendChild(row);
        });
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
