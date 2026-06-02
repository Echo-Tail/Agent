package cafe.snails.ecomagents.service;

import cafe.snails.ecomagents.dto.*;
import cafe.snails.ecomagents.model.*;
import cafe.snails.ecomagents.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.GrantedAuthority;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * {@link TicketService} 单元测试，覆盖 CRUD / 状态流转 / 权限校验 / 附件管理。
 */
@ExtendWith(MockitoExtension.class)
class TicketServiceTest {
    private static final DateTimeFormatter NUMBER_DATE = DateTimeFormatter.ofPattern("yyyyMMdd");

    @Mock
    private TicketRepository ticketRepository;
    @Mock
    private TicketAttachmentRepository ticketAttachmentRepository;
    @Mock
    private TicketChangeRecordRepository changeRecordRepository;
    @Mock
    private FileRecordRepository fileRecordRepository;
    @Mock
    private UserRepository userRepository;

    private TicketService ticketService;

    private final Long userId = 1L;
    private final Long otherUserId = 2L;
    private final Long handlerId = 3L;
    private final Long ticketId = 100L;

    @BeforeEach
    void setUp() {
        ticketService = new TicketService(ticketRepository, ticketAttachmentRepository,
                changeRecordRepository, fileRecordRepository, userRepository);
        SecurityContextHolder.clearContext();
    }

    // ==================== createTicket ====================

    @Test
    void createTicket_shouldSucceed() {
        var request = new TicketCreateRequest();
        request.setTitle("测试标题");
        request.setContent("测试内容");
        request.setAffectedMenu(AffectedMenu.CHAT);
        request.setPriority(TicketPriority.HIGH);

        var savedTicket = Ticket.builder()
                .id(ticketId).ticketNumber("TK-20260528-0001")
                .title("测试标题").content("测试内容")
                .affectedMenu(AffectedMenu.CHAT).priority(TicketPriority.HIGH)
                .status(TicketStatus.PENDING).submitterId(userId)
                .createdAt(LocalDateTime.now()).updatedAt(LocalDateTime.now())
                .build();

        when(ticketRepository.findTopByTicketNumberStartingWithOrderByTicketNumberDesc(todayTicketPrefix()))
                .thenReturn(Optional.empty());
        when(ticketRepository.save(any())).thenReturn(savedTicket);

        var result = ticketService.createTicket(request, userId);

        assertEquals(200, result.getCode());
        assertEquals("测试标题", result.getData().getTitle());
    }

    @Test
    void createTicket_shouldRejectInvalidMenuForNonAdmin() {
        var request = new TicketCreateRequest();
        request.setTitle("测试");
        request.setContent("内容");
        request.setAffectedMenu(AffectedMenu.MODEL_MANAGE); // admin-only menu
        request.setPriority(TicketPriority.MEDIUM);

        var result = ticketService.createTicket(request, userId);

        assertEquals(400, result.getCode());
    }

    @Test
    void createTicket_shouldDefaultPriorityToMedium() {
        var request = new TicketCreateRequest();
        request.setTitle("测试标题");
        request.setContent("测试内容");
        request.setAffectedMenu(AffectedMenu.CHAT);
        request.setPriority(null);

        var savedTicket = Ticket.builder()
                .id(ticketId).ticketNumber("TK-20260528-0001")
                .title("测试标题")
                .priority(TicketPriority.MEDIUM)
                .status(TicketStatus.PENDING).submitterId(userId)
                .createdAt(LocalDateTime.now()).updatedAt(LocalDateTime.now())
                .build();

        when(ticketRepository.findTopByTicketNumberStartingWithOrderByTicketNumberDesc(anyString()))
                .thenReturn(Optional.empty());
        when(ticketRepository.save(any())).thenReturn(savedTicket);

        var result = ticketService.createTicket(request, userId);

        assertEquals(200, result.getCode());
        assertEquals(TicketPriority.MEDIUM, result.getData().getPriority());
    }

    @Test
    void createTicket_shouldRejectOversizedAttachments() {
        var request = new TicketCreateRequest();
        request.setTitle("测试");
        request.setContent("内容");
        request.setAffectedMenu(AffectedMenu.CHAT);
        request.setPriority(TicketPriority.LOW);
        request.setAttachmentIds(List.of(1L));

        var file = FileRecord.builder().id(1L).fileSize(21L * 1024 * 1024).uploadedBy(userId).build();
        when(fileRecordRepository.findAllById(List.of(1L))).thenReturn(List.of(file));

        var result = ticketService.createTicket(request, userId);

        assertEquals(400, result.getCode());
    }

    @Test
    void createTicket_shouldRejectOthersAttachments() {
        var request = new TicketCreateRequest();
        request.setTitle("测试");
        request.setContent("内容");
        request.setAffectedMenu(AffectedMenu.CHAT);
        request.setPriority(TicketPriority.LOW);
        request.setAttachmentIds(List.of(1L));

        var file = FileRecord.builder().id(1L).fileSize(100L).uploadedBy(otherUserId).build();
        when(fileRecordRepository.findAllById(List.of(1L))).thenReturn(List.of(file));

        var result = ticketService.createTicket(request, userId);

        assertEquals(400, result.getCode());
    }

    // ==================== listMyTickets ====================

    @Test
    void listMyTickets_shouldReturnUserTickets() {
        var ticket = Ticket.builder().id(ticketId).submitterId(userId).build();
        when(ticketRepository.findAll(any(org.springframework.data.jpa.domain.Specification.class), any(Sort.class))).thenReturn(List.of(ticket));

        var result = ticketService.listMyTickets(userId, null, null, null, null);

        assertEquals(200, result.getCode());
        assertEquals(1, result.getData().size());
    }

    @Test
    void listMyTickets_shouldFilterByStatus() {
        when(ticketRepository.findAll(any(org.springframework.data.jpa.domain.Specification.class), any(Sort.class))).thenReturn(List.of());

        var result = ticketService.listMyTickets(userId, TicketStatus.PENDING, null, null, null);

        assertEquals(200, result.getCode());
    }

    // ==================== getMyTicket ====================

    @Test
    void getMyTicket_shouldReturnTicket() {
        var ticket = Ticket.builder().id(ticketId).submitterId(userId).build();
        when(ticketRepository.findById(ticketId)).thenReturn(Optional.of(ticket));
        when(userRepository.findById(userId)).thenReturn(Optional.of(new User()));

        var result = ticketService.getMyTicket(ticketId, userId);

        assertEquals(200, result.getCode());
    }

    @Test
    void getMyTicket_shouldRejectOtherUser() {
        var ticket = Ticket.builder().id(ticketId).submitterId(otherUserId).build();
        when(ticketRepository.findById(ticketId)).thenReturn(Optional.of(ticket));

        var result = ticketService.getMyTicket(ticketId, userId);

        assertEquals(403, result.getCode());
    }

    @Test
    void getMyTicket_shouldReturn404() {
        when(ticketRepository.findById(999L)).thenReturn(Optional.empty());

        var result = ticketService.getMyTicket(999L, userId);

        assertEquals(404, result.getCode());
    }

    // ==================== startHandling ====================

    @Test
    void startHandling_shouldChangeToInProgress() {
        var ticket = Ticket.builder().id(ticketId).status(TicketStatus.PENDING).build();
        when(ticketRepository.findById(ticketId)).thenReturn(Optional.of(ticket));
        when(ticketRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        var result = ticketService.startHandling(ticketId, handlerId);

        assertEquals(200, result.getCode());
        assertEquals(TicketStatus.IN_PROGRESS, result.getData().getStatus());
        assertEquals(handlerId, result.getData().getHandlerId());
        verify(changeRecordRepository).save(any());
    }

    @Test
    void startHandling_shouldRejectNonPending() {
        var ticket = Ticket.builder().id(ticketId).status(TicketStatus.IN_PROGRESS).build();
        when(ticketRepository.findById(ticketId)).thenReturn(Optional.of(ticket));

        var result = ticketService.startHandling(ticketId, handlerId);

        assertEquals(400, result.getCode());
    }

    @Test
    void startHandling_shouldReturn404() {
        when(ticketRepository.findById(999L)).thenReturn(Optional.empty());

        var result = ticketService.startHandling(999L, handlerId);

        assertEquals(404, result.getCode());
    }

    // ==================== completeTicket ====================

    @Test
    void completeTicket_shouldChangeToCompleted() {
        var request = new TicketHandleRequest();
        request.setHandlingNote("已修复");
        var ticket = Ticket.builder().id(ticketId).status(TicketStatus.IN_PROGRESS).handlerId(handlerId).build();
        when(ticketRepository.findById(ticketId)).thenReturn(Optional.of(ticket));
        when(ticketRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        var result = ticketService.completeTicket(ticketId, request, handlerId);

        assertEquals(200, result.getCode());
        assertEquals(TicketStatus.COMPLETED, result.getData().getStatus());
        assertEquals("已修复", result.getData().getHandlingNote());
        verify(changeRecordRepository, times(2)).save(any());
    }

    @Test
    void completeTicket_shouldSetHandlerIfNull() {
        var request = new TicketHandleRequest();
        request.setHandlingNote("完成");
        var ticket = Ticket.builder().id(ticketId).status(TicketStatus.IN_PROGRESS).handlerId(null).build();
        when(ticketRepository.findById(ticketId)).thenReturn(Optional.of(ticket));
        when(ticketRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        var result = ticketService.completeTicket(ticketId, request, handlerId);

        assertEquals(200, result.getCode());
        assertEquals(handlerId, result.getData().getHandlerId());
    }

    @Test
    void completeTicket_shouldRejectNonInProgress() {
        var request = new TicketHandleRequest();
        var ticket = Ticket.builder().id(ticketId).status(TicketStatus.PENDING).build();
        when(ticketRepository.findById(ticketId)).thenReturn(Optional.of(ticket));

        var result = ticketService.completeTicket(ticketId, request, handlerId);

        assertEquals(400, result.getCode());
    }

    // ==================== updateTicket ====================

    @Test
    void updateTicket_shouldSucceed() {
        var request = new TicketUpdateRequest();
        request.setTitle("新标题");
        request.setContent("新内容");
        request.setAffectedMenu(AffectedMenu.SETTINGS);
        request.setPriority(TicketPriority.LOW);

        var ticket = Ticket.builder().id(ticketId).submitterId(userId).status(TicketStatus.PENDING)
                .title("旧标题").content("旧内容")
                .affectedMenu(AffectedMenu.CHAT).priority(TicketPriority.HIGH)
                .build();
        when(ticketRepository.findById(ticketId)).thenReturn(Optional.of(ticket));
        when(ticketRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        var result = ticketService.updateTicket(ticketId, request, userId);

        assertEquals(200, result.getCode());
        assertEquals("新标题", result.getData().getTitle());
        verify(changeRecordRepository, atLeast(1)).save(any());
    }

    @Test
    void updateTicket_shouldRejectOtherUser() {
        var request = new TicketUpdateRequest();
        request.setTitle("新标题");
        request.setContent("新内容");
        request.setAffectedMenu(AffectedMenu.CHAT);
        request.setPriority(TicketPriority.MEDIUM);

        var ticket = Ticket.builder().id(ticketId).submitterId(otherUserId).build();
        when(ticketRepository.findById(ticketId)).thenReturn(Optional.of(ticket));

        var result = ticketService.updateTicket(ticketId, request, userId);

        assertEquals(403, result.getCode());
    }

    @Test
    void updateTicket_shouldRejectCompleted() {
        var request = new TicketUpdateRequest();
        request.setTitle("新标题");
        request.setContent("新内容");
        request.setAffectedMenu(AffectedMenu.CHAT);
        request.setPriority(TicketPriority.MEDIUM);

        var ticket = Ticket.builder().id(ticketId).submitterId(userId).status(TicketStatus.COMPLETED).build();
        when(ticketRepository.findById(ticketId)).thenReturn(Optional.of(ticket));

        var result = ticketService.updateTicket(ticketId, request, userId);

        assertEquals(400, result.getCode());
    }

    // ==================== listChangeRecords ====================

    @Test
    void listChangeRecords_shouldReturnRecords() {
        var ticket = Ticket.builder().id(ticketId).submitterId(userId).build();
        when(ticketRepository.findById(ticketId)).thenReturn(Optional.of(ticket));
        when(changeRecordRepository.findByTicketIdOrderByChangedAtDesc(ticketId)).thenReturn(List.of());

        var result = ticketService.listChangeRecords(ticketId, userId);

        assertEquals(200, result.getCode());
    }

    @Test
    void listChangeRecords_shouldReturn404() {
        when(ticketRepository.findById(999L)).thenReturn(Optional.empty());

        var result = ticketService.listChangeRecords(999L, userId);

        assertEquals(404, result.getCode());
    }

    @Test
    void listChangeRecords_shouldRejectOtherUserWhenNotAdmin() {
        var ticket = Ticket.builder().id(ticketId).submitterId(otherUserId).build();
        when(ticketRepository.findById(ticketId)).thenReturn(Optional.of(ticket));

        var result = ticketService.listChangeRecords(ticketId, userId);

        assertEquals(403, result.getCode());
    }

    @Test
    void listChangeRecords_shouldAllowAdmin() {
        var auth = mock(Authentication.class);
        var securityContext = mock(SecurityContext.class);
        when(securityContext.getAuthentication()).thenReturn(auth);
        when(auth.getAuthorities()).thenReturn((Collection) List.of(new SimpleGrantedAuthority("ROLE_ADMIN")));
        SecurityContextHolder.setContext(securityContext);

        var ticket = Ticket.builder().id(ticketId).submitterId(otherUserId).build();
        when(ticketRepository.findById(ticketId)).thenReturn(Optional.of(ticket));
        when(changeRecordRepository.findByTicketIdOrderByChangedAtDesc(ticketId)).thenReturn(List.of());

        var result = ticketService.listChangeRecords(ticketId, userId);

        assertEquals(200, result.getCode());
    }

    // ==================== nextTicketNumber ====================

    @Test
    void nextTicketNumber_shouldStartFrom1() {
        when(ticketRepository.findTopByTicketNumberStartingWithOrderByTicketNumberDesc(todayTicketPrefix()))
                .thenReturn(Optional.empty());
        when(ticketRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        var result = ticketService.createTicket(createBasicRequest(), userId);

        assertTrue(result.getData().getTicketNumber().endsWith("0001"));
    }

    @Test
    void nextTicketNumber_shouldIncrement() {
        var existingTicket = Ticket.builder().ticketNumber(todayTicketPrefix() + "0003").build();
        when(ticketRepository.findTopByTicketNumberStartingWithOrderByTicketNumberDesc(todayTicketPrefix()))
                .thenReturn(Optional.of(existingTicket));
        when(ticketRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        var result = ticketService.createTicket(createBasicRequest(), userId);

        // Should generate "TK-20260528-0004"
        assertTrue(result.getData().getTicketNumber().endsWith("0004"));
    }

    // ==================== utils ====================

    private TicketCreateRequest createBasicRequest() {
        var req = new TicketCreateRequest();
        req.setTitle("测试");
        req.setContent("内容");
        req.setAffectedMenu(AffectedMenu.CHAT);
        req.setPriority(TicketPriority.MEDIUM);
        return req;
    }

    private String todayTicketPrefix() {
        return "TK-" + LocalDate.now().format(NUMBER_DATE) + "-";
    }
}
