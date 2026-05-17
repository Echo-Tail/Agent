// js/constants/config.js - 常量配置

// API配置
const ApiConfig = {
    BASE_URL: (window.location.port === '3000' || window.location.port === '8888') ? '/v1' : 'http://localhost:8888/v1',  // API基础路径
    TIMEOUT: 10000,              // 请求超时时间(ms)
    RETRY_COUNT: 3,              // 重试次数
    RETRY_DELAY: 1000,           // 重试延迟(ms)

    // 接口路径
    ENDPOINTS: {
        // 用户相关
        LOGIN: '/login',
        REGISTER: '/register',
        LOGOUT: '/logout',
        USERS: '/users',
        USER_STATUS: (id) => `/users/${id}/status`,
        USER_TOGGLE: (id) => `/users/${id}/toggle`,

        // Agent相关
        AGENTS: '/agents',
        AGENT_DETAIL: (id) => `/agents/${id}`,
        AGENT_STATUS: (id) => `/agents/${id}/status`,

        // 对话相关
        CHAT: (agentId) => `/chat/${agentId}`,
        CHAT_STREAM: (agentId) => `/chat/${agentId}/stream`,
        SESSIONS: '/sessions',
        SESSION: (id) => `/sessions/${id}`,

        // 邀请码相关
        INVITE_CODES: '/invite-codes',
        INVITE_CODES_BATCH: '/invite-codes/batch',
        INVITE_CODE: (code) => `/invite-codes/${code}`,

        // 统计相关
        STATS: '/stats',
        STATS_AGENTS: '/stats/agents',
        STATS_USERS: '/stats/users'
    }
};

// 分页配置
const PaginationConfig = {
    DEFAULT_PAGE_SIZE: 10,
    PAGE_SIZE_OPTIONS: [10, 20, 50, 100],
    MAX_PAGE_SIZE: 100
};

// 表单验证规则
const ValidationRules = {
    USERNAME: {
        min: 2,
        max: 20,
        pattern: /^[a-zA-Z一-龥][a-zA-Z0-9一-龥]{1,19}$/,
        message: '用户名格式不正确（2-20个字符，字母、数字、中文）'
    },
    PASSWORD: {
        min: 6,
        max: 20,
        pattern: /^[a-zA-Z0-9!@#$%^&*]{6,20}$/,
        message: '密码格式不正确（6-20个字符，字母、数字、特殊符号）'
    },
    EMAIL: {
        pattern: /^[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\.[a-zA-Z]{2,}$/,
        message: '邮箱格式不正确'
    },
    AGENT_NAME: {
        min: 2,
        max: 30,
        message: 'Agent名称长度为2-30个字符'
    },
    INVITE_CODE: {
        length: 6,
        pattern: /^[A-Z0-9]{6,}$/,
        message: '邀请码格式不正确'
    }
};

// 本地存储Key
const StorageKeys = {
    TOKEN: 'ecomagents_token',
    CURRENT_USER: 'ecomagents_current_user',
    THEME: 'ecomagents_theme',
    LANGUAGE: 'ecomagents_language',
    SIDEBAR_COLLAPSED: 'ecomagents_sidebar_collapsed'
};

// 主题配置
const ThemeConfig = {
    LIGHT: 'light',
    DARK: 'dark',
    AUTO: 'auto',

    getDefault() {
        return this.LIGHT;
    }
};

// 时间格式配置
const DateFormat = {
    DATE: 'YYYY-MM-DD',
    TIME: 'HH:mm:ss',
    DATETIME: 'YYYY-MM-DD HH:mm:ss',
    CHINESE_DATE: 'YYYY年MM月DD日',
    CHINESE_DATETIME: 'YYYY年MM月DD日 HH时mm分ss秒'
};

// 缓存时间配置（单位：毫秒）
const CacheConfig = {
    USER_INFO: 30 * 60 * 1000,
    AGENT_LIST: 5 * 60 * 1000,
    STATS_DATA: 10 * 60 * 1000,
    INVITE_CODES: 60 * 60 * 1000
};

// 默认头像配置
const DefaultAvatars = [
    'bi-robot',
    'bi-cpu',
    'bi-chat-dots',
    'bi-stars',
    'bi-lightbulb',
    'bi-gem'
];

// 页面标题配置
const PageTitles = {
    DASHBOARD: '工作台',
    AGENTS: '我的Agent',
    CREATE_AGENT: '创建Agent',
    CHAT: '对话',
    USER_MANAGE: '用户管理',
    SETTINGS: '设置'
};
