package cafe.snails.ecomagents.dto.image;

import cafe.snails.ecomagents.dto.ImageJobResponse;
import cafe.snails.ecomagents.model.ImageSessionOperation;
import java.time.LocalDateTime;

/**
 * 图片会话生成任务的响应。
 */
public record SessionImageJobResponse(Long sessionId, ImageSessionOperation operation, Long parentJobId,
                                      LocalDateTime createdAt, ImageJobResponse job) {}
