package cafe.snails.ecomagents.exception;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * {@link BusinessException} 单元测试。
 */
class BusinessExceptionTest {
    @Test
    void shouldCreateWithErrorCode() {
        var ex = new BusinessException(ErrorCode.NOT_FOUND);
        assertEquals(ErrorCode.NOT_FOUND, ex.getErrorCode());
        assertEquals(ErrorCode.NOT_FOUND.getDefaultMessage(), ex.getMessage());
    }

    @Test
    void shouldCreateWithCustomMessage() {
        var ex = new BusinessException(ErrorCode.BAD_REQUEST, "自定义消息");
        assertEquals(ErrorCode.BAD_REQUEST, ex.getErrorCode());
        assertEquals("自定义消息", ex.getMessage());
    }

    @Test
    void shouldCreateWithData() {
        var ex = new BusinessException(ErrorCode.FORBIDDEN, "禁止操作", "extra");
        assertEquals(ErrorCode.FORBIDDEN, ex.getErrorCode());
        assertEquals("extra", ex.getData());
    }
}
