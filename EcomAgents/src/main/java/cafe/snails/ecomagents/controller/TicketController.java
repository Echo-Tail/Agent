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

@RestController
@RequestMapping("/v1")
@RequiredArgsConstructor
public class TicketController {

    private final TicketService ticketService;

    @GetMapping("/tickets/my")
    public ApiResponse<List<TicketResponse>> listMyTickets(
            @CurrentUserId Long userId,
            @RequestParam(required = false) TicketStatus status,
            @RequestParam(required = false) AffectedMenu affectedMenu,
            @RequestParam(required = false) TicketPriority priority,
            @RequestParam(required = false) String title) {
        return ticketService.listMyTickets(userId, status, affectedMenu, priority, title);
    }

    @GetMapping("/tickets/my/{id}")
    public ApiResponse<TicketResponse> getMyTicket(@PathVariable Long id, @CurrentUserId Long userId) {
        return ticketService.getMyTicket(id, userId);
    }

    @PostMapping("/tickets")
    public ApiResponse<TicketResponse> createTicket(@Valid @RequestBody TicketCreateRequest request,
                                                    @CurrentUserId Long userId) {
        return ticketService.createTicket(request, userId);
    }

    @PutMapping("/tickets/{id}")
    public ApiResponse<TicketResponse> updateTicket(@PathVariable Long id,
                                                    @Valid @RequestBody TicketUpdateRequest request,
                                                    @CurrentUserId Long userId) {
        return ticketService.updateTicket(id, request, userId);
    }

    @GetMapping("/tickets/{id}/changes")
    public ApiResponse<List<TicketChangeRecordResponse>> listChangeRecords(@PathVariable Long id,
                                                                           @CurrentUserId Long userId) {
        return ticketService.listChangeRecords(id, userId);
    }

    @GetMapping("/admin/tickets")
    public ApiResponse<List<TicketResponse>> listAdminTickets(
            @RequestParam(required = false) TicketStatus status,
            @RequestParam(required = false) AffectedMenu affectedMenu,
            @RequestParam(required = false) TicketPriority priority,
            @RequestParam(required = false) String title,
            @RequestParam(required = false) Long submitterId) {
        return ticketService.listAdminTickets(status, affectedMenu, priority, title, submitterId);
    }

    @GetMapping("/admin/tickets/{id}")
    public ApiResponse<TicketResponse> getAdminTicket(@PathVariable Long id) {
        return ticketService.getAdminTicket(id);
    }

    @PostMapping("/admin/tickets/{id}/start")
    public ApiResponse<TicketResponse> startHandling(@PathVariable Long id, @CurrentUserId Long handlerId) {
        return ticketService.startHandling(id, handlerId);
    }

    @PostMapping("/admin/tickets/{id}/complete")
    public ApiResponse<TicketResponse> completeTicket(@PathVariable Long id,
                                                      @Valid @RequestBody TicketHandleRequest request,
                                                      @CurrentUserId Long handlerId) {
        return ticketService.completeTicket(id, request, handlerId);
    }
}
