package cafe.snails.ecomagents.security;

import cafe.snails.ecomagents.model.User;
import cafe.snails.ecomagents.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

import java.time.LocalDate;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

/**
 * {@link CustomUserDetailsService} 的单元测试。
 */
@ExtendWith(MockitoExtension.class)
class CustomUserDetailsServiceTest {

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private CustomUserDetailsService userDetailsService;

    @Test
    void loadUserByUsername_shouldReturnUserDetails() {
        var user = User.builder()
                .id(1L)
                .username("testuser")
                .password("encoded-password")
                .role("user")
                .status("active")
                .createdAt(LocalDate.now())
                .build();
        when(userRepository.findByUsername("testuser")).thenReturn(Optional.of(user));

        UserDetails details = userDetailsService.loadUserByUsername("testuser");

        assertEquals("testuser", details.getUsername());
        assertEquals("encoded-password", details.getPassword());
        assertTrue(details.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_USER")));
    }

    @Test
    void loadUserByUsername_shouldUseAdminRole() {
        var user = User.builder()
                .id(2L)
                .username("admin")
                .password("pwd")
                .role("admin")
                .status("active")
                .createdAt(LocalDate.now())
                .build();
        when(userRepository.findByUsername("admin")).thenReturn(Optional.of(user));

        UserDetails details = userDetailsService.loadUserByUsername("admin");

        assertTrue(details.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN")));
    }

    @Test
    void loadUserByUsername_shouldThrowForNonexistentUser() {
        when(userRepository.findByUsername("unknown")).thenReturn(Optional.empty());

        assertThrows(UsernameNotFoundException.class,
                () -> userDetailsService.loadUserByUsername("unknown"));
    }

    @Test
    void loadUserByUsername_shouldThrowForDisabledUser() {
        var user = User.builder()
                .id(3L)
                .username("disabled_user")
                .password("pwd")
                .role("user")
                .status("disabled")
                .createdAt(LocalDate.now())
                .build();
        when(userRepository.findByUsername("disabled_user")).thenReturn(Optional.of(user));

        assertThrows(UsernameNotFoundException.class,
                () -> userDetailsService.loadUserByUsername("disabled_user"));
    }
}
