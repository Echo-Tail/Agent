package cafe.snails.ecomagents.config;

import cafe.snails.ecomagents.security.JwtAuthenticationFilter;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.util.matcher.DispatcherTypeRequestMatcher;

import jakarta.servlet.DispatcherType;
import jakarta.servlet.http.HttpServletResponse;
import java.util.Map;

/**
 * Spring Security 配置。定义 JWT 无状态认证、公开端点白名单、管理员端点隔离。
 * <p>认证流程：JwtAuthenticationFilter → SecurityContextHolder → @CurrentUserId</p>
 */
@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthFilter;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session ->
                        session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        // SSE 异步派发不重新认证（安全上下文已在初始请求中建立）
                        .requestMatchers(new DispatcherTypeRequestMatcher(DispatcherType.ASYNC)).permitAll()
                        // 公开端点
                        .requestMatchers("/v1/login", "/v1/register").permitAll()
                        .requestMatchers(HttpMethod.GET, "/v1/files/*/download").permitAll()
                        .requestMatchers(HttpMethod.GET, "/v1/groups/*/files/*/download").permitAll()
                        .requestMatchers(HttpMethod.GET, "/v1/groups/*/files/*").permitAll()
                        .requestMatchers("/error").permitAll()
                        .requestMatchers("/uploads/**").permitAll()
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        // 普通用户可查看单个用户信息
                        .requestMatchers(HttpMethod.GET, "/v1/users/*").authenticated()
                        // 管理员端点
                        .requestMatchers("/v1/users/**").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.GET, "/v1/models", "/v1/models/**").authenticated()
                        .requestMatchers("/v1/models/**").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.GET, "/v1/tools", "/v1/tools/**").authenticated()
                        .requestMatchers("/v1/tools/**").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.POST, "/v1/knowledge-bases").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.PUT, "/v1/knowledge-bases", "/v1/knowledge-bases/*").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.DELETE, "/v1/knowledge-bases/*").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.GET, "/v1/knowledge-bases/audit-logs").hasRole("ADMIN")
                        .requestMatchers("/v1/invite-codes/**").hasRole("ADMIN")
                        .requestMatchers("/v1/admin/tickets/**").hasRole("ADMIN")
                        // 系统日志：写入需登录，查询/清空仅管理员
                        .requestMatchers(HttpMethod.GET, "/v1/system-logs", "/v1/system-logs/**").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.DELETE, "/v1/system-logs").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.POST, "/v1/system-logs").authenticated()
                        // 其余端点需认证
                        .anyRequest().authenticated()
                )
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint((request, response, authException) -> {
                            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                            response.setCharacterEncoding("UTF-8");
                            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                            ObjectMapper mapper = new ObjectMapper();
                            mapper.writeValue(response.getWriter(),
                                    Map.of("code", 401, "message", "未登录或登录已过期"));
                        })
                )
                .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
