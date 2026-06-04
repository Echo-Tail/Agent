package cafe.snails.ecomagents.service;

import cafe.snails.ecomagents.model.Session;
import cafe.snails.ecomagents.model.SessionMessage;
import cafe.snails.ecomagents.repository.SessionRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * HarnessAgent JSONL 会话 ↔ DB 会话元数据的映射服务。
 * <p>
 * 消息内容存储在 HarnessAgent 的 JSONL 文件中，DB 仅保存会话标题、创建时间等元数据，
 * 供前端列表/文件夹展示。
 * </p>
 */
@Service
@RequiredArgsConstructor
public class SessionMapper {

    /** 当前服务日志记录器。 */
    private static final Logger log = LoggerFactory.getLogger(SessionMapper.class);

    /** DB 会话仓库。 */
    private final SessionRepository sessionRepository;

    /**
     * 获取或创建 DB 会话记录，返回 HarnessAgent sessionId（UUID 格式）。
     * <p>
     * 如果 dbSessionId 不为 null，则根据已有 DB 记录获取关联的 harnessSessionId；
     * 否则新建 DB 记录并生成新的 harnessSessionId。
     * </p>
     *
     * @param dbSessionId 可选，已有 DB 会话 ID
     * @param agentId     Agent ID
     * @param userId      用户 ID
     * @return harnessSessionId (格式: "sess-{agentId}-{userId}-{uuid}")
     */
    @Transactional
    public String resolveHarnessSessionId(Long dbSessionId, Long agentId, Long userId) {
        if (dbSessionId != null) {
            Session existing = sessionRepository.findById(dbSessionId).orElse(null);
            if (existing != null && (!userId.equals(existing.getUserId()) || !agentId.equals(existing.getAgentId()))) {
                throw new IllegalArgumentException("Session not found or not accessible");
            }
            if (existing != null && existing.getHarnessSessionId() != null) {
                return existing.getHarnessSessionId();
            }
            if (existing != null) {
                // DB 记录存在但还没有 harnessSessionId（旧数据迁移）
                String harnessId = generateHarnessSessionId(agentId, userId);
                existing.setHarnessSessionId(harnessId);
                sessionRepository.save(existing);
                return harnessId;
            }
        }

        // 创建新会话
        String harnessId = generateHarnessSessionId(agentId, userId);
        Session session = Session.builder()
                .agentId(agentId)
                .userId(userId)
                .title("新对话")
                .harnessSessionId(harnessId)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
        sessionRepository.save(session);
        log.info("Created session {} for agent {}, user {}", harnessId, agentId, userId);
        return harnessId;
    }

    /**
     * 同步会话元数据到 DB（在每次 chat 完成后调用）。
     * 如果会话标题仍为默认值，自动用第一条用户消息生成标题预览。
     */
    @Transactional
    public void syncSessionMetadata(Long agentId, String harnessSessionId,
                                     Long userId, String userMessage, String replyText) {
        Session session = sessionRepository.findByHarnessSessionId(harnessSessionId).orElse(null);
        if (session == null) {
            log.warn("Session not found for sync: {}", harnessSessionId);
            return;
        }

        session.setUpdatedAt(LocalDateTime.now());

        // 标题自动生成：首次回复时用用户消息的前 20 个字作为标题
        if ("新对话".equals(session.getTitle()) && userMessage != null && !userMessage.isBlank()) {
            String title = userMessage.trim();
            if (title.length() > 30) title = title.substring(0, 30);
            session.setTitle(title);
        }

        sessionRepository.save(session);
    }

    /**
     * 向 DB 会话中追加一条消息（同步写入 session_messages 表）。
     */
    @Transactional
    public void saveMessage(String harnessSessionId, String role, String content) {
        saveMessage(harnessSessionId, role, content, null, null);
    }

    /**
     * 向 DB 会话中追加一条消息，可附带文件元数据。
     */
    @Transactional
    public void saveMessage(String harnessSessionId, String role, String content,
                            Long fileId, String fileName) {
        sessionRepository.findByHarnessSessionId(harnessSessionId).ifPresent(session -> {
            session.getMessages().add(SessionMessage.builder()
                    .role(role)
                    .content(content)
                    .timestamp(LocalDateTime.now())
                    .fileId(fileId)
                    .fileName(fileName)
                    .build());
            sessionRepository.save(session);
        });
    }

    /**
     * 删除 DB 会话记录。
     */
    @Transactional
    public void deleteSession(Long dbSessionId) {
        sessionRepository.findById(dbSessionId).ifPresent(session -> {
            sessionRepository.delete(session);
            log.debug("Deleted session {}", dbSessionId);
        });
    }

    /**
     * 生成 HarnessAgent JSONL 会话 ID，包含 Agent、用户和随机 UUID 以避免冲突。
     */
    private static String generateHarnessSessionId(Long agentId, Long userId) {
        return "sess-" + agentId + "-" + userId + "-" + UUID.randomUUID().toString().replace("-", "");
    }
}
