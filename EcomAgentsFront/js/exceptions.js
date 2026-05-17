// js/exceptions.js - 异常类定义

// ==================== 基础异常类 ====================

/**
 * 基础异常类
 * 所有自定义异常的基类
 */
class BaseException extends Error {
    constructor(message, errorCode, statusCode = 500, details = null) {
        super(message);
        this.name = this.constructor.name;
        this.errorCode = errorCode;
        this.statusCode = statusCode;
        this.details = details;
        this.timestamp = new Date().toISOString();
        
        // 保持原型链正确
        Object.setPrototypeOf(this, new.target.prototype);
        Error.captureStackTrace(this, this.constructor);
    }
    
    toJSON() {
        return {
            name: this.name,
            message: this.message,
            errorCode: this.errorCode,
            statusCode: this.statusCode,
            details: this.details,
            timestamp: this.timestamp
        };
    }
}


// ==================== 系统级异常 (1xxx) ====================

/**
 * 系统未知异常
 * 场景：未捕获的未知错误、系统内部错误
 */
class SystemUnknownException extends BaseException {
    constructor(message = '系统内部错误', details = null) {
        super(message, 1000, 500, details);
    }
}

/**
 * 系统繁忙异常
 * 场景：服务器负载过高、请求队列已满
 */
class SystemBusyException extends BaseException {
    constructor(message = '系统繁忙，请稍后再试', details = null) {
        super(message, 1001, 503, details);
    }
}

/**
 * 服务不可用异常
 * 场景：依赖服务宕机、数据库连接失败
 */
class ServiceUnavailableException extends BaseException {
    constructor(message = '服务暂不可用', details = null) {
        super(message, 1002, 503, details);
    }
}

/**
 * 请求超时异常
 * 场景：网络超时、数据库查询超时
 */
class TimeoutException extends BaseException {
    constructor(message = '请求超时', details = null) {
        super(message, 1003, 408, details);
    }
}

/**
 * 配置错误异常
 * 场景：配置文件缺失、配置项错误
 */
class ConfigurationException extends BaseException {
    constructor(message = '系统配置错误', details = null) {
        super(message, 1004, 500, details);
    }
}


// ==================== 请求级异常 (2xxx) ====================

/**
 * 参数校验异常
 * 场景：请求参数格式错误、缺少必填参数
 */
class ValidationException extends BaseException {
    constructor(message = '参数校验失败', details = null) {
        super(message, 2001, 400, details);
    }
}

/**
 * 参数缺失异常
 * 场景：缺少必填参数
 */
class MissingParameterException extends ValidationException {
    constructor(paramName) {
        super(`缺少必填参数: ${paramName}`, { missingParam: paramName });
        this.paramName = paramName;
    }
}

/**
 * 参数类型错误异常
 * 场景：参数类型不匹配
 */
class InvalidParameterTypeException extends ValidationException {
    constructor(paramName, expectedType, actualValue) {
        super(`参数 ${paramName} 类型错误，期望 ${expectedType}，实际 ${typeof actualValue}`, {
            paramName,
            expectedType,
            actualValue
        });
    }
}

/**
 * 参数长度超限异常
 * 场景：字符串长度超过限制
 */
class ParameterTooLongException extends ValidationException {
    constructor(paramName, maxLength, actualLength) {
        super(`参数 ${paramName} 长度超限，最大 ${maxLength} 字符，实际 ${actualLength} 字符`, {
            paramName,
            maxLength,
            actualLength
        });
    }
}

/**
 * 参数范围异常
 * 场景：数值超出允许范围
 */
class ParameterOutOfRangeException extends ValidationException {
    constructor(paramName, min, max, actualValue) {
        super(`参数 ${paramName} 超出范围，允许范围 [${min}, ${max}]，实际 ${actualValue}`, {
            paramName,
            min,
            max,
            actualValue
        });
    }
}

/**
 * 不支持的媒体类型异常
 * 场景：Content-Type 不支持
 */
class UnsupportedMediaTypeException extends BaseException {
    constructor(message = '不支持的媒体类型', details = null) {
        super(message, 2002, 415, details);
    }
}

/**
 * 请求方法不允许异常
 * 场景：使用了错误的HTTP方法
 */
class MethodNotAllowedException extends BaseException {
    constructor(method, allowedMethods = []) {
        super(`请求方法 ${method} 不允许`, {
            method,
            allowedMethods
        });
        this.errorCode = 2003;
        this.statusCode = 405;
    }
}


// ==================== 认证授权异常 (3xxx) ====================

/**
 * 未认证异常
 * 场景：未登录、Token缺失
 */
class UnauthorizedException extends BaseException {
    constructor(message = '请先登录') {
        super(message, 3001, 401);
    }
}

/**
 * Token过期异常
 * 场景：JWT Token 已过期
 */
class TokenExpiredException extends UnauthorizedException {
    constructor(expiredAt = null) {
        super('登录已过期，请重新登录');
        this.errorCode = 3002;
        this.expiredAt = expiredAt;
        this.details = { expiredAt };
    }
}

/**
 * Token无效异常
 * 场景：Token 格式错误、签名验证失败
 */
class InvalidTokenException extends UnauthorizedException {
    constructor(reason = 'Token无效') {
        super(reason);
        this.errorCode = 3003;
        this.reason = reason;
    }
}

/**
 * Token缺失异常
 * 场景：请求未携带Token
 */
class MissingTokenException extends UnauthorizedException {
    constructor() {
        super('缺少认证Token');
        this.errorCode = 3004;
    }
}

/**
 * 权限不足异常
 * 场景：用户角色无权访问资源
 */
class PermissionDeniedException extends BaseException {
    constructor(requiredRole = null, currentRole = null) {
        super('权限不足，无法执行此操作', {
            requiredRole,
            currentRole
        });
        this.errorCode = 3005;
        this.statusCode = 403;
    }
}

/**
 * 账号已禁用异常
 * 场景：用户账号被管理员禁用
 */
class AccountDisabledException extends BaseException {
    constructor(username = null) {
        super('账号已被禁用，请联系管理员', { username });
        this.errorCode = 3006;
        this.statusCode = 403;
    }
}

/**
 * 账号不存在异常
 * 场景：登录时账号不存在
 */
class AccountNotFoundException extends BaseException {
    constructor(username) {
        super(`账号 ${username} 不存在`, { username });
        this.errorCode = 3007;
        this.statusCode = 404;
    }
}

/**
 * 密码错误异常
 * 场景：登录密码错误
 */
class InvalidPasswordException extends BaseException {
    constructor(retryCount = 0, maxRetries = 5) {
        super('用户名或密码错误', { retryCount, maxRetries });
        this.errorCode = 3008;
        this.statusCode = 401;
    }
}

/**
 * 登录失败次数过多异常
 * 场景：密码错误次数超过限制
 */
class TooManyLoginAttemptsException extends BaseException {
    constructor(maxAttempts, lockoutMinutes) {
        super(`登录失败次数过多，请 ${lockoutMinutes} 分钟后再试`, {
            maxAttempts,
            lockoutMinutes
        });
        this.errorCode = 3009;
        this.statusCode = 429;
    }
}


// ==================== 用户异常 (4xxx) ====================

/**
 * 用户已存在异常
 * 场景：注册时用户名已被占用
 */
class UserAlreadyExistsException extends BaseException {
    constructor(username) {
        super(`用户 ${username} 已存在`, { username });
        this.errorCode = 4001;
        this.statusCode = 409;
    }
}

/**
 * 邮箱已被注册异常
 * 场景：注册时邮箱已被使用
 */
class EmailAlreadyExistsException extends BaseException {
    constructor(email) {
        super(`邮箱 ${email} 已被注册`, { email });
        this.errorCode = 4002;
        this.statusCode = 409;
    }
}

/**
 * 用户不存在异常
 * 场景：查询的用户不存在
 */
class UserNotFoundException extends BaseException {
    constructor(userId) {
        super(`用户 ${userId} 不存在`, { userId });
        this.errorCode = 4003;
        this.statusCode = 404;
    }
}

/**
 * 无法禁用管理员异常
 * 场景：尝试禁用管理员账号
 */
class CannotDisableAdminException extends BaseException {
    constructor(adminUsername) {
        super('无法禁用管理员账号', { adminUsername });
        this.errorCode = 4004;
        this.statusCode = 403;
    }
}

/**
 * 无法删除管理员异常
 * 场景：尝试删除管理员账号
 */
class CannotDeleteAdminException extends BaseException {
    constructor(adminUsername) {
        super('无法删除管理员账号', { adminUsername });
        this.errorCode = 4005;
        this.statusCode = 403;
    }
}

/**
 * 用户状态无效异常
 * 场景：操作用户时状态不正确
 */
class InvalidUserStatusException extends BaseException {
    constructor(userId, currentStatus, expectedStatus) {
        super(`用户状态无效，当前状态 ${currentStatus}，期望 ${expectedStatus}`, {
            userId,
            currentStatus,
            expectedStatus
        });
        this.errorCode = 4006;
        this.statusCode = 400;
    }
}


// ==================== 邀请码异常 (5xxx) ====================

/**
 * 邀请码无效异常
 * 场景：邀请码不存在或已使用
 */
class InvalidInviteCodeException extends BaseException {
    constructor(code, reason = '不存在或已使用') {
        super(`邀请码 ${code} 无效：${reason}`, { code, reason });
        this.errorCode = 5001;
        this.statusCode = 400;
    }
}

/**
 * 邀请码已使用异常
 * 场景：邀请码已被其他用户使用
 */
class InviteCodeAlreadyUsedException extends InvalidInviteCodeException {
    constructor(code, usedBy) {
        super(code, `已被用户 ${usedBy} 使用`);
        this.errorCode = 5002;
        this.usedBy = usedBy;
    }
}

/**
 * 邀请码已过期异常
 * 场景：邀请码超过有效期
 */
class InviteCodeExpiredException extends InvalidInviteCodeException {
    constructor(code, expiresAt) {
        super(code, `已过期，有效期至 ${expiresAt}`);
        this.errorCode = 5003;
        this.expiresAt = expiresAt;
    }
}

/**
 * 邀请码生成失败异常
 * 场景：批量生成邀请码时出错
 */
class InviteCodeGenerationException extends BaseException {
    constructor(reason, attemptedCount = null) {
        super(`邀请码生成失败：${reason}`, { reason, attemptedCount });
        this.errorCode = 5004;
        this.statusCode = 500;
    }
}

/**
 * 邀请码删除失败异常
 * 场景：删除已使用的邀请码
 */
class CannotDeleteUsedInviteCodeException extends BaseException {
    constructor(code, usedBy) {
        super(`无法删除已使用的邀请码 ${code}，使用人 ${usedBy}`, { code, usedBy });
        this.errorCode = 5005;
        this.statusCode = 400;
    }
}


// ==================== Agent异常 (6xxx) ====================

/**
 * Agent不存在异常
 * 场景：查询的Agent不存在
 */
class AgentNotFoundException extends BaseException {
    constructor(agentId) {
        super(`Agent ${agentId} 不存在`, { agentId });
        this.errorCode = 6001;
        this.statusCode = 404;
    }
}

/**
 * Agent已存在异常
 * 场景：创建时Agent名称已存在
 */
class AgentAlreadyExistsException extends BaseException {
    constructor(agentName) {
        super(`Agent ${agentName} 已存在`, { agentName });
        this.errorCode = 6002;
        this.statusCode = 409;
    }
}

/**
 * Agent状态错误异常
 * 场景：Agent状态不允许执行当前操作
 */
class InvalidAgentStatusException extends BaseException {
    constructor(agentId, currentStatus, requiredStatus) {
        super(`Agent状态错误，当前状态 ${currentStatus}，需要 ${requiredStatus}`, {
            agentId,
            currentStatus,
            requiredStatus
        });
        this.errorCode = 6003;
        this.statusCode = 400;
    }
}

/**
 * Agent工具配置无效异常
 * 场景：配置的工具能力不被支持
 */
class InvalidAgentToolsException extends BaseException {
    constructor(invalidTools, availableTools) {
        super(`工具配置无效：${invalidTools.join(', ')} 不可用`, {
            invalidTools,
            availableTools
        });
        this.errorCode = 6004;
        this.statusCode = 400;
    }
}

/**
 * Agent创建失败异常
 * 场景：创建Agent时发生错误
 */
class AgentCreationException extends BaseException {
    constructor(reason) {
        super(`Agent创建失败：${reason}`, { reason });
        this.errorCode = 6005;
        this.statusCode = 500;
    }
}

/**
 * Agent更新失败异常
 * 场景：更新Agent时发生错误
 */
class AgentUpdateException extends BaseException {
    constructor(agentId, reason) {
        super(`Agent ${agentId} 更新失败：${reason}`, { agentId, reason });
        this.errorCode = 6006;
        this.statusCode = 500;
    }
}

/**
 * Agent删除失败异常
 * 场景：删除Agent时发生错误
 */
class AgentDeleteException extends BaseException {
    constructor(agentId, reason) {
        super(`Agent ${agentId} 删除失败：${reason}`, { agentId, reason });
        this.errorCode = 6007;
        this.statusCode = 500;
    }
}


// ==================== 对话异常 (7xxx) ====================

/**
 * 会话不存在异常
 * 场景：查询的会话不存在
 */
class SessionNotFoundException extends BaseException {
    constructor(sessionId) {
        super(`会话 ${sessionId} 不存在`, { sessionId });
        this.errorCode = 7001;
        this.statusCode = 404;
    }
}

/**
 * 消息发送失败异常
 * 场景：发送消息时发生错误
 */
class MessageSendException extends BaseException {
    constructor(reason) {
        super(`消息发送失败：${reason}`, { reason });
        this.errorCode = 7002;
        this.statusCode = 500;
    }
}

/**
 * Agent无响应异常
 * 场景：Agent服务没有返回响应
 */
class AgentNotRespondingException extends BaseException {
    constructor(agentId, timeoutMs) {
        super(`Agent ${agentId} 无响应（超时 ${timeoutMs}ms）`, { agentId, timeoutMs });
        this.errorCode = 7003;
        this.statusCode = 504;
    }
}

/**
 * 消息内容为空异常
 * 场景：发送空消息
 */
class EmptyMessageException extends BaseException {
    constructor(message = '消息内容不能为空') {
        super(message);
        this.errorCode = 7004;
        this.statusCode = 400;
    }
}

/**
 * 消息内容过长异常
 * 场景：单条消息超过最大长度限制
 */
class MessageTooLongException extends BaseException {
    constructor(maxLength, actualLength) {
        super(`消息内容过长，最大 ${maxLength} 字符，实际 ${actualLength} 字符`, {
            maxLength,
            actualLength
        });
        this.errorCode = 7005;
        this.statusCode = 400;
    }
}

/**
 * 会话保存失败异常
 * 场景：保存对话记录时出错
 */
class ConversationSaveException extends BaseException {
    constructor(reason) {
        super(`对话记录保存失败：${reason}`, { reason });
        this.errorCode = 7006;
        this.statusCode = 500;
    }
}


// ==================== 数据库异常 (8xxx) ====================

/**
 * 数据库异常基类
 */
class DatabaseException extends BaseException {
    constructor(message, errorCode, details = null) {
        super(message, errorCode, 500, details);
    }
}

/**
 * 数据库查询异常
 * 场景：SQL查询错误
 */
class DatabaseQueryException extends DatabaseException {
    constructor(query, originalError) {
        super('数据库查询失败', 8001, { query, originalError: originalError.message });
    }
}

/**
 * 数据库插入异常
 * 场景：数据插入失败
 */
class DatabaseInsertException extends DatabaseException {
    constructor(table, data, originalError) {
        super('数据插入失败', 8002, { table, data, originalError: originalError.message });
    }
}

/**
 * 数据库更新异常
 * 场景：数据更新失败
 */
class DatabaseUpdateException extends DatabaseException {
    constructor(table, condition, originalError) {
        super('数据更新失败', 8003, { table, condition, originalError: originalError.message });
    }
}

/**
 * 数据库删除异常
 * 场景：数据删除失败
 */
class DatabaseDeleteException extends DatabaseException {
    constructor(table, condition, originalError) {
        super('数据删除失败', 8004, { table, condition, originalError: originalError.message });
    }
}

/**
 * 数据库连接异常
 * 场景：无法连接到数据库
 */
class DatabaseConnectionException extends DatabaseException {
    constructor(host, port, originalError) {
        super('数据库连接失败', 8005, { host, port, originalError: originalError.message });
    }
}

/**
 * 数据重复异常
 * 场景：违反唯一约束
 */
class DuplicateKeyException extends DatabaseException {
    constructor(key, value) {
        super('数据重复', 8006, { key, value });
        this.statusCode = 409;
    }
}

/**
 * 数据完整性异常
 * 场景：违反外键约束等完整性规则
 */
class DataIntegrityException extends DatabaseException {
    constructor(message, details = null) {
        super(message || '数据完整性约束违反', 8007, details);
        this.statusCode = 409;
    }
}


// ==================== 缓存异常 (9xxx) ====================

/**
 * 缓存异常基类
 */
class CacheException extends BaseException {
    constructor(message, errorCode, details = null) {
        super(message, errorCode, 500, details);
    }
}

/**
 * Redis连接异常
 * 场景：无法连接到Redis
 */
class RedisConnectionException extends CacheException {
    constructor(host, port, originalError) {
        super('Redis连接失败', 9001, { host, port, originalError: originalError.message });
    }
}

/**
 * 缓存操作异常
 * 场景：读写缓存时出错
 */
class CacheOperationException extends CacheException {
    constructor(operation, key, originalError) {
        super('缓存操作失败', 9002, { operation, key, originalError: originalError.message });
    }
}

/**
 * 缓存键不存在异常
 * 场景：获取不存在的缓存键
 */
class CacheKeyNotFoundException extends CacheException {
    constructor(key) {
        super('缓存键不存在', 9003, { key });
        this.statusCode = 404;
    }
}


// ==================== 第三方服务异常 (Axxx) ====================

/**
 * LLM服务异常
 * 场景：调用大模型服务失败
 */
class LLMServiceException extends BaseException {
    constructor(serviceName, reason) {
        super(`LLM服务 ${serviceName} 调用失败：${reason}`, { serviceName, reason });
        this.errorCode = 10001;
        this.statusCode = 502;
    }
}

/**
 * LLM响应解析异常
 * 场景：LLM返回内容格式错误
 */
class LLMResponseParseException extends BaseException {
    constructor(rawResponse, expectedFormat) {
        super('LLM响应解析失败', { rawResponse: rawResponse.substring(0, 200), expectedFormat });
        this.errorCode = 10002;
        this.statusCode = 500;
    }
}

/**
 * LLM配额不足异常
 * 场景：API调用次数用完
 */
class LLMQuotaExceededException extends BaseException {
    constructor(serviceName) {
        super(`LLM服务 ${serviceName} 配额不足`, { serviceName });
        this.errorCode = 10003;
        this.statusCode = 429;
    }
}


// ==================== 导出所有异常 ====================

// 创建异常映射，便于根据错误码查找对应的异常类
const ExceptionMap = new Map();

// 自动注册所有异常类
const exceptions = {
    // 系统级
    SystemUnknownException,
    SystemBusyException,
    ServiceUnavailableException,
    TimeoutException,
    ConfigurationException,
    
    // 请求级
    ValidationException,
    MissingParameterException,
    InvalidParameterTypeException,
    ParameterTooLongException,
    ParameterOutOfRangeException,
    UnsupportedMediaTypeException,
    MethodNotAllowedException,
    
    // 认证授权
    UnauthorizedException,
    TokenExpiredException,
    InvalidTokenException,
    MissingTokenException,
    PermissionDeniedException,
    AccountDisabledException,
    AccountNotFoundException,
    InvalidPasswordException,
    TooManyLoginAttemptsException,
    
    // 用户
    UserAlreadyExistsException,
    EmailAlreadyExistsException,
    UserNotFoundException,
    CannotDisableAdminException,
    CannotDeleteAdminException,
    InvalidUserStatusException,
    
    // 邀请码
    InvalidInviteCodeException,
    InviteCodeAlreadyUsedException,
    InviteCodeExpiredException,
    InviteCodeGenerationException,
    CannotDeleteUsedInviteCodeException,
    
    // Agent
    AgentNotFoundException,
    AgentAlreadyExistsException,
    InvalidAgentStatusException,
    InvalidAgentToolsException,
    AgentCreationException,
    AgentUpdateException,
    AgentDeleteException,
    
    // 对话
    SessionNotFoundException,
    MessageSendException,
    AgentNotRespondingException,
    EmptyMessageException,
    MessageTooLongException,
    ConversationSaveException,
    
    // 数据库
    DatabaseException,
    DatabaseQueryException,
    DatabaseInsertException,
    DatabaseUpdateException,
    DatabaseDeleteException,
    DatabaseConnectionException,
    DuplicateKeyException,
    DataIntegrityException,
    
    // 缓存
    CacheException,
    RedisConnectionException,
    CacheOperationException,
    CacheKeyNotFoundException,
    
    // 第三方服务
    LLMServiceException,
    LLMResponseParseException,
    LLMQuotaExceededException
};

// 注册到Map
Object.values(exceptions).forEach(ExceptionClass => {
    if (ExceptionClass.prototype instanceof BaseException) {
        // 使用静态属性或实例属性获取错误码
        const tempInstance = new ExceptionClass();
        ExceptionMap.set(tempInstance.errorCode, ExceptionClass);
    }
});

// 根据错误码创建异常实例
function createExceptionFromCode(errorCode, details = null) {
    const ExceptionClass = ExceptionMap.get(errorCode);
    if (ExceptionClass) {
        return new ExceptionClass();
    }
    return new SystemUnknownException(details);
}

// 所有异常类已通过全局定义加载（非 ES module 方式）