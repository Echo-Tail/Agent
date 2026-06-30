package cafe.snails.ecomagents.service;

import cafe.snails.ecomagents.dto.ApiResponse;
import cafe.snails.ecomagents.dto.UserDTO;
import cafe.snails.ecomagents.model.User;
import cafe.snails.ecomagents.repository.InviteCodeRepository;
import cafe.snails.ecomagents.repository.UserRepository;
import cafe.snails.ecomagents.security.JwtUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
/**
 * 用户服务测试，验证注册、登录、用户管理和权限相关行为。
 */
class UserServiceTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private InviteCodeRepository inviteCodeRepository;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private JwtUtil jwtUtil;

    private UserService service;

    private User adminUser;
    private User normalUser;

    @BeforeEach
    void setUp() {
        service = new UserService(userRepository, inviteCodeRepository, passwordEncoder, jwtUtil);

        adminUser = User.builder()
                .id(1L).username("admin").role("admin").status("active").build();

        normalUser = User.builder()
                .id(2L).username("user1").role("user").status("active").build();
    }

    /* ====== toggleStatus protection ====== */

    @Test
    void toggleStatus_self_shouldReturn400() {
        ApiResponse<UserDTO> result = service.toggleStatus(1L, 1L);

        assertEquals(400, result.getCode());
        assertEquals("不能禁用自己", result.getMessage());
        verifyNoInteractions(userRepository);
    }

    @Test
    void toggleStatus_otherAdmin_shouldReturn400() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(adminUser));

        ApiResponse<UserDTO> result = service.toggleStatus(1L, 2L);

        assertEquals(400, result.getCode());
        assertEquals("不能禁用管理员账号", result.getMessage());
        verify(userRepository, never()).save(any());
    }

    @Test
    void toggleStatus_normalUser_shouldSucceed() {
        when(userRepository.findById(2L)).thenReturn(Optional.of(normalUser));
        when(userRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        ApiResponse<UserDTO> result = service.toggleStatus(2L, 1L);

        assertEquals(200, result.getCode());
        assertEquals("disabled", result.getData().getStatus());
    }

    @Test
    void toggleStatus_notFound_shouldReturn404() {
        when(userRepository.findById(99L)).thenReturn(Optional.empty());

        ApiResponse<UserDTO> result = service.toggleStatus(99L, 1L);

        assertEquals(404, result.getCode());
    }

    /* ====== updateStatus protection ====== */

    @Test
    void updateStatus_self_shouldReturn400() {
        ApiResponse<UserDTO> result = service.updateStatus(1L, "disabled", 1L);

        assertEquals(400, result.getCode());
        assertEquals("不能禁用自己", result.getMessage());
    }

    @Test
    void updateStatus_otherAdmin_shouldReturn400() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(adminUser));

        ApiResponse<UserDTO> result = service.updateStatus(1L, "disabled", 2L);

        assertEquals(400, result.getCode());
        assertEquals("不能禁用管理员账号", result.getMessage());
    }
}
