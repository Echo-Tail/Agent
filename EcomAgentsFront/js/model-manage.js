// js/model-manage.js - 模型管理模块

// ========== API层 ==========
async function fetchModels() {
    const r = await get('/models');
    return r.success ? (r.data || []) : [];
}
async function createModel(data) {
    const r = await post('/models', data);
    if (r.success) { showToast('模型创建成功', 'success'); return r.data; }
    showToast(r.message || '创建失败', 'error'); return null;
}
async function updateModel(id, data) {
    const r = await put('/models/' + id, data);
    if (r.success) { showToast('模型已更新', 'success'); return r.data; }
    showToast(r.message || '更新失败', 'error'); return null;
}
async function deleteModel(id) {
    const r = await del('/models/' + id);
    if (r.success) { showToast('模型已删除', 'success'); return true; }
    showToast(r.message || '删除失败', 'error'); return false;
}

// ========== 状态 ==========
let modelsState = { list: [], editingModel: null };

// ========== 渲染 ==========
async function renderModelManage() {
    if (!isAdmin()) {
        document.getElementById('dynamicContent').innerHTML =
            '<div class="alert alert-danger">权限不足，仅管理员可管理模型配置</div>';
        return;
    }

    modelsState.list = await fetchModels();

    const html = `
        <div class="d-flex justify-content-between align-items-center mb-3">
            <h5 class="mb-0"><i class="bi bi-cpu"></i> 模型管理</h5>
            <button class="btn btn-primary btn-sm" onclick="showModelModal()">
                <i class="bi bi-plus-lg"></i> 添加模型
            </button>
        </div>
        <div class="table-responsive">
            <table class="table table-hover align-middle">
                <thead class="table-light">
                    <tr>
                        <th>名称</th>
                        <th>供应商</th>
                        <th>模型ID</th>
                        <th>API地址</th>
                        <th style="width:80px">最大Token</th>
                        <th style="width:70px">温度</th>
                        <th style="width:70px">默认</th>
                        <th style="width:70px">状态</th>
                        <th style="width:160px">操作</th>
                    </tr>
                </thead>
                <tbody>
                    ${modelsState.list.length === 0
                        ? '<tr><td colspan="9" class="text-center text-muted py-4">暂无模型配置</td></tr>'
                        : modelsState.list.map(m => renderModelRow(m)).join('')
                    }
                </tbody>
            </table>
        </div>
        <!-- 模型编辑弹窗 -->
        <div class="modal fade" id="modelModal" tabindex="-1">
            <div class="modal-dialog modal-lg">
                <div class="modal-content">
                    <div class="modal-header">
                        <h5 class="modal-title" id="modelModalTitle">添加模型</h5>
                        <button type="button" class="btn-close" data-bs-dismiss="modal"></button>
                    </div>
                    <div class="modal-body">
                        <form id="modelForm">
                            <div class="row g-3">
                                <div class="col-md-6">
                                    <label class="form-label">名称 <span class="text-danger">*</span></label>
                                    <input type="text" class="form-control" id="mName" required
                                           placeholder="例: GPT-4o">
                                </div>
                                <div class="col-md-6">
                                    <label class="form-label">供应商</label>
                                    <select class="form-select" id="mProvider">
                                        <option value="openai">OpenAI</option>
                                        <option value="deepseek">DeepSeek</option>
                                        <option value="qwen">通义千问</option>
                                        <option value="ollama">Ollama (本地)</option>
                                        <option value="custom">自定义</option>
                                    </select>
                                </div>
                                <div class="col-md-6">
                                    <label class="form-label">模型ID <span class="text-danger">*</span></label>
                                    <input type="text" class="form-control" id="mModelName" required
                                           placeholder="例: gpt-4o">
                                </div>
                                <div class="col-md-6">
                                    <label class="form-label">最大Token</label>
                                    <input type="number" class="form-control" id="mMaxTokens" value="4096" min="1" max="128000">
                                </div>
                                <div class="col-12">
                                    <label class="form-label">API地址</label>
                                    <input type="url" class="form-control" id="mApiUrl"
                                           placeholder="https://api.openai.com/v1/chat/completions">
                                </div>
                                <div class="col-md-8">
                                    <label class="form-label">API Key</label>
                                    <input type="password" class="form-control" id="mApiKey"
                                           placeholder="sk-...">
                                </div>
                                <div class="col-md-4">
                                    <label class="form-label">温度</label>
                                    <input type="number" class="form-control" id="mTemperature"
                                           value="0.7" min="0" max="2" step="0.1">
                                </div>
                                <div class="col-12">
                                    <div class="form-check form-check-inline">
                                        <input class="form-check-input" type="checkbox" id="mIsDefault">
                                        <label class="form-check-label" for="mIsDefault">设为默认模型</label>
                                    </div>
                                    <div class="form-check form-check-inline">
                                        <input class="form-check-input" type="checkbox" id="mEnabled" checked>
                                        <label class="form-check-label" for="mEnabled">启用</label>
                                    </div>
                                </div>
                            </div>
                        </form>
                    </div>
                    <div class="modal-footer">
                        <button type="button" class="btn btn-secondary" data-bs-dismiss="modal">取消</button>
                        <button type="button" class="btn btn-primary" onclick="saveModel()">保存</button>
                    </div>
                </div>
            </div>
        </div>
    `;

    document.getElementById('dynamicContent').innerHTML = html;
}

function renderModelRow(m) {
    return `
        <tr>
            <td><strong>${escHtml(m.name)}</strong></td>
            <td><span class="badge bg-secondary">${escHtml(m.provider || '-')}</span></td>
            <td><code>${escHtml(m.modelName)}</code></td>
            <td style="max-width:200px;overflow:hidden;text-overflow:ellipsis;white-space:nowrap;" title="${escHtml(m.apiUrl || '')}">
                ${escHtml(m.apiUrl || '-')}
            </td>
            <td>${m.maxTokens || '-'}</td>
            <td>${m.temperature != null ? m.temperature : '-'}</td>
            <td>${m.isDefault ? '<span class="badge bg-warning text-dark">默认</span>' : ''}</td>
            <td>${m.enabled
                ? '<span class="badge bg-success">启用</span>'
                : '<span class="badge bg-secondary">禁用</span>'}
            </td>
            <td>
                <button class="btn btn-sm btn-outline-primary me-1" onclick="showModelModal(${m.id})" title="编辑">
                    <i class="bi bi-pencil"></i>
                </button>
                <button class="btn btn-sm btn-outline-danger" onclick="confirmDeleteModel(${m.id})" title="删除">
                    <i class="bi bi-trash"></i>
                </button>
            </td>
        </tr>
    `;
}

// ========== 弹窗逻辑 ==========
function showModelModal(id) {
    modelsState.editingModel = null;
    document.getElementById('modelModalTitle').textContent = '添加模型';
    document.getElementById('mName').value = '';
    document.getElementById('mProvider').value = 'openai';
    document.getElementById('mModelName').value = '';
    document.getElementById('mApiUrl').value = '';
    document.getElementById('mApiKey').value = '';
    document.getElementById('mMaxTokens').value = '4096';
    document.getElementById('mTemperature').value = '0.7';
    document.getElementById('mIsDefault').checked = false;
    document.getElementById('mEnabled').checked = true;

    if (id != null) {
        const m = modelsState.list.find(x => x.id === id);
        if (!m) return;
        modelsState.editingModel = m;
        document.getElementById('modelModalTitle').textContent = '编辑模型';
        document.getElementById('mName').value = m.name || '';
        document.getElementById('mProvider').value = m.provider || 'openai';
        document.getElementById('mModelName').value = m.modelName || '';
        document.getElementById('mApiUrl').value = m.apiUrl || '';
        document.getElementById('mApiKey').value = '';
        document.getElementById('mMaxTokens').value = m.maxTokens || '4096';
        document.getElementById('mTemperature').value = m.temperature != null ? m.temperature : '0.7';
        document.getElementById('mIsDefault').checked = !!m.isDefault;
        document.getElementById('mEnabled').checked = m.enabled !== false;
    }

    const modal = new bootstrap.Modal(document.getElementById('modelModal'));
    modal.show();
}

async function saveModel() {
    const data = {
        name: document.getElementById('mName').value.trim(),
        provider: document.getElementById('mProvider').value,
        modelName: document.getElementById('mModelName').value.trim(),
        apiUrl: document.getElementById('mApiUrl').value.trim() || null,
        apiKey: document.getElementById('mApiKey').value.trim() || null,
        maxTokens: parseInt(document.getElementById('mMaxTokens').value) || null,
        temperature: parseFloat(document.getElementById('mTemperature').value) || null,
        isDefault: document.getElementById('mIsDefault').checked,
        enabled: document.getElementById('mEnabled').checked
    };

    if (!data.name || !data.modelName) {
        showToast('请填写名称和模型ID', 'error');
        return;
    }

    const editing = modelsState.editingModel;
    let success;
    if (editing) {
        if (editing.apiKey && !data.apiKey) {
            delete data.apiKey;
        }
        success = await updateModel(editing.id, data);
    } else {
        success = await createModel(data);
    }

    if (success) {
        bootstrap.Modal.getInstance(document.getElementById('modelModal')).hide();
        renderModelManage();
    }
}

async function confirmDeleteModel(id) {
    if (!confirm('确定要删除此模型配置吗？')) return;
    const ok = await deleteModel(id);
    if (ok) renderModelManage();
}

function escHtml(s) {
    if (s == null) return '';
    return String(s).replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;')
        .replace(/"/g, '&quot;');
}
