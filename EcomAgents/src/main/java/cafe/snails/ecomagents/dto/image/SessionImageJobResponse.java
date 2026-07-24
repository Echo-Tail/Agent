package cafe.snails.ecomagents.dto.image;

import cafe.snails.ecomagents.dto.ImageJobResponse;
import cafe.snails.ecomagents.model.ImageSessionOperation;
import java.time.LocalDateTime;

public record SessionImageJobResponse(Long sessionId, ImageSessionOperation operation, Long parentJobId,
                                      LocalDateTime createdAt, ImageJobResponse job) {}
