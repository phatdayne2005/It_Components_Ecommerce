(function () {
    'use strict';

    const mount = document.getElementById('productReviewsMount');
    if (!mount) return;

    const productId = mount.getAttribute('data-product-id');
    const listEl = document.getElementById('reviewsList');
    const summaryEl = document.getElementById('reviewSummaryLine');
    const placeholder = document.getElementById('reviewsPlaceholder');

    function escapeHtml(text) {
        return String(text || '')
            .replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;')
            .replace(/"/g, '&quot;').replace(/'/g, '&#039;');
    }

    function starRowHtml(rating) {
        const r = Math.max(0, Math.min(5, rating || 0));
        return '<span class="text-amber-400 tracking-tight" aria-label="' + r + ' sao">' +
            '★'.repeat(r) + '<span class="text-slate-300">' + '★'.repeat(5 - r) + '</span></span>';
    }

    function renderSummary(count, average) {
        if (!summaryEl) return;
        if (!count) {
            summaryEl.textContent = 'Chưa có nhận xét nào. Hãy là người đầu tiên sau khi nhận hàng!';
            return;
        }
        summaryEl.innerHTML = '<span class="text-amber-500 text-lg">' + starRowHtml(Math.round(average)) + '</span> ' +
            '<span class="font-semibold text-slate-800">' + average.toFixed(1) + '/5</span>' +
            ' <span class="text-slate-500">— ' + count + ' đánh giá</span>';
    }

    function renderList(rows) {
        if (!listEl) return;
        if (placeholder) placeholder.remove();
        if (!rows || !rows.length) {
            listEl.innerHTML = '<p class="text-slate-500 text-sm py-6">Chưa có nhận xét nào.</p>';
            return;
        }
        listEl.innerHTML = rows.map(function (r) {
            const dt = r.createdAt ? formatDt(r.createdAt) : '';
            const titleBlock = r.title ? '<div class="font-semibold text-slate-900 mt-1">' + escapeHtml(r.title) + '</div>' : '';
            return '<article class="py-4">' +
                '<div class="flex flex-wrap items-start justify-between gap-2">' +
                '<div class="text-sm">' + starRowHtml(r.rating) + titleBlock + '</div>' +
                '<span class="text-xs text-slate-400">' + escapeHtml(dt) + '</span></div>' +
                '<div class="text-xs text-slate-500 mt-1">' + escapeHtml(r.authorDisplay || '') + '</div>' +
                '<p class="mt-2 text-slate-800 text-sm leading-relaxed whitespace-pre-wrap">' + escapeHtml(r.comment || '') + '</p>' +
                '</article>';
        }).join('');
    }

    function formatDt(iso) {
        try {
            const d = new Date(iso);
            if (isNaN(d.getTime())) return String(iso || '');
            return d.toLocaleString('vi-VN');
        } catch (e) { return String(iso || ''); }
    }

    async function loadSummaryAndList() {
        try {
            const [sumRes, listRes] = await Promise.all([
                fetch('/api/v1/products/' + productId + '/reviews/summary', { headers: { Accept: 'application/json' } }),
                fetch('/api/v1/products/' + productId + '/reviews', { headers: { Accept: 'application/json' } })
            ]);
            const sum = await sumRes.json().catch(function () { return { count: 0, average: 0 }; });
            const list = await listRes.json().catch(function () { return []; });
            renderSummary(sum.count || 0, typeof sum.average === 'number' ? sum.average : 0);
            renderList(Array.isArray(list) ? list : []);
        } catch (e) {
            if (summaryEl) summaryEl.textContent = 'Không tải được tóm tắt đánh giá.';
            if (listEl) listEl.innerHTML = '<p class="text-red-600 text-sm py-4">Không tải được nhận xét.</p>';
        }
    }

    loadSummaryAndList();
})();
