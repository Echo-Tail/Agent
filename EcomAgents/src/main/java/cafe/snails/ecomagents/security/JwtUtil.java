package cafe.snails.ecomagents.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

/**
 * JWT 令牌工具类。
 * <p>使用 HMAC-SHA256 算法签名，负责：
 * <ul>
 *   <li>生成带 userId / username / role 声明的令牌</li>
 *   <li>解析和验证令牌签名与有效期</li>
 *   <li>从令牌中提取用户 ID、用户名、角色</li>
 * </ul>
 * </p>
 */
@Component
public class JwtUtil {

    /** HMAC 签名密钥，由配置项 jwt.secret 派生。 */
    private final SecretKey key;
    /** 令牌有效期，单位为秒。 */
    private final long expiration;

    /**
     * 创建 JWT 工具并初始化签名密钥和过期时间。
     */
    public JwtUtil(@Value("${jwt.secret}") String secret,
                   @Value("${jwt.expiration}") long expiration) {
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.expiration = expiration;
    }

    /**
     * 生成包含用户 ID、用户名和角色声明的访问令牌。
     */
    public String generateToken(Long userId, String username, String role) {
        Date now = new Date();
        return Jwts.builder()
                .subject(username)
                .claim("userId", userId)
                .claim("role", role)
                .issuedAt(now)
                .expiration(new Date(now.getTime() + expiration * 1000L))
                .signWith(key)
                .compact();
    }

    /**
     * 解析并验证令牌签名，返回 Claims；签名无效或过期时抛出异常。
     */
    public Claims parseToken(String token) {
        return Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    /**
     * 判断令牌是否能通过签名和过期时间验证。
     */
    public boolean isTokenValid(String token) {
        try {
            parseToken(token);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /** 从令牌声明中读取用户 ID。 */
    public Long getUserIdFromToken(String token) {
        return parseToken(token).get("userId", Long.class);
    }

    /** 从令牌 subject 中读取用户名。 */
    public String getUsernameFromToken(String token) {
        return parseToken(token).getSubject();
    }

    /** 从令牌声明中读取用户角色。 */
    public String getRoleFromToken(String token) {
        return parseToken(token).get("role", String.class);
    }
}
