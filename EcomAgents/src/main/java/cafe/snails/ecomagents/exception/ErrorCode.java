package cafe.snails.ecomagents.exception;

/**
 * 业务错误码枚举，统一管理系统中所有错误码及其对应的默认提示信息。
 */
public enum ErrorCode {

    // ========== 通用错误 (1xxx) ==========
    /** 请求参数校验失败 */
    BAD_REQUEST(400, "请求参数错误"),
    /** 未授权访问 */
    UNAUTHORIZED(401, "未登录或 token 已过期"),
    /** 无操作权限 */
    FORBIDDEN(403, "权限不足"),
    /** 请求的资源不存在 */
    NOT_FOUND(404, "请求的资源不存在"),
    /** 资源冲突（如重复创建） */
    CONFLICT(409, "资源冲突"),
    /** 服务器内部错误 */
    INTERNAL_ERROR(500, "服务器内部错误"),

    // ========== 认证 & 用户 (2xxx) ==========
    /** 用户名或密码错误 */
    LOGIN_FAILED(2100, "用户名或密码错误"),
    /** 账号已被禁用 */
    ACCOUNT_DISABLED(2101, "账号已被禁用"),
    /** 用户名已存在 */
    USERNAME_EXISTS(2102, "用户名已存在"),
    /** 无法操作管理员用户 */
    ADMIN_OPERATION_DENIED(2103, "无法操作管理员用户"),

    // ========== 邀请码 (3xxx) ==========
    /** 邀请码无效或已使用 */
    INVITE_CODE_INVALID(3100, "无效或已使用的邀请码"),
    /** 邀请码已被使用，无法删除 */
    INVITE_CODE_USED(3101, "邀请码已使用，无法删除"),

    // ========== 知识库 (4xxx) ==========
    /** 知识库不存在 */
    KNOWLEDGE_BASE_NOT_FOUND(4100, "知识库不存在"),
    /** 文档不存在 */
    DOCUMENT_NOT_FOUND(4101, "文档不存在"),
    /** 文件名为空 */
    FILE_NAME_EMPTY(4102, "文件名不能为空"),
    /** 文件解析失败 */
    FILE_PARSE_ERROR(4103, "文件解析失败"),

    // ========== 会话 (5xxx) ==========
    /** 会话不存在 */
    SESSION_NOT_FOUND(5100, "会话不存在"),
    /** 会话文件夹不存在 */
    FOLDER_NOT_FOUND(5101, "文件夹不存在"),
    /** 文件夹下有子文件夹，无法删除 */
    FOLDER_HAS_CHILDREN(5102, "该文件夹下有子文件夹，无法删除"),

    // ========== Agent & 模型 (6xxx) ==========
    /** Agent 不存在 */
    AGENT_NOT_FOUND(6100, "Agent不存在"),
    /** 模型不存在 */
    MODEL_NOT_FOUND(6101, "模型不存在"),
    /** 模型 API 未配置 */
    LLM_NOT_CONFIGURED(6102, "LLM API key 未配置");

    /** API 响应中的业务状态码。 */
    private final int code;
    /** 未传入自定义消息时使用的默认提示。 */
    private final String defaultMessage;

    /** 创建错误码枚举项。 */
    ErrorCode(int code, String defaultMessage) {
        this.code = code;
        this.defaultMessage = defaultMessage;
    }

    /** 获取业务状态码。 */
    public int getCode() {
        return code;
    }

    /** 获取默认错误提示。 */
    public String getDefaultMessage() {
        return defaultMessage;
    }
}
