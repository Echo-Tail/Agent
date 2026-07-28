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

    /** 当前认证用户的数据库主键。 */
    private final Long userId;
    /** JWT 声明中携带的用户角色。 */
    private final String jwtRole;

    /**
     * 创建携带业务用户标识和角色的认证主体。
     */
    public JwtPrincipal(Long userId, String username, String jwtRole,
                        Collection<? extends GrantedAuthority> authorities) {
        super(username, "", authorities);
        this.userId = userId;
        this.jwtRole = jwtRole;
    }
}
