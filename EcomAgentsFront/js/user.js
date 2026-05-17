// js/user.js - 用户管理

// 获取用户列表
async function fetchUsers(page = 1, pageSize = PaginationConfig.DEFAULT_PAGE_SIZE) {
    const result = await get(`/users?page=${page}&size=${pageSize}`);
    if (result.success) {
        return result.data;
    }
    showToast(result.message, 'error');
    return [];
}

// 获取用户统计
async function fetchUserStats() {
    const result = await get('/users/stats');
    if (result.success) {
        return result.data;
    }
    return {
        total: 0,
        active: 0,
        disabled: 0,
        admin: 0
    };
}

// 切换用户状态
async function toggleUserStatus(userId) {
    const result = await post(`/users/${userId}/toggle`);
    if (result.success) {
        showToast(result.message, 'success');
        return { success: true, data: result.data };
    }
    
    // 使用错误码判断
    if (result.errorCode === ErrorCode.CANNOT_DISABLE_ADMIN.code) {
        showToast('无法禁用管理员账号', 'warning');
    } else {
        showToast(result.message, 'error');
    }
    return { success: false };
}

// 获取邀请码列表
async function fetchInviteCodes() {
    const result = await get('/invite-codes');
    if (result.success) {
        return result.data;
    }
    return [];
}

// 批量生成邀请码
async function batchGenerateInviteCodes(count, prefix = '') {
    const result = await post('/invite-codes/batch', { count, prefix });
    if (result.success) {
        showToast(`成功生成${count}个邀请码`, 'success');
        return result.data;
    }
    showToast(result.message, 'error');
    return [];
}

// 渲染用户管理页面
async function renderUserManage() {
    if (!isAdmin()) {
        window.loadDashboard();
        return;
    }
    
    const users = await fetchUsers();
    const stats = await fetchUserStats();
    const currentUser = getCurrentUser();
    
    const html = `
        <div class="dashboard-card">
            <div class="d-flex justify-content-between align-items-center mb-4">
                <h5 class="mb-0"><i class="bi bi-people"></i> 用户管理</h5>
                <div>
                    <button class="btn btn-outline-primary me-2" onclick="window.showInviteCodeManager()">
                        <i class="bi bi-qr-code"></i> 邀请码管理
                    </button>
                    <button class="btn btn-primary" onclick="window.showBatchGenerateModal()">
                        <i class="bi bi-plus-lg"></i> 批量生成邀请码
                    </button>
                </div>
            </div>
            
            <!-- 统计卡片 -->
            <div class="row g-4 mb-4">
                <div class="col-md-3">
                    <div class="stat-card">
                        <div><div class="stat-value">${stats.total}</div><div class="stat-label">总用户数</div></div>
                        <div class="stat-icon"><i class="bi bi-people"></i></div>
                    </div>
                </div>
                <div class="col-md-3">
                    <div class="stat-card">
                        <div><div class="stat-value">${stats.active}</div><div class="stat-label">活跃用户</div></div>
                        <div class="stat-icon"><i class="bi bi-check-circle"></i></div>
                    </div>
                </div>
                <div class="col-md-3">
                    <div class="stat-card">
                        <div><div class="stat-value">${stats.disabled}</div><div class="stat-label">已禁用</div></div>
                        <div class="stat-icon"><i class="bi bi-x-circle"></i></div>
                    </div>
                </div>
                <div class="col-md-3">
                    <div class="stat-card">
                        <div><div class="stat-value">${stats.admin}</div><div class="stat-label">管理员</div></div>
                        <div class="stat-icon"><i class="bi bi-shield-check"></i></div>
                    </div>
                </div>
            </div>
            
            <div class="table-responsive">
                <table class="user-table table">
                    <thead><tr><th>ID</th><th>用户名</th><th>邮箱</th><th>角色</th><th>状态</th><th>注册时间</th><th>邀请码</th><th>操作</th></tr></thead>
                    <tbody>
                        ${users.map(user => `
                            <tr>
                                <td>${user.id}</td>
                                <td>${user.username}${user.role === UserRole.ADMIN ? ' <span class="badge bg-warning">管理员</span>' : ''}</td>
                                <td>${user.email}</td>
                                <td>${UserRole.getLabel(user.role)}</td>
                                <td><span class="user-status ${UserStatus.getBadgeClass(user.status)}">${UserStatus.getLabel(user.status)}</span></td>
                                <td>${user.createdAt}</td>
                                <td><code>${user.inviteCode}</code></td>
                                <td>
                                    ${user.id !== currentUser?.id ? 
                                        `<button class="btn btn-sm ${user.status === UserStatus.ACTIVE ? 'btn-outline-danger' : 'btn-outline-success'}" onclick="window.handleToggleUser(${user.id})">
                                            <i class="bi ${user.status === UserStatus.ACTIVE ? 'bi-ban' : 'bi-check-circle'}"></i> ${user.status === UserStatus.ACTIVE ? '禁用' : '启用'}
                                        </button>` : 
                                        '<span class="text-muted">当前用户</span>'
                                    }
                                </td>
                            </tr>
                        `).join('')}
                    </tbody>
                </table>
            </div>
        </div>
    `;
    
    document.getElementById('dynamicContent').innerHTML = html;
}

// 处理用户状态切换
window.handleToggleUser = async function(userId) {
    const result = await toggleUserStatus(userId);
    if (result.success) {
        await renderUserManage();
    }
};

// ========== 邀请码管理 ==========

// 删除邀请码（从API删除并刷新列表）
async function removeInviteCode(code) {
    const result = await del(`/invite-codes/${code}`);
    if (result.success) {
        showToast('邀请码已删除', 'success');
        return true;
    }
    showToast(result.message, 'error');
    return false;
}

window.showInviteCodeManager = async function() {
    const codes = await fetchInviteCodes();
    const usedCodes = codes.filter(c => c.used);
    const unusedCodes = codes.filter(c => !c.used);

    const modalHtml = `
        <div class="modal fade" id="inviteCodeModal" tabindex="-1">
            <div class="modal-dialog modal-lg">
                <div class="modal-content">
                    <div class="modal-header">
                        <h5 class="modal-title"><i class="bi bi-qr-code"></i> 邀请码管理</h5>
                        <button type="button" class="btn-close" data-bs-dismiss="modal"></button>
                    </div>
                    <div class="modal-body">
                        <ul class="nav nav-tabs" id="inviteTab" role="tablist">
                            <li class="nav-item"><button class="nav-link active" data-bs-toggle="tab" data-bs-target="#unused">未使用 (${unusedCodes.length})</button></li>
                            <li class="nav-item"><button class="nav-link" data-bs-toggle="tab" data-bs-target="#used">已使用 (${usedCodes.length})</button></li>
                        </ul>
                        <div class="tab-content mt-3">
                            <div class="tab-pane fade show active" id="unused">
                                ${unusedCodes.length === 0 ? '<p class="text-muted text-center py-3">暂无未使用的邀请码</p>' :
                                    unusedCodes.map(c => `
                                        <div class="invite-code-card">
                                            <code class="invite-code-text">${c.code}</code>
                                            <button class="btn btn-sm btn-outline-danger" onclick="window.deleteInviteCode('${c.code}')">
                                                <i class="bi bi-trash"></i> 删除
                                            </button>
                                        </div>
                                    `).join('')}
                            </div>
                            <div class="tab-pane fade" id="used">
                                ${usedCodes.map(c => `
                                    <div class="invite-code-card">
                                        <div><code>${c.code}</code><small class="text-muted ms-2">使用人: ${c.usedBy || '-'}</small></div>
                                    </div>
                                `).join('')}
                            </div>
                        </div>
                    </div>
                    <div class="modal-footer">
                        <button type="button" class="btn btn-secondary" data-bs-dismiss="modal">关闭</button>
                    </div>
                </div>
            </div>
        </div>
    `;

    const existing = document.getElementById('inviteCodeModal');
    if (existing) existing.remove();
    document.body.insertAdjacentHTML('beforeend', modalHtml);
    new bootstrap.Modal(document.getElementById('inviteCodeModal')).show();
};

window.deleteInviteCode = async function(code) {
    const ok = await removeInviteCode(code);
    if (ok) {
        const m = bootstrap.Modal.getInstance(document.getElementById('inviteCodeModal'));
        if (m) m.hide();
        window.showInviteCodeManager();
    }
};

window.showBatchGenerateModal = function() {
    const modalHtml = `
        <div class="modal fade" id="batchGenerateModal" tabindex="-1">
            <div class="modal-dialog">
                <div class="modal-content">
                    <div class="modal-header">
                        <h5 class="modal-title"><i class="bi bi-plus-circle"></i> 批量生成邀请码</h5>
                        <button type="button" class="btn-close" data-bs-dismiss="modal"></button>
                    </div>
                    <div class="modal-body">
                        <div class="mb-3">
                            <label class="form-label">生成数量</label>
                            <input type="number" id="generateCount" class="form-control" min="1" max="100" value="10">
                        </div>
                        <div class="mb-3">
                            <label class="form-label">邀请码前缀（可选）</label>
                            <input type="text" id="codePrefix" class="form-control" placeholder="例如: EC">
                        </div>
                        <div class="batch-generate-area" id="generatedCodesArea" style="display: none;">
                            <label class="form-label fw-bold">生成的邀请码：</label>
                            <div id="generatedCodesList"></div>
                            <button class="btn btn-sm btn-success mt-2" onclick="window.copyAllCodes()">
                                <i class="bi bi-clipboard"></i> 复制全部
                            </button>
                        </div>
                    </div>
                    <div class="modal-footer">
                        <button type="button" class="btn btn-secondary" data-bs-dismiss="modal">取消</button>
                        <button type="button" class="btn btn-primary" onclick="window.handleGenerateCodes()">生成</button>
                    </div>
                </div>
            </div>
        </div>
    `;

    const existing = document.getElementById('batchGenerateModal');
    if (existing) existing.remove();
    document.body.insertAdjacentHTML('beforeend', modalHtml);
    new bootstrap.Modal(document.getElementById('batchGenerateModal')).show();
};

window.handleGenerateCodes = async function() {
    const count = parseInt(document.getElementById('generateCount').value) || 10;
    const prefix = document.getElementById('codePrefix').value || '';

    const newCodes = await batchGenerateInviteCodes(count, prefix);
    if (newCodes.length === 0) return;

    document.getElementById('generatedCodesArea').style.display = 'block';
    document.getElementById('generatedCodesList').innerHTML = newCodes.map(code => `
        <div class="invite-code-card">
            <code class="invite-code-text">${code.code}</code>
            <button class="btn btn-sm btn-outline-primary" onclick="navigator.clipboard.writeText('${code.code}').then(() => showToast('已复制', 'success'))">
                <i class="bi bi-copy"></i> 复制
            </button>
        </div>
    `).join('');
    reinitializeDropdowns();
};

window.copyAllCodes = function() {
    const codes = Array.from(document.querySelectorAll('#generatedCodesList .invite-code-text'))
        .map(el => el.textContent).join('\n');
    navigator.clipboard.writeText(codes);
    showToast('已复制全部邀请码', 'success');
};