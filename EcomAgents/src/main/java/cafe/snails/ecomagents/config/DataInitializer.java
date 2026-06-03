package cafe.snails.ecomagents.config;

import cafe.snails.ecomagents.model.EmojiPack;
import cafe.snails.ecomagents.model.ToolConfig;
import cafe.snails.ecomagents.model.User;
import cafe.snails.ecomagents.repository.EmojiPackRepository;
import cafe.snails.ecomagents.repository.ToolConfigRepository;
import cafe.snails.ecomagents.repository.UserRepository;
import cafe.snails.ecomagents.service.SessionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

/**
 * 数据初始化器，应用启动时自动填充种子数据。
 * <p>仅包含默认用户和邀请码，模型、Agent 等由用户自行创建。</p>
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class DataInitializer implements CommandLineRunner {

    private final UserRepository userRepository;
    private final ToolConfigRepository toolConfigRepository;
    private final EmojiPackRepository emojiPackRepository;
    private final PasswordEncoder passwordEncoder;
    private final SessionService sessionService;

    @Override
    @Transactional
    public void run(String... args) {
        // 重置管理员账号：更新密码确保为 BCrypt 编码
        User admin = userRepository.findByUsername("admin").orElse(null);
        if (admin == null) {
            log.info("创建管理员账号...");
            admin = User.builder()
                    .username("admin").email("admin@ecomagents.com")
                    .password(passwordEncoder.encode("123456")).role("admin").status("active")
                    .inviteCode("ADMIN001").createdAt(LocalDate.of(2024, 1, 1)).build();
        } else {
            log.info("重置管理员密码...");
            admin.setPassword(passwordEncoder.encode("123456"));
        }
        userRepository.save(admin);

        log.info("数据初始化完成");
        initTools();
        initEmojis();
        cleanupEmptySessions();
    }

    /**
     * 初始化内置 Emoji 种子数据。
     */
    @Transactional
    protected void initEmojis() {
        if (emojiPackRepository.count() > 0) {
            log.info("Emoji 数据已存在，跳过初始化");
            return;
        }

        log.info("初始化内置 Emoji...");
        List<EmojiPack> emojis = List.of(
                // 笑脸与情感
                pack("😀", "smileys"), pack("😃", "smileys"), pack("😄", "smileys"),
                pack("😁", "smileys"), pack("😅", "smileys"), pack("😂", "smileys"),
                pack("🤣", "smileys"), pack("😊", "smileys"), pack("😇", "smileys"),
                pack("🙂", "smileys"), pack("😉", "smileys"), pack("😌", "smileys"),
                pack("😍", "smileys"), pack("🥰", "smileys"), pack("😘", "smileys"),
                pack("😗", "smileys"), pack("😋", "smileys"), pack("😛", "smileys"),
                pack("😜", "smileys"), pack("🤪", "smileys"), pack("😝", "smileys"),
                pack("🤑", "smileys"),
                // 手势与动作
                pack("👍", "gestures"), pack("👎", "gestures"), pack("👌", "gestures"),
                pack("✌", "gestures"), pack("🤞", "gestures"), pack("🤟", "gestures"),
                pack("🤙", "gestures"), pack("👋", "gestures"), pack("🤚", "gestures"),
                pack("✋", "gestures"), pack("👏", "gestures"), pack("🙌", "gestures"),
                pack("🤲", "gestures"), pack("🙏", "gestures"), pack("💪", "gestures"),
                // 爱心与符号
                pack("❤", "hearts"), pack("💛", "hearts"), pack("💚", "hearts"),
                pack("💙", "hearts"), pack("💜", "hearts"), pack("🖤", "hearts"),
                pack("💔", "hearts"), pack("💕", "hearts"), pack("💖", "hearts"),
                pack("💗", "hearts"), pack("💘", "hearts"), pack("💝", "hearts"),
                pack("💞", "hearts"), pack("💓", "hearts"), pack("❣", "hearts"),
                // 常用物品
                pack("🔥", "objects"), pack("⭐", "objects"), pack("🌟", "objects"),
                pack("✨", "objects"), pack("💫", "objects"), pack("🎉", "objects"),
                pack("🎊", "objects"), pack("🎈", "objects"), pack("🎁", "objects"),
                pack("🎂", "objects"), pack("🎄", "objects"), pack("🎃", "objects"),
                pack("🎀", "objects"), pack("🎗", "objects"), pack("🎫", "objects")
        );
        emojiPackRepository.saveAll(emojis);
        log.info("已初始化 {} 个 Emoji", emojis.size());
    }

    private static EmojiPack pack(String emoji, String category) {
        return EmojiPack.builder()
                .name(emoji)
                .imageUrl(emoji)
                .category(category)
                .build();
    }

    /**
     * 清理无消息的空壳会话，避免历史记录中出现无效会话。
     */
    private void cleanupEmptySessions() {
        int count = sessionService.cleanupEmptySessions();
        if (count > 0) {
            log.info("启动时清理了 {} 个空会话", count);
        }
    }

    /**
     * 初始化默认系统工具配置。
     */
    @Transactional
    protected void initTools() {
        if (toolConfigRepository.count() > 0) {
            log.info("工具配置已存在，跳过初始化");
            return;
        }

        log.info("初始化默认工具配置...");

        List<ToolConfig> tools = List.of(
                ToolConfig.builder().id("web_search").name("网页搜索")
                        .description("搜索互联网获取最新信息").category("web").build()
        );

        toolConfigRepository.saveAll(tools);
        log.info("已初始化 {} 个默认工具", tools.size());
    }
}
