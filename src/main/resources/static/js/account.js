(function () {
    'use strict';

    const gate = document.getElementById('accountGate');
    const panel = document.getElementById('accountPanel');

    init();

    async function init() {
        if (!getAuthToken()) {
            window.location.href = '/login';
            return;
        }
        try {
            const res = await fetch('/api/v1/me', { headers: buildHeaders(true) });
            if (res.status === 401) {
                window.location.href = '/login';
                return;
            }
            const p = await res.json();
            if (!res.ok) throw new Error(p.message || 'Lỗi tải hồ sơ');
            document.getElementById('pfUsername').value = p.username || '';
            document.getElementById('pfEmail').value = p.email || '';
            document.getElementById('pfFullName').value = p.fullName || '';
            document.getElementById('pfPhone').value = p.phone || '';
            gate.classList.add('hidden');
            panel.classList.remove('hidden');
        } catch (e) {
            gate.textContent = e.message || 'Không tải được tài khoản.';
        }

        document.getElementById('btnSaveProfile').addEventListener('click', saveProfile);
        document.getElementById('btnChangePwd').addEventListener('click', changePassword);
    }

    async function saveProfile() {
        clearMsg('profileMsg');
        const body = {
            fullName: document.getElementById('pfFullName').value.trim(),
            phone: document.getElementById('pfPhone').value.trim()
        };
        const res = await fetch('/api/v1/me', {
            method: 'PATCH',
            headers: buildHeaders(false),
            body: JSON.stringify(body)
        });
        const data = await res.json().catch(() => ({}));
        if (!res.ok) {
            showMsg('profileMsg', data.message || 'Lưu thất bại', true);
            return;
        }
        showMsg('profileMsg', 'Đã cập nhật hồ sơ.', false);
        try {
            const auth = JSON.parse(localStorage.getItem('auth') || '{}');
            auth.fullName = data.fullName;
            localStorage.setItem('auth', JSON.stringify(auth));
        } catch (err) { /* ignore */ }
    }

    async function changePassword() {
        clearMsg('pwdMsg');
        const currentPassword = document.getElementById('pwCurrent').value;
        const newPassword = document.getElementById('pwNew').value;
        const res = await fetch('/api/v1/me/password', {
            method: 'POST',
            headers: buildHeaders(false),
            body: JSON.stringify({ currentPassword, newPassword })
        });
        const data = await res.json().catch(() => ({}));
        if (!res.ok) {
            showMsg('pwdMsg', data.message || 'Đổi mật khẩu thất bại', true);
            return;
        }
        document.getElementById('pwCurrent').value = '';
        document.getElementById('pwNew').value = '';
        showMsg('pwdMsg', data.message || 'Đã đổi mật khẩu.', false);
    }

    function showMsg(id, text, isError) {
        const el = document.getElementById(id);
        if (!el) return;
        el.textContent = text;
        el.className = 'mb-3 text-sm ' + (isError ? 'text-red-600' : 'text-emerald-700');
    }

    function clearMsg(id) {
        const el = document.getElementById(id);
        if (el) { el.textContent = ''; el.className = 'mb-3 text-sm'; }
    }

    function api() { return window.TechPartsApi; }

    function getAuthToken() {
        const a = api();
        return a && a.getAuthToken ? a.getAuthToken() : '';
    }

    function buildHeaders(isGet) {
        const a = api();
        return a && a.buildJsonHeaders ? a.buildJsonHeaders(isGet) : { 'Accept': 'application/json' };
    }
})();
