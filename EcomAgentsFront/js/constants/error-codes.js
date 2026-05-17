// js/constants/error-codes.js - 错误码定义

const ErrorCode = {
    // 成功
    SUCCESS: { code: 200, message: '操作成功' },

    // 系统级 (1xxx)
    UNKNOWN_ERROR: { code: 1000, message: '系统内部错误' },
    SYSTEM_BUSY: { code: 1001, message: '系统繁忙，请稍后再试' },
    SERVICE_UNAVAILABLE: { code: 1002, message: '服务暂不可用' },
    TIMEOUT: { code: 1003, message: '请求超时' },

    // 认证授权 (3xxx)
    UNAUTHORIZED: { code: 3001, message: '请先登录' },
    TOKEN_EXPIRED: { code: 3002, message: '登录已过期，请重新登录' },
    INVALID_TOKEN: { code: 3003, message: 'Token无效' },
    MISSING_TOKEN: { code: 3004, message: '缺少认证Token' },
    PERMISSION_DENIED: { code: 3005, message: '权限不足' },
    ACCOUNT_DISABLED: { code: 3006, message: '账号已被禁用' },
    ACCOUNT_NOT_FOUND: { code: 3007, message: '账号不存在' },
    INVALID_PASSWORD: { code: 3008, message: '用户名或密码错误' },

    // 用户 (4xxx)
    USER_ALREADY_EXISTS: { code: 4001, message: '用户已存在' },
    USER_NOT_FOUND: { code: 4003, message: '用户不存在' },
    CANNOT_DISABLE_ADMIN: { code: 4004, message: '无法禁用管理员账号' },

    // 邀请码 (5xxx)
    INVALID_INVITE_CODE: { code: 5001, message: '无效或已使用的邀请码' },

    // Agent (6xxx)
    AGENT_NOT_FOUND: { code: 6001, message: 'Agent不存在' },
    AGENT_ALREADY_EXISTS: { code: 6002, message: 'Agent已存在' },

    // 对话 (7xxx)
    SESSION_NOT_FOUND: { code: 7001, message: '会话不存在' },
    EMPTY_MESSAGE: { code: 7004, message: '消息内容不能为空' }
};

function getErrorMessage(errorCode) {
    for (const key in ErrorCode) {
        if (ErrorCode[key].code === errorCode) {
            return ErrorCode[key].message;
        }
    }
    return '未知错误';
}

function getErrorByCode(code) {
    for (const key in ErrorCode) {
        if (ErrorCode[key].code === code) {
            return ErrorCode[key];
        }
    }
    return ErrorCode.UNKNOWN_ERROR;
}
