package cafe.snails.ecomagents.exception;

import lombok.Getter;

/**
 * 业务异常，在 Service 层抛出，由 GlobalExceptionHandler 统一捕获并转换为 ApiResponse 返回。
 * <p>
 * 携带 {@link ErrorCode} 枚举，调用方可根据 code 判断异常类型，
 * 也可通过 {@link #getData()} 携带额外的错误上下文。
 */
@Getter
public class BusinessException extends RuntimeException {

    private final ErrorCode errorCode;
    private final transient Object data;

    public BusinessException(ErrorCode errorCode) {
        super(errorCode.getDefaultMessage());
        this.errorCode = errorCode;
        this.data = null;
    }

    public BusinessException(ErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
        this.data = null;
    }

    public BusinessException(ErrorCode errorCode, String message, Object data) {
        super(message);
        this.errorCode = errorCode;
        this.data = data;
    }

    public BusinessException(ErrorCode errorCode, Throwable cause) {
        super(errorCode.getDefaultMessage(), cause);
        this.errorCode = errorCode;
        this.data = null;
    }
}
