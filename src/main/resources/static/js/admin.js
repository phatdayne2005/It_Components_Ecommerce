(function () {
    'use strict';

    const auth = JSON.parse(localStorage.getItem('auth') || 'null');
    if (!auth || !(auth.roles || []).includes('ROLE_ADMIN')) {
        alert('Bạn cần đăng nhập với tài khoản ADMIN.');
        window.location.href = '/login';
        return;
    }

    document.getElementById('loadingGate').classList.add('hidden');
    document.getElementById('adminContent').classList.remove('hidden');
    const badge = document.getElementById('userBadge');
    const badgeName = document.getElementById('userBadgeName');
    if (badge && badgeName) {
        badge.classList.remove('hidden');
        badgeName.textContent = auth.username;
    }

    const headers = () => ({
        'Content-Type': 'application/json',
        'Authorization': 'Bearer ' + auth.token
    });

    async function api(method, url, body) {
        const opts = { method, headers: headers() };
        if (body) opts.body = JSON.stringify(body);
        const res = await fetch(url, opts);
        if (res.status === 401 || res.status === 403) {
            alert('Phiên đăng nhập hết hạn hoặc không có quyền.');
            logout();
            return null;
        }
        if (res.status === 204) return null;
        const data = await res.json().catch(() => ({}));
        if (!res.ok) {
            const msg = data.message || data.error || ('HTTP ' + res.status);
            throw new Error(msg);
        }
        return data;
    }

    window.logout = function () {
        localStorage.removeItem('auth');
        window.location.href = '/login';
    };

    const fmtPrice = n => (n == null ? '' : Number(n).toLocaleString('vi-VN') + 'đ');

    function showToast(msg) {
        const t = document.getElementById('toast');
        document.getElementById('toastMsg').textContent = msg;
        t.classList.add('show');
        clearTimeout(showToast._t);
        showToast._t = setTimeout(() => t.classList.remove('show'), 2000);
    }

    document.querySelectorAll('.tab-btn').forEach(btn => {
        btn.addEventListener('click', () => {
            document.querySelectorAll('.tab-btn').forEach(b => {
                b.classList.remove('border-brand-600', 'text-brand-600');
                b.classList.add('border-transparent', 'text-slate-600');
            });
            btn.classList.add('border-brand-600', 'text-brand-600');
            btn.classList.remove('border-transparent', 'text-slate-600');
            const target = btn.dataset.tab;
            document.querySelectorAll('.tab-panel').forEach(p => p.classList.add('hidden'));
            document.getElementById('tab-' + target).classList.remove('hidden');
        });
    });

    let categories = [], brands = [], products = [], vouchers = [], users = [];
    let revenueChart = null;

    async function loadAll() {
        try {
            const [cat, br, pr, voc, usr, rev] = await Promise.all([
                api('GET', '/api/admin/categories'),
                api('GET', '/api/admin/brands'),
                api('GET', '/api/admin/products'),
                api('GET', '/api/admin/vouchers'),
                api('GET', '/api/admin/users'),
                api('GET', '/api/admin/reports/revenue?days=30')
            ]);
            categories = cat;
            brands = br;
            products = pr;
            vouchers = voc || [];
            users = usr || [];
            renderCategories();
            renderBrands();
            renderProducts();
            renderVouchers();
            renderUsers();
            document.getElementById('statCategories').textContent = categories.length;
            document.getElementById('statBrands').textContent = brands.length;
            document.getElementById('statProducts').textContent = products.length;
            renderRevenueChart(rev);
        } catch (err) {
            alert('Lỗi tải dữ liệu: ' + err.message);
        }
    }

    function renderRevenueChart(rev) {
        const summary = document.getElementById('revenueSummary');
        if (summary && rev) {
            const total = rev.totalRevenue != null ? Number(rev.totalRevenue) : 0;
            const orders = rev.totalOrders != null ? rev.totalOrders : 0;
            summary.textContent = fmtPrice(total) + ' • ' + orders + ' đơn';
        }
        const canvas = document.getElementById('revenueChart');
        if (!canvas || typeof Chart === 'undefined') return;
        const series = (rev && rev.series) ? rev.series : [];
        const labels = series.map(p => (p.date || '').toString().slice(0, 10));
        const data = series.map(p => (p.revenue != null ? Number(p.revenue) : 0));
        if (revenueChart) revenueChart.destroy();
        revenueChart = new Chart(canvas.getContext('2d'), {
            type: 'line',
            data: {
                labels: labels.length ? labels : ['(trống)'],
                datasets: [{
                    label: 'Doanh thu (đ)',
                    data: data.length ? data : [0],
                    borderColor: '#2563eb',
                    backgroundColor: 'rgba(37,99,235,0.15)',
                    tension: 0.25,
                    fill: true
                }]
            },
            options: {
                responsive: true,
                maintainAspectRatio: false,
                scales: {
                    y: { beginAtZero: true }
                }
            }
        });
    }

    function renderVouchers() {
        const tbody = document.getElementById('vouchersBody');
        if (!tbody) return;
        if (!vouchers.length) {
            tbody.innerHTML = '<tr><td colspan="8" class="text-center py-8 text-slate-400">Chưa có voucher</td></tr>';
            return;
        }
        tbody.innerHTML = vouchers.map(v => {
            const type = v.discountType === 'PERCENT' ? '%' : 'Cố định';
            const val = v.discountType === 'PERCENT' ? (v.discountValue + '%') : fmtPrice(Number(v.discountValue || 0));
            const used = (v.usedCount != null ? v.usedCount : 0) + (v.usageLimit != null ? '/' + v.usageLimit : '');
            const active = v.active ? '<span class="text-emerald-600">Bật</span>' : '<span class="text-slate-400">Tắt</span>';
            const valid = (v.validTo ? ('Đến ' + String(v.validTo).slice(0, 10)) : '—') + ' • ' + active;
            return `<tr class="border-t border-slate-100 hover:bg-slate-50">
                <td class="px-3 py-2">${v.id}</td>
                <td class="px-3 py-2 font-mono font-medium">${escapeHtml(v.code)}</td>
                <td class="px-3 py-2">${escapeHtml(v.name)}</td>
                <td class="px-3 py-2">${type}</td>
                <td class="px-3 py-2 text-right">${escapeHtml(val)}</td>
                <td class="px-3 py-2 text-right">${escapeHtml(String(used))}</td>
                <td class="px-3 py-2 text-xs">${escapeHtml(valid)}</td>
                <td class="px-3 py-2 text-right whitespace-nowrap">
                    <button type="button" onclick="openVoucherModal(${v.id})" class="text-blue-600 hover:underline px-1"><i class="fa-solid fa-pen"></i></button>
                    <button type="button" onclick="deleteVoucher(${v.id})" class="text-red-600 hover:underline px-1"><i class="fa-solid fa-trash"></i></button>
                </td>
            </tr>`;
        }).join('');
    }

    function dtLocalInput(v) {
        if (!v) return '';
        const s = String(v);
        if (s.length >= 16) return s.slice(0, 16);
        return '';
    }

    window.openVoucherModal = function (id) {
        const v = id ? vouchers.find(x => x.id === id) : {};
        const fields =
            `${field('Mã *', 'vCode', 'text', v.code || '', 'required maxlength="50"')}` +
            `${field('Tên *', 'vName', 'text', v.name || '', 'required')}` +
            `<div><label class="block text-sm text-slate-600 mb-1">Loại giảm</label>
                <select id="vDiscountType" class="w-full border border-slate-300 rounded-lg px-3 py-2">
                    <option value="PERCENT" ${v.discountType === 'FIXED' ? '' : 'selected'}>Phần trăm (%)</option>
                    <option value="FIXED" ${v.discountType === 'FIXED' ? 'selected' : ''}>Số tiền cố định</option>
                </select></div>` +
            `${field('Giá trị *', 'vDiscountValue', 'number', v.discountValue ?? '', 'required min="0" step="1"')}` +
            `${field('Đơn tối thiểu', 'vMinOrder', 'number', v.minOrder ?? '', 'min="0" step="1000"')}` +
            `${field('Giảm tối đa', 'vMaxDiscount', 'number', v.maxDiscount ?? '', 'min="0" step="1000"')}` +
            `${field('Giới hạn lượt (trống = không giới hạn)', 'vUsageLimit', 'number', v.usageLimit ?? '', 'min="1" step="1"')}` +
            `<div class="grid grid-cols-2 gap-3">
                <div><label class="block text-sm text-slate-600 mb-1">Từ</label>
                    <input id="vValidFrom" type="datetime-local" value="${escapeHtml(dtLocalInput(v.validFrom))}" class="w-full border rounded-lg px-3 py-2"></div>
                <div><label class="block text-sm text-slate-600 mb-1">Đến</label>
                    <input id="vValidTo" type="datetime-local" value="${escapeHtml(dtLocalInput(v.validTo))}" class="w-full border rounded-lg px-3 py-2"></div>
            </div>` +
            `<label class="flex items-center gap-2"><input id="vActive" type="checkbox" ${v.active === false ? '' : 'checked'} class="w-4 h-4"> <span class="text-sm">Đang hoạt động</span></label>`;

        openModal(id ? 'Sửa voucher' : 'Thêm voucher', fields, async () => {
            const body = {
                code: document.getElementById('vCode').value.trim(),
                name: document.getElementById('vName').value.trim(),
                discountType: document.getElementById('vDiscountType').value,
                discountValue: document.getElementById('vDiscountValue').value || 0,
                minOrder: document.getElementById('vMinOrder').value || null,
                maxDiscount: document.getElementById('vMaxDiscount').value || null,
                validFrom: document.getElementById('vValidFrom').value ? document.getElementById('vValidFrom').value : null,
                validTo: document.getElementById('vValidTo').value ? document.getElementById('vValidTo').value : null,
                usageLimit: document.getElementById('vUsageLimit').value ? parseInt(document.getElementById('vUsageLimit').value, 10) : null,
                active: document.getElementById('vActive').checked
            };
            if (body.minOrder) body.minOrder = parseFloat(body.minOrder);
            else body.minOrder = null;
            if (body.maxDiscount) body.maxDiscount = parseFloat(body.maxDiscount);
            else body.maxDiscount = null;
            body.discountValue = parseFloat(body.discountValue);
            if (id) await api('PUT', `/api/admin/vouchers/${id}`, body);
            else await api('POST', '/api/admin/vouchers', body);
        });
    };

    window.deleteVoucher = async function (id) {
        if (!confirm('Xoá voucher này?')) return;
        try {
            await api('DELETE', `/api/admin/vouchers/${id}`);
            await loadAll();
            showToast('Đã xoá');
        } catch (err) { alert('Lỗi: ' + err.message); }
    };

    // ===== Users (USER / STAFF / ADMIN) =====
    function roleBadge(roleName) {
        const cleaned = (roleName || '').replace(/^ROLE_/, '');
        const cls = cleaned === 'ADMIN' ? 'bg-red-100 text-red-700'
                  : cleaned === 'STAFF' ? 'bg-indigo-100 text-indigo-700'
                  : 'bg-slate-100 text-slate-700';
        return `<span class="px-2 py-0.5 rounded-full text-xs font-medium ${cls}">${escapeHtml(cleaned)}</span>`;
    }

    function renderUsers() {
        const tbody = document.getElementById('usersBody');
        if (!tbody) return;
        if (!users.length) {
            tbody.innerHTML = `<tr><td colspan="7" class="text-center py-8 text-slate-400">Chưa có user nào</td></tr>`;
            return;
        }
        tbody.innerHTML = users.map(u => {
            const rolesHtml = (u.roles || []).map(roleBadge).join(' ');
            const enabledLabel = u.enabled
                ? '<span class="text-emerald-600 text-xs"><i class="fa-solid fa-check"></i> Active</span>'
                : '<span class="text-slate-400 text-xs">Disabled</span>';
            return `
            <tr class="border-t border-slate-100 hover:bg-slate-50">
                <td class="px-3 py-2">${u.id}</td>
                <td class="px-3 py-2 font-medium">${escapeHtml(u.username || '')}</td>
                <td class="px-3 py-2 text-slate-600">${escapeHtml(u.email || '')}</td>
                <td class="px-3 py-2 text-slate-600">${escapeHtml(u.fullName || '')}</td>
                <td class="px-3 py-2 text-slate-500 text-xs">${escapeHtml(u.phone || '')}</td>
                <td class="px-3 py-2">${rolesHtml || '<span class="text-slate-300">—</span>'}<div class="mt-1">${enabledLabel}</div></td>
                <td class="px-3 py-2 text-right whitespace-nowrap">
                    <button onclick="openUserModal(${u.id})" class="text-blue-600 hover:underline px-1" title="Sửa"><i class="fa-solid fa-pen"></i></button>
                    <button onclick="deleteUser(${u.id})" class="text-red-600 hover:underline px-1" title="Xóa"><i class="fa-solid fa-trash"></i></button>
                </td>
            </tr>`;
        }).join('');
    }

    function buildRoleCheckboxes(selected) {
        const sel = new Set(
            (selected || []).map(r => r.replace(/^ROLE_/, '').toUpperCase())
        );
        const opts = ['USER', 'STAFF', 'ADMIN'];
        return opts.map(r => `
            <label class="inline-flex items-center gap-1.5 mr-3">
                <input type="checkbox" value="${r}" class="user-role-cb" ${sel.has(r) ? 'checked' : ''}>
                <span class="text-sm">${r}</span>
            </label>`).join('');
    }

    window.openUserModal = function (id) {
        const u = id ? users.find(x => x.id === id) : null;
        const isEdit = !!u;
        const roleHtml = `<div>
            <label class="block text-sm font-medium text-slate-700 mb-1">Phân quyền</label>
            <div class="flex flex-wrap items-center gap-1 px-3 py-2 border border-slate-300 rounded-lg bg-slate-50">
                ${buildRoleCheckboxes(u ? u.roles : ['ROLE_USER'])}
            </div>
            <div class="text-xs text-slate-400 mt-1">Để trống = USER. Có thể chọn nhiều role cùng lúc.</div>
        </div>`;
        const enabledHtml = `<div>
            <label class="inline-flex items-center gap-2">
                <input type="checkbox" id="uEnabled" ${(!u || u.enabled) ? 'checked' : ''}>
                <span class="text-sm font-medium text-slate-700">Đang hoạt động (enabled)</span>
            </label>
        </div>`;

        const fields = (isEdit
            ? `<div><label class="block text-sm font-medium text-slate-700 mb-1">Username</label>
                 <input type="text" value="${escapeHtml(u.username)}" disabled class="w-full px-3 py-2 border border-slate-200 bg-slate-50 text-slate-500 rounded-lg"></div>`
            : field('Username *', 'uUsername', 'text', '', 'required minlength="3" maxlength="80"')
        )
        + field('Email *', 'uEmail', 'email', u ? u.email : '', 'required maxlength="120"')
        + field(isEdit ? 'Mật khẩu mới (bỏ trống nếu giữ nguyên)' : 'Mật khẩu *', 'uPassword', 'password', '',
                isEdit ? 'minlength="6" maxlength="100"' : 'required minlength="6" maxlength="100"')
        + field('Họ và tên', 'uFullName', 'text', u ? (u.fullName || '') : '', 'maxlength="120"')
        + field('Số điện thoại', 'uPhone', 'text', u ? (u.phone || '') : '',
                'pattern="^(\\+84|0)(3|5|7|8|9)\\d{8}$" placeholder="VD: 0901234567"')
        + roleHtml
        + enabledHtml;

        openModal(isEdit ? 'Sửa user #' + u.id : 'Tạo user mới', fields, async () => {
            const checked = Array.from(document.querySelectorAll('.user-role-cb'))
                .filter(cb => cb.checked).map(cb => cb.value);
            const phoneVal = document.getElementById('uPhone').value.trim();
            const enabledVal = document.getElementById('uEnabled').checked;
            const passwordVal = document.getElementById('uPassword').value;

            if (isEdit) {
                const body = {
                    email: document.getElementById('uEmail').value.trim(),
                    fullName: document.getElementById('uFullName').value.trim(),
                    phone: phoneVal,
                    enabled: enabledVal,
                    roles: checked
                };
                if (passwordVal && passwordVal.trim()) body.password = passwordVal;
                await api('PUT', `/api/admin/users/${u.id}`, body);
            } else {
                const body = {
                    username: document.getElementById('uUsername').value.trim(),
                    email: document.getElementById('uEmail').value.trim(),
                    password: passwordVal,
                    fullName: document.getElementById('uFullName').value.trim(),
                    phone: phoneVal,
                    enabled: enabledVal,
                    roles: checked
                };
                await api('POST', '/api/admin/users', body);
            }
        });
    };

    window.deleteUser = async function (id) {
        const u = users.find(x => x.id === id);
        if (!u) return;
        if (!confirm(`Xóa user "${u.username}"?\nHành động này không thể hoàn tác.`)) return;
        try {
            await api('DELETE', `/api/admin/users/${id}`);
            await loadAll();
            showToast('Đã xóa user');
        } catch (err) {
            alert('Lỗi: ' + err.message);
        }
    };

    function renderCategories() {
        const tbody = document.getElementById('categoriesBody');
        if (!categories.length) {
            tbody.innerHTML = `<tr><td colspan="6" class="text-center py-8 text-slate-400">Chưa có danh mục nào</td></tr>`;
            return;
        }
        tbody.innerHTML = categories.map(c => `
            <tr class="border-t border-slate-100 hover:bg-slate-50">
                <td class="px-3 py-2">${c.id}</td>
                <td class="px-3 py-2 font-medium">${escapeHtml(c.name)}</td>
                <td class="px-3 py-2 text-slate-500">${escapeHtml(c.slug || '')}</td>
                <td class="px-3 py-2"><i class="fa-solid ${escapeHtml(c.icon || '')}"></i> <span class="text-xs text-slate-400">${escapeHtml(c.icon || '')}</span></td>
                <td class="px-3 py-2 text-slate-600">${escapeHtml(c.description || '')}</td>
                <td class="px-3 py-2 text-right whitespace-nowrap">
                    <button onclick="openCategoryModal(${c.id})" class="text-blue-600 hover:underline px-1"><i class="fa-solid fa-pen"></i></button>
                    <button onclick="deleteCategory(${c.id})" class="text-red-600 hover:underline px-1"><i class="fa-solid fa-trash"></i></button>
                </td>
            </tr>
        `).join('');
    }

    function renderBrands() {
        const tbody = document.getElementById('brandsBody');
        if (!brands.length) {
            tbody.innerHTML = `<tr><td colspan="6" class="text-center py-8 text-slate-400">Chưa có thương hiệu nào</td></tr>`;
            return;
        }
        tbody.innerHTML = brands.map(b => `
            <tr class="border-t border-slate-100 hover:bg-slate-50">
                <td class="px-3 py-2">${b.id}</td>
                <td class="px-3 py-2">${b.logoUrl ? `<img src="${escapeHtml(b.logoUrl)}" class="w-10 h-10 object-contain rounded">` : '<span class="text-slate-300">—</span>'}</td>
                <td class="px-3 py-2 font-medium">${escapeHtml(b.name)}</td>
                <td class="px-3 py-2 text-slate-500">${escapeHtml(b.slug || '')}</td>
                <td class="px-3 py-2 text-slate-600">${escapeHtml(b.description || '')}</td>
                <td class="px-3 py-2 text-right whitespace-nowrap">
                    <button onclick="openBrandModal(${b.id})" class="text-blue-600 hover:underline px-1"><i class="fa-solid fa-pen"></i></button>
                    <button onclick="deleteBrand(${b.id})" class="text-red-600 hover:underline px-1"><i class="fa-solid fa-trash"></i></button>
                </td>
            </tr>
        `).join('');
    }

    function renderProducts() {
        const tbody = document.getElementById('productsBody');
        if (!products.length) {
            tbody.innerHTML = `<tr><td colspan="9" class="text-center py-8 text-slate-400">Chưa có sản phẩm nào</td></tr>`;
            return;
        }
        tbody.innerHTML = products.map(p => `
            <tr class="border-t border-slate-100 hover:bg-slate-50">
                <td class="px-3 py-2">${p.id}</td>
                <td class="px-3 py-2">${p.imageUrl ? `<img src="${escapeHtml(p.imageUrl)}" class="w-12 h-12 object-cover rounded">` : '<span class="text-slate-300">—</span>'}</td>
                <td class="px-3 py-2 font-medium max-w-xs truncate" title="${escapeHtml(p.name)}">${escapeHtml(p.name)}</td>
                <td class="px-3 py-2">${escapeHtml(p.categoryName || '')}</td>
                <td class="px-3 py-2">${escapeHtml(p.brandName || '')}</td>
                <td class="px-3 py-2 text-right text-red-600 font-semibold">${fmtPrice(p.price)}</td>
                <td class="px-3 py-2 text-right">${p.stock}</td>
                <td class="px-3 py-2">
                    ${p.active
                        ? '<span class="text-xs bg-emerald-100 text-emerald-700 px-2 py-0.5 rounded-full">Hiển thị</span>'
                        : '<span class="text-xs bg-slate-200 text-slate-600 px-2 py-0.5 rounded-full">Ẩn</span>'}
                </td>
                <td class="px-3 py-2 text-right whitespace-nowrap">
                    <button onclick="openProductModal(${p.id})" class="text-blue-600 hover:underline px-1"><i class="fa-solid fa-pen"></i></button>
                    <button onclick="deleteProduct(${p.id})" class="text-red-600 hover:underline px-1"><i class="fa-solid fa-trash"></i></button>
                </td>
            </tr>
        `).join('');
    }

    function escapeHtml(s) {
        if (s == null) return '';
        return String(s).replace(/[&<>"']/g, c => ({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c]));
    }

    // ===== Modal =====
    let modalSubmit = null;

    function openModal(title, fieldsHtml, onSubmit) {
        document.getElementById('modalTitle').textContent = title;
        document.getElementById('modalFields').innerHTML = fieldsHtml;
        document.getElementById('modalAlert').classList.add('hidden');
        modalSubmit = onSubmit;
        document.getElementById('modal').classList.remove('hidden');
    }

    window.closeModal = function () {
        document.getElementById('modal').classList.add('hidden');
        modalSubmit = null;
    };

    window.submitModal = async function (e) {
        e.preventDefault();
        if (!modalSubmit) return false;
        try {
            await modalSubmit();
            closeModal();
            await loadAll();
            showToast('Đã lưu thành công');
        } catch (err) {
            const a = document.getElementById('modalAlert');
            a.textContent = err.message;
            a.classList.remove('hidden');
        }
        return false;
    };

    function field(label, id, type = 'text', value = '', extra = '') {
        const v = value == null ? '' : escapeHtml(String(value));
        return `<div>
            <label class="block text-sm font-medium text-slate-700 mb-1">${label}</label>
            <input id="${id}" type="${type}" value="${v}" ${extra}
                   class="w-full px-3 py-2 border border-slate-300 rounded-lg focus:border-brand-500 focus:ring-2 focus:ring-brand-100 outline-none">
        </div>`;
    }

    function textarea(label, id, value = '', rows = 3) {
        const v = value == null ? '' : escapeHtml(String(value));
        const r = Number(rows) > 0 ? Number(rows) : 3;
        return `<div>
            <label class="block text-sm font-medium text-slate-700 mb-1">${label}</label>
            <textarea id="${id}" rows="${r}" class="w-full px-3 py-2 border border-slate-300 rounded-lg focus:border-brand-500 focus:ring-2 focus:ring-brand-100 outline-none font-mono text-sm">${v}</textarea>
        </div>`;
    }

    function select(label, id, options, selectedId) {
        const opts = ['<option value="">-- Không chọn --</option>']
            .concat(options.map(o => `<option value="${o.id}" ${o.id === selectedId ? 'selected' : ''}>${escapeHtml(o.name)}</option>`))
            .join('');
        return `<div>
            <label class="block text-sm font-medium text-slate-700 mb-1">${label}</label>
            <select id="${id}" class="w-full px-3 py-2 border border-slate-300 rounded-lg focus:border-brand-500 focus:ring-2 focus:ring-brand-100 outline-none">${opts}</select>
        </div>`;
    }

    // ===== Categories =====
    window.openCategoryModal = function (id) {
        const c = id ? categories.find(x => x.id === id) : {};
        openModal(id ? 'Sửa danh mục' : 'Thêm danh mục',
            field('Tên *', 'cName', 'text', c.name || '', 'required') +
            field('Slug (tự sinh nếu trống)', 'cSlug', 'text', c.slug || '') +
            field('Icon (Font Awesome class, vd: fa-microchip)', 'cIcon', 'text', c.icon || '') +
            textarea('Mô tả', 'cDesc', c.description),
            async () => {
                const body = {
                    name: document.getElementById('cName').value.trim(),
                    slug: document.getElementById('cSlug').value.trim(),
                    icon: document.getElementById('cIcon').value.trim(),
                    description: document.getElementById('cDesc').value.trim()
                };
                if (id) await api('PUT', `/api/admin/categories/${id}`, body);
                else await api('POST', '/api/admin/categories', body);
            });
    };

    window.deleteCategory = async function (id) {
        if (!confirm('Xoá danh mục này?')) return;
        try { await api('DELETE', `/api/admin/categories/${id}`); await loadAll(); showToast('Đã xoá'); }
        catch (err) { alert('Lỗi: ' + err.message); }
    };

    // ===== Brands =====
    window.openBrandModal = function (id) {
        const b = id ? brands.find(x => x.id === id) : {};
        openModal(id ? 'Sửa thương hiệu' : 'Thêm thương hiệu',
            field('Tên *', 'bName', 'text', b.name || '', 'required') +
            field('Slug', 'bSlug', 'text', b.slug || '') +
            field('Logo URL', 'bLogo', 'text', b.logoUrl || '') +
            textarea('Mô tả', 'bDesc', b.description),
            async () => {
                const body = {
                    name: document.getElementById('bName').value.trim(),
                    slug: document.getElementById('bSlug').value.trim(),
                    logoUrl: document.getElementById('bLogo').value.trim(),
                    description: document.getElementById('bDesc').value.trim()
                };
                if (id) await api('PUT', `/api/admin/brands/${id}`, body);
                else await api('POST', '/api/admin/brands', body);
            });
    };

    window.deleteBrand = async function (id) {
        if (!confirm('Xoá thương hiệu này?')) return;
        try { await api('DELETE', `/api/admin/brands/${id}`); await loadAll(); showToast('Đã xoá'); }
        catch (err) { alert('Lỗi: ' + err.message); }
    };

    // ===== Products =====
    async function uploadFile(file) {
        const fd = new FormData();
        fd.append('file', file);
        const res = await fetch('/api/admin/upload', {
            method: 'POST',
            headers: { 'Authorization': 'Bearer ' + auth.token },
            body: fd
        });
        const data = await res.json().catch(() => ({}));
        if (!res.ok) throw new Error(data.message || ('HTTP ' + res.status));
        return data.url;
    }

    function imageWidget(id, label, initialUrl) {
        const u = escapeHtml(initialUrl || '');
        return `<div>
            <label class="block text-sm font-medium text-slate-700 mb-1">${label}</label>
            <div class="flex gap-2">
                <input id="${id}" type="text" value="${u}" placeholder="Dán URL hoặc upload file"
                       class="flex-1 px-3 py-2 border border-slate-300 rounded-lg focus:border-brand-500 focus:ring-2 focus:ring-brand-100 outline-none">
                <label class="bg-slate-100 hover:bg-slate-200 border border-slate-300 rounded-lg px-3 py-2 cursor-pointer text-sm whitespace-nowrap">
                    <i class="fa-solid fa-upload"></i> Tải lên
                    <input type="file" accept="image/*" class="hidden" data-target="${id}">
                </label>
            </div>
            <div id="${id}_preview" class="mt-2">${u ? `<img src="${u}" class="h-20 rounded border border-slate-200">` : ''}</div>
        </div>`;
    }

    function attachImageUploadHandlers() {
        document.querySelectorAll('input[type=file][data-target]').forEach(inp => {
            inp.addEventListener('change', async (ev) => {
                const file = ev.target.files[0];
                if (!file) return;
                const target = ev.target.dataset.target;
                try {
                    const url = await uploadFile(file);
                    document.getElementById(target).value = url;
                    document.getElementById(target + '_preview').innerHTML =
                        `<img src="${url}" class="h-20 rounded border border-slate-200">`;
                    showToast('Đã upload ảnh');
                } catch (err) {
                    alert('Upload lỗi: ' + err.message);
                }
                ev.target.value = '';
            });
        });
    }

    function galleryWidget(initial) {
        const items = (initial || []).map((url, i) => galleryRow(url, i)).join('');
        return `<div>
            <label class="block text-sm font-medium text-slate-700 mb-1">Thư viện ảnh (gallery)</label>
            <div id="galleryList" class="space-y-2">${items}</div>
            <div class="mt-2 flex gap-2">
                <button type="button" onclick="addGalleryRow()" class="text-sm text-brand-600 hover:underline">
                    <i class="fa-solid fa-plus"></i> Thêm URL
                </button>
                <label class="text-sm text-brand-600 hover:underline cursor-pointer">
                    <i class="fa-solid fa-upload"></i> Upload nhiều ảnh
                    <input id="galleryUpload" type="file" accept="image/*" multiple class="hidden">
                </label>
            </div>
        </div>`;
    }

    function galleryRow(url, idx) {
        const u = escapeHtml(url || '');
        return `<div class="flex gap-2 items-center" data-row="gallery">
            ${u ? `<img src="${u}" class="w-12 h-12 object-cover rounded border border-slate-200">` : '<div class="w-12 h-12 rounded border border-dashed border-slate-300"></div>'}
            <input type="text" value="${u}" class="gallery-url flex-1 px-3 py-2 border border-slate-300 rounded-lg outline-none focus:border-brand-500" placeholder="URL ảnh">
            <button type="button" onclick="this.parentElement.remove()" class="text-red-600 hover:text-red-800 px-2"><i class="fa-solid fa-trash"></i></button>
        </div>`;
    }

    window.addGalleryRow = function () {
        const list = document.getElementById('galleryList');
        list.insertAdjacentHTML('beforeend', galleryRow('', list.children.length));
    };

    function attachGalleryHandlers() {
        const upInput = document.getElementById('galleryUpload');
        if (!upInput) return;
        upInput.addEventListener('change', async (ev) => {
            for (const file of ev.target.files) {
                try {
                    const url = await uploadFile(file);
                    document.getElementById('galleryList').insertAdjacentHTML('beforeend', galleryRow(url, 0));
                } catch (err) {
                    alert('Upload lỗi: ' + err.message);
                }
            }
            ev.target.value = '';
        });
    }

    function specsWidget(initial) {
        const items = (initial || []).map(s => specRow(s.name, s.value)).join('');
        return `<div>
            <label class="block text-sm font-medium text-slate-700 mb-1">Thông số kỹ thuật</label>
            <div id="specList" class="space-y-2">${items}</div>
            <button type="button" onclick="addSpecRow()" class="mt-2 text-sm text-brand-600 hover:underline">
                <i class="fa-solid fa-plus"></i> Thêm thông số
            </button>
        </div>`;
    }

    function specRow(name, value) {
        const n = escapeHtml(name || '');
        const v = escapeHtml(value || '');
        return `<div class="grid grid-cols-[1fr_2fr_auto] gap-2" data-row="spec">
            <input type="text" value="${n}" placeholder="Tên (vd: Socket)" class="spec-name px-3 py-2 border border-slate-300 rounded-lg outline-none focus:border-brand-500">
            <input type="text" value="${v}" placeholder="Giá trị (vd: LGA1700)" class="spec-value px-3 py-2 border border-slate-300 rounded-lg outline-none focus:border-brand-500">
            <button type="button" onclick="this.parentElement.remove()" class="text-red-600 hover:text-red-800 px-2"><i class="fa-solid fa-trash"></i></button>
        </div>`;
    }

    window.addSpecRow = function () {
        document.getElementById('specList').insertAdjacentHTML('beforeend', specRow('', ''));
    };

    window.openProductModal = function (id) {
        const p = id ? products.find(x => x.id === id) : {};
        const fields =
            `<div class="grid grid-cols-2 gap-3">
                ${field('Tên *', 'pName', 'text', p.name || '', 'required')}
                ${field('SKU', 'pSku', 'text', p.sku || '')}
            </div>` +
            field('Slug', 'pSlug', 'text', p.slug || '') +
            imageWidget('pImage', 'Ảnh chính', p.imageUrl) +
            `<div class="grid grid-cols-2 gap-3">
                ${field('Giá *', 'pPrice', 'number', p.price || 0, 'required min="0" step="1000"')}
                ${field('Giá cũ', 'pOldPrice', 'number', p.oldPrice || '', 'min="0" step="1000"')}
            </div>` +
            `<div class="grid grid-cols-3 gap-3">
                ${field('Tồn kho', 'pStock', 'number', p.stock ?? 0, 'min="0"')}
                ${field('Bảo hành (tháng)', 'pWarranty', 'number', p.warrantyMonths || '', 'min="0"')}
                ${select('Danh mục', 'pCategory', categories, p.categoryId)}
            </div>` +
            select('Thương hiệu', 'pBrand', brands, p.brandId) +
            field('Mô tả ngắn', 'pShortDesc', 'text', p.shortDescription || '') +
            textarea('Mô tả chi tiết (text thuần)', 'pDesc', p.description) +
            `<div><label class="block text-sm font-medium text-slate-700 mb-1">Đánh giá chi tiết (HTML)</label>
                <p class="text-xs text-slate-500 mb-1">Tiêu đề, đoạn văn, ảnh, chú thích — dán HTML an toàn do shop biên tập.</p></div>` +
            textarea('', 'pEditorial', p.editorialReview || '', 14) +
            galleryWidget(p.imageUrls) +
            specsWidget(p.specifications) +
            `<label class="flex items-center gap-2"><input id="pActive" type="checkbox" ${p.active === false ? '' : 'checked'} class="w-4 h-4"> <span class="text-sm">Hiển thị</span></label>`;

        openModal(id ? 'Sửa sản phẩm' : 'Thêm sản phẩm', fields, async () => {
            const galleryUrls = Array.from(document.querySelectorAll('.gallery-url'))
                .map(i => i.value.trim()).filter(Boolean);
            const specs = Array.from(document.querySelectorAll('[data-row="spec"]')).map(row => ({
                name: row.querySelector('.spec-name').value.trim(),
                value: row.querySelector('.spec-value').value.trim()
            })).filter(s => s.name && s.value);

            const body = {
                name: document.getElementById('pName').value.trim(),
                sku: document.getElementById('pSku').value.trim() || null,
                slug: document.getElementById('pSlug').value.trim(),
                imageUrl: document.getElementById('pImage').value.trim(),
                price: document.getElementById('pPrice').value || 0,
                oldPrice: document.getElementById('pOldPrice').value || null,
                stock: parseInt(document.getElementById('pStock').value || '0', 10),
                warrantyMonths: document.getElementById('pWarranty').value
                    ? parseInt(document.getElementById('pWarranty').value, 10) : null,
                categoryId: document.getElementById('pCategory').value || null,
                brandId: document.getElementById('pBrand').value || null,
                shortDescription: document.getElementById('pShortDesc').value.trim(),
                description: document.getElementById('pDesc').value.trim(),
                editorialReview: (function () {
                    const t = document.getElementById('pEditorial');
                    const s = t ? t.value.trim() : '';
                    return s || null;
                })(),
                active: document.getElementById('pActive').checked,
                imageUrls: galleryUrls,
                specifications: specs
            };
            if (body.categoryId) body.categoryId = parseInt(body.categoryId, 10);
            if (body.brandId) body.brandId = parseInt(body.brandId, 10);
            if (id) await api('PUT', `/api/admin/products/${id}`, body);
            else await api('POST', '/api/admin/products', body);
        });

        attachImageUploadHandlers();
        attachGalleryHandlers();
    };

    window.deleteProduct = async function (id) {
        if (!confirm('Xoá sản phẩm này?')) return;
        try { await api('DELETE', `/api/admin/products/${id}`); await loadAll(); showToast('Đã xoá'); }
        catch (err) { alert('Lỗi: ' + err.message); }
    };

    // ===== GearVN Import =====
    let importPreviewItems = [];

    async function loadImportCollections() {
        try {
            const opts = await api('GET', '/api/admin/import/gearvn/collections');
            const sel = document.getElementById('importCollection');
            if (!sel) return;
            sel.innerHTML = opts.map(o =>
                `<option value="${escapeHtml(o.handle)}">${escapeHtml(o.label)} (${escapeHtml(o.handle)})</option>`
            ).join('');
        } catch (err) {
            console.error('Không tải được collection list:', err.message);
        }
    }

    window.loadImportPreview = async function () {
        const handle = document.getElementById('importCollection').value;
        const limit = parseInt(document.getElementById('importLimit').value, 10) || 20;
        const page = parseInt(document.getElementById('importPage').value, 10) || 1;
        const tbody = document.getElementById('importBody');
        tbody.innerHTML = `<tr><td colspan="8" class="text-center py-8 text-slate-400"><i class="fa-solid fa-spinner fa-spin"></i> Đang tải từ GearVN...</td></tr>`;
        document.getElementById('btnDoImport').disabled = true;
        document.getElementById('importSummary').textContent = '';
        try {
            const data = await api('GET', `/api/admin/import/gearvn/preview?handle=${encodeURIComponent(handle)}&page=${page}&limit=${limit}`);
            importPreviewItems = data.items || [];
            renderImportPreview(data);
        } catch (err) {
            tbody.innerHTML = `<tr><td colspan="8" class="text-center py-6 text-red-600">${escapeHtml(err.message)}</td></tr>`;
        }
    };

    function renderImportPreview(data) {
        const tbody = document.getElementById('importBody');
        if (!data.items || !data.items.length) {
            tbody.innerHTML = `<tr><td colspan="8" class="text-center py-8 text-slate-400">Không có sản phẩm</td></tr>`;
            document.getElementById('importSummary').textContent = '0 sản phẩm';
            return;
        }
        tbody.innerHTML = data.items.map((p, idx) => {
            const dup = p.duplicate;
            const checkbox = dup
                ? `<input type="checkbox" disabled class="opacity-40">`
                : `<input type="checkbox" class="import-cb" data-idx="${idx}" onchange="updateImportSelection()">`;
            const status = dup
                ? `<span class="text-xs bg-amber-100 text-amber-700 px-2 py-0.5 rounded-full">Đã tồn tại (#${p.existingId})</span>`
                : `<span class="text-xs bg-emerald-100 text-emerald-700 px-2 py-0.5 rounded-full">Mới</span>`;
            const oldPrice = p.oldPrice ? `<div class="text-xs text-slate-400 line-through">${fmtPrice(p.oldPrice)}</div>` : '';
            return `
                <tr class="border-t border-slate-100 ${dup ? 'bg-slate-50' : 'hover:bg-slate-50'}">
                    <td class="px-3 py-2 align-top">${checkbox}</td>
                    <td class="px-3 py-2 align-top">${p.mainImageUrl ? `<img src="${escapeHtml(p.mainImageUrl)}" loading="lazy" class="w-16 h-16 object-cover rounded border border-slate-200">` : '<span class="text-slate-300">—</span>'}</td>
                    <td class="px-3 py-2 align-top max-w-md"><div class="font-medium">${escapeHtml(p.title || '')}</div><div class="text-xs text-slate-400">${escapeHtml(p.productType || '')}</div></td>
                    <td class="px-3 py-2 align-top text-xs text-slate-500">${escapeHtml(p.handle)}</td>
                    <td class="px-3 py-2 align-top">${escapeHtml(p.vendor || '—')}</td>
                    <td class="px-3 py-2 align-top text-right text-red-600 font-semibold">${fmtPrice(p.price)}${oldPrice}</td>
                    <td class="px-3 py-2 align-top text-center">${(p.specs || []).length}</td>
                    <td class="px-3 py-2 align-top">${status}</td>
                </tr>
            `;
        }).join('');
        const newCount = data.items.filter(p => !p.duplicate).length;
        const dupCount = data.items.length - newCount;
        let summary = `Trang ${data.page} — ${data.items.length} sản phẩm (${newCount} mới, ${dupCount} đã tồn tại)`;
        if (data.hasMore) summary += ' • có trang tiếp';
        document.getElementById('importSummary').textContent = summary;
        document.getElementById('importAll').checked = false;
        document.getElementById('btnDoImport').disabled = true;
    }

    window.updateImportSelection = function () {
        const checked = document.querySelectorAll('.import-cb:checked').length;
        document.getElementById('btnDoImport').disabled = checked === 0;
        document.getElementById('btnDoImport').innerHTML =
            `<i class="fa-solid fa-floppy-disk mr-1"></i> Import ${checked} sản phẩm`;
    };

    window.toggleAllImport = function (cb) {
        document.querySelectorAll('.import-cb').forEach(c => { c.checked = cb.checked; });
        updateImportSelection();
    };

    window.doImport = async function () {
        const handle = document.getElementById('importCollection').value;
        const selected = Array.from(document.querySelectorAll('.import-cb:checked'))
            .map(cb => importPreviewItems[parseInt(cb.dataset.idx, 10)].handle);
        if (!selected.length) return;
        if (!confirm(`Import ${selected.length} sản phẩm? Ảnh sẽ được tải về máy chủ — có thể mất vài phút.`)) return;

        const btn = document.getElementById('btnDoImport');
        const original = btn.innerHTML;
        btn.disabled = true;
        btn.innerHTML = `<i class="fa-solid fa-spinner fa-spin mr-1"></i> Đang import ${selected.length} sản phẩm...`;
        try {
            const result = await api('POST', '/api/admin/import/gearvn/import', { handle, handles: selected });
            const errMsg = result.errors && result.errors.length
                ? `\nLỗi:\n- ${result.errors.join('\n- ')}` : '';
            showToast(`Imported: ${result.imported}, Skipped: ${result.skipped}`);
            if (errMsg) alert(`Kết quả: ${result.imported} thành công, ${result.skipped} bỏ qua.${errMsg}`);
            await loadAll();
            await loadImportPreview();
        } catch (err) {
            alert('Lỗi import: ' + err.message);
            btn.disabled = false;
            btn.innerHTML = original;
        }
    };

    loadImportCollections();
    loadAll();
})();
