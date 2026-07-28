package cafe.snails.ecomagents.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * JWT 认证过滤器，拦截每次请求并验证 JWT 令牌。
 * <p>支持两种令牌传递方式：
 * <ul>
 *   <li>{@code Authorization: Bearer <token>} 请求头</li>
 *   <li>{@code ?token=<token>} URL 查询参数（用于 SSE EventSource）</li>
 * </ul>
 * 验证通过后将 {@link JwtPrincipal} 设置到 SecurityContext 中，
 * 供 {@link CurrentUserIdArgumentResolver} 提取用户 ID。
 * </p>
 */
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    /** JWT 工具，用于解析和校验令牌。 */
    private final JwtUtil jwtUtil;
    /** 用户详情服务，用于加载认证主体的权限信息。 */
    private final CustomUserDetailsService userDetailsService;

    /**
     * 提取并校验请求中的 JWT，认证成功后写入安全上下文。
     */
    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String token = extractToken(request);

        if (token != null && jwtUtil.isTokenValid(token)) {
            String username = jwtUtil.getUsernameFromToken(token);
            UserDetails userDetails = userDetailsService.loadUserByUsername(username);

            UsernamePasswordAuthenticationToken authentication =
                    new UsernamePasswordAuthenticationToken(
                            userDetails, null, userDetails.getAuthorities());
            authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));

            // 将 JWT 中的 userId 和 role 存入 details 以便后续提取
            JwtPrincipal principal = new JwtPrincipal(
                    jwtUtil.getUserIdFromToken(token),
                    username,
                    jwtUtil.getRoleFromToken(token),
                    userDetails.getAuthorities()
            );
            authentication = new UsernamePasswordAuthenticationToken(
                    principal, null, userDetails.getAuthorities());
            authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));

            SecurityContextHolder.getContext().setAuthentication(authentication);
        }

        filterChain.doFilter(request, response);
    }

    /**
     * 从 Authorization 请求头或 SSE token 查询参数中提取 JWT。
     */
    private String extractToken(HttpServletRequest request) {
        String bearer = request.getHeader("Authorization");
        if (StringUtils.hasText(bearer) && bearer.startsWith("Bearer ")) {
            return bearer.substring(7);
        }
        // SSE EventSource 不支持自定义请求头，通过 query param 传递 token
        String tokenParam = request.getParameter("token");
        if (StringUtils.hasText(tokenParam)) {
            return tokenParam;
        }
        return null;
    }
}
