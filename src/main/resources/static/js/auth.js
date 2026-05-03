(function () {
    'use strict';

    function normalizeAuth(data) {
        if (!data || typeof data !== 'object') return data;
        let roles = data.roles;
        if (!Array.isArray(roles)) {
            if (roles && typeof roles === 'object') roles = Object.values(roles);
            else roles = [];
        }
        data.roles = roles;
        return data;
    }

    function showAlert(msg) {
        const el = document.getElementById('alert');
        if (!el) return;
        el.textContent = msg;
        el.classList.remove('hidden');
    }

    window.doLogin = async function (e) {
        e.preventDefault();
        const username = document.getElementById('username').value.trim();
        const password = document.getElementById('password').value;
        try {
            const res = await fetch('/api/auth/login', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ username, password })
            });
            const data = await res.json();
            if (!res.ok) {
                showAlert(data.message || 'Đăng nhập thất bại');
                return false;
            }
            normalizeAuth(data);
            localStorage.setItem('auth', JSON.stringify(data));
            const isAdmin = (data.roles || []).indexOf('ROLE_ADMIN') >= 0;
            let next = null;
            try {
                const q = new URLSearchParams(window.location.search).get('next');
                if (q && q.startsWith('/') && !q.startsWith('//')) next = q;
            } catch (e) { /* ignore */ }
            window.location.href = isAdmin ? '/admin' : (next || '/');
        } catch (err) {
            showAlert('Lỗi kết nối: ' + err.message);
        }
        return false;
    };

    window.doRegister = async function (e) {
        e.preventDefault();
        const body = {
            username: document.getElementById('username').value.trim(),
            email: document.getElementById('email').value.trim(),
            password: document.getElementById('password').value,
            fullName: document.getElementById('fullName').value.trim(),
            phone: document.getElementById('phone').value.trim()
        };
        try {
            const res = await fetch('/api/auth/register', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify(body)
            });
            const data = await res.json();
            if (!res.ok) {
                showAlert(data.message || 'Đăng ký thất bại');
                return false;
            }
            normalizeAuth(data);
            localStorage.setItem('auth', JSON.stringify(data));
            window.location.href = '/';
        } catch (err) {
            showAlert('Lỗi kết nối: ' + err.message);
        }
        return false;
    };
})();
