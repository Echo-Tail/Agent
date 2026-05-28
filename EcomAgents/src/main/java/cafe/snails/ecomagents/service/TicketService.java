package cafe.snails.ecomagents.service;

import cafe.snails.ecomagents.dto.*;
import cafe.snails.ecomagents.model.*;
import cafe.snails.ecomagents.repository.*;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 工单（Ticket）业务逻辑服务。
 * <p>管理用户提交的反馈/问题工单，包含完整生命周期：
 * <ul>
 *   <li>{@link TicketStatus#PENDING PENDING} → {@link TicketStatus#IN_PROGRESS IN_PROGRESS} → {@link TicketStatus#COMPLETED COMPLETED}</li>
 *   <li>支持附件管理（FileRecord）、变更记录审计、工单编号自动生成</li>
 *   <li>普通用户仅管理自己的工单；管理员可查看和操作所有工单</li>
 * </ul>
 * </p>
 */
@Service
@RequiredArgsConstructor
public class TicketService {

    /** 附件总大小上限：20MB */
    private static final long MAX_ATTACHMENT_TOTAL_SIZE = 20L * 1024 * 1024;
    /** 工单编号日期格式化：yyyyMMdd */
    private static final DateTimeFormatter NUMBER_DATE = DateTimeFormatter.ofPattern("yyyyMMdd");
    /** 普通用户可选的"受影响菜单"范围 */
    private static final Set<AffectedMenu> SUBMITTER_VISIBLE_MENUS = Set.of(
            AffectedMenu.DASHBOARD,
            AffectedMenu.CHAT,
            AffectedMenu.AGENT_LIST,
            AffectedMenu.HISTORY,
            AffectedMenu.MY_TICKETS,
            AffectedMenu.KNOWLEDGE_BASE,
            AffectedMenu.SETTINGS,
            AffectedMenu.OTHER
    );

    private final TicketRepository ticketRepository;
    private final TicketAttachmentRepository ticketAttachmentRepository;
    private final TicketChangeRecordRepository changeRecordRepository;
    private final FileRecordRepository fileRecordRepository;
    private final UserRepository userRepository;

    /**
     * 获取当前用户的工单列表，支持多条件筛选。
     *
     * @param userId       当前用户 ID
     * @param status       工单状态（可选）
     * @param affectedMenu 受影响菜单（可选）
     * @param priority     优先级（可选）
     * @param title        标题关键词模糊搜索（可选）
     * @return 按创建时间降序排列的工单列表
     */
    @Transactional(readOnly = true)
    public ApiResponse<List<TicketResponse>> listMyTickets(
            Long userId, TicketStatus status, AffectedMenu affectedMenu, TicketPriority priority, String title) {
        Sort sort = Sort.by(Sort.Direction.DESC, "createdAt");
        return ApiResponse.success(ticketRepository.findAll(filter(userId, status, affectedMenu, priority, title), sort)
                .stream().map(this::toResponse).toList());
    }

    /**
     * 管理员获取所有工单列表，支持多条件筛选。
     * <p>结果按优先级（HIGH > MEDIUM > LOW）和创建时间降序排列，
     * 高优先级的工单排在前面。</p>
     *
     * @param status       工单状态（可选）
     * @param affectedMenu 受影响菜单（可选）
     * @param priority     优先级（可选）
     * @param title        标题关键词模糊搜索（可选）
     * @param submitterId  提交者用户 ID（可选）
     * @return 按优先级+时间排序的工单列表
     */
    @Transactional(readOnly = true)
    public ApiResponse<List<TicketResponse>> listAdminTickets(
            TicketStatus status, AffectedMenu affectedMenu, TicketPriority priority, String title, Long submitterId) {
        Comparator<Ticket> adminSort = Comparator
                .comparingInt((Ticket ticket) -> priorityRank(ticket.getPriority()))
                .thenComparing(Ticket::getCreatedAt, Comparator.reverseOrder());
        return ApiResponse.success(ticketRepository.findAll(filter(submitterId, status, affectedMenu, priority, title))
                .stream().sorted(adminSort).map(this::toResponse).toList());
    }

    /**
     * 获取当前用户的指定工单详情。
     * <p>权限检查：工单的提交者必须是当前用户。</p>
     *
     * @param id     工单 ID
     * @param userId 当前用户 ID
     * @return 工单响应；无权限返回 403，不存在返回 404
     */
    @Transactional(readOnly = true)
    public ApiResponse<TicketResponse> getMyTicket(Long id, Long userId) {
        return ticketRepository.findById(id)
                .map(ticket -> {
                    if (!ticket.getSubmitterId().equals(userId)) {
                        return ApiResponse.<TicketResponse>error(403, "没有权限查看该工单");
                    }
                    return ApiResponse.success(toResponse(ticket));
                })
                .orElse(ApiResponse.error(404, "工单不存在"));
    }

    /**
     * 管理员获取任意工单详情。
     *
     * @param id 工单 ID
     * @return 工单响应；不存在返回 404
     */
    @Transactional(readOnly = true)
    public ApiResponse<TicketResponse> getAdminTicket(Long id) {
        return ticketRepository.findById(id)
                .map(ticket -> ApiResponse.success(toResponse(ticket)))
                .orElse(ApiResponse.error(404, "工单不存在"));
    }

    /**
     * 创建工单。
     * <p>执行流程：
     * <ol>
     *   <li>校验 affectedMenu 是否对当前用户可见</li>
     *   <li>加载附件文件记录并校验归属</li>
     *   <li>校验附件总大小不超过 20MB</li>
     *   <li>生成工单编号（TK-yyyyMMdd-XXXX）</li>
     *   <li>保存工单记录（状态为 PENDING）</li>
     *   <li>建立附件关联</li>
     * </ol>
     * </p>
     *
     * @param request 创建请求（标题、内容、受影响菜单、优先级、附件 ID 列表）
     * @param userId  提交者用户 ID
     * @return 创建后的工单响应；校验失败返回对应错误码
     */
    @Transactional
    public ApiResponse<TicketResponse> createTicket(TicketCreateRequest request, Long userId) {
        ApiResponse<Void> menuCheck = validateSubmitterVisibleMenu(request.getAffectedMenu());
        if (menuCheck.getCode() != 200) {
            return ApiResponse.error(menuCheck.getCode(), menuCheck.getMessage());
        }

        List<FileRecord> files;
        try {
            files = loadFiles(request.getAttachmentIds(), userId);
        } catch (IllegalArgumentException e) {
            return ApiResponse.error(400, e.getMessage());
        }
        ApiResponse<Void> attachmentCheck = validateAttachmentTotal(files);
        if (attachmentCheck.getCode() != 200) {
            return ApiResponse.error(attachmentCheck.getCode(), attachmentCheck.getMessage());
        }

        LocalDateTime now = LocalDateTime.now();
        Ticket ticket = Ticket.builder()
                .ticketNumber(nextTicketNumber())
                .title(request.getTitle().trim())
                .affectedMenu(request.getAffectedMenu())
                .priority(request.getPriority() == null ? TicketPriority.MEDIUM : request.getPriority())
                .content(request.getContent().trim())
                .status(TicketStatus.PENDING)
                .submitterId(userId)
                .createdAt(now)
                .updatedAt(now)
                .build();
        Ticket saved = ticketRepository.save(ticket);
        addAttachments(saved, files, userId, now, false);
        return ApiResponse.success("创建成功", toResponse(saved));
    }

    /**
     * 更新工单（标题/菜单/优先级/内容/附件）。
     * <p>仅工单提交者可以更新；已完成工单不能修改。
     * 更新时会记录变更到 {@link TicketChangeRecord}。</p>
     *
     * @param id      工单 ID
     * @param request 更新请求
     * @param userId  当前用户 ID
     * @return 更新后的工单响应
     */
    @Transactional
    public ApiResponse<TicketResponse> updateTicket(Long id, TicketUpdateRequest request, Long userId) {
        return ticketRepository.findById(id)
                .map(ticket -> {
                    if (!ticket.getSubmitterId().equals(userId)) {
                        return ApiResponse.<TicketResponse>error(403, "没有权限修改该工单");
                    }
                    if (ticket.getStatus() == TicketStatus.COMPLETED) {
                        return ApiResponse.<TicketResponse>error(400, "已完成工单不能修改");
                    }
                    ApiResponse<Void> menuCheck = validateSubmitterVisibleMenu(request.getAffectedMenu());
                    if (menuCheck.getCode() != 200) {
                        return ApiResponse.<TicketResponse>error(menuCheck.getCode(), menuCheck.getMessage());
                    }

                    List<FileRecord> files;
                    try {
                        files = loadFiles(request.getAttachmentIds(), userId);
                    } catch (IllegalArgumentException e) {
                        return ApiResponse.<TicketResponse>error(400, e.getMessage());
                    }
                    ApiResponse<Void> attachmentCheck = validateAttachmentTotal(files);
                    if (attachmentCheck.getCode() != 200) {
                        return ApiResponse.<TicketResponse>error(attachmentCheck.getCode(), attachmentCheck.getMessage());
                    }

                    LocalDateTime now = LocalDateTime.now();
                    recordChange(ticket, "title", ticket.getTitle(), request.getTitle().trim(), userId, now);
                    recordChange(ticket, "affectedMenu", ticket.getAffectedMenu().name(), request.getAffectedMenu().name(), userId, now);
                    recordChange(ticket, "priority", ticket.getPriority().name(), request.getPriority().name(), userId, now);
                    recordChange(ticket, "content", ticket.getContent(), request.getContent().trim(), userId, now);

                    ticket.setTitle(request.getTitle().trim());
                    ticket.setAffectedMenu(request.getAffectedMenu());
                    ticket.setPriority(request.getPriority());
                    ticket.setContent(request.getContent().trim());
                    ticket.setUpdatedAt(now);
                    syncAttachments(ticket, files, userId, now);
                    return ApiResponse.success("更新成功", toResponse(ticketRepository.save(ticket)));
                })
                .orElse(ApiResponse.error(404, "工单不存在"));
    }

    /**
     * 开始处理工单（管理员操作）。
     * <p>将工单状态从 PENDING 变更为 IN_PROGRESS，同时记录处理人 ID 和开始时间。</p>
     *
     * @param id        工单 ID
     * @param handlerId 处理人（管理员）用户 ID
     * @return 更新后的工单响应；非 PENDING 状态返回 400
     */
    @Transactional
    public ApiResponse<TicketResponse> startHandling(Long id, Long handlerId) {
        return ticketRepository.findById(id)
                .map(ticket -> {
                    if (ticket.getStatus() != TicketStatus.PENDING) {
                        return ApiResponse.<TicketResponse>error(400, "只有待处理工单可以开始处理");
                    }
                    LocalDateTime now = LocalDateTime.now();
                    ticket.setStatus(TicketStatus.IN_PROGRESS);
                    ticket.setHandlerId(handlerId);
                    ticket.setStartedAt(now);
                    ticket.setUpdatedAt(now);
                    recordChange(ticket, "status", TicketStatus.PENDING.name(), TicketStatus.IN_PROGRESS.name(), handlerId, now);
                    return ApiResponse.success("已开始处理", toResponse(ticketRepository.save(ticket)));
                })
                .orElse(ApiResponse.error(404, "工单不存在"));
    }

    /**
     * 完成工单处理（管理员操作）。
     * <p>将工单状态从 IN_PROGRESS 变更为 COMPLETED，记录处理备注和完成时间。
     * 若工单尚未指定处理人，自动设置为当前操作人。</p>
     *
     * @param id        工单 ID
     * @param request   处理完成请求（含处理备注）
     * @param handlerId 处理人用户 ID
     * @return 更新后的工单响应；非 IN_PROGRESS 状态返回 400
     */
    @Transactional
    public ApiResponse<TicketResponse> completeTicket(Long id, TicketHandleRequest request, Long handlerId) {
        return ticketRepository.findById(id)
                .map(ticket -> {
                    if (ticket.getStatus() != TicketStatus.IN_PROGRESS) {
                        return ApiResponse.<TicketResponse>error(400, "只有处理中工单可以完成");
                    }
                    LocalDateTime now = LocalDateTime.now();
                    String note = request.getHandlingNote().trim();
                    recordChange(ticket, "handlingNote", ticket.getHandlingNote(), note, handlerId, now);
                    recordChange(ticket, "status", TicketStatus.IN_PROGRESS.name(), TicketStatus.COMPLETED.name(), handlerId, now);
                    ticket.setHandlingNote(note);
                    ticket.setStatus(TicketStatus.COMPLETED);
                    ticket.setHandlerId(ticket.getHandlerId() == null ? handlerId : ticket.getHandlerId());
                    ticket.setCompletedAt(now);
                    ticket.setUpdatedAt(now);
                    return ApiResponse.success("处理完成", toResponse(ticketRepository.save(ticket)));
                })
                .orElse(ApiResponse.error(404, "工单不存在"));
    }

    /**
     * 获取工单的变更记录列表。
     * <p>权限检查：管理员可以查看所有工单的变更记录；普通用户只能查看自己工单的变更记录。</p>
     *
     * @param ticketId 工单 ID
     * @param userId   当前用户 ID
     * @return 按时间降序排列的变更记录列表
     */
    @Transactional(readOnly = true)
    public ApiResponse<List<TicketChangeRecordResponse>> listChangeRecords(Long ticketId, Long userId) {
        Optional<Ticket> ticketOpt = ticketRepository.findById(ticketId);
        if (ticketOpt.isEmpty()) {
            return ApiResponse.error(404, "工单不存在");
        }
        Ticket ticket = ticketOpt.get();
        if (!isAdmin() && !ticket.getSubmitterId().equals(userId)) {
            return ApiResponse.error(403, "没有权限查看该工单修改记录");
        }
        return ApiResponse.success(changeRecordRepository.findByTicketIdOrderByChangedAtDesc(ticketId)
                .stream().map(this::toChangeResponse).toList());
    }

    /**
     * 构建动态查询条件（Specification），支持按提交者/状态/菜单/优先级/标题模糊搜索。
     *
     * @param submitterId  提交者 ID（可选）
     * @param status       工单状态（可选）
     * @param affectedMenu 受影响菜单（可选）
     * @param priority     优先级（可选）
     * @param title        标题模糊搜索（可选）
     * @return JPA Specification 查询条件
     */
    private Specification<Ticket> filter(Long submitterId, TicketStatus status, AffectedMenu affectedMenu,
                                         TicketPriority priority, String title) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (submitterId != null) predicates.add(cb.equal(root.get("submitterId"), submitterId));
            if (status != null) predicates.add(cb.equal(root.get("status"), status));
            if (affectedMenu != null) predicates.add(cb.equal(root.get("affectedMenu"), affectedMenu));
            if (priority != null) predicates.add(cb.equal(root.get("priority"), priority));
            if (title != null && !title.isBlank()) {
                predicates.add(cb.like(cb.lower(root.get("title")), "%" + title.toLowerCase(Locale.ROOT).trim() + "%"));
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
    }

    /** 优先级排序权重（值越小优先级越高） */
    private int priorityRank(TicketPriority priority) {
        return switch (priority) {
            case HIGH -> 0;
            case MEDIUM -> 1;
            case LOW -> 2;
        };
    }

    /**
     * 加载附件文件记录，校验存在性和归属。
     *
     * @param attachmentIds 附件文件 ID 列表
     * @param userId        当前用户 ID
     * @return 文件记录列表
     * @throws IllegalArgumentException 文件不存在或不属于当前用户时抛出
     */
    private List<FileRecord> loadFiles(List<Long> attachmentIds, Long userId) {
        if (attachmentIds == null || attachmentIds.isEmpty()) {
            return List.of();
        }
        List<Long> distinctIds = attachmentIds.stream().filter(Objects::nonNull).distinct().toList();
        List<FileRecord> files = fileRecordRepository.findAllById(distinctIds);
        if (files.size() != distinctIds.size()) {
            throw new IllegalArgumentException("附件不存在");
        }
        if (files.stream().anyMatch(file -> !userId.equals(file.getUploadedBy()))) {
            throw new IllegalArgumentException("不能使用其他用户上传的附件");
        }
        return files;
    }

    /**
     * 校验附件总大小是否超过 20MB 上限。
     *
     * @param files 附件文件记录列表
     * @return 校验通过返回 success，否则返回 400
     */
    private ApiResponse<Void> validateAttachmentTotal(List<FileRecord> files) {
        long total = files.stream().mapToLong(file -> file.getFileSize() == null ? 0 : file.getFileSize()).sum();
        if (total > MAX_ATTACHMENT_TOTAL_SIZE) {
            return ApiResponse.error(400, "附件总大小不能超过20MB");
        }
        return ApiResponse.success(null);
    }

    /**
     * 校验普通用户选择的 "受影响菜单" 是否在允许范围内。
     *
     * @param affectedMenu 选择的菜单
     * @return 校验通过返回 success，否则返回 400
     */
    private ApiResponse<Void> validateSubmitterVisibleMenu(AffectedMenu affectedMenu) {
        if (!isAdmin() && !SUBMITTER_VISIBLE_MENUS.contains(affectedMenu)) {
            return ApiResponse.error(400, "普通用户的受影响菜单只能选择用户可见菜单");
        }
        return ApiResponse.success(null);
    }

    /**
     * 同步工单附件：移除不在新列表中的旧附件，添加不在旧列表中的新附件。
     *
     * @param ticket      工单实体
     * @param desiredFiles 期望的附件文件列表
     * @param userId      操作者用户 ID
     * @param now         当前时间
     */
    private void syncAttachments(Ticket ticket, List<FileRecord> desiredFiles, Long userId, LocalDateTime now) {
        Map<Long, TicketAttachment> current = ticketAttachmentRepository.findByTicketIdAndActiveTrue(ticket.getId())
                .stream().collect(Collectors.toMap(a -> a.getFileRecord().getId(), Function.identity()));
        Set<Long> desiredIds = desiredFiles.stream().map(FileRecord::getId).collect(Collectors.toSet());

        for (TicketAttachment attachment : current.values()) {
            if (!desiredIds.contains(attachment.getFileRecord().getId())) {
                attachment.setActive(false);
                attachment.setRemovedAt(now);
                attachment.setRemovedBy(userId);
                ticketAttachmentRepository.save(attachment);
                recordChange(ticket, "attachment", attachment.getFileRecord().getOriginalName(), null, userId, now);
            }
        }

        List<FileRecord> additions = desiredFiles.stream()
                .filter(file -> !current.containsKey(file.getId()))
                .toList();
        addAttachments(ticket, additions, userId, now, true);
    }

    /**
     * 为工单添加附件记录。
     *
     * @param ticket       工单实体
     * @param files        要添加的文件记录列表
     * @param userId       操作者用户 ID
     * @param now          当前时间
     * @param recordChange 是否记录变更到审计（true = 更新场景，false = 创建场景）
     */
    private void addAttachments(Ticket ticket, List<FileRecord> files, Long userId, LocalDateTime now, boolean recordChange) {
        for (FileRecord file : files) {
            ticketAttachmentRepository.save(TicketAttachment.builder()
                    .ticket(ticket)
                    .fileRecord(file)
                    .active(true)
                    .addedAt(now)
                    .addedBy(userId)
                    .build());
            if (recordChange) {
                recordChange(ticket, "attachment", null, file.getOriginalName(), userId, now);
            }
        }
    }

    /**
     * 记录字段变更到 {@link TicketChangeRecord}。
     * <p>若新旧值相同（Objects.equals）则不记录，避免无意义的空记录。</p>
     *
     * @param ticket    工单实体
     * @param field     变更字段名
     * @param oldValue  旧值
     * @param newValue  新值
     * @param changedBy 操作者用户 ID
     * @param changedAt 变更时间
     */
    private void recordChange(Ticket ticket, String field, String oldValue, String newValue, Long changedBy, LocalDateTime changedAt) {
        if (Objects.equals(oldValue, newValue)) {
            return;
        }
        changeRecordRepository.save(TicketChangeRecord.builder()
                .ticket(ticket)
                .fieldName(field)
                .oldValue(oldValue)
                .newValue(newValue)
                .changedBy(changedBy)
                .changedAt(changedAt)
                .build());
    }

    /**
     * 生成下一个工单编号。
     * <p>格式：{@code TK-yyyyMMdd-XXXX}，其中 XXXX 是当天内自增的 4 位序号。
     * 线程安全（synchronized）。</p>
     *
     * @return 工单编号
     */
    private synchronized String nextTicketNumber() {
        String prefix = "TK-" + LocalDate.now().format(NUMBER_DATE) + "-";
        int next = ticketRepository.findTopByTicketNumberStartingWithOrderByTicketNumberDesc(prefix)
                .map(ticket -> Integer.parseInt(ticket.getTicketNumber().substring(prefix.length())) + 1)
                .orElse(1);
        return prefix + String.format("%04d", next);
    }

    /** 将 Ticket 实体转换为 API 响应 DTO */
    private TicketResponse toResponse(Ticket ticket) {
        return TicketResponse.builder()
                .id(ticket.getId())
                .ticketNumber(ticket.getTicketNumber())
                .title(ticket.getTitle())
                .affectedMenu(ticket.getAffectedMenu())
                .priority(ticket.getPriority())
                .content(ticket.getContent())
                .status(ticket.getStatus())
                .submitterId(ticket.getSubmitterId())
                .submitterName(username(ticket.getSubmitterId()))
                .handlerId(ticket.getHandlerId())
                .handlerName(username(ticket.getHandlerId()))
                .handlingNote(ticket.getHandlingNote())
                .createdAt(ticket.getCreatedAt())
                .updatedAt(ticket.getUpdatedAt())
                .startedAt(ticket.getStartedAt())
                .completedAt(ticket.getCompletedAt())
                .attachments(ticketAttachmentRepository.findByTicketIdAndActiveTrue(ticket.getId())
                        .stream().map(TicketAttachment::getFileRecord).toList())
                .build();
    }

    /** 将 TicketChangeRecord 实体转换为 API 响应 DTO */
    private TicketChangeRecordResponse toChangeResponse(TicketChangeRecord record) {
        return TicketChangeRecordResponse.builder()
                .id(record.getId())
                .fieldName(record.getFieldName())
                .oldValue(record.getOldValue())
                .newValue(record.getNewValue())
                .changedBy(record.getChangedBy())
                .changedByName(username(record.getChangedBy()))
                .changedAt(record.getChangedAt())
                .build();
    }

    /**
     * 根据用户 ID 查询用户名。
     *
     * @param userId 用户 ID
     * @return 用户名；用户不存在时返回 "用户#{id}"；ID 为 null 时返回 null
     */
    private String username(Long userId) {
        if (userId == null) {
            return null;
        }
        return userRepository.findById(userId).map(User::getUsername).orElse("用户#" + userId);
    }

    /** 判断当前请求上下文是否为管理员 */
    private boolean isAdmin() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null && auth.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
    }
}
