package cafe.snails.ecomagents.security;

import lombok.Getter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.User;

import java.util.Collection;

/**
 * 扩展 Spring Security User，携带 JWT 中的 userId 和 role。
 */
@Getter
public class JwtPrincipal extends User {

    private final Long userId;
    private final String jwtRole;

    public JwtPrincipal(Long userId, String username, String jwtRole,
                        Collection<? extends GrantedAuthority> authorities) {
        super(username, "", authorities);
        this.userId = userId;
        this.jwtRole = jwtRole;
    }
}
