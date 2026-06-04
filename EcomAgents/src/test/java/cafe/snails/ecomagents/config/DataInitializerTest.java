package cafe.snails.ecomagents.config;

import cafe.snails.ecomagents.model.User;
import cafe.snails.ecomagents.repository.EmojiPackRepository;
import cafe.snails.ecomagents.repository.ToolConfigRepository;
import cafe.snails.ecomagents.repository.UserRepository;
import cafe.snails.ecomagents.service.SessionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
/**
 * 数据初始化器测试，验证默认管理员、工具、表情和空会话清理逻辑。
 */
class DataInitializerTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private ToolConfigRepository toolConfigRepository;
    @Mock
    private EmojiPackRepository emojiPackRepository;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private SessionService sessionService;

    private DataInitializer initializer;

    @BeforeEach
    void setUp() {
        initializer = new DataInitializer(userRepository, toolConfigRepository,
                emojiPackRepository, passwordEncoder, sessionService);
    }

    @Test
    void run_shouldCreateAdminWhenMissingAndInitializeSeeds() {
        when(userRepository.findByUsername("admin")).thenReturn(Optional.empty());
        when(passwordEncoder.encode("123456")).thenReturn("encoded");
        when(toolConfigRepository.count()).thenReturn(0L);
        when(emojiPackRepository.count()).thenReturn(0L);
        when(sessionService.cleanupEmptySessions()).thenReturn(2);

        initializer.run();

        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(userCaptor.capture());
        assertEquals("admin", userCaptor.getValue().getUsername());
        assertEquals("encoded", userCaptor.getValue().getPassword());
        assertEquals("admin", userCaptor.getValue().getRole());
        verify(toolConfigRepository).saveAll(argThat(tools -> ((List<?>) tools).size() == 1));
        verify(emojiPackRepository).saveAll(argThat(emojis -> ((List<?>) emojis).size() > 20));
        verify(sessionService).cleanupEmptySessions();
    }

    @Test
    void run_shouldResetExistingAdminPasswordAndSkipExistingSeeds() {
        var admin = User.builder().username("admin").password("old").role("admin").build();
        when(userRepository.findByUsername("admin")).thenReturn(Optional.of(admin));
        when(passwordEncoder.encode("123456")).thenReturn("encoded");
        when(toolConfigRepository.count()).thenReturn(1L);
        when(emojiPackRepository.count()).thenReturn(1L);
        when(sessionService.cleanupEmptySessions()).thenReturn(0);

        initializer.run();

        assertEquals("encoded", admin.getPassword());
        verify(userRepository).save(admin);
        verify(toolConfigRepository, never()).saveAll(any());
        verify(emojiPackRepository, never()).saveAll(any());
    }

    @Test
    void initTools_shouldSkipWhenToolsExist() {
        when(toolConfigRepository.count()).thenReturn(1L);

        initializer.initTools();

        verify(toolConfigRepository, never()).saveAll(any());
    }

    @Test
    void initEmojis_shouldSkipWhenEmojisExist() {
        when(emojiPackRepository.count()).thenReturn(1L);

        initializer.initEmojis();

        verify(emojiPackRepository, never()).saveAll(any());
    }
}
