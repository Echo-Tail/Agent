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

@Service
@RequiredArgsConstructor
public class TicketService {

    private static final long MAX_ATTACHMENT_TOTAL_SIZE = 20L * 1024 * 1024;
    private static final DateTimeFormatter NUMBER_DATE = DateTimeFormatter.ofPattern("yyyyMMdd");
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

    @Transactional(readOnly = true)
    public ApiResponse<List<TicketResponse>> listMyTickets(
            Long userId, TicketStatus status, AffectedMenu affectedMenu, TicketPriority priority, String title) {
        Sort sort = Sort.by(Sort.Direction.DESC, "createdAt");
        return ApiResponse.success(ticketRepository.findAll(filter(userId, status, affectedMenu, priority, title), sort)
                .stream().map(this::toResponse).toList());
    }

    @Transactional(readOnly = true)
    public ApiResponse<List<TicketResponse>> listAdminTickets(
            TicketStatus status, AffectedMenu affectedMenu, TicketPriority priority, String title, Long submitterId) {
        Comparator<Ticket> adminSort = Comparator
                .comparingInt((Ticket ticket) -> priorityRank(ticket.getPriority()))
                .thenComparing(Ticket::getCreatedAt, Comparator.reverseOrder());
        return ApiResponse.success(ticketRepository.findAll(filter(submitterId, status, affectedMenu, priority, title))
                .stream().sorted(adminSort).map(this::toResponse).toList());
    }

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

    @Transactional(readOnly = true)
    public ApiResponse<TicketResponse> getAdminTicket(Long id) {
        return ticketRepository.findById(id)
                .map(ticket -> ApiResponse.success(toResponse(ticket)))
                .orElse(ApiResponse.error(404, "工单不存在"));
    }

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

    private int priorityRank(TicketPriority priority) {
        return switch (priority) {
            case HIGH -> 0;
            case MEDIUM -> 1;
            case LOW -> 2;
        };
    }

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

    private ApiResponse<Void> validateAttachmentTotal(List<FileRecord> files) {
        long total = files.stream().mapToLong(file -> file.getFileSize() == null ? 0 : file.getFileSize()).sum();
        if (total > MAX_ATTACHMENT_TOTAL_SIZE) {
            return ApiResponse.error(400, "附件总大小不能超过20MB");
        }
        return ApiResponse.success(null);
    }

    private ApiResponse<Void> validateSubmitterVisibleMenu(AffectedMenu affectedMenu) {
        if (!isAdmin() && !SUBMITTER_VISIBLE_MENUS.contains(affectedMenu)) {
            return ApiResponse.error(400, "普通用户的受影响菜单只能选择用户可见菜单");
        }
        return ApiResponse.success(null);
    }

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

    private synchronized String nextTicketNumber() {
        String prefix = "TK-" + LocalDate.now().format(NUMBER_DATE) + "-";
        int next = ticketRepository.findTopByTicketNumberStartingWithOrderByTicketNumberDesc(prefix)
                .map(ticket -> Integer.parseInt(ticket.getTicketNumber().substring(prefix.length())) + 1)
                .orElse(1);
        return prefix + String.format("%04d", next);
    }

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

    private String username(Long userId) {
        if (userId == null) {
            return null;
        }
        return userRepository.findById(userId).map(User::getUsername).orElse("用户#" + userId);
    }

    private boolean isAdmin() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null && auth.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
    }
}
