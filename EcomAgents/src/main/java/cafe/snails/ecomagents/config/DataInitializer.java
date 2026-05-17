package cafe.snails.ecomagents.config;

import cafe.snails.ecomagents.model.InviteCode;
import cafe.snails.ecomagents.model.User;
import cafe.snails.ecomagents.repository.InviteCodeRepository;
import cafe.snails.ecomagents.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

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

    @Override
    @Transactional
    public void run(String... args) {
        if (userRepository.count() > 0) {
            log.info("数据库已存在数据，跳过初始化");
            return;
        }

        log.info("初始化种子数据...");

        userRepository.save(User.builder()
                .username("admin").email("admin@ecomagents.com")
                .password("123456").role("admin").status("active")
                .inviteCode("ADMIN001").createdAt(LocalDate.of(2024, 1, 1)).build());
        userRepository.save(User.builder()
                .username("张三").email("zhangsan@example.com")
                .password("123456").role("user").status("active")
                .inviteCode("INVITE001").createdAt(LocalDate.of(2024, 1, 15)).build());
        userRepository.save(User.builder()
                .username("李四").email("lisi@example.com")
                .password("123456").role("user").status("active")
                .inviteCode("INVITE002").createdAt(LocalDate.of(2024, 2, 1)).build());

        inviteCodeRepository.save(InviteCode.builder().code("INVITE001")
                .used(true).usedBy("张三").usedByUserId(2L)
                .createdAt(LocalDate.of(2024, 1, 10)).build());
        inviteCodeRepository.save(InviteCode.builder().code("FREE001")
                .used(false).createdAt(LocalDate.of(2024, 3, 1)).build());
        inviteCodeRepository.save(InviteCode.builder().code("FREE002")
                .used(false).createdAt(LocalDate.of(2024, 3, 1)).build());
        inviteCodeRepository.save(InviteCode.builder().code("EC2026")
                .used(false).createdAt(LocalDate.of(2026, 5, 1)).build());
        inviteCodeRepository.save(InviteCode.builder().code("AGENT01")
                .used(false).createdAt(LocalDate.of(2026, 5, 1)).build());

        log.info("种子数据初始化完成");
    }
}
