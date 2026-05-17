// js/agent.js - Agent管理模块

// ========== 预定义配置 ==========
const AGENT_ICONS = [
    { value: 'bi-robot', label: '机器人' },
    { value: 'bi-cpu', label: '芯片' },
    { value: 'bi-chat-dots', label: '对话' },
    { value: 'bi-stars', label: '星星' },
    { value: 'bi-lightbulb', label: '灯泡' },
    { value: 'bi-gem', label: '宝石' },
    { value: 'bi-headset', label: '耳麦' },
    { value: 'bi-cart', label: '购物车' },
    { value: 'bi-megaphone', label: '喇叭' },
    { value: 'bi-graph-up', label: '图表' }
];

const TOOL_OPTIONS = [
    { value: 'dialog', label: '对话能力' },
    { value: 'query', label: '信息查询' },
    { value: 'order', label: '订单查询' },
    { value: 'track', label: '物流追踪' },
    { value: 'product', label: '商品分析' },
    { value: 'analysis', label: '数据分析' },
    { value: 'marketing', label: '营销工具' },
    { value: 'copywriting', label: '文案生成' }
];

// ========== API层 ==========
async function fetchAgents() {
    const result = await get('/agents');
    if (result.success) return result.data;
    showToast(result.message, 'error');
    return [];
}

async function fetchAgent(id) {
    const result = await get(`/agents/${id}`);
    if (result.success) return result.data;
    showToast(result.message, 'error');
    return null;
}

async function createAgent(data) {
    const result = await post('/agents', data);
    if (result.success) {
        showToast('Agent创建成功', 'success');
        return result.data;
    }
    showToast(result.message, 'error');
    return null;
}

async function updateAgent(id, data) {
    const result = await put(`/agents/${id}`, data);
    if (result.success) {
        showToast('Agent更新成功', 'success');
        return result.data;
    }
    showToast(result.message, 'error');
    return null;
}

async function deleteAgent(id) {
    const result = await del(`/agents/${id}`);
    if (result.success) {
        showToast('Agent已删除', 'success');
        return true;
    }
    showToast(result.message, 'error');
    return false;
}

// ========== 表单工具 ==========
function collectAgentFormData() {
    const name = document.getElementById('agentName').value.trim();
    if (!name) {
        showToast('请输入Agent名称', 'warning');
        return null;
    }

    const tagsInput = document.getElementById('agentTags').value.trim();
    const tags = tagsInput ? tagsInput.split(',').map(t => t.trim()).filter(Boolean) : [];

    const tools = TOOL_OPTIONS
        .filter(t => document.getElementById(`tool_${t.value}`).checked)
        .map(t => t.value);

    return {
        name,
        icon: document.getElementById('agentIcon').value,
        description: document.getElementById('agentDesc').value.trim(),
        systemPrompt: document.getElementById('agentPrompt').value.trim(),
        greeting: document.getElementById('agentGreeting').value.trim(),
        tags,
        tools,
        status: 'active'
    };
}

// ========== 页面渲染 ==========

// Agent管理列表（卡片布局）
async function renderAgentManage() {
    const agents = await fetchAgents();

    const html = `
        <div>
            <div class="d-flex justify-content-between align-items-center mb-4">
                <h5 class="mb-0"><i class="bi bi-robot"></i> 所有Agent</h5>
                <button class="btn btn-primary" onclick="window.showCreateAgent()">
                    <i class="bi bi-plus"></i> 创建Agent
                </button>
            </div>
            ${agents.length === 0 ? `
                <div class="text-center py-5 text-muted">
                    <i class="bi bi-robot" style="font-size: 3rem;"></i>
                    <p class="mt-3">暂无Agent，点击上方按钮创建</p>
                </div>
            ` : `
            <div class="row g-4">
                ${agents.map(agent => `
                    <div class="col-md-6 col-lg-4">
                        <div class="agent-manage-card" onclick="window.goToAgentChat(${agent.id})">
                            <div class="agent-manage-actions" onclick="event.stopPropagation()">
                                <button class="btn btn-sm btn-outline-secondary action-btn" onclick="window.editAgent(${agent.id})" title="编辑">
                                    <i class="bi bi-pencil"></i>
                                </button>
                                <button class="btn btn-sm btn-outline-danger action-btn" onclick="window.confirmDeleteAgent(${agent.id}, '${agent.name.replace(/'/g, "\\'")}')" title="删除">
                                    <i class="bi bi-trash"></i>
                                </button>
                            </div>
                            <div class="agent-manage-header">
                                <div class="agent-manage-icon">
                                    <i class="${agent.icon || 'bi-robot'}"></i>
                                </div>
                                <div class="agent-manage-info">
                                    <div class="agent-manage-name">${agent.name}</div>
                                    <span class="user-status ${agent.status === 'active' ? 'status-active' : 'status-disabled'}">${agent.status === 'active' ? '启用' : '禁用'}</span>
                                </div>
                            </div>
                            <div class="agent-manage-desc">${agent.description || '暂无描述'}</div>
                            <div class="agent-tags">
                                ${(agent.tags || []).map(t => `<span class="tag">${t}</span>`).join('')}
                            </div>
                        </div>
                    </div>
                `).join('')}
            </div>
            `}
        </div>
    `;
    document.getElementById('dynamicContent').innerHTML = html;
}

// 创建Agent表单
function renderCreateAgentForm() {
    const html = `
        <div class="dashboard-card">
            <div class="d-flex align-items-center mb-4">
                <button class="btn btn-outline-secondary me-3" onclick="navigateTo('agents')">
                    <i class="bi bi-arrow-left"></i>
                </button>
                <h5 class="mb-0"><i class="bi bi-plus-circle"></i> 创建新Agent</h5>
            </div>
            <form id="agentForm" onsubmit="window.handleSaveAgent(event)">
                <div class="row">
                    <div class="col-md-6 mb-3">
                        <label class="form-label">Agent名称 *</label>
                        <input type="text" class="form-control" id="agentName" placeholder="例如：客服助手" required>
                    </div>
                    <div class="col-md-6 mb-3">
                        <label class="form-label">图标</label>
                        <div class="d-flex gap-2 flex-wrap" id="iconSelector">
                            ${AGENT_ICONS.map(icon => `
                                <div class="icon-option ${icon.value === 'bi-robot' ? 'selected' : ''}" data-icon="${icon.value}" onclick="window.selectAgentIcon(this)">
                                    <i class="bi ${icon.value}"></i>
                                </div>
                            `).join('')}
                        </div>
                        <input type="hidden" id="agentIcon" value="bi-robot">
                    </div>
                </div>
                <div class="mb-3">
                    <label class="form-label">描述</label>
                    <textarea class="form-control" id="agentDesc" rows="2" placeholder="简单描述这个Agent的用途"></textarea>
                </div>
                <div class="mb-3">
                    <label class="form-label">角色设定（System Prompt）</label>
                    <textarea class="form-control" id="agentPrompt" rows="4" placeholder="你是一个专业的电商客服助手，擅长处理退换货、物流查询等问题..."></textarea>
                </div>
                <div class="mb-3">
                    <label class="form-label">开场白（Greeting）</label>
                    <input type="text" class="form-control" id="agentGreeting" placeholder="你好！我是...，有什么可以帮你的吗？">
                </div>
                <div class="mb-3">
                    <label class="form-label">标签（用逗号分隔）</label>
                    <input type="text" class="form-control" id="agentTags" placeholder="例如：对话, 查询, 客服">
                </div>
                <div class="mb-3">
                    <label class="form-label">能力配置</label>
                    <div class="row">
                        ${TOOL_OPTIONS.map(tool => `
                            <div class="col-md-3">
                                <div class="form-check">
                                    <input class="form-check-input" type="checkbox" value="${tool.value}" id="tool_${tool.value}">
                                    <label class="form-check-label" for="tool_${tool.value}">${tool.label}</label>
                                </div>
                            </div>
                        `).join('')}
                    </div>
                </div>
                <button type="submit" class="btn btn-primary">创建Agent</button>
            </form>
        </div>
    `;
    document.getElementById('dynamicContent').innerHTML = html;
}

// 编辑Agent表单
function renderEditAgentForm(agent) {
    const tags = (agent.tags || []).join(', ');

    const html = `
        <div class="dashboard-card">
            <div class="d-flex align-items-center mb-4">
                <button class="btn btn-outline-secondary me-3" onclick="navigateTo('agents')">
                    <i class="bi bi-arrow-left"></i>
                </button>
                <h5 class="mb-0"><i class="bi bi-pencil-square"></i> 编辑Agent</h5>
            </div>
            <form id="agentForm" onsubmit="window.handleUpdateAgent(event, ${agent.id})">
                <div class="row">
                    <div class="col-md-6 mb-3">
                        <label class="form-label">Agent名称 *</label>
                        <input type="text" class="form-control" id="agentName" value="${agent.name.replace(/"/g, '&quot;')}" required>
                    </div>
                    <div class="col-md-6 mb-3">
                        <label class="form-label">图标</label>
                        <div class="d-flex gap-2 flex-wrap" id="iconSelector">
                            ${AGENT_ICONS.map(icon => `
                                <div class="icon-option ${icon.value === agent.icon ? 'selected' : ''}" data-icon="${icon.value}" onclick="window.selectAgentIcon(this)">
                                    <i class="bi ${icon.value}"></i>
                                </div>
                            `).join('')}
                        </div>
                        <input type="hidden" id="agentIcon" value="${agent.icon || 'bi-robot'}">
                    </div>
                </div>
                <div class="mb-3">
                    <label class="form-label">描述</label>
                    <textarea class="form-control" id="agentDesc" rows="2">${(agent.description || '').replace(/"/g, '&quot;')}</textarea>
                </div>
                <div class="mb-3">
                    <label class="form-label">角色设定（System Prompt）</label>
                    <textarea class="form-control" id="agentPrompt" rows="4">${(agent.systemPrompt || '').replace(/"/g, '&quot;')}</textarea>
                </div>
                <div class="mb-3">
                    <label class="form-label">开场白（Greeting）</label>
                    <input type="text" class="form-control" id="agentGreeting" value="${(agent.greeting || '').replace(/"/g, '&quot;')}">
                </div>
                <div class="mb-3">
                    <label class="form-label">标签（用逗号分隔）</label>
                    <input type="text" class="form-control" id="agentTags" value="${tags.replace(/"/g, '&quot;')}">
                </div>
                <div class="mb-3">
                    <label class="form-label">能力配置</label>
                    <div class="row">
                        ${TOOL_OPTIONS.map(tool => `
                            <div class="col-md-3">
                                <div class="form-check">
                                    <input class="form-check-input" type="checkbox" value="${tool.value}" id="tool_${tool.value}" ${(agent.tools || []).includes(tool.value) ? 'checked' : ''}>
                                    <label class="form-check-label" for="tool_${tool.value}">${tool.label}</label>
                                </div>
                            </div>
                        `).join('')}
                    </div>
                </div>
                <div class="mb-3">
                    <label class="form-label">状态</label>
                    <select class="form-select" id="agentStatus" style="width: auto;">
                        <option value="active" ${agent.status === 'active' ? 'selected' : ''}>启用</option>
                        <option value="disabled" ${agent.status === 'disabled' ? 'selected' : ''}>禁用</option>
                    </select>
                </div>
                <button type="submit" class="btn btn-primary">保存修改</button>
            </form>
        </div>
    `;
    document.getElementById('dynamicContent').innerHTML = html;
}

// ========== 全局事件处理 ==========

// 从Agent管理页面跳转到对话
window.goToAgentChat = function(agentId) {
    window._pendingAgentId = agentId;
    navigateTo('chat');
};

window.selectAgentIcon = function(el) {
    document.querySelectorAll('.icon-option').forEach(opt => opt.classList.remove('selected'));
    el.classList.add('selected');
    document.getElementById('agentIcon').value = el.dataset.icon;
};

window.showCreateAgent = function() {
    renderCreateAgentForm();
};

window.editAgent = async function(id) {
    const agent = await fetchAgent(id);
    if (agent) {
        renderEditAgentForm(agent);
    }
};

window.confirmDeleteAgent = function(id, name) {
    if (confirm(`确定要删除Agent「${name}」吗？此操作不可恢复。`)) {
        window.handleDeleteAgent(id);
    }
};

window.handleDeleteAgent = async function(id) {
    const success = await deleteAgent(id);
    if (success) {
        renderAgentManage();
    }
};

window.handleSaveAgent = async function(event) {
    event.preventDefault();
    const data = collectAgentFormData();
    if (!data) return;

    const agent = await createAgent(data);
    if (agent) {
        navigateTo('agents');
    }
};

window.handleUpdateAgent = async function(event, id) {
    event.preventDefault();
    const data = collectAgentFormData();
    if (!data) return;

    data.status = document.getElementById('agentStatus').value;

    const agent = await updateAgent(id, data);
    if (agent) {
        navigateTo('agents');
    }
};
