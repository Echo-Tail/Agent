package cafe.snails.ecomagents.config;

import cafe.snails.ecomagents.model.InviteCode;
import cafe.snails.ecomagents.model.ToolConfig;
import cafe.snails.ecomagents.model.User;
import cafe.snails.ecomagents.repository.InviteCodeRepository;
import cafe.snails.ecomagents.repository.ToolConfigRepository;
import cafe.snails.ecomagents.repository.UserRepository;
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
    private final InviteCodeRepository inviteCodeRepository;
    private final ToolConfigRepository toolConfigRepository;
    private final PasswordEncoder passwordEncoder;

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

        // 删除除管理员外的所有用户
        Long adminId = admin.getId();
        var toDelete = userRepository.findAll().stream()
                .filter(u -> !u.getId().equals(adminId)).toList();
        if (!toDelete.isEmpty()) {
            userRepository.deleteAll(toDelete);
            log.info("已删除 {} 个非管理员用户", toDelete.size());
        }

        // 初始化邀请码（仅当不存在时）
        if (inviteCodeRepository.count() == 0) {
            log.info("初始化邀请码...");
            inviteCodeRepository.save(InviteCode.builder().code("FREE001")
                    .used(false).createdAt(LocalDate.of(2024, 3, 1)).build());
            inviteCodeRepository.save(InviteCode.builder().code("FREE002")
                    .used(false).createdAt(LocalDate.of(2024, 3, 1)).build());
            inviteCodeRepository.save(InviteCode.builder().code("EC2026")
                    .used(false).createdAt(LocalDate.of(2026, 5, 1)).build());
            inviteCodeRepository.save(InviteCode.builder().code("AGENT01")
                    .used(false).createdAt(LocalDate.of(2026, 5, 1)).build());
        }

        log.info("数据初始化完成");
        initTools();
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
                        .description("搜索互联网获取最新信息").category("web").build(),
                ToolConfig.builder().id("image_generation").name("图片生成")
                        .description("根据文字描述生成图片").category("media").build(),
                ToolConfig.builder().id("browser_automation").name("浏览器自动化")
                        .description("自动浏览网页并提取内容").category("browser").build(),
                ToolConfig.builder().id("file_operation").name("文件操作")
                        .description("读取和写入本地文件").category("terminal_files").build(),
                ToolConfig.builder().id("code_execution").name("代码执行")
                        .description("运行 Python / JavaScript 等代码片段").category("terminal_files").build(),
                ToolConfig.builder().id("memory_read").name("记忆读取")
                        .description("读取持久化的对话记忆").category("memory").build()
        );

        toolConfigRepository.saveAll(tools);
        log.info("已初始化 {} 个默认工具", tools.size());
    }
}
