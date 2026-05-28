package cafe.snails.ecomagents.exception;

import jakarta.validation.ConstraintViolationException;
import org.junit.jupiter.api.Test;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * {@link GlobalExceptionHandler} 单元测试。
 */
class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void handleBusinessException_shouldReturnErrorCode() {
        var ex = new BusinessException(ErrorCode.NOT_FOUND);
        var resp = handler.handleBusinessException(ex);
        assertEquals(404, resp.getCode());
        assertEquals(ErrorCode.NOT_FOUND.getDefaultMessage(), resp.getMessage());
    }

    @Test
    void handleBusinessException_with500shouldLog() {
        var ex = new BusinessException(ErrorCode.INTERNAL_ERROR);
        var resp = handler.handleBusinessException(ex);
        assertEquals(500, resp.getCode());
    }

    @Test
    void handleValidation_shouldReturnFieldErrors() {
        var ex = mock(MethodArgumentNotValidException.class);
        var bindingResult = mock(BindingResult.class);
        when(ex.getBindingResult()).thenReturn(bindingResult);
        when(bindingResult.getFieldErrors()).thenReturn(List.of(
                new FieldError("obj", "title", "标题不能为空"),
                new FieldError("obj", "content", "内容不能为空")
        ));

        var resp = handler.handleValidation(ex);
        assertEquals(400, resp.getCode());
        assertTrue(resp.getMessage().contains("title"));
        assertTrue(resp.getMessage().contains("content"));
    }

    @Test
    void handleConstraintViolation_shouldReturn400() {
        var ex = new ConstraintViolationException("参数不合法", null);
        var resp = handler.handleConstraintViolation(ex);
        assertEquals(400, resp.getCode());
        assertEquals("参数不合法", resp.getMessage());
    }

    @Test
    void handleAsyncRequestNotUsable_shouldReturnVoid() {
        var ex = new org.springframework.web.context.request.async.AsyncRequestNotUsableException("disconnected");
        handler.handleAsyncRequestNotUsable(ex);
    }

    @Test
    void handleRuntimeException_shouldReturn500() {
        var ex = new RuntimeException("数据库连接失败");
        var resp = handler.handleRuntimeException(ex);
        assertEquals(500, resp.getCode());
        assertEquals("服务器内部错误", resp.getMessage());
    }

    @Test
    void handleException_shouldReturn500() {
        var ex = new Exception("未知错误");
        var resp = handler.handleException(ex);
        assertEquals(500, resp.getCode());
    }
}
