// js/auth.js - 认证相关

// 登录
async function login(username, password) {
    try {
        const result = await post('/login', { username, password });
        if (result.success) {
            setCurrentUser(result.data.user);
            setToken(result.data.token);
            return { success: true, user: result.data.user };
        }
        return { success: false, message: result.message || '登录失败' };
    } catch (err) {
        return { success: false, message: '网络错误，请稍后重试' };
    }
}

// 注册
async function register(userData) {
    try {
        const result = await post('/register', userData);
        if (result.success) {
            return { success: true };
        }
        return { success: false, message: result.message || '注册失败' };
    } catch (err) {
        return { success: false, message: '网络错误，请稍后重试' };
    }
}

// 登出
function logout() {
    clearAuth();
    window.location.href = '/pages/login.html';
}

// 检查认证状态（用于 SPA 页面）
function checkAuth() {
    const user = getCurrentUser();
    const token = getToken();
    if (!token || !user) {
        window.location.href = '/pages/login.html';
        return false;
    }
    return true;
}

// 检查是否为管理员
function isAdmin() {
    const user = getCurrentUser();
    return user && user.role === 'admin';
}
