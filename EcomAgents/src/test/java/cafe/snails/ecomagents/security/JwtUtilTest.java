package cafe.snails.ecomagents.security;

import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;

/**
 * {@link JwtUtil} 的单元测试。
 * <p>覆盖令牌生成、解析、验证、提取各类声明。</p>
 */
@ExtendWith(MockitoExtension.class)
class JwtUtilTest {

    /** 使用固定密钥确保测试可重现 */
    private static final String TEST_SECRET = "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef";
    private static final long TEST_EXPIRATION = 3600L;

    private JwtUtil jwtUtil;

    @BeforeEach
    void setUp() {
        jwtUtil = new JwtUtil(TEST_SECRET, TEST_EXPIRATION);
    }

    @Test
    void generateToken_shouldCreateValidToken() {
        String token = jwtUtil.generateToken(1L, "testuser", "USER");
        assertNotNull(token);
        assertTrue(token.split("\\.").length == 3, "JWT should have 3 parts");
    }

    @Test
    void parseToken_shouldReturnCorrectClaims() {
        String token = jwtUtil.generateToken(42L, "alice", "ADMIN");
        Claims claims = jwtUtil.parseToken(token);

        assertEquals("alice", claims.getSubject());
        assertEquals(42L, claims.get("userId", Long.class).longValue());
        assertEquals("ADMIN", claims.get("role"));
    }

    @Test
    void isTokenValid_shouldReturnTrueForValidToken() {
        String token = jwtUtil.generateToken(1L, "bob", "USER");
        assertTrue(jwtUtil.isTokenValid(token));
    }

    @Test
    void isTokenValid_shouldReturnFalseForExpiredToken() {
        // 使用过期时间 = 0 生成立即过期的令牌
        JwtUtil shortLived = new JwtUtil(TEST_SECRET, 0L);
        String token = shortLived.generateToken(1L, "bob", "USER");
        assertFalse(shortLived.isTokenValid(token));
    }

    @Test
    void isTokenValid_shouldReturnFalseForGarbage() {
        assertFalse(jwtUtil.isTokenValid("garbage.token.string"));
        assertFalse(jwtUtil.isTokenValid(""));
        assertFalse(jwtUtil.isTokenValid(null));
    }

    @Test
    void getUserIdFromToken_shouldReturnCorrectId() {
        String token = jwtUtil.generateToken(99L, "charlie", "USER");
        assertEquals(99L, jwtUtil.getUserIdFromToken(token).longValue());
    }

    @Test
    void getUsernameFromToken_shouldReturnSubject() {
        String token = jwtUtil.generateToken(1L, "dave", "USER");
        assertEquals("dave", jwtUtil.getUsernameFromToken(token));
    }

    @Test
    void getRoleFromToken_shouldReturnRole() {
        String token = jwtUtil.generateToken(1L, "eve", "ADMIN");
        assertEquals("ADMIN", jwtUtil.getRoleFromToken(token));
    }
}
