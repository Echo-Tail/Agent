package cafe.snails.ecomagents.exception;

import cafe.snails.ecomagents.dto.ApiResponse;
import jakarta.validation.ConstraintViolationException;
import org.springframework.web.context.request.async.AsyncRequestNotUsableException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.stream.Collectors;

/**
 * 全局异常处理器，统一捕获各类异常并转换为 {@link ApiResponse} 格式返回。
 * <p>
 * 优先级：BusinessException → 参数校验异常 → RuntimeException → 兜底。
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    /** 当前异常处理器日志记录器。 */
    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /**
     * 处理业务异常，返回对应的错误码和消息。
     */
    @ExceptionHandler(BusinessException.class)
    public ApiResponse<Void> handleBusinessException(BusinessException e) {
        if (e.getErrorCode().getCode() >= 500) {
            log.warn("BusinessException: [{}] {}", e.getErrorCode(), e.getMessage(), e);
        }
        return ApiResponse.error(e.getErrorCode().getCode(), e.getMessage());
    }

    /**
     * 处理 {@code @Valid} 参数校验失败异常。
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ApiResponse<Void> handleValidation(MethodArgumentNotValidException e) {
        String msg = e.getBindingResult().getFieldErrors().stream()
                .map(err -> err.getField() + ": " + err.getDefaultMessage())
                .collect(Collectors.joining("; "));
        return ApiResponse.error(400, msg);
    }

    /**
     * 处理路径参数或查询参数校验失败。
     */
    @ExceptionHandler(ConstraintViolationException.class)
    public ApiResponse<Void> handleConstraintViolation(ConstraintViolationException e) {
        return ApiResponse.error(400, e.getMessage());
    }

    /**
     * 处理 SSE 客户端断开连接的异常，无需告警。
     */
    @ExceptionHandler(AsyncRequestNotUsableException.class)
    public void handleAsyncRequestNotUsable(AsyncRequestNotUsableException e) {
        log.debug("SSE client disconnected: {}", e.getMessage());
    }

    /**
     * 处理未预期的运行时异常，返回 500，避免堆栈泄露给客户端。
     */
    @ExceptionHandler(RuntimeException.class)
    public ApiResponse<Void> handleRuntimeException(RuntimeException e) {
        log.error("Unexpected error", e);
        return ApiResponse.error(500, "服务器内部错误");
    }

    /**
     * 兜底处理所有未捕获的异常。
     */
    @ExceptionHandler(Exception.class)
    public ApiResponse<Void> handleException(Exception e) {
        log.error("Unexpected error", e);
        return ApiResponse.error(HttpStatus.INTERNAL_SERVER_ERROR.value(), "服务器内部错误");
    }
}
