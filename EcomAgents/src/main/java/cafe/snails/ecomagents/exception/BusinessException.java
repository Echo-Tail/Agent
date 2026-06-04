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

    /** 业务错误码，用于统一转换为 API 响应 code 和默认消息。 */
    private final ErrorCode errorCode;
    /** 附加错误上下文，不参与异常序列化。 */
    private final transient Object data;

    /** 使用错误码默认消息创建业务异常。 */
    public BusinessException(ErrorCode errorCode) {
        super(errorCode.getDefaultMessage());
        this.errorCode = errorCode;
        this.data = null;
    }

    /** 使用自定义消息创建业务异常。 */
    public BusinessException(ErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
        this.data = null;
    }

    /** 使用自定义消息和附加上下文创建业务异常。 */
    public BusinessException(ErrorCode errorCode, String message, Object data) {
        super(message);
        this.errorCode = errorCode;
        this.data = data;
    }

    /** 使用错误码和底层异常原因创建业务异常。 */
    public BusinessException(ErrorCode errorCode, Throwable cause) {
        super(errorCode.getDefaultMessage(), cause);
        this.errorCode = errorCode;
        this.data = null;
    }
}
