package cafe.snails.ecomagents.service;

import cafe.snails.ecomagents.dto.ApiResponse;
import cafe.snails.ecomagents.dto.SessionSummary;
import cafe.snails.ecomagents.model.Session;
import cafe.snails.ecomagents.model.SessionMessage;
import cafe.snails.ecomagents.repository.SessionFolderRepository;
import cafe.snails.ecomagents.repository.SessionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 会话业务逻辑，支持按文件夹 / Agent 筛选、消息增删等。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SessionService {

    private final SessionRepository sessionRepository;
    private final SessionFolderRepository folderRepository;

    /**
     * 查询非空会话列表，可按 folderId 或 agentId 筛选。
     * 无消息的会话（空壳会话）会被自动过滤。
     * 始终按 userId 做用户隔离。
     */
    @Transactional(readOnly = true)
    public ApiResponse<List<SessionSummary>> listSessions(Long userId, Long folderId, Long agentId) {
        List<Session> sessions;
        if (folderId != null) {
            sessions = sessionRepository.findNonEmptyByFolderIdAndUserId(folderId, userId);
        } else if (agentId != null) {
            sessions = sessionRepository.findNonEmptyByAgentIdAndUserId(agentId, userId);
        } else {
            sessions = sessionRepository.findNonEmptyByUserId(userId);
        }

        List<SessionSummary> summaries = sessions.stream()
                .map(this::toSummary)
                .sorted(Comparator.comparing(SessionSummary::getUpdatedAt).reversed())
                .collect(Collectors.toList());
        return ApiResponse.success(summaries);
    }

    /** 获取单个会话详情（含完整消息列表） */
    public ApiResponse<Session> getSession(Long id) {
        return sessionRepository.findById(id)
                .map(session -> ApiResponse.success(session))
                .orElse(ApiResponse.error(404, "会话不存在"));
    }

    /** 获取单个会话详情并预加载 messages（避免 SSE 后台线程中的 LazyInitializationException） */
    public ApiResponse<Session> getSessionWithMessages(Long id, Long userId) {
        return sessionRepository.findByIdAndUserIdWithMessages(id, userId)
                .map(session -> ApiResponse.success(session))
                .orElse(ApiResponse.error(404, "会话不存在"));
    }

    /** 创建新会话 */
    @Transactional
    public ApiResponse<Session> createSession(Long agentId, String title, Long folderId, Long userId) {
        Session session = Session.builder()
                .agentId(agentId)
                .userId(userId)
                .title(title != null ? title : "新对话")
                .folderId(folderId)
                .messages(new ArrayList<>())
                .tags(new ArrayList<>())
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
        Session saved = sessionRepository.save(session);
        return ApiResponse.success("会话创建成功", saved);
    }

    /** 更新会话标题和/或所属文件夹 */
    @Transactional
    public ApiResponse<Session> updateSession(Long id, String title, Long folderId, Long userId) {
        return sessionRepository.findById(id)
                .filter(session -> userId.equals(session.getUserId()))
                .map(session -> {
                    if (folderId != null && !folderRepository.existsByIdAndUserId(folderId, userId)) {
                        return ApiResponse.<Session>error(404, "文件夹不存在");
                    }
                    if (title != null) session.setTitle(title);
                    if (folderId != null) session.setFolderId(folderId);
                    session.setUpdatedAt(LocalDateTime.now());
                    Session saved = sessionRepository.save(session);
                    return ApiResponse.success("会话已更新", saved);
                })
                .orElse(ApiResponse.error(404, "会话不存在"));
    }

    /** 删除会话 */
    @Transactional
    public ApiResponse<Void> deleteSession(Long id, Long userId) {
        return sessionRepository.findById(id)
                .filter(session -> userId.equals(session.getUserId()))
                .map(session -> {
                    sessionRepository.delete(session);
                    return ApiResponse.<Void>success("会话已删除", null);
                })
                .orElse(ApiResponse.error(404, "会话不存在"));
    }

    /** 向指定会话中添加一条消息 */
    @Transactional
    public ApiResponse<SessionMessage> addMessage(Long sessionId, String role, String content, Long userId) {
        return sessionRepository.findById(sessionId)
                .filter(session -> userId.equals(session.getUserId()))
                .map(session -> {
                    SessionMessage msg = SessionMessage.builder()
                            .role(role)
                            .content(content)
                            .timestamp(LocalDateTime.now())
                            .build();
                    session.getMessages().add(msg);
                    session.setUpdatedAt(LocalDateTime.now());
                    sessionRepository.save(session);
                    return ApiResponse.success("消息已发送", msg);
                })
                .orElse(ApiResponse.error(404, "会话不存在"));
    }

    /**
     * 清理所有无消息的空壳会话，返回删除数量。
     */
    @Transactional
    public int cleanupEmptySessions() {
        int deleted = sessionRepository.deleteAllEmpty();
        if (deleted > 0) {
            log.info("已清理 {} 个空会话", deleted);
        }
        return deleted;
    }

    /** 将 Session 实体转换为 SessionSummary DTO */
    private SessionSummary toSummary(Session session) {
        SessionSummary s = new SessionSummary();
        s.setId(session.getId());
        s.setAgentId(session.getAgentId());
        s.setTitle(session.getTitle());
        s.setFolderId(session.getFolderId());
        s.setTags(session.getTags());
        s.setCreatedAt(session.getCreatedAt());
        s.setUpdatedAt(session.getUpdatedAt());

        List<SessionMessage> msgs = session.getMessages();
        s.setMessageCount(msgs != null ? msgs.size() : 0);

        if (msgs != null && !msgs.isEmpty()) {
            SessionMessage last = msgs.get(msgs.size() - 1);
            SessionSummary.SessionMessageDTO dto = new SessionSummary.SessionMessageDTO();
            dto.setRole(last.getRole());
            dto.setContent(last.getContent());
            dto.setTimestamp(last.getTimestamp());
            s.setLastMessage(dto);
        }
        return s;
    }
}
