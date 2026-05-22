package cafe.snails.ecomagents.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class TicketChangeRecordResponse {
    private Long id;
    private String fieldName;
    private String oldValue;
    private String newValue;
    private Long changedBy;
    private String changedByName;
    private LocalDateTime changedAt;
}
