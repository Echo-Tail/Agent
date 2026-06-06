package cafe.snails.ecomagents.service;

import cafe.snails.ecomagents.dto.ApiResponse;
import cafe.snails.ecomagents.model.*;
import cafe.snails.ecomagents.repository.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 群消息业务逻辑。
 */
@Service
@RequiredArgsConstructor
public class GroupMessageService {

    /** 当前服务日志记录器。 */
    private static final Logger log = LoggerFactory.getLogger(GroupMessageService.class);
    /** 群消息中 @Agent 的 Markdown 语法匹配规则：@[名称](agent:id)。 */
    private static final Pattern AGENT_MENTION_PATTERN = Pattern.compile("@\\[([^]]+)]\\(agent:(\\d+)\\)");

    private final GroupMessageRepository groupMessageRepository;
    private final GroupMemberRepository groupMemberRepository;
    private final GroupAgentRepository groupAgentRepository;
    private final SseService sseService;
    private final GroupService groupService;
    private final HarnessChatService harnessChatService;
    private final ObjectMapper objectMapper;

    /** 共享线程池，用于异步触发 Agent 回复，避免每次 new Thread()。 */
    private final ExecutorService agentReplyExecutor = Executors.newCachedThreadPool();

    /**
     * 发送群消息。
     * <p>如果是用户发送的消息且包含 @Agent，则异步触发 Agent 回复。</p>
     * <p>注意：未使用 @Transactional，因为 SSE 广播在保存之后执行，
     * 广播失败不应回滚已保存的消息。Spring Data JPA 的 save() 自带事务保护。</p>
     */
    public ApiResponse<GroupMessage> sendMessage(Long groupId, Long senderId, String content) {
        if (!groupService.isMember(groupId, senderId)) {
            return ApiResponse.error(403, "你不是群成员，无法发送消息");
        }
        if (content == null || content.isBlank()) {
            return ApiResponse.error(400, "消息内容不能为空");
        }

        GroupMessage msg = GroupMessage.builder()
                .groupId(groupId)
                .senderId(senderId)
                .senderType(SenderType.USER)
                .content(content.trim())
                .build();
        msg = groupMessageRepository.save(msg);

        // 通过 SSE 广播新消息
        GroupMessage finalMsg = msg;
        sseService.broadcast(groupId, "message", Map.of(
                "id", finalMsg.getId(),
                "senderId", finalMsg.getSenderId(),
                "senderType", "USER",
                "content", finalMsg.getContent(),
                "createdAt", finalMsg.getCreatedAt().toString()
        ));
        // 广播群聊未读事件
        sseService.broadcast(groupId, "unread_group", Map.of(
                "groupId", groupId,
                "senderId", senderId
        ));

        // 检测 @Agent 并异步触发回复
        triggerAgentReplies(groupId, msg);

        return ApiResponse.success("消息已发送", msg);
    }

    /**
     * 解析消息中的 @[名称](agent:id) 并异步触发 Agent 回复。
     */
    private void triggerAgentReplies(Long groupId, GroupMessage userMsg) {
        Matcher matcher = AGENT_MENTION_PATTERN.matcher(userMsg.getContent());
        while (matcher.find()) {
            Long agentId = Long.parseLong(matcher.group(2));
            String agentName = matcher.group(1);

            // 检查 Agent 是否在群中
            if (!groupAgentRepository.existsByGroupIdAndAgentId(groupId, agentId)) {
                log.warn("Agent {} not in group {}, skip", agentId, groupId);
                continue;
            }

            // 异步触发 Agent 回复（使用共享线程池）
            Long finalAgentId = agentId;
            Long msgId = userMsg.getId();
            agentReplyExecutor.submit(() -> {
                try {
                    triggerAgentReply(groupId, finalAgentId, agentName, userMsg.getContent(), msgId);
                } catch (Exception e) {
                    log.error("Agent reply failed: agentId={}, groupId={}", finalAgentId, groupId, e);
                }
            });
        }
    }

    /**
     * 关闭 Agent 回复线程池。
     */
    @PreDestroy
    public void shutdownExecutor() {
        agentReplyExecutor.shutdown();
    }

    /**
     * 执行 Agent 推理并通过群 SSE 广播回复。
     */
    private void triggerAgentReply(Long groupId, Long agentId, String agentName, String userContent, Long replyToMsgId) {
        try {
            // 提取去除 @ 标记后的用户真实消息
            String cleanContent = AGENT_MENTION_PATTERN.matcher(userContent).replaceAll("").trim();

            // 调用 HarnessChatService 获取 Agent 回复
            // 使用 existingSessionId=null 让 HarnessChatService 自动创建临时会话
            String replyText = harnessChatService.simpleChat(agentId, cleanContent);

            if (replyText == null || replyText.isBlank()) return;

            // 保存 Agent 回复消息
            GroupMessage reply = GroupMessage.builder()
                    .groupId(groupId)
                    .senderId(agentId)
                    .senderType(SenderType.AGENT)
                    .content(replyText)
                    .replyToMsgId(replyToMsgId)
                    .build();
            reply = groupMessageRepository.save(reply);

            // 通过 SSE 广播 Agent 回复
            GroupMessage finalReply = reply;
            sseService.broadcast(groupId, "message", Map.of(
                    "id", finalReply.getId(),
                    "senderId", finalReply.getSenderId(),
                    "senderType", "AGENT",
                    "agentName", agentName,
                    "content", finalReply.getContent(),
                    "replyToMsgId", finalReply.getReplyToMsgId(),
                    "createdAt", finalReply.getCreatedAt().toString()
            ));
        } catch (Exception e) {
            log.error("Agent reply error: agentId={}", agentId, e);
            sseService.broadcast(groupId, "error", Map.of(
                    "message", "Agent 「" + agentName + "」回复失败: " + e.getMessage()
            ));
        }
    }

    /**
     * 获取群消息历史（分页，按时间倒序）。
     */
    public ApiResponse<java.util.List<GroupMessage>> listMessages(Long groupId, int page, int size) {
        var messages = groupMessageRepository.findByGroupIdOrderByCreatedAtDesc(groupId, PageRequest.of(page, size));
        return ApiResponse.success(messages);
    }
}
