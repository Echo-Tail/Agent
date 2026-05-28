package cafe.snails.ecomagents.security;

import cafe.snails.ecomagents.model.User;
import cafe.snails.ecomagents.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 自定义用户认证服务，从数据库加载用户信息。
 * <p>实现 Spring Security 的 {@link UserDetailsService} 接口：
 * <ul>
 *   <li>根据用户名查询 {@link User} 表</li>
 *   <li>检查账号状态是否为 {@code active}</li>
 *   <li>构建用户角色权限（{@code ROLE_USER} / {@code ROLE_ADMIN}）</li>
 * </ul>
 * </p>
 */
@Service
@RequiredArgsConstructor
public class CustomUserDetailsService implements UserDetailsService {

    private final UserRepository userRepository;

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new UsernameNotFoundException("用户不存在: " + username));

        if (!"active".equals(user.getStatus())) {
            throw new UsernameNotFoundException("账号已被禁用");
        }

        List<SimpleGrantedAuthority> authorities = List.of(
                new SimpleGrantedAuthority("ROLE_" + user.getRole().toUpperCase())
        );

        return new org.springframework.security.core.userdetails.User(
                user.getUsername(),
                user.getPassword(),
                authorities
        );
    }
}
