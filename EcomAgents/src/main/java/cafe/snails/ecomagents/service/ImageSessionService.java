package cafe.snails.ecomagents.service;

import cafe.snails.ecomagents.dto.image.ImageSessionDtos.*;
import cafe.snails.ecomagents.exception.*;
import cafe.snails.ecomagents.model.*;
import cafe.snails.ecomagents.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;
import java.util.*;

@Service
@RequiredArgsConstructor
public class ImageSessionService {
    private final ImageSessionRepository sessionRepository;
    private final CanvasDocumentRepository canvasRepository;
    private final ImageAssetRepository assetRepository;

    public List<SessionResponse> list(Long userId) {
        return sessionRepository.findByUserIdAndDeletedAtIsNullOrderByUpdatedAtDesc(userId).stream().map(this::toResponse).toList();
    }

    @Transactional
    public SessionResponse create(CreateRequest request, Long userId) {
        var now = LocalDateTime.now();
        var session = ImageSession.builder().userId(userId).title(request.title().trim()).status("ACTIVE")
                .createdAt(now).updatedAt(now).build();
        return toResponse(sessionRepository.save(session));
    }

    public SessionResponse get(Long id, Long userId) { return toResponse(requireOwned(id, userId)); }

    public WorkspaceResponse workspace(Long id, Long userId) {
        var session = requireOwned(id, userId);
        var assets = assetRepository.findBySessionIdAndDeletedAtIsNullOrderByCreatedAt(id).stream()
                .map(value -> new AssetResponse(value.getId(), value.getSessionId(), value.getType(), value.getMimeType(), value.getWidth(), value.getHeight(), value.getFileSize(), value.getStorageKey(), value.getCreatedAt()))
                .toList();
        return new WorkspaceResponse(toResponse(session), canvasRepository.findById(id).map(this::toResponse).orElse(null), assets);
    }

    @Transactional
    public SessionResponse update(Long id, UpdateRequest request, Long userId) {
        var session = requireOwned(id, userId);
        session.setTitle(request.title().trim());
        session.setUpdatedAt(LocalDateTime.now());
        return toResponse(sessionRepository.save(session));
    }

    @Transactional
    public SessionResponse delete(Long id, Long userId) {
        var session = requireOwned(id, userId);
        session.setDeletedAt(LocalDateTime.now());
        session.setStatus("ARCHIVED");
        session.setUpdatedAt(session.getDeletedAt());
        return toResponse(sessionRepository.save(session));
    }

    public CanvasResponse getCanvas(Long id, Long userId) {
        requireOwned(id, userId);
        return canvasRepository.findById(id).map(this::toResponse)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "画布不存在"));
    }

    @Transactional
    public CanvasResponse saveCanvas(Long id, SaveCanvasRequest request, Long userId) {
        var session = requireOwned(id, userId);
        var document = canvasRepository.findById(id).orElse(null);
        if (document == null) {
            if (request.revision() != 0) throw revisionConflict(0L, request.revision());
            document = CanvasDocument.builder().sessionId(id).schemaVersion(request.schemaVersion())
                    .snapshot(request.snapshot()).updatedAt(LocalDateTime.now()).build();
        } else {
            if (!Objects.equals(document.getRevision(), request.revision())) {
                throw revisionConflict(document.getRevision(), request.revision());
            }
            document.setSchemaVersion(request.schemaVersion());
            document.setSnapshot(request.snapshot());
            document.setUpdatedAt(LocalDateTime.now());
        }
        session.setUpdatedAt(LocalDateTime.now());
        sessionRepository.save(session);
        return toResponse(canvasRepository.saveAndFlush(document));
    }

    private ImageSession requireOwned(Long id, Long userId) {
        return sessionRepository.findByIdAndUserIdAndDeletedAtIsNull(id, userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "图像会话不存在"));
    }

    private BusinessException revisionConflict(Long current, Long supplied) {
        return new BusinessException(ErrorCode.CONFLICT, "CANVAS_REVISION_CONFLICT", Map.of("currentRevision", current, "suppliedRevision", supplied));
    }

    private SessionResponse toResponse(ImageSession value) {
        return new SessionResponse(value.getId(), value.getTitle(), value.getStatus(), value.getThumbnailAssetId(), assetRepository.countBySessionIdAndDeletedAtIsNull(value.getId()), value.getCreatedAt(), value.getUpdatedAt());
    }
    private CanvasResponse toResponse(CanvasDocument value) {
        return new CanvasResponse(value.getSessionId(), value.getRevision(), value.getSchemaVersion(), value.getSnapshot(), value.getUpdatedAt());
    }
}
