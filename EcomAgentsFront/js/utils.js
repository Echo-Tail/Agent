// js/utils.js - 工具函数

// 显示提示消息
function showToast(message, type = 'info') {
    console.log(`[${type.toUpperCase()}] ${message}`);

    // 使用Bootstrap Toast（若已初始化）
    const toastContainer = document.getElementById('toastContainer');
    if (toastContainer) {
        const colors = { success: 'text-bg-success', error: 'text-bg-danger', warning: 'text-bg-warning', info: 'text-bg-info' };
        const toast = document.createElement('div');
        toast.className = `toast ${colors[type] || colors.info} border-0`;
        toast.role = 'alert';
        toast.innerHTML = `<div class="d-flex"><div class="toast-body">${message}</div><button type="button" class="btn-close btn-close-white me-2 m-auto" data-bs-dismiss="toast"></button></div>`;
        toastContainer.appendChild(toast);
        const bsToast = new bootstrap.Toast(toast);
        bsToast.show();
        toast.addEventListener('hidden.bs.toast', () => toast.remove());
    } else if (typeof alert === 'function') {
        alert(message);
    }
}

// 格式化日期
function formatDate(date, format) {
    if (!date) return '';
    if (!format) format = 'YYYY-MM-DD HH:mm:ss';

    const d = new Date(date);
    const year = d.getFullYear();
    const month = String(d.getMonth() + 1).padStart(2, '0');
    const day = String(d.getDate()).padStart(2, '0');
    const hours = String(d.getHours()).padStart(2, '0');
    const minutes = String(d.getMinutes()).padStart(2, '0');
    const seconds = String(d.getSeconds()).padStart(2, '0');

    return format
        .replace('YYYY', year)
        .replace('MM', month)
        .replace('DD', day)
        .replace('HH', hours)
        .replace('mm', minutes)
        .replace('ss', seconds);
}

// 复制到剪贴板
async function copyToClipboard(text) {
    try {
        await navigator.clipboard.writeText(text);
        showToast('已复制到剪贴板', 'success');
        return true;
    } catch (error) {
        showToast('复制失败', 'error');
        return false;
    }
}

// 获取当前用户
function getCurrentUser() {
    const userStr = localStorage.getItem(StorageKeys.CURRENT_USER);
    return userStr ? JSON.parse(userStr) : null;
}

// 设置当前用户
function setCurrentUser(user) {
    if (user) {
        localStorage.setItem(StorageKeys.CURRENT_USER, JSON.stringify(user));
    } else {
        localStorage.removeItem(StorageKeys.CURRENT_USER);
    }
}

// 获取Token
function getToken() {
    return localStorage.getItem(StorageKeys.TOKEN);
}

// 设置Token
function setToken(token) {
    if (token) {
        localStorage.setItem(StorageKeys.TOKEN, token);
    } else {
        localStorage.removeItem(StorageKeys.TOKEN);
    }
}

// 清除所有认证信息
function clearAuth() {
    localStorage.removeItem(StorageKeys.TOKEN);
    localStorage.removeItem(StorageKeys.CURRENT_USER);
}

// 检查是否为管理员
function isAdmin() {
    const user = getCurrentUser();
    return user && user.role === 'admin';
}

// 表单验证
function validateField(value, rules) {
    if (rules.required && !value) {
        return { valid: false, message: `${rules.label || ''}不能为空` };
    }

    if (rules.min && value.length < rules.min) {
        return { valid: false, message: `${rules.label || ''}长度不能小于${rules.min}个字符` };
    }

    if (rules.max && value.length > rules.max) {
        return { valid: false, message: `${rules.label || ''}长度不能大于${rules.max}个字符` };
    }

    if (rules.pattern && !rules.pattern.test(value)) {
        return { valid: false, message: rules.message || `${rules.label || ''}格式不正确` };
    }

    return { valid: true };
}

// 防抖函数
function debounce(func, wait) {
    let timeout;
    return function executedFunction(...args) {
        const later = () => {
            clearTimeout(timeout);
            func(...args);
        };
        clearTimeout(timeout);
        timeout = setTimeout(later, wait);
    };
}

// 节流函数
function throttle(func, limit) {
    let inThrottle;
    return function(...args) {
        if (!inThrottle) {
            func.apply(this, args);
            inThrottle = true;
            setTimeout(() => inThrottle = false, limit);
        }
    };
}
