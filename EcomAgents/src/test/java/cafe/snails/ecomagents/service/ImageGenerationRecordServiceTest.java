package cafe.snails.ecomagents.service;

import cafe.snails.ecomagents.exception.BusinessException;
import cafe.snails.ecomagents.model.ImageGenerationRecord;
import cafe.snails.ecomagents.repository.ImageGenerationRecordRepository;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageRequest;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class ImageGenerationRecordServiceTest {
    private final ImageGenerationRecordRepository records = mock(ImageGenerationRecordRepository.class);
    private final ImageGenerationRecordService service = new ImageGenerationRecordService(records);

    @Test
    void listRecordsUsesRepositorySpecification() {
        service.listRecords(7L, null, null, "product", PageRequest.of(0, 20));
        verify(records).findAll(any(org.springframework.data.jpa.domain.Specification.class), any(PageRequest.class));
    }

    @Test
    void getRecordAllowsOwner() {
        ImageGenerationRecord record = ImageGenerationRecord.builder().id(1L).userId(7L).build();
        when(records.findById(1L)).thenReturn(Optional.of(record));
        assertSame(record, service.getRecord(1L, 7L));
    }

    @Test
    void deleteRecordRejectsOtherUser() {
        when(records.findById(1L)).thenReturn(Optional.of(ImageGenerationRecord.builder().id(1L).userId(8L).build()));
        assertThrows(BusinessException.class, () -> service.deleteRecord(1L, 7L));
        verify(records, never()).delete(any(ImageGenerationRecord.class));
    }
}
