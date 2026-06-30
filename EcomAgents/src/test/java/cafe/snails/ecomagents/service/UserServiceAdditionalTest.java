package cafe.snails.ecomagents.service;

import cafe.snails.ecomagents.dto.LoginRequest;
import cafe.snails.ecomagents.dto.RegisterRequest;
import cafe.snails.ecomagents.model.InviteCode;
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

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
/**
 * 用户服务补充测试，覆盖用户状态切换和管理操作边界。
 */
class UserServiceAdditionalTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private InviteCodeRepository inviteCodeRepository;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private JwtUtil jwtUtil;

    private UserService service;
    private User user;

    @BeforeEach
    void setUp() {
        service = new UserService(userRepository, inviteCodeRepository, passwordEncoder, jwtUtil);
        user = User.builder()
                .id(2L)
                .username("user1")
                .email("u@example.com")
                .password("encoded")
                .role("user")
                .status("active")
                .inviteCode("INVITE")
                .build();
    }

    @Test
    void login_unknownUser_shouldReturn401() {
        when(userRepository.findByUsername("missing")).thenReturn(Optional.empty());

        assertEquals(401, service.login(loginRequest("missing", "pw")).getCode());
    }

    @Test
    void login_wrongPassword_shouldReturn401() {
        when(userRepository.findByUsername("user1")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("bad", "encoded")).thenReturn(false);

        assertEquals(401, service.login(loginRequest("user1", "bad")).getCode());
    }

    @Test
    void login_disabledUser_shouldReturn403() {
        user.setStatus("disabled");
        when(userRepository.findByUsername("user1")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("pw", "encoded")).thenReturn(true);

        assertEquals(403, service.login(loginRequest("user1", "pw")).getCode());
    }

    @Test
    void login_validCredentials_shouldReturnTokenAndUser() {
        when(userRepository.findByUsername("user1")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("pw", "encoded")).thenReturn(true);
        when(jwtUtil.generateToken(2L, "user1", "user")).thenReturn("jwt");

        var result = service.login(loginRequest("user1", "pw"));

        assertEquals(200, result.getCode());
        assertEquals("jwt", result.getData().getToken());
        assertEquals("user1", result.getData().getUser().getUsername());
    }

    @Test
    void register_invalidInvite_shouldReturn400() {
        var request = registerRequest("new-user", "new@example.com", "pw123456", "BAD");
        when(inviteCodeRepository.findByCodeAndUsedFalse("BAD")).thenReturn(Optional.empty());

        var result = service.register(request);

        assertEquals(400, result.getCode());
        verify(userRepository, never()).save(any());
    }

    @Test
    void register_duplicateUsername_shouldReturn400() {
        var request = registerRequest("user1", null, "pw123456", "INVITE");
        when(inviteCodeRepository.findByCodeAndUsedFalse("INVITE"))
                .thenReturn(Optional.of(InviteCode.builder().code("INVITE").build()));
        when(userRepository.existsByUsername("user1")).thenReturn(true);

        var result = service.register(request);

        assertEquals(400, result.getCode());
        verify(userRepository, never()).save(any());
    }

    @Test
    void register_validRequest_shouldCreateUserAndMarkInviteUsed() {
        var request = registerRequest("new-user", null, "pw123456", "INVITE");
        var invite = InviteCode.builder().code("INVITE").used(false).build();
        when(inviteCodeRepository.findByCodeAndUsedFalse("INVITE")).thenReturn(Optional.of(invite));
        when(userRepository.existsByUsername("new-user")).thenReturn(false);
        when(passwordEncoder.encode("pw123456")).thenReturn("encoded");
        when(userRepository.save(any())).thenAnswer(invocation -> {
            User saved = invocation.getArgument(0);
            saved.setId(5L);
            return saved;
        });

        var result = service.register(request);

        assertEquals(200, result.getCode());
        assertEquals("new-user", result.getData().getUsername());
        assertEquals("", result.getData().getEmail());
        assertTrue(invite.getUsed());
        assertEquals("new-user", invite.getUsedBy());
        assertEquals(5L, invite.getUsedByUserId());
        verify(inviteCodeRepository).save(invite);
    }

    @Test
    void listSearchAndGetUsers_shouldMapEntitiesToDtos() {
        when(userRepository.findAll()).thenReturn(List.of(user));
        when(userRepository.findByUsernameContainingIgnoreCase("ser")).thenReturn(List.of(user));
        when(userRepository.findById(2L)).thenReturn(Optional.of(user));
        when(userRepository.findById(99L)).thenReturn(Optional.empty());

        assertEquals(1, service.listUsers().getData().size());
        assertEquals(1, service.searchUsers(" ser ").getData().size());
        assertEquals(1, service.searchUsers(" ").getData().size());
        assertEquals("user1", service.getUser(2L).getData().getUsername());
        assertEquals(404, service.getUser(99L).getCode());
    }

    @Test
    void updateStatus_normalUser_shouldPersistRequestedStatus() {
        when(userRepository.findById(2L)).thenReturn(Optional.of(user));
        when(userRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        var result = service.updateStatus(2L, "disabled", 1L);

        assertEquals(200, result.getCode());
        assertEquals("disabled", result.getData().getStatus());
    }

    private static LoginRequest loginRequest(String username, String password) {
        var request = new LoginRequest();
        request.setUsername(username);
        request.setPassword(password);
        return request;
    }

    private static RegisterRequest registerRequest(String username, String email, String password, String inviteCode) {
        var request = new RegisterRequest();
        request.setUsername(username);
        request.setEmail(email);
        request.setPassword(password);
        request.setInviteCode(inviteCode);
        return request;
    }
}
