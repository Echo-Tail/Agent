package cafe.snails.ecomagents.dto;

import cafe.snails.ecomagents.model.AffectedMenu;
import cafe.snails.ecomagents.model.FileRecord;
import cafe.snails.ecomagents.model.TicketPriority;
import cafe.snails.ecomagents.model.TicketStatus;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
public class TicketResponse {
    private Long id;
    private String ticketNumber;
    private String title;
    private AffectedMenu affectedMenu;
    private TicketPriority priority;
    private String content;
    private TicketStatus status;
    private Long submitterId;
    private String submitterName;
    private Long handlerId;
    private String handlerName;
    private String handlingNote;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private LocalDateTime startedAt;
    private LocalDateTime completedAt;
    private List<FileRecord> attachments;
}
