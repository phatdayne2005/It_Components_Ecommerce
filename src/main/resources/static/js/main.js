(function () {
    'use strict';

    const cart = JSON.parse(localStorage.getItem('cart') || '[]');
    const cartCountEl = document.getElementById('cartCount');

    function updateCartBadge() {
        if (!cartCountEl) return;
        const total = cart.reduce((sum, item) => sum + item.qty, 0);
        cartCountEl.textContent = total;
        cartCountEl.classList.remove('cart-bump');
        void cartCountEl.offsetWidth;
        cartCountEl.classList.add('cart-bump');
    }

    window.addToCart = function (btn) {
        const name = btn.getAttribute('data-name');
        const price = parseInt(btn.getAttribute('data-price'), 10);
        const existing = cart.find(i => i.name === name);
        if (existing) existing.qty += 1;
        else cart.push({ name, price, qty: 1 });
        localStorage.setItem('cart', JSON.stringify(cart));
        updateCartBadge();
        showToast('Đã thêm "' + name + '" vào giỏ hàng');
    };

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
    if (cartBtn) {
        cartBtn.addEventListener('click', (e) => {
            e.preventDefault();
            if (!cart.length) {
                showToast('Giỏ hàng đang trống');
                return;
            }
            const total = cart.reduce((s, i) => s + i.price * i.qty, 0);
            const summary = cart.map(i => `• ${i.name} x${i.qty}`).join('\n');
            alert(`Giỏ hàng (${cart.length} sản phẩm):\n\n${summary}\n\nTổng: ${total.toLocaleString('vi-VN')}đ`);
        });
    }

    updateCartBadge();
})();
