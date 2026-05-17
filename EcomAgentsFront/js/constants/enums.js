// js/constants/enums.js - 枚举常量

const UserRole = {
    ADMIN: 'admin',
    USER: 'user',

    getLabel(role) {
        const labels = { 'admin': '管理员', 'user': '普通用户' };
        return labels[role] || role;
    }
};

const UserStatus = {
    ACTIVE: 'active',
    DISABLED: 'disabled',

    getLabel(status) {
        const labels = { 'active': '启用', 'disabled': '禁用' };
        return labels[status] || status;
    },

    getBadgeClass(status) {
        const classes = { 'active': 'status-active', 'disabled': 'status-disabled' };
        return classes[status] || '';
    }
};

const ToolCategory = {
    WEB: 'web',
    TERMINAL_FILES: 'terminal_files',
    BROWSER: 'browser',
    MEDIA: 'media',
    MEMORY: 'memory',
    MCP: 'mcp',

    getLabel(category) {
        const labels = {
            'web': 'Web搜索',
            'terminal_files': '终端与文件',
            'browser': '浏览器',
            'media': '媒体处理',
            'memory': '记忆与回溯',
            'mcp': 'MCP Server'
        };
        return labels[category] || category;
    }
};

const SessionStatus = {
    ACTIVE: 'active',
    ARCHIVED: 'archived',
    DELETED: 'deleted'
};

const AgentStatus = {
    ACTIVE: 'active',
    DISABLED: 'disabled'
};

const SkillStatus = {
    ACTIVE: 'active',
    DISABLED: 'disabled'
};
