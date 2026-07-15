package cafe.snails.ecomagents.service;

import cafe.snails.ecomagents.exception.BusinessException;
import cafe.snails.ecomagents.exception.ErrorCode;
import cafe.snails.ecomagents.model.ImageGenerationRecord;
import cafe.snails.ecomagents.repository.ImageGenerationRecordRepository;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;

/** 图片生成记录查询服务。图片生成统一由 ImageGenerationRuntime 负责。 */
@Service
@RequiredArgsConstructor
public class ImageGenerationRecordService {
    private final ImageGenerationRecordRepository recordRepository;

    public Page<ImageGenerationRecord> listRecords(Long userId, LocalDate startDate, LocalDate endDate,
            String prompt, Pageable pageable) {
        Specification<ImageGenerationRecord> spec = (root, query, cb) -> {
            var predicates = new ArrayList<Predicate>();
            predicates.add(cb.equal(root.get("userId"), userId));
            if (startDate != null) predicates.add(cb.greaterThanOrEqualTo(root.get("createdAt"), startDate.atStartOfDay()));
            if (endDate != null) predicates.add(cb.lessThanOrEqualTo(root.get("createdAt"), endDate.atTime(LocalTime.MAX)));
            if (prompt != null && !prompt.isBlank()) {
                predicates.add(cb.like(cb.lower(root.get("prompt")), "%" + prompt.toLowerCase() + "%"));
            }
            query.orderBy(cb.desc(root.get("createdAt")));
            return cb.and(predicates.toArray(new Predicate[0]));
        };
        return recordRepository.findAll(spec, pageable);
    }

    public ImageGenerationRecord getRecord(Long recordId, Long userId) {
        ImageGenerationRecord record = recordRepository.findById(recordId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "记录不存在"));
        if (!record.getUserId().equals(userId)) throw new BusinessException(ErrorCode.FORBIDDEN, "无权查看此记录");
        return record;
    }

    public void deleteRecord(Long recordId, Long userId) {
        ImageGenerationRecord record = getRecord(recordId, userId);
        recordRepository.delete(record);
    }
}
