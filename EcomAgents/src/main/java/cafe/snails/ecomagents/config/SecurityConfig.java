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
                        .requestMatchers("/error").permitAll()
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        // 管理员端点
                        .requestMatchers("/v1/users/**").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.GET, "/v1/models", "/v1/models/**").authenticated()
                        .requestMatchers("/v1/models/**").hasRole("ADMIN")
                        .requestMatchers("/v1/tools/**").hasRole("ADMIN")
                        .requestMatchers("/v1/invite-codes/**").hasRole("ADMIN")
                        .requestMatchers("/v1/admin/tickets/**").hasRole("ADMIN")
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
