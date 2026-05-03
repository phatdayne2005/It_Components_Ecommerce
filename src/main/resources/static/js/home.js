(function () {
    'use strict';

    const p = new URLSearchParams(window.location.search || '');
    const code = p.get('checkoutSuccess');
    if (!code) return;

    const toast = window.TechPartsUi && typeof window.TechPartsUi.showToast === 'function'
        ? window.TechPartsUi.showToast
        : null;
    if (toast) {
        toast('Đặt hàng thành công! Mã đơn: ' + code);
    }

    p.delete('checkoutSuccess');
    const qs = p.toString();
    const next = window.location.pathname + (qs ? '?' + qs : '') + window.location.hash;
    window.history.replaceState({}, '', next);
})();
