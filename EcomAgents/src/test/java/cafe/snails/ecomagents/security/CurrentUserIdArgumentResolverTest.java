package cafe.snails.ecomagents.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.MethodParameter;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.ModelAndViewContainer;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * {@link CurrentUserIdArgumentResolver} 的单元测试。
 */
@ExtendWith(MockitoExtension.class)
class CurrentUserIdArgumentResolverTest {

    private CurrentUserIdArgumentResolver resolver;

    @Mock
    private MethodParameter methodParameter;
    @Mock
    private ModelAndViewContainer mavContainer;
    @Mock
    private NativeWebRequest webRequest;
    @Mock
    private WebDataBinderFactory binderFactory;

    @BeforeEach
    void setUp() {
        resolver = new CurrentUserIdArgumentResolver();
        SecurityContextHolder.clearContext();
    }

    @Test
    void supportsParameter_shouldReturnTrueForAnnotatedLong() {
        when(methodParameter.hasParameterAnnotation(CurrentUserId.class)).thenReturn(true);
        when(methodParameter.getParameterType()).thenReturn((Class) Long.class);
        assertTrue(resolver.supportsParameter(methodParameter));
    }

    @Test
    void supportsParameter_shouldReturnFalseWithoutAnnotation() {
        when(methodParameter.hasParameterAnnotation(CurrentUserId.class)).thenReturn(false);
        assertFalse(resolver.supportsParameter(methodParameter));
    }

    @Test
    void supportsParameter_shouldReturnFalseForNonLong() {
        when(methodParameter.hasParameterAnnotation(CurrentUserId.class)).thenReturn(true);
        when(methodParameter.getParameterType()).thenReturn((Class) String.class);
        assertFalse(resolver.supportsParameter(methodParameter));
    }

    @Test
    void resolveArgument_shouldReturnUserId() {
        var principal = new JwtPrincipal(42L, "testuser", "USER", List.of());
        Authentication auth = new org.springframework.security.authentication
                .UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities());
        SecurityContextHolder.getContext().setAuthentication(auth);

        Object result = resolver.resolveArgument(methodParameter, mavContainer, webRequest, binderFactory);

        assertEquals(42L, result);
    }

    @Test
    void resolveArgument_shouldThrowWhenNotAuthenticated() {
        SecurityContextHolder.getContext().setAuthentication(null);
        assertThrows(IllegalStateException.class, () ->
                resolver.resolveArgument(methodParameter, mavContainer, webRequest, binderFactory));
    }
}
