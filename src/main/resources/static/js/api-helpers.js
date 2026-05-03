/**
 * Tiện ích dùng chung cho frontend TechParts (đồng bộ với Spring Security + JWT + CSRF cookie).
 * Nạp file này TRƯỚC các script gọi /api/v1/** (trừ /api/auth/** nếu không cần CSRF).
 */
(function (global) {
    'use strict';

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

    function getAuthPayload() {
        try {
            return JSON.parse(localStorage.getItem('auth') || 'null') || {};
        } catch (e) {
            return {};
        }
    }

    function getAuthToken() {
        const auth = getAuthPayload();
        return auth && auth.token ? auth.token : '';
    }

    function isLoggedIn() {
        return !!getAuthToken();
    }

    /** Luôn trả về mảng chuỗi role (backend có thể trả Set → JSON array). */
    function getRoles() {
        const auth = getAuthPayload();
        const r = auth.roles;
        if (Array.isArray(r)) return r;
        if (r && typeof r === 'object') return Object.values(r);
        return [];
    }

    function hasRole(role) {
        return getRoles().indexOf(role) >= 0;
    }

    function buildJsonHeaders(isGet) {
        const headers = { 'Accept': 'application/json' };
        if (!isGet) {
            headers['Content-Type'] = 'application/json';
            headers['X-XSRF-TOKEN'] = readCsrfTokenFromCookie();
        }
        const token = getAuthToken();
        if (token) headers['Authorization'] = 'Bearer ' + token;
        return headers;
    }

    function buildMultipartHeaders() {
        const headers = {
            'X-XSRF-TOKEN': readCsrfTokenFromCookie(),
            'Accept': 'application/json'
        };
        const token = getAuthToken();
        if (token) headers['Authorization'] = 'Bearer ' + token;
        return headers;
    }

    global.TechPartsApi = {
        readCsrfTokenFromCookie: readCsrfTokenFromCookie,
        getAuthPayload: getAuthPayload,
        getAuthToken: getAuthToken,
        isLoggedIn: isLoggedIn,
        getRoles: getRoles,
        hasRole: hasRole,
        buildJsonHeaders: buildJsonHeaders,
        buildMultipartHeaders: buildMultipartHeaders
    };
})(typeof window !== 'undefined' ? window : this);
