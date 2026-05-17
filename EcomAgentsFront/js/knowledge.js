// js/knowledge.js - 知识库管理模块

// ========== 状态 ==========
let kbState = {
    currentKbId: null,
    kbs: [],
    docs: [],
    searchQuery: ''
};

// ========== API层 ==========
async function fetchKBs() {
    const r = await get('/knowledge-bases');
    return r.success ? (r.data || []) : [];
}
async function fetchKB(id) {
    const r = await get('/knowledge-bases/' + id);
    return r.success ? r.data : null;
}
async function createKB(data) {
    const r = await post('/knowledge-bases', data);
    if (r.success) { showToast('知识库创建成功', 'success'); return r.data; }
    showToast(r.message, 'error'); return null;
}
async function updateKB(id, data) {
    const r = await put('/knowledge-bases/' + id, data);
    if (r.success) { showToast('知识库已更新', 'success'); return r.data; }
    showToast(r.message, 'error'); return null;
}
async function deleteKB(id) {
    const r = await del('/knowledge-bases/' + id);
    if (r.success) { showToast('知识库已删除', 'success'); return true; }
    showToast(r.message, 'error'); return false;
}
async function fetchDocs(kbId) {
    const r = await get('/knowledge-bases/' + kbId + '/documents');
    return r.success ? (r.data || []) : [];
}
async function uploadDoc(kbId, file) {
    const token = localStorage.getItem(StorageKeys.TOKEN);
    const baseUrl = ApiConfig.BASE_URL;
    const formData = new FormData();
    formData.append('file', file);
    try {
        const resp = await fetch(baseUrl + '/knowledge-bases/' + kbId + '/documents', {
            method: 'POST',
            headers: token ? { 'Authorization': 'Bearer ' + token } : {},
            body: formData
        });
        const text = await resp.text();
        const result = JSON.parse(text);
        if (result.code === 200) {
            showToast('文档上传成功', 'success');
            return result.data;
        }
        showToast(result.message || '上传失败', 'error');
        return null;
    } catch (e) {
        showToast('上传失败: ' + e.message, 'error');
        return null;
    }
}
async function deleteDoc(kbId, docId) {
    const r = await del('/knowledge-bases/' + kbId + '/documents/' + docId);
    if (r.success) { showToast('文档已删除', 'success'); return true; }
    showToast(r.message, 'error'); return false;
}
async function searchKBs(q) {
    const r = await get('/knowledge-bases/search?q=' + encodeURIComponent(q));
    return r.success ? (r.data || []) : [];
}

// ========== 知识库列表页 ==========
async function renderKnowledgeBaseList() {
    kbState.currentKbId = null;
    kbState.kbs = await fetchKBs();

    const html = `
        <div>
            <div class="d-flex justify-content-between align-items-center mb-4">
                <h5 class="mb-0"><i class="bi bi-book"></i> 知识库</h5>
                <button class="btn btn-primary" onclick="window.showCreateKB()">
                    <i class="bi bi-plus"></i> 新建知识库
                </button>
            </div>
            <!-- Search -->
            <div class="mb-4" style="max-width: 400px;">
                <div class="input-group">
                    <span class="input-group-text"><i class="bi bi-search"></i></span>
                    <input type="text" class="form-control" id="kbSearch" placeholder="搜索知识库内容..."
                           onkeydown="if(event.key==='Enter') window.searchAllKBs()">
                    <button class="btn btn-outline-primary" onclick="window.searchAllKBs()">搜索</button>
                </div>
            </div>
            <!-- Search results (if any) -->
            <div id="kbSearchResults" class="d-none mb-4"></div>
            <!-- KB List -->
            ${kbState.kbs.length === 0 ? `
                <div class="text-center py-5 text-muted">
                    <i class="bi bi-book" style="font-size: 3rem;"></i>
                    <p class="mt-3">暂无知识库，点击上方按钮创建</p>
                </div>
            ` : `
            <div class="row g-4">
                ${kbState.kbs.map(kb => `
                    <div class="col-md-6 col-lg-4">
                        <div class="kb-card" onclick="window.openKB(${kb.id})">
                            <div class="kb-card-header">
                                <i class="bi bi-folder"></i>
                                <span class="kb-card-name">${escHtml(kb.name)}</span>
                            </div>
                            <div class="kb-card-desc">${escHtml(kb.description || '暂无描述')}</div>
                            <div class="kb-card-footer">
                                <span><i class="bi bi-file-text"></i> <span id="kbDocCount_${kb.id}">0</span> 个文档</span>
                                <span class="kb-card-acts">
                                    <i class="bi bi-pencil" onclick="event.stopPropagation(); window.editKB(${kb.id})" title="编辑"></i>
                                    <i class="bi bi-trash" onclick="event.stopPropagation(); window.confirmDeleteKB(${kb.id})" title="删除"></i>
                                </span>
                            </div>
                        </div>
                    </div>
                `).join('')}
            </div>
            `}
        </div>
    `;
    document.getElementById('dynamicContent').innerHTML = html;
    // Load doc counts async
    kbState.kbs.forEach(async kb => {
        const docs = await fetchDocs(kb.id);
        const el = document.getElementById('kbDocCount_' + kb.id);
        if (el) el.textContent = docs.length;
    });
}

// ========== 知识库详情页 ==========
async function renderKBDetail(kbId) {
    kbState.currentKbId = kbId;
    const kb = await fetchKB(kbId);
    if (!kb) { renderKnowledgeBaseList(); return; }
    kbState.docs = await fetchDocs(kbId);

    const html = `
        <div>
            <div class="d-flex align-items-center mb-4">
                <button class="btn btn-outline-secondary me-3" onclick="renderKnowledgeBaseList()">
                    <i class="bi bi-arrow-left"></i>
                </button>
                <div class="flex-grow-1">
                    <h5 class="mb-0"><i class="bi bi-folder"></i> ${escHtml(kb.name)}</h5>
                    ${kb.description ? '<small class="text-muted">' + escHtml(kb.description) + '</small>' : ''}
                </div>
                <button class="btn btn-outline-danger btn-sm" onclick="window.confirmDeleteKB(${kb.id})">
                    <i class="bi bi-trash"></i> 删除
                </button>
            </div>

            <!-- Upload -->
            <div class="dashboard-card mb-4">
                <h6 class="mb-3"><i class="bi bi-upload"></i> 上传文档</h6>
                <div class="upload-area" id="uploadArea"
                     ondrop="window.handleDrop(event)" ondragover="event.preventDefault()"
                     onclick="document.getElementById('fileInput').click()">
                    <i class="bi bi-cloud-arrow-up" style="font-size: 2rem; color: #9ca3af;"></i>
                    <p class="mb-0 text-muted">点击或拖拽文件到此处上传</p>
                    <small class="text-muted">支持 TXT, MD, CSV, JSON, XML 格式</small>
                    <input type="file" id="fileInput" style="display:none"
                           onchange="window.uploadDocFile(event)" accept=".txt,.md,.csv,.json,.xml,.yml,.yaml,.log,.properties">
                </div>
            </div>

            <!-- Document list -->
            <h6 class="mb-3"><i class="bi bi-file-text"></i> 文档列表 (${kbState.docs.length})</h6>
            ${kbState.docs.length === 0 ? `
                <div class="text-center py-4 text-muted">
                    <p>暂无文档，请上传</p>
                </div>
            ` : `
            <div class="table-responsive">
                <table class="table table-hover">
                    <thead>
                        <tr>
                            <th>文件名</th>
                            <th>类型</th>
                            <th>字符数</th>
                            <th>上传时间</th>
                            <th>操作</th>
                        </tr>
                    </thead>
                    <tbody>
                        ${kbState.docs.map(doc => `
                            <tr>
                                <td><i class="bi bi-file-earmark-text me-2"></i>${escHtml(doc.fileName)}</td>
                                <td><span class="badge bg-secondary">${doc.fileType || '-'}</span></td>
                                <td>${doc.charCount || 0}</td>
                                <td>${fmtTime(doc.uploadedAt)}</td>
                                <td>
                                    <button class="btn btn-sm btn-outline-primary" onclick="window.previewDoc(${doc.id})" title="预览">
                                        <i class="bi bi-eye"></i>
                                    </button>
                                    <button class="btn btn-sm btn-outline-danger" onclick="window.confirmDeleteDoc(${kb.id}, ${doc.id})" title="删除">
                                        <i class="bi bi-trash"></i>
                                    </button>
                                </td>
                            </tr>
                        `).join('')}
                    </tbody>
                </table>
            </div>
            `}
        </div>

        <!-- Preview Modal -->
        <div class="modal fade" id="previewModal" tabindex="-1">
            <div class="modal-dialog modal-lg modal-dialog-scrollable">
                <div class="modal-content">
                    <div class="modal-header">
                        <h6 class="modal-title" id="previewTitle">文档预览</h6>
                        <button type="button" class="btn-close" data-bs-dismiss="modal"></button>
                    </div>
                    <div class="modal-body">
                        <pre id="previewContent" style="white-space: pre-wrap; word-break: break-word; max-height: 70vh; font-size: 0.875rem;"></pre>
                    </div>
                </div>
            </div>
        </div>
    `;
    document.getElementById('dynamicContent').innerHTML = html;
}

// ========== 搜索 ==========
window.searchAllKBs = async function() {
    const input = document.getElementById('kbSearch');
    const q = input ? input.value.trim() : '';
    if (!q) return;

    const results = await searchKBs(q);
    const container = document.getElementById('kbSearchResults');
    if (!container) return;

    if (results.length === 0) {
        container.className = 'mb-4';
        container.innerHTML = '<div class="text-muted py-3">未找到匹配的文档</div>';
        return;
    }

    container.className = 'mb-4';
    container.innerHTML = `
        <div class="dashboard-card">
            <h6 class="mb-3">搜索结果: "${escHtml(q)}" (${results.length} 条)</h6>
            ${results.slice(0, 10).map(doc => {
                const snippet = doc.content ? doc.content.substring(0, 200) : '';
                return `
                    <div class="search-result-item mb-2 p-2 border-bottom" style="cursor:pointer;"
                         onclick="window.openKB(${doc.knowledgeBaseId})">
                        <div class="fw-bold small">${escHtml(doc.fileName)}</div>
                        <div class="text-muted small">${escHtml(snippet)}${snippet.length >= 200 ? '...' : ''}</div>
                    </div>
                `;
            }).join('')}
            ${results.length > 10 ? '<p class="text-muted small mt-2">...还有 ' + (results.length - 10) + ' 条结果</p>' : ''}
        </div>
    `;
};

// ========== 知识库操作 ==========
window.showCreateKB = function() {
    const name = prompt('请输入知识库名称：');
    if (!name || !name.trim()) return;
    const desc = prompt('请输入知识库描述（可选）：') || '';
    createKB({ name: name.trim(), description: desc.trim() }).then(kb => {
        if (kb) renderKnowledgeBaseList();
    });
};

window.editKB = async function(id) {
    const kb = await fetchKB(id);
    if (!kb) return;
    const name = prompt('请输入新名称：', kb.name);
    if (!name || !name.trim()) return;
    const desc = prompt('请输入新描述：', kb.description || '') || '';
    updateKB(id, { name: name.trim(), description: desc.trim() }).then(() => {
        renderKnowledgeBaseList();
    });
};

window.confirmDeleteKB = function(id) {
    if (confirm('确定要删除此知识库及其所有文档吗？此操作不可恢复。')) {
        deleteKB(id).then(ok => {
            if (ok) renderKnowledgeBaseList();
        });
    }
};

window.openKB = function(id) {
    renderKBDetail(id);
};

// ========== 文档操作 ==========
window.uploadDocFile = async function(event) {
    const file = event.target.files[0];
    if (!file) return;
    await uploadDoc(kbState.currentKbId, file);
    renderKBDetail(kbState.currentKbId);
    event.target.value = '';
};

window.handleDrop = async function(event) {
    event.preventDefault();
    const file = event.dataTransfer.files[0];
    if (!file) return;
    await uploadDoc(kbState.currentKbId, file);
    renderKBDetail(kbState.currentKbId);
};

window.confirmDeleteDoc = async function(kbId, docId) {
    if (confirm('确定要删除此文档吗？')) {
        const ok = await deleteDoc(kbId, docId);
        if (ok) renderKBDetail(kbId);
    }
};

window.previewDoc = async function(docId) {
    const doc = kbState.docs.find(d => d.id === docId);
    if (!doc) return;
    document.getElementById('previewTitle').textContent = doc.fileName;
    document.getElementById('previewContent').textContent = doc.content || '(空内容)';
    const modal = new bootstrap.Modal(document.getElementById('previewModal'));
    modal.show();
};

// ========== 工具函数（复用 chat.js 中的） ==========
function escHtml(s) { if (!s) return ''; const d = document.createElement('div'); d.textContent = s; return d.innerHTML; }
function fmtTime(d) {
    if (!d) return '';
    const t = new Date(d), n = new Date();
    const sameDay = t.toDateString() === n.toDateString();
    const yesterday = new Date(n); yesterday.setDate(yesterday.getDate() - 1);
    const isYesterday = t.toDateString() === yesterday.toDateString();
    if (sameDay) return String(t.getHours()).padStart(2,'0') + ':' + String(t.getMinutes()).padStart(2,'0');
    if (isYesterday) return '昨天';
    if (t.getFullYear() === n.getFullYear()) return (t.getMonth()+1) + '/' + t.getDate();
    return t.getFullYear() + '/' + (t.getMonth()+1) + '/' + t.getDate();
}
