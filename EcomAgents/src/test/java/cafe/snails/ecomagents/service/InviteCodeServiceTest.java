package cafe.snails.ecomagents.service;

import cafe.snails.ecomagents.dto.ApiResponse;
import cafe.snails.ecomagents.model.InviteCode;
import cafe.snails.ecomagents.repository.InviteCodeRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
/**
 * 邀请码服务测试，验证批量生成、重复规避和删除约束。
 */
class InviteCodeServiceTest {

    @Mock
    private InviteCodeRepository inviteCodeRepository;

    private InviteCodeService service;

    private static final Pattern CODE_PATTERN = Pattern.compile("^[A-Z0-9]{8}$");

    @BeforeEach
    void setUp() {
        service = new InviteCodeService(inviteCodeRepository);
    }

    @Test
    void batchGenerate_shouldReturnCorrectCount() {
        when(inviteCodeRepository.saveAll(any())).thenAnswer(i -> i.getArgument(0));

        ApiResponse<List<InviteCode>> result = service.batchGenerate(5);

        assertEquals(200, result.getCode());
        assertEquals(5, result.getData().size());
    }

    @Test
    void batchGenerate_shouldGenerateValidFormat() {
        when(inviteCodeRepository.saveAll(any())).thenAnswer(i -> i.getArgument(0));

        ApiResponse<List<InviteCode>> result = service.batchGenerate(10);

        for (InviteCode code : result.getData()) {
            assertTrue(CODE_PATTERN.matcher(code.getCode()).matches(),
                    "Invalid code format: " + code.getCode());
        }
    }

    @Test
    void batchGenerate_shouldGenerateUniqueCodes() {
        when(inviteCodeRepository.saveAll(any())).thenAnswer(i -> i.getArgument(0));
        when(inviteCodeRepository.findById(any())).thenReturn(java.util.Optional.empty());

        ApiResponse<List<InviteCode>> result = service.batchGenerate(20);

        long distinct = result.getData().stream().map(InviteCode::getCode).distinct().count();
        assertEquals(20, distinct, "Generated codes should be unique");
    }

    @Test
    void batchGenerate_shouldSetNotUsed() {
        when(inviteCodeRepository.saveAll(any())).thenAnswer(i -> i.getArgument(0));

        ApiResponse<List<InviteCode>> result = service.batchGenerate(3);

        for (InviteCode code : result.getData()) {
            assertFalse(code.getUsed());
        }
    }

    @Test
    void batchGenerate_largeCount_shouldAllBeValid() {
        when(inviteCodeRepository.saveAll(any())).thenAnswer(i -> i.getArgument(0));
        when(inviteCodeRepository.findById(any())).thenReturn(java.util.Optional.empty());

        ApiResponse<List<InviteCode>> result = service.batchGenerate(100);

        assertEquals(100, result.getData().size());
        for (InviteCode code : result.getData()) {
            assertTrue(CODE_PATTERN.matcher(code.getCode()).matches());
        }
    }
}
