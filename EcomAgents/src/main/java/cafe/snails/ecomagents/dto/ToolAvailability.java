package cafe.snails.ecomagents.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Describes whether a tool is actually usable by a specific Agent.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ToolAvailability {
    private String toolId;
    private Long agentId;
    private boolean boundToAgent;
    private boolean globallyEnabled;
    private boolean configured;
    private boolean available;
    private String message;
}
