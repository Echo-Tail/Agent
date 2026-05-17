// js/api.js - API封装

// 统一的响应格式
class ApiResponse {
    constructor(success, data = null, errorCode = null, message = '') {
        this.success = success;
        this.data = data;
        this.errorCode = errorCode;
        this.message = message;
    }

    static success(data, message = '操作成功') {
        return new ApiResponse(true, data, null, message);
    }

    static error(errorCode, customMessage = null) {
        const message = customMessage || errorCode.message;
        return new ApiResponse(false, null, errorCode.code, message);
    }
}

// 获取请求头
function getHeaders() {
    const token = localStorage.getItem(StorageKeys.TOKEN);
    return {
        'Content-Type': 'application/json',
        ...(token && { 'Authorization': `Bearer ${token}` })
    };
}

// 通用请求方法（带重试）
async function request(url, options = {}, retryCount = 0) {
    try {
        const response = await fetch(`${ApiConfig.BASE_URL}${url}`, {
            ...options,
            headers: getHeaders()
        });

        // 先取文本，防止非 JSON 响应导致崩溃
        const text = await response.text();
        if (!text) {
            throw new Error('后端服务返回为空，请确认服务是否正常');
        }

        let result;
        try {
            result = JSON.parse(text);
        } catch (e) {
            throw new Error('后端服务异常，请确认服务已启动（node js/server.js）');
        }

        if (result.code === ErrorCode.SUCCESS.code) {
            return ApiResponse.success(result.data, result.message);
        }

        // 认证失败，清除本地存储
        if (result.code === ErrorCode.UNAUTHORIZED.code || result.code === ErrorCode.TOKEN_EXPIRED.code) {
            localStorage.removeItem(StorageKeys.TOKEN);
            localStorage.removeItem(StorageKeys.CURRENT_USER);
            if (window.location.pathname !== '/pages/login.html') {
                window.location.href = '/pages/login.html';
            }
        }

        const error = getErrorByCode(result.code);
        return ApiResponse.error(error, result.message);

    } catch (error) {
        console.error('Request Error:', error);

        if (retryCount < ApiConfig.RETRY_COUNT) {
            await new Promise(resolve => setTimeout(resolve, ApiConfig.RETRY_DELAY));
            return request(url, options, retryCount + 1);
        }

        return ApiResponse.error(ErrorCode.UNKNOWN_ERROR, error.message);
    }
}

// HTTP方法
async function get(url) {
    return request(url, { method: 'GET' });
}

async function post(url, data) {
    return request(url, {
        method: 'POST',
        body: JSON.stringify(data)
    });
}

async function put(url, data) {
    return request(url, {
        method: 'PUT',
        body: JSON.stringify(data)
    });
}

async function del(url) {
    return request(url, { method: 'DELETE' });
}

// 流式对话 — 使用 SSE 从后端流式读取 LLM 回复
async function streamChat(agentId, sessionId, content, onToken, onDone, onError, signal) {
    const token = localStorage.getItem(StorageKeys.TOKEN);
    const baseUrl = ApiConfig.BASE_URL.endsWith('/v1') ? ApiConfig.BASE_URL.slice(0, -3) : ApiConfig.BASE_URL;
    const url = `${baseUrl}/chat/${agentId}/stream`;

    try {
        const response = await fetch(url, {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json',
                ...(token && { 'Authorization': `Bearer ${token}` })
            },
            body: JSON.stringify({ sessionId, content }),
            signal
        });

        if (!response.ok) {
            const errText = await response.text().catch(() => `HTTP ${response.status}`);
            onError(new Error(errText));
            return;
        }

        const reader = response.body.getReader();
        const decoder = new TextDecoder();
        let buffer = '';
        let fullText = '';

        while (true) {
            const { done, value } = await reader.read();
            if (done) break;

            buffer += decoder.decode(value, { stream: true });

            const lines = buffer.split('\n');
            buffer = lines.pop() || '';

            for (const line of lines) {
                if (!line.startsWith('data: ')) continue;
                const data = line.slice(6).trim();
                if (!data) continue;

                try {
                    const parsed = JSON.parse(data);
                    if (parsed.type === 'token' && parsed.content) {
                        fullText += parsed.content;
                        onToken(parsed.content, fullText);
                    } else if (parsed.type === 'done') {
                        onDone(parsed.content || fullText);
                        return;
                    } else if (parsed.type === 'error') {
                        onError(new Error(parsed.message || 'LLM 返回错误'));
                        return;
                    }
                } catch (e) {
                    // skip unparseable lines
                }
            }
        }

        onDone(fullText);
    } catch (err) {
        onError(err);
    }
}
