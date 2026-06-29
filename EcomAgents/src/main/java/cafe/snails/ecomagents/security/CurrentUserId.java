package cafe.snails.ecomagents.security;

import java.lang.annotation.*;

/**
 * 从 JWT 认证上下文中提取当前用户 ID 的注解。
 * 用于 Controller 方法参数，自动注入 Long 类型的 userId。
 *
 * <pre>{@code
 * @PostMapping("/agents")
 * public ApiResponse<Agent> createAgent(@RequestBody Agent agent,
 *                                       @CurrentUserId Long userId)
 * }</pre>
 */
@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface CurrentUserId {
}
