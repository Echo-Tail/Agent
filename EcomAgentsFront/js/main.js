// js/main.js - 主应用逻辑

// 侧边栏折叠
function toggleSidebar() {
    const sidebar = document.getElementById('sidebar');
    const mainContent = document.getElementById('mainContent');
    sidebar.classList.toggle('collapsed');
    mainContent.classList.toggle('expanded');
    const isCollapsed = sidebar.classList.contains('collapsed');
    localStorage.setItem(StorageKeys.SIDEBAR_COLLAPSED, isCollapsed ? '1' : '0');
}

// 初始化侧边栏状态
function initSidebar() {
    const collapsed = localStorage.getItem(StorageKeys.SIDEBAR_COLLAPSED);
    if (collapsed === '1') {
        document.getElementById('sidebar').classList.add('collapsed');
        document.getElementById('mainContent').classList.add('expanded');
    }
}

// 导航函数
function navigateTo(page) {
    // 更新导航激活状态
    document.querySelectorAll('.nav-item').forEach(item => {
        item.classList.remove('active');
    });
    if (event && event.currentTarget) {
        event.currentTarget.classList.add('active');
    }

    // 更新页面标题
    const titles = {
        'dashboard': '工作台',
        'agents': '我的Agent',
        'create': '创建Agent',
        'chat': '对话',
        'history': '历史会话',
        'userManage': '用户管理',
        'knowledge': '知识库',
        'models': '模型管理',
        'settings': '设置'
    };
    document.getElementById('pageTitle').innerText = titles[page] || 'EcomAgents';

    // 加载对应页面内容
    switch (page) {
        case 'dashboard':
            loadDashboard();
            break;
        case 'agents':
            loadAgents();
            break;
        case 'create':
            loadCreateAgent();
            break;
        case 'chat':
            loadChat();
            break;
        case 'history':
            loadHistory();
            break;
        case 'userManage':
            loadUserManage();
            break;
        case 'knowledge':
            loadKnowledge();
            break;
        case 'models':
            loadModels();
            break;
        case 'settings':
            loadSettings();
            break;
    }
}

// ========== 工作台 ==========

async function loadDashboard() {
    const agents = await loadAgentsForDashboard();
    const activeAgents = agents.filter(a => a.status === 'active');

    const html = `
        <div class="dashboard-card" style="background: linear-gradient(135deg, #10b981 0%, #059669 100%); color: white;">
            <h3>欢迎回来！</h3>
            <p class="mb-0 opacity-90">今天需要创建新的Agent，还是继续对话？</p>
        </div>

        <div class="row g-4 mb-4">
            <div class="col-md-3">
                <div class="stat-card">
                    <div>
                        <div class="stat-value">${agents.length}</div>
                        <div class="stat-label">Agent总数</div>
                    </div>
                    <div class="stat-icon"><i class="bi bi-robot"></i></div>
                </div>
            </div>
            <div class="col-md-3">
                <div class="stat-card">
                    <div>
                        <div class="stat-value">${activeAgents.length}</div>
                        <div class="stat-label">在线Agent</div>
                    </div>
                    <div class="stat-icon"><i class="bi bi-check-circle"></i></div>
                </div>
            </div>
            <div class="col-md-3">
                <div class="stat-card">
                    <div>
                        <div class="stat-value">${agents.length - activeAgents.length}</div>
                        <div class="stat-label">离线Agent</div>
                    </div>
                    <div class="stat-icon"><i class="bi bi-clock"></i></div>
                </div>
            </div>
            <div class="col-md-3">
                <div class="stat-card">
                    <div>
                        <div class="stat-value">4</div>
                        <div class="stat-label">已配置工具</div>
                    </div>
                    <div class="stat-icon"><i class="bi bi-tools"></i></div>
                </div>
            </div>
        </div>

        <div class="dashboard-card">
            <div class="d-flex justify-content-between align-items-center mb-3">
                <h5 class="mb-0"><i class="bi bi-plus-circle"></i> 快速创建Agent</h5>
                <button class="btn btn-sm btn-primary" onclick="navigateTo('create')">创建新Agent</button>
            </div>
        </div>

        <h5 class="mb-3"><i class="bi bi-grid-3x3-gap-fill"></i> 我的Agent</h5>
        <div class="row g-4" id="agentList">
            ${renderAgentCards(activeAgents)}
        </div>
    `;
    document.getElementById('dynamicContent').innerHTML = html;
}

let agentsCache = [];

async function loadAgentsForDashboard() {
    const result = await get('/agents');
    if (result.success) {
        agentsCache = result.data;
    }
    return agentsCache;
}

function renderAgentCards(agents) {
    if (!agents || agents.length === 0) {
        return '<div class="col-12 text-center text-muted py-4">暂无Agent</div>';
    }
    return agents.map(agent => `
        <div class="col-md-6 col-lg-3">
            <div class="agent-card" onclick="goToChat(${agent.id})">
                <div class="agent-header">
                    <div class="agent-avatar"><i class="${agent.icon || 'bi-robot'}"></i></div>
                    <div>
                        <div class="agent-name">${agent.name}</div>
                        <small class="text-muted">${agent.status === 'active' ? '在线' : '离线'}</small>
                    </div>
                </div>
                <div class="agent-desc">${agent.description || ''}</div>
                <div class="agent-tags">
                    ${(agent.tags || []).map(tag => `<span class="tag">${tag}</span>`).join('')}
                </div>
            </div>
        </div>
    `).join('');
}

function goToChat(agentId) {
    window._pendingAgentId = agentId;
    navigateTo('chat');
}

// ========== 页面加载器 ==========

function loadAgents() {
    renderAgentManage();
}

function loadCreateAgent() {
    window.showCreateAgent();
}

function loadChat() {
    renderChat();
}

function loadHistory() {
    renderHistoryPage();
}

function loadKnowledge() {
    renderKnowledgeBaseList();
}

function loadModels() {
    renderModelManage();
}

function loadUserManage() {
    renderUserManage();
}

function loadSettings() {
    const currentTheme = localStorage.getItem(StorageKeys.THEME) || ThemeConfig.getDefault();
    const sidebarCollapsed = localStorage.getItem(StorageKeys.SIDEBAR_COLLAPSED) === '1';
    const apiUrl = localStorage.getItem('ecomagents_api_url') || ApiConfig.BASE_URL;

    const html = `
        <div class="dashboard-card">
            <h5 class="mb-4"><i class="bi bi-gear"></i> 系统设置</h5>
            <div class="mb-3">
                <label class="form-label">侧边栏默认状态</label>
                <select class="form-select" id="settingsSidebarState" style="width: auto;">
                    <option value="expanded" ${sidebarCollapsed ? '' : 'selected'}>展开</option>
                    <option value="collapsed" ${sidebarCollapsed ? 'selected' : ''}>折叠</option>
                </select>
            </div>
            <div class="mb-3">
                <div class="form-check form-switch">
                    <input class="form-check-input" type="checkbox" id="settingsDarkMode" ${currentTheme === ThemeConfig.DARK ? 'checked' : ''}>
                    <label class="form-check-label" for="settingsDarkMode">深色模式</label>
                </div>
            </div>
            <hr>
            <h6 class="mb-3">API配置</h6>
            <div class="mb-3">
                <label class="form-label">后端服务地址</label>
                <input type="text" class="form-control" id="settingsApiUrl" value="${apiUrl}" style="width: 300px;">
            </div>
            <button class="btn btn-primary" onclick="saveSettings()">保存设置</button>
        </div>
    `;
    document.getElementById('dynamicContent').innerHTML = html;
}

function saveSettings() {
    // 深色模式
    const darkMode = document.getElementById('settingsDarkMode').checked;
    const theme = darkMode ? ThemeConfig.DARK : ThemeConfig.LIGHT;
    localStorage.setItem(StorageKeys.THEME, theme);
    document.documentElement.setAttribute('data-theme', darkMode ? 'dark' : '');

    // 侧边栏默认状态
    const sidebarState = document.getElementById('settingsSidebarState').value;
    const shouldCollapse = sidebarState === 'collapsed';
    localStorage.setItem(StorageKeys.SIDEBAR_COLLAPSED, shouldCollapse ? '1' : '0');

    // API地址
    const apiUrl = document.getElementById('settingsApiUrl').value.trim();
    if (apiUrl) {
        localStorage.setItem('ecomagents_api_url', apiUrl);
    }

    showToast('设置已保存', 'success');
}

function applyTheme() {
    const theme = localStorage.getItem(StorageKeys.THEME) || ThemeConfig.getDefault();
    if (theme === ThemeConfig.DARK) {
        document.documentElement.setAttribute('data-theme', 'dark');
    } else {
        document.documentElement.removeAttribute('data-theme');
    }
}

// ========== 侧边栏历史会话折叠 ==========

let _historyExpanded = true;

function toggleHistoryNav() {
    _historyExpanded = !_historyExpanded;
    const submenu = document.getElementById('historySubmenu');
    const arrow = document.getElementById('historyArrow');
    if (submenu) submenu.style.display = _historyExpanded ? 'block' : 'none';
    if (arrow) arrow.style.transform = _historyExpanded ? 'rotate(0deg)' : 'rotate(-90deg)';
}

// ========== 用户菜单初始化 ==========

function initUserMenu() {
    const userManageNav = document.getElementById('userManageNav');
    if (userManageNav) {
        userManageNav.style.display = isAdmin() ? 'flex' : 'none';
    }
    const modelManageNav = document.getElementById('modelManageNav');
    if (modelManageNav) {
        modelManageNav.style.display = isAdmin() ? 'flex' : 'none';
    }
}

function updateSidebarUser() {
    const user = getCurrentUser();
    if (!user) return;
    const nameEl = document.getElementById('sidebarName');
    const emailEl = document.getElementById('sidebarEmail');
    const avatarEl = document.getElementById('sidebarAvatar');
    if (nameEl) nameEl.textContent = user.username || '用户';
    if (emailEl) emailEl.textContent = user.email || '';
    if (avatarEl) {
        avatarEl.textContent = (user.username || '用')[0];
    }
}

// ========== Bootstrap 工具 ==========

function reinitializeDropdowns() {
    if (typeof bootstrap !== 'undefined') {
        document.querySelectorAll('[data-bs-toggle="dropdown"]').forEach(el => {
            new bootstrap.Dropdown(el);
        });
    }
}

// ========== 页面初始化 ==========

function initNavigation() {
    if (!checkAuth()) return;
    applyTheme();
    initSidebar();
    initUserMenu();
    updateSidebarUser();
    loadDashboard();
}

document.addEventListener('DOMContentLoaded', function () {
    reinitializeDropdowns();
});
