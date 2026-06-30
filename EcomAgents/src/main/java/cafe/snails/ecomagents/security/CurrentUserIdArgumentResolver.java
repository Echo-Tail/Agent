package cafe.snails.ecomagents.security;

import org.springframework.core.MethodParameter;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

/**
 * {@code @CurrentUserId} 注解的参数解析器。
 * <p>从 Spring SecurityContext 中提取当前认证用户的 {@link JwtPrincipal}，
 * 将其 userId 注入到 Controller 方法标注了 {@code @CurrentUserId} 的 Long 参数中。</p>
 * <p>若请求未认证或 Principal 类型不匹配，抛出 {@link IllegalStateException}。</p>
 */
@Component
public class CurrentUserIdArgumentResolver implements HandlerMethodArgumentResolver {

    /**
     * 判断参数是否为带 @CurrentUserId 注解的 Long 类型。
     */
    @Override
    public boolean supportsParameter(MethodParameter parameter) {
        return parameter.hasParameterAnnotation(CurrentUserId.class)
                && parameter.getParameterType().equals(Long.class);
    }

    /**
     * 从当前 SecurityContext 中解析 JwtPrincipal 并返回用户 ID。
     */
    @Override
    public Object resolveArgument(MethodParameter parameter,
                                  ModelAndViewContainer mavContainer,
                                  NativeWebRequest webRequest,
                                  WebDataBinderFactory binderFactory) {
        Authentication authentication =
                org.springframework.security.core.context.SecurityContextHolder
                        .getContext().getAuthentication();

        if (authentication != null && authentication.getPrincipal() instanceof JwtPrincipal principal) {
            return principal.getUserId();
        }

        throw new IllegalStateException("无法获取当前用户 ID，请确认请求已认证");
    }
}
