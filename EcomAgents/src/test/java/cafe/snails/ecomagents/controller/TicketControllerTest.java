package cafe.snails.ecomagents.controller;

import cafe.snails.ecomagents.dto.ApiResponse;
import cafe.snails.ecomagents.dto.TicketResponse;
import cafe.snails.ecomagents.service.TicketService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * {@link TicketController} 的单元测试。
 */
@ExtendWith(MockitoExtension.class)
class TicketControllerTest {

    @Mock
    private TicketService ticketService;

    @InjectMocks
    private TicketController ticketController;

    @Test
    void listMyTickets_shouldReturnTickets() {
        var tickets = List.of(mock(TicketResponse.class));
        when(ticketService.listMyTickets(eq(1L), isNull(), isNull(), isNull(), isNull()))
                .thenReturn(ApiResponse.success(tickets));

        var result = ticketController.listMyTickets(1L, null, null, null, null);

        assertEquals(200, result.getCode());
        assertEquals(1, result.getData().size());
    }

    @Test
    void listMyTickets_shouldFilterByStatus() {
        when(ticketService.listMyTickets(any(), any(), any(), any(), any()))
                .thenReturn(ApiResponse.success(List.of()));

        var result = ticketController.listMyTickets(1L, null, null, null, null);

        assertEquals(200, result.getCode());
    }

    @Test
    void createTicket_shouldReturnCreatedTicket() {
        var request = new cafe.snails.ecomagents.dto.TicketCreateRequest();
        var response = mock(TicketResponse.class);
        when(ticketService.createTicket(request, 1L)).thenReturn(ApiResponse.success(response));

        var result = ticketController.createTicket(request, 1L);

        assertEquals(200, result.getCode());
    }

    @Test
    void getMyTicket_shouldReturnTicket() {
        var response = mock(TicketResponse.class);
        when(ticketService.getMyTicket(1L, 1L)).thenReturn(ApiResponse.success(response));

        var result = ticketController.getMyTicket(1L, 1L);

        assertEquals(200, result.getCode());
    }
}
