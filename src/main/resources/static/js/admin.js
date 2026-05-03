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

    let categories = [], brands = [], products = [];

    async function loadAll() {
        try {
            [categories, brands, products] = await Promise.all([
                api('GET', '/api/admin/categories'),
                api('GET', '/api/admin/brands'),
                api('GET', '/api/admin/products')
            ]);
            renderCategories();
            renderBrands();
            renderProducts();
            document.getElementById('statCategories').textContent = categories.length;
            document.getElementById('statBrands').textContent = brands.length;
            document.getElementById('statProducts').textContent = products.length;
        } catch (err) {
            alert('Lỗi tải dữ liệu: ' + err.message);
        }
    }

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

    function textarea(label, id, value = '') {
        const v = value == null ? '' : escapeHtml(String(value));
        return `<div>
            <label class="block text-sm font-medium text-slate-700 mb-1">${label}</label>
            <textarea id="${id}" rows="3" class="w-full px-3 py-2 border border-slate-300 rounded-lg focus:border-brand-500 focus:ring-2 focus:ring-brand-100 outline-none">${v}</textarea>
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
            textarea('Mô tả chi tiết', 'pDesc', p.description) +
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
