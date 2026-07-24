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
 * Spring Security 閰嶇疆銆傚畾涔?JWT 鏃犵姸鎬佽璇併€佸叕寮€绔偣鐧藉悕鍗曘€佺鐞嗗憳绔偣闅旂銆?
 * <p>璁よ瘉娴佺▼锛欽wtAuthenticationFilter 鈫?SecurityContextHolder 鈫?@CurrentUserId</p>
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
                        // SSE 寮傛娲惧彂涓嶉噸鏂拌璇侊紙瀹夊叏涓婁笅鏂囧凡鍦ㄥ垵濮嬭姹備腑寤虹珛锛?
                        .requestMatchers(new DispatcherTypeRequestMatcher(DispatcherType.ASYNC)).permitAll()
                        // 鍏紑绔偣
                        .requestMatchers("/v1/login", "/v1/register").permitAll()
                        .requestMatchers(HttpMethod.GET, "/v1/files/*/download").permitAll()
                        .requestMatchers(HttpMethod.GET, "/v1/groups/*/files/*/download").permitAll()
                        .requestMatchers(HttpMethod.GET, "/v1/groups/*/files/*").permitAll()
                        .requestMatchers("/error").permitAll()
                        .requestMatchers("/actuator/health", "/actuator/health/**").permitAll()
                        .requestMatchers("/actuator/**").hasRole("ADMIN")
                        .requestMatchers("/uploads/**").permitAll()
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        // 绠＄悊鍛樼鐐?
                        .requestMatchers("/v1/users/**").hasRole("ADMIN")
                        .requestMatchers("/v1/model-credentials/**").hasRole("ADMIN")
                        .requestMatchers("/v1/admin/image-runtime/**").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.GET, "/v1/models", "/v1/models/**").authenticated()
                        .requestMatchers("/v1/models/**").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.GET, "/v1/tools", "/v1/tools/**").authenticated()
                        .requestMatchers("/v1/tools/**").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.POST, "/v1/knowledge-bases").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.PUT, "/v1/knowledge-bases", "/v1/knowledge-bases/*").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.DELETE, "/v1/knowledge-bases/*").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.GET, "/v1/knowledge-bases/audit-logs").hasRole("ADMIN")
                                                .requestMatchers("/v1/invite-codes/**").hasRole("ADMIN")
                        .requestMatchers("/v1/assets/**").authenticated()
                        .requestMatchers("/v1/admin/tickets/**").hasRole("ADMIN")
                        // 绠＄悊鍛樹笅鏋剁敾寤婁綔鍝?
                        .requestMatchers(HttpMethod.DELETE, "/v1/gallery/items/*/admin").hasRole("ADMIN")
                        // 绯荤粺鏃ュ織锛氬啓鍏ラ渶鐧诲綍锛屾煡璇?娓呯┖浠呯鐞嗗憳
                        .requestMatchers(HttpMethod.GET, "/v1/system-logs", "/v1/system-logs/**").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.DELETE, "/v1/system-logs").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.POST, "/v1/system-logs").authenticated()
                        .requestMatchers(HttpMethod.POST, "/v1/client-logs").authenticated()
                        // 鍏朵綑绔偣闇€璁よ瘉
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

    /**
     * 鎻愪緵 BCrypt 瀵嗙爜缂栫爜鍣ㄣ€?
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}

