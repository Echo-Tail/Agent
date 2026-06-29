package cafe.snails.ecomagents.dto;

import lombok.*;

/**
 * 通用 API 响应体，所有 REST 接口统一使用此格式返回。
 * <p>通过泛型 {@code T} 支持不同的 data 类型，配合静态工厂方法快速构建。</p>
 *
 * @param <T> data 字段的类型
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ApiResponse<T> {
    /** 状态码：200 成功，4xx 客户端错误，5xx 服务端错误 */
    private int code;
    /** 提示消息 */
    private String message;
    /** 响应数据 */
    private T data;

    /** 创建成功响应（含自定义消息和数据） */
    public static <T> ApiResponse<T> success(String message, T data) {
        return new ApiResponse<>(200, message, data);
    }

    /** 创建成功响应（默认消息"success"） */
    public static <T> ApiResponse<T> success(T data) {
        return new ApiResponse<>(200, "success", data);
    }

    /** 创建错误响应（无数据） */
    public static <T> ApiResponse<T> error(int code, String message) {
        return new ApiResponse<>(code, message, null);
    }

    /** 创建错误响应（含附加数据） */
    public static <T> ApiResponse<T> error(int code, String message, T data) {
        return new ApiResponse<>(code, message, data);
    }
}
