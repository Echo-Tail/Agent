package cafe.snails.ecomagents.dto;

import lombok.Data;

@Data
public class SystemLogRequest {
    private String level;
    private String category;
    private String message;
    private String data;
    private Long duration;
    private String route;
    private Long userId;
}
