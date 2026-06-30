package cafe.snails.ecomagents.controller;

import cafe.snails.ecomagents.dto.*;
import cafe.snails.ecomagents.model.AffectedMenu;
import cafe.snails.ecomagents.model.TicketPriority;
import cafe.snails.ecomagents.model.TicketStatus;
import cafe.snails.ecomagents.security.CurrentUserId;
import cafe.snails.ecomagents.service.TicketService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 工单控制器，提供普通用户工单流程和管理员处理流程接口。
 */
@RestController
@RequestMapping("/v1")
@RequiredArgsConstructor
public class TicketController {

    /** 工单业务服务。 */
    private final TicketService ticketService;

    /** 查询当前用户提交的工单列表。 */
    @GetMapping("/tickets/my")
    public ApiResponse<List<TicketResponse>> listMyTickets(
            @CurrentUserId Long userId,
            @RequestParam(required = false) TicketStatus status,
            @RequestParam(required = false) AffectedMenu affectedMenu,
            @RequestParam(required = false) TicketPriority priority,
            @RequestParam(required = false) String title) {
        return ticketService.listMyTickets(userId, status, affectedMenu, priority, title);
    }

    /** 查询当前用户自己的工单详情。 */
    @GetMapping("/tickets/my/{id}")
    public ApiResponse<TicketResponse> getMyTicket(@PathVariable Long id, @CurrentUserId Long userId) {
        return ticketService.getMyTicket(id, userId);
    }

    /** 当前用户创建新工单。 */
    @PostMapping("/tickets")
    public ApiResponse<TicketResponse> createTicket(@Valid @RequestBody TicketCreateRequest request,
                                                    @CurrentUserId Long userId) {
        return ticketService.createTicket(request, userId);
    }

    /** 当前用户更新自己尚可编辑的工单。 */
    @PutMapping("/tickets/{id}")
    public ApiResponse<TicketResponse> updateTicket(@PathVariable Long id,
                                                    @Valid @RequestBody TicketUpdateRequest request,
                                                    @CurrentUserId Long userId) {
        return ticketService.updateTicket(id, request, userId);
    }

    /** 查询工单字段变更历史。 */
    @GetMapping("/tickets/{id}/changes")
    public ApiResponse<List<TicketChangeRecordResponse>> listChangeRecords(@PathVariable Long id,
                                                                           @CurrentUserId Long userId) {
        return ticketService.listChangeRecords(id, userId);
    }

    /** 管理员按条件查询全部工单。 */
    @GetMapping("/admin/tickets")
    public ApiResponse<List<TicketResponse>> listAdminTickets(
            @RequestParam(required = false) TicketStatus status,
            @RequestParam(required = false) AffectedMenu affectedMenu,
            @RequestParam(required = false) TicketPriority priority,
            @RequestParam(required = false) String title,
            @RequestParam(required = false) Long submitterId) {
        return ticketService.listAdminTickets(status, affectedMenu, priority, title, submitterId);
    }

    /** 管理员查询任意工单详情。 */
    @GetMapping("/admin/tickets/{id}")
    public ApiResponse<TicketResponse> getAdminTicket(@PathVariable Long id) {
        return ticketService.getAdminTicket(id);
    }

    /** 管理员开始处理工单并成为处理人。 */
    @PostMapping("/admin/tickets/{id}/start")
    public ApiResponse<TicketResponse> startHandling(@PathVariable Long id, @CurrentUserId Long handlerId) {
        return ticketService.startHandling(id, handlerId);
    }

    /** 管理员完成工单并写入处理意见。 */
    @PostMapping("/admin/tickets/{id}/complete")
    public ApiResponse<TicketResponse> completeTicket(@PathVariable Long id,
                                                      @Valid @RequestBody TicketHandleRequest request,
                                                      @CurrentUserId Long handlerId) {
        return ticketService.completeTicket(id, request, handlerId);
    }
}
