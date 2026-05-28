package cafe.snails.ecomagents.exception;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * {@link ErrorCode} 枚举单元测试。
 */
class ErrorCodeTest {
    @Test
    void shouldHaveCodeAndMessage() {
        assertNotNull(ErrorCode.BAD_REQUEST.getCode());
        assertNotNull(ErrorCode.BAD_REQUEST.getDefaultMessage());
        assertNotNull(ErrorCode.UNAUTHORIZED.getCode());
        assertNotNull(ErrorCode.UNAUTHORIZED.getDefaultMessage());
        assertNotNull(ErrorCode.FORBIDDEN.getCode());
        assertNotNull(ErrorCode.FORBIDDEN.getDefaultMessage());
        assertNotNull(ErrorCode.NOT_FOUND.getCode());
        assertNotNull(ErrorCode.NOT_FOUND.getDefaultMessage());
        assertNotNull(ErrorCode.INTERNAL_ERROR.getCode());
        assertNotNull(ErrorCode.INTERNAL_ERROR.getDefaultMessage());
    }
}
