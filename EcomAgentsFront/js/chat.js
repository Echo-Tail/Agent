// js/chat.js - 对话与历史会话管理

// ========== 状态 ==========
let chatState = {
    currentAgentId: null,
    currentSessionId: null,
    folders: [],
    sessions: [],
    agents: [],
    historyFilter: { folderId: null, search: '' },
    isStreaming: false,
    abortController: null
};

// ========== API层 ==========
async function fetchFolders() {
    const r = await get('/session-folders');
    return r.success ? (r.data || []) : [];
}
async function createFolder(name, parentId = null) {
    const r = await post('/session-folders', { name, parentId });
    if (r.success) { showToast('文件夹已创建', 'success'); return r.data; }
    showToast(r.message, 'error'); return null;
}
async function renameFolder(id, name) {
    const r = await put(`/session-folders/${id}`, { name });
    if (r.success) { showToast('文件夹已重命名', 'success'); return r.data; }
    showToast(r.message, 'error'); return null;
}
async function deleteFolderApi(id) {
    const r = await del(`/session-folders/${id}`);
    if (r.success) { showToast('文件夹已删除', 'success'); return true; }
    showToast(r.message, 'error'); return false;
}
async function fetchSessions(folderId, agentId) {
    let url = '/sessions';
    const p = [];
    if (folderId !== undefined && folderId !== null) p.push('folderId=' + folderId);
    if (agentId) p.push('agentId=' + agentId);
    if (p.length) url += '?' + p.join('&');
    const r = await get(url);
    return r.success ? (r.data || []) : [];
}
async function fetchAllSessions() {
    const r = await get('/sessions');
    return r.success ? (r.data || []) : [];
}
async function fetchSession(id) {
    const r = await get('/sessions/' + id);
    return r.success ? r.data : null;
}
async function createSession(agentId, folderId) {
    const agents = chatState.agents;
    const agent = agents.find(a => a.id === agentId);
    const title = agent ? '与 ' + agent.name + ' 的对话' : '新对话';
    const r = await post('/sessions', { agentId, folderId: folderId || null, title });
    if (r.success) return r.data;
    showToast(r.message, 'error'); return null;
}
async function updateSession(id, data) {
    const r = await put('/sessions/' + id, data);
    if (r.success) return r.data;
    showToast(r.message, 'error'); return null;
}
async function deleteSessionApi(id) {
    const r = await del('/sessions/' + id);
    if (r.success) { showToast('会话已删除', 'success'); return true; }
    showToast(r.message, 'error'); return false;
}
async function sendMsg(sessionId, role, content) {
    const r = await post('/sessions/' + sessionId + '/messages', { role, content });
    if (r.success) return r.data;
    showToast(r.message, 'error'); return null;
}

// ========== 对话页面 ==========
async function renderChat() {
    chatState.agents = await fetchAgents();
    chatState.currentSessionId = null;
    chatState.currentAgentId = null;

    // 处理从Agent卡片跳转过来的逻辑
    const pendingAgentId = window._pendingAgentId;
    window._pendingAgentId = null;
    if (pendingAgentId) {
        chatState.currentAgentId = pendingAgentId;
        // 如果有该Agent的已有会话，打开最新的
        const sessions = await fetchSessions(null, pendingAgentId);
        if (sessions.length > 0) {
            chatState.currentSessionId = sessions[0].id;
            renderChatInterface(sessions[0].id);
        } else {
            const session = await createSession(pendingAgentId);
            if (session) {
                chatState.currentSessionId = session.id;
                renderChatInterface(session.id);
            }
        }
        return;
    }

    showAgentSelector();
}

function showAgentSelector() {
    const html = `
        <div class="dashboard-card text-center py-5">
            <div style="font-size: 4rem; color: #d1d5db; margin-bottom: 16px;">
                <i class="bi bi-chat-dots"></i>
            </div>
            <h4 class="mb-3">选择一个Agent开始对话</h4>
            <p class="text-muted mb-4">请选择要对话的AI助手</p>
            <div class="d-flex justify-content-center gap-3 flex-wrap" style="max-width: 600px; margin: 0 auto;">
                ${(chatState.agents.filter(a => a.status === 'active') || []).map(a => `
                    <div class="agent-select-card" onclick="window.startChatWithAgent(${a.id})">
                        <div class="agent-select-card-icon"><i class="${a.icon || 'bi-robot'}"></i></div>
                        <div class="fw-bold mt-2">${a.name}</div>
                        <small class="text-muted">${a.description || ''}</small>
                    </div>
                `).join('')}
            </div>
        </div>
    `;
    document.getElementById('dynamicContent').innerHTML = html;
}

window.startChatWithAgent = async function(agentId) {
    chatState.currentAgentId = agentId;
    const session = await createSession(agentId);
    if (session) {
        chatState.currentSessionId = session.id;
        renderChatInterface(session.id);
    }
};

async function renderChatInterface(sessionId) {
    const session = await fetchSession(sessionId);
    if (!session) return showAgentSelector();

    const agent = chatState.agents.find(a => a.id === session.agentId) || {};
    const msgs = session.messages || [];
    const gretting = agent.greeting || '';

    const html = `
        <div class="chat-interface">
            <div class="chat-interface-header">
                <div class="d-flex align-items-center gap-3">
                    <div class="chat-agent-avatar-sm"><i class="${agent.icon || 'bi-robot'}"></i></div>
                    <div>
                        <div class="fw-bold">${agent.name || 'Agent'}</div>
                        <span class="user-status status-active" style="font-size:0.75rem;">在线</span>
                    </div>
                </div>
                <button class="btn btn-outline-primary btn-sm" onclick="window.startNewChat()">
                    <i class="bi bi-plus-lg"></i> 新建对话
                </button>
            </div>
            <div class="chat-interface-body" id="chatInterfaceBody">
                ${msgs.length === 0 && gretting ? `
                    <div class="text-center text-muted py-5">
                        <div style="font-size: 1.2rem; margin-bottom: 8px;">${gretting}</div>
                        <small>开始我们的对话吧！</small>
                    </div>
                ` : msgs.map(m => `
                    <div class="ci-msg ${m.role === 'user' ? 'ci-msg-right' : 'ci-msg-left'}">
                        ${m.role === 'assistant' ? `<div class="ci-avatar"><i class="${agent.icon || 'bi-robot'}"></i></div>` : ''}
                        <div class="ci-bubble ${m.role === 'user' ? 'ci-bubble-user' : 'ci-bubble-agent'}">
                            <div>${escHtml(m.content)}</div>
                            <div class="ci-time">${fmtTime(m.timestamp)}</div>
                        </div>
                    </div>
                `).join('')}
            </div>
            <div class="chat-interface-input">
                <div class="input-group">
                    <input type="text" class="form-control" id="chatInput" placeholder="输入你的问题..." onkeydown="if(event.key==='Enter') window.sendChatMsg()">
                    <button class="btn btn-danger d-none" id="stopStreamingBtn" onclick="window.stopStreaming()"><i class="bi bi-stop-fill"></i> 停止</button>
                    <button class="btn btn-primary" id="chatSendBtn" onclick="window.sendChatMsg()"><i class="bi bi-send"></i> 发送</button>
                </div>
            </div>
        </div>
    `;
    document.getElementById('dynamicContent').innerHTML = html;
    scrollToBottom('chatInterfaceBody');

    // 如果没有任何消息，发个欢迎消息
    if (msgs.length === 0 && gretting) {
        setTimeout(async () => {
            await sendMsg(sessionId, 'assistant', gretting);
            renderChatInterface(sessionId);
        }, 300);
    }
}

window.startNewChat = function() {
    renderChat();
};

// ========== 流式对话 ==========

window.sendChatMsg = async function() {
    const input = document.getElementById('chatInput');
    const sendBtn = document.getElementById('chatSendBtn');
    if (!input || !input.value.trim()) return;
    if (chatState.isStreaming) return;

    const content = input.value.trim();
    input.value = '';
    const sessionId = chatState.currentSessionId;
    const agentId = chatState.currentAgentId;
    if (!sessionId || !agentId) return;

    // Save user message and re-render
    await sendMsg(sessionId, 'user', content);
    await renderChatInterface(sessionId);

    // Disable input during streaming
    chatState.isStreaming = true;
    if (input) input.disabled = true;
    if (sendBtn) { sendBtn.classList.add('d-none'); }
    const stopBtn = document.getElementById('stopStreamingBtn');
    if (stopBtn) { stopBtn.classList.remove('d-none'); }

    // Add streaming placeholder bubble
    const body = document.getElementById('chatInterfaceBody');
    if (body) {
        const agent = chatState.agents.find(a => a.id === agentId) || {};
        const placeholder = document.createElement('div');
        placeholder.id = 'streamingMsg';
        placeholder.className = 'ci-msg ci-msg-left';
        placeholder.innerHTML = `
            <div class="ci-avatar"><i class="${agent.icon || 'bi-robot'}"></i></div>
            <div class="ci-bubble ci-bubble-agent ci-streaming">
                <span id="streamingContent"></span>
            </div>
        `;
        body.appendChild(placeholder);
        scrollToBottom('chatInterfaceBody');
    }

    // Start streaming
    chatState.abortController = new AbortController();

    streamChat(agentId, sessionId, content,
        // onToken
        (token, fullText) => {
            const el = document.getElementById('streamingContent');
            if (el) {
                el.textContent = fullText;
                scrollToBottom('chatInterfaceBody');
            }
        },
        // onDone
        async (fullText) => {
            chatState.isStreaming = false;
            chatState.abortController = null;
            if (fullText && fullText.trim()) {
                await sendMsg(sessionId, 'assistant', fullText);
            }
            await renderChatInterface(sessionId);
            enableChatInput();
        },
        // onError
        async (err) => {
            chatState.isStreaming = false;
            chatState.abortController = null;
            showToast('AI回复中断: ' + err.message, 'error');
            enableChatInput();
            // Keep partial message visible
            const streamingEl = document.getElementById('streamingMsg');
            if (streamingEl) {
                streamingEl.classList.add('text-muted');
                streamingEl.style.opacity = '0.6';
            }
        },
        chatState.abortController ? chatState.abortController.signal : undefined
    );
};

function enableChatInput() {
    const input = document.getElementById('chatInput');
    const sendBtn = document.getElementById('chatSendBtn');
    const stopBtn = document.getElementById('stopStreamingBtn');
    if (input) input.disabled = false;
    if (sendBtn) { sendBtn.classList.remove('d-none'); sendBtn.disabled = false; }
    if (stopBtn) { stopBtn.classList.add('d-none'); }
    if (input) input.focus();
}

window.stopStreaming = function() {
    if (chatState.abortController) {
        chatState.abortController.abort();
        chatState.abortController = null;
    }
    chatState.isStreaming = false;
    enableChatInput();
    const streamingEl = document.getElementById('streamingMsg');
    if (streamingEl) {
        streamingEl.style.borderLeft = '3px solid #ef4444';
        streamingEl.style.opacity = '0.6';
    }
};

// ========== 历史会话页面 ==========
let histState = { folderId: null, search: '', selectedSessionId: null };

async function renderHistoryPage() {
    const [folders, sessions] = await Promise.all([fetchFolders(), fetchAllSessions()]);
    chatState.folders = folders;
    chatState.sessions = sessions;
    histState.selectedSessionId = null;

    const html = `
        <div class="hist-layout">
            <div class="hist-sidebar">
                <div class="hist-sidebar-header">
                    <h6 class="mb-0"><i class="bi bi-clock-history"></i> 历史会话</h6>
                    <button class="btn btn-sm btn-outline-primary" onclick="window.histCreateFolder()" title="新建文件夹">
                        <i class="bi bi-folder-plus"></i>
                    </button>
                </div>
                <div class="hist-folder-list">
                    <div class="hist-folder-item ${histState.folderId === null && histState.search === '' ? 'active' : ''}" onclick="window.histFilterByFolder(null)">
                        <i class="bi bi-inbox"></i> 所有会话
                        <span class="hist-badge">${sessions.length}</span>
                    </div>
                    <div class="hist-folder-item ${histState.folderId === 'uncategorized' ? 'active' : ''}" onclick="window.histFilterByFolder('uncategorized')">
                        <i class="bi bi-dispatch"></i> 未归档
                        <span class="hist-badge">${sessions.filter(s => !s.folderId).length}</span>
                    </div>
                    <div class="hist-folder-divider"></div>
                    ${folders.filter(f => !f.parentId).map(f => {
                        const childFolders = folders.filter(c => c.parentId === f.id);
                        const folderSessionCount = sessions.filter(s => s.folderId === f.id || childFolders.some(c => c.id === s.folderId)).length;
                        return `
                            <div class="hist-folder-group">
                                <div class="hist-folder-item ${histState.folderId === f.id ? 'active' : ''}" onclick="window.histFilterByFolder(${f.id})">
                                    <i class="bi bi-folder"></i> ${f.name}
                                    <span class="hist-badge">${folderSessionCount}</span>
                                    <span class="hist-folder-acts">
                                        <i class="bi bi-pencil" onclick="event.stopPropagation(); window.histRenameFolder(${f.id}, '${escHtml(f.name)}')" title="重命名"></i>
                                        <i class="bi bi-trash" onclick="event.stopPropagation(); window.histDeleteFolder(${f.id}, '${escHtml(f.name)}')" title="删除"></i>
                                    </span>
                                </div>
                                ${childFolders.map(cf => {
                                    const cfCount = sessions.filter(s => s.folderId === cf.id).length;
                                    return `
                                        <div class="hist-folder-item hist-folder-child ${histState.folderId === cf.id ? 'active' : ''}" onclick="window.histFilterByFolder(${cf.id})">
                                            <i class="bi bi-folder"></i> ${cf.name}
                                            <span class="hist-badge">${cfCount}</span>
                                            <span class="hist-folder-acts">
                                                <i class="bi bi-pencil" onclick="event.stopPropagation(); window.histRenameFolder(${cf.id}, '${escHtml(cf.name)}')" title="重命名"></i>
                                                <i class="bi bi-trash" onclick="event.stopPropagation(); window.histDeleteFolder(${cf.id}, '${escHtml(cf.name)}')" title="删除"></i>
                                            </span>
                                        </div>
                                    `;
                                }).join('')}
                            </div>
                        `;
                    }).join('')}
                </div>
            </div>
            <div class="hist-main">
                <div class="hist-toolbar">
                    <div class="input-group" style="max-width: 320px;">
                        <span class="input-group-text"><i class="bi bi-search"></i></span>
                        <input type="text" class="form-control" id="histSearch" placeholder="搜索会话..." value="${escHtml(histState.search)}" oninput="window.histSearch()">
                    </div>
                    ${histState.folderId && histState.folderId !== 'uncategorized' ? `
                        <button class="btn btn-sm btn-outline-secondary" onclick="window.histMoveToFolder(null)">清空筛选</button>
                    ` : ''}
                </div>
                <div class="hist-session-list" id="histSessionList">
                    ${renderHistSessions()}
                </div>
            </div>
            <div class="hist-preview" id="histPreview">
                <div class="hist-preview-empty">
                    <i class="bi bi-chat-square-text"></i>
                    <p class="text-muted mt-2">选择一个会话查看详情</p>
                </div>
            </div>
        </div>
    `;
    document.getElementById('dynamicContent').innerHTML = html;
    updateNavFolders();
}

function renderHistSessions() {
    let sessions = chatState.sessions;
    if (histState.folderId === 'uncategorized') {
        sessions = sessions.filter(s => !s.folderId);
    } else if (histState.folderId !== null) {
        const folderIds = [histState.folderId];
        chatState.folders.filter(f => f.parentId === histState.folderId).forEach(cf => folderIds.push(cf.id));
        sessions = sessions.filter(s => folderIds.includes(s.folderId));
    }
    if (histState.search) {
        const q = histState.search.toLowerCase();
        sessions = sessions.filter(s => s.title.toLowerCase().includes(q));
    }

    sessions.sort((a, b) => new Date(b.updatedAt || b.createdAt) - new Date(a.updatedAt || a.createdAt));

    if (sessions.length === 0) {
        return '<div class="text-center text-muted py-5">暂无会话</div>';
    }

    return sessions.map(s => {
        const agent = chatState.agents.find(a => a.id === s.agentId);
        const isActive = histState.selectedSessionId === s.id;
        const lastMsg = s.lastMessage;
        return `
            <div class="hist-session-card ${isActive ? 'active' : ''}" onclick="window.histSelectSession(${s.id})">
                <div class="d-flex justify-content-between align-items-start">
                    <div class="hist-session-title">${escHtml(s.title)}</div>
                    <div class="hist-session-acts">
                        <button class="btn btn-sm btn-outline-danger py-0 px-1" onclick="event.stopPropagation(); window.histDeleteSession(${s.id})" title="删除">
                            <i class="bi bi-trash"></i>
                        </button>
                    </div>
                </div>
                <div class="hist-session-meta">
                    <span><i class="${agent ? (agent.icon || 'bi-robot') : 'bi-robot'}"></i> ${agent ? agent.name : '未知'}</span>
                    <span>${s.messageCount || 0} 条消息</span>
                    <span>${fmtTime(s.updatedAt || s.createdAt)}</span>
                </div>
                <div class="hist-session-preview">${lastMsg ? escHtml(lastMsg.content.substring(0, 80)) + (lastMsg.content.length > 80 ? '...' : '') : '暂无消息'}</div>
            </div>
        `;
    }).join('');
}

// 预览会话
window.histSelectSession = async function(id) {
    histState.selectedSessionId = id;
    refreshHistList();

    const session = await fetchSession(id);
    if (!session) return;
    const agent = chatState.agents.find(a => a.id === session.agentId) || {};
    const msgs = session.messages || [];

    const preview = document.getElementById('histPreview');
    if (!preview) return;

    preview.innerHTML = `
        <div class="hist-preview-header">
            <div class="d-flex align-items-center gap-3">
                <div class="chat-agent-avatar-sm"><i class="${agent.icon || 'bi-robot'}"></i></div>
                <div>
                    <div class="fw-bold">${escHtml(session.title)}</div>
                    <small class="text-muted"><i class="${agent.icon || 'bi-robot'}"></i> ${agent.name || 'Agent'} · ${msgs.length} 条消息</small>
                </div>
            </div>
            <button class="btn btn-primary btn-sm" onclick="window.histContinueSession(${session.id})">
                <i class="bi bi-chat-dots"></i> 继续对话
            </button>
        </div>
        <div class="hist-preview-body">
            ${msgs.map(m => `
                <div class="hp-msg ${m.role === 'user' ? 'hp-msg-right' : 'hp-msg-left'}">
                    ${m.role === 'assistant' ? `<div class="hp-avatar"><i class="${agent.icon || 'bi-robot'}"></i></div>` : ''}
                    <div class="hp-bubble ${m.role === 'user' ? 'hp-bubble-user' : 'hp-bubble-agent'}">
                        <div>${escHtml(m.content)}</div>
                        <div class="hp-time">${fmtTime(m.timestamp)}</div>
                    </div>
                </div>
            `).join('')}
        </div>
    `;
};

// 继续对话
window.histContinueSession = function(id) {
    chatState.currentSessionId = id;
    const session = chatState.sessions.find(s => s.id === id);
    if (session) chatState.currentAgentId = session.agentId;
    renderChatInterface(id);
};

// ========== 文件夹操作 ==========
window.histCreateFolder = async function() {
    const name = prompt('请输入文件夹名称：');
    if (!name || !name.trim()) return;
    const f = await createFolder(name.trim());
    if (f) renderHistoryPage();
};

window.histRenameFolder = async function(id, currentName) {
    const name = prompt('请输入新名称：', currentName);
    if (!name || !name.trim() || name.trim() === currentName) return;
    const f = await renameFolder(id, name.trim());
    if (f) renderHistoryPage();
};

window.histDeleteFolder = async function(id, name) {
    const sessions = chatState.sessions.filter(s => s.folderId === id);
    let deleteSessions = false;
    if (sessions.length > 0) {
        deleteSessions = confirm('文件夹 "' + name + '" 内有 ' + sessions.length + ' 个会话。\n点击"确定"同时删除所有会话，\n点击"取消"保留会话（将会变为未归档）。');
    }
    const ok = await deleteFolderApi(id);
    if (!ok) return;
    if (deleteSessions) {
        for (const s of sessions) {
            await deleteSessionApi(s.id);
        }
    } else {
        for (const s of sessions) {
            await updateSession(s.id, { folderId: null });
        }
    }
    renderHistoryPage();
};

// ========== 会话操作 ==========
window.histFilterByFolder = function(folderId) {
    histState.folderId = folderId;
    histState.selectedSessionId = null;
    refreshHistList();
    const preview = document.getElementById('histPreview');
    if (preview) {
        preview.innerHTML = '<div class="hist-preview-empty"><i class="bi bi-chat-square-text"></i><p class="text-muted mt-2">选择一个会话查看详情</p></div>';
    }
};

window.histSearch = function() {
    const input = document.getElementById('histSearch');
    if (input) histState.search = input.value;
    histState.selectedSessionId = null;
    refreshHistList();
};

window.histDeleteSession = async function(id) {
    if (!confirm('确定要删除此会话吗？')) return;
    await deleteSessionApi(id);
    chatState.sessions = chatState.sessions.filter(s => s.id !== id);
    if (histState.selectedSessionId === id) {
        histState.selectedSessionId = null;
        const preview = document.getElementById('histPreview');
        if (preview) {
            preview.innerHTML = '<div class="hist-preview-empty"><i class="bi bi-chat-square-text"></i><p class="text-muted mt-2">选择一个会话查看详情</p></div>';
        }
    }
    refreshHistList();
};

function refreshHistList() {
    const el = document.getElementById('histSessionList');
    if (el) el.innerHTML = renderHistSessions();
    // 更新文件夹高亮
    document.querySelectorAll('.hist-folder-item').forEach(item => item.classList.remove('active'));
    const activeFolderEl = document.querySelector(`.hist-folder-item[onclick*="${histState.folderId === null ? 'null' : histState.folderId}"]`);
    if (activeFolderEl) activeFolderEl.classList.add('active');
}

// ========== 侧边栏 ==========
async function updateNavFolders() {
    const submenu = document.getElementById('historySubmenu');
    if (!submenu) return;
    const r = await get('/session-folders');
    if (!r.success) return;
    const folders = r.data || [];
    const sessions = await fetchAllSessions();
    const rootFolders = folders.filter(f => !f.parentId);

    // 默认展开
    const arrow = document.getElementById('historyArrow');
    if (arrow) arrow.style.transform = 'rotate(0deg)';

    submenu.innerHTML = `
        <div class="nav-sub-item" onclick="navigateTo('history')">
            <i class="bi bi-inbox"></i> 全部 <span class="nav-sub-badge">${sessions.length}</span>
        </div>
        ${rootFolders.map(f => {
            const count = sessions.filter(s => s.folderId === f.id || folders.some(c => c.parentId === f.id && c.id === s.folderId)).length;
            return `
                <div class="nav-sub-item" onclick="navigateTo('history'); setTimeout(() => window.histFilterByFolder && window.histFilterByFolder(${f.id}), 50)">
                    <i class="bi bi-folder"></i> ${f.name} <span class="nav-sub-badge">${count}</span>
                </div>
            `;
        }).join('')}
        <div class="nav-sub-item" onclick="navigateTo('history'); setTimeout(() => window.histFilterByFolder && window.histFilterByFolder('uncategorized'), 50)">
            <i class="bi bi-dispatch"></i> 未归档 <span class="nav-sub-badge">${sessions.filter(s => !s.folderId).length}</span>
        </div>
    `;
}

// ========== 工具函数 ==========
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
function scrollToBottom(id) { const el = document.getElementById(id); if (el) setTimeout(() => el.scrollTop = el.scrollHeight, 50); }
