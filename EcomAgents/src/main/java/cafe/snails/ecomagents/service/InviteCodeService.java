package cafe.snails.ecomagents.service;

import cafe.snails.ecomagents.dto.ApiResponse;
import cafe.snails.ecomagents.model.InviteCode;
import cafe.snails.ecomagents.repository.InviteCodeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 邀请码业务逻辑，支持批量生成和删除。
 */
@Service
@RequiredArgsConstructor
public class InviteCodeService {

    /** 邀请码可用字符集。 */
    private static final String CODE_CHARS = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
    /** 邀请码固定长度。 */
    private static final int CODE_LENGTH = 8;
    /** 安全随机数生成器，用于降低邀请码可预测性。 */
    private final SecureRandom secureRandom = new SecureRandom();

    /** 邀请码仓库。 */
    private final InviteCodeRepository inviteCodeRepository;

    /** 获取所有邀请码列表 */
    public ApiResponse<List<InviteCode>> listCodes() {
        return ApiResponse.success(inviteCodeRepository.findAll());
    }

    /**
     * 批量生成邀请码。
     * 每个码为 8 位大写字母+数字组合（字符集 36），使用 SecureRandom 确保随机性。
     */
    @Transactional
    public ApiResponse<List<InviteCode>> batchGenerate(int count) {
        List<InviteCode> codes = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (int i = 0; i < count; i++) {
            String code = generateUniqueCode(seen);
            seen.add(code);
            InviteCode ic = InviteCode.builder()
                    .code(code)
                    .used(false)
                    .createdAt(LocalDate.now())
                    .build();
            codes.add(ic);
        }
        inviteCodeRepository.saveAll(codes);
        return ApiResponse.success("生成成功", codes);
    }

    /** 生成一个不重复的随机邀请码 */
    private String generateUniqueCode(Set<String> seen) {
        StringBuilder sb = new StringBuilder(CODE_LENGTH);
        for (int attempt = 0; attempt < 10; attempt++) {
            sb.setLength(0);
            for (int j = 0; j < CODE_LENGTH; j++) {
                sb.append(CODE_CHARS.charAt(secureRandom.nextInt(CODE_CHARS.length())));
            }
            String code = sb.toString();
            if (!seen.contains(code) && inviteCodeRepository.findById(code).isEmpty()) {
                return code;
            }
        }
        // Fallback: extremely unlikely to reach here
        return sb.toString();
    }

    /** 删除指定邀请码（已使用的不可删除） */
    @Transactional
    public ApiResponse<Void> deleteCode(String code) {
        return inviteCodeRepository.findById(code)
                .map(ic -> {
                    if (ic.getUsed()) {
                        return ApiResponse.<Void>error(400, "邀请码已使用，无法删除");
                    }
                    inviteCodeRepository.delete(ic);
                    return ApiResponse.<Void>success("删除成功", null);
                })
                .orElse(ApiResponse.error(404, "邀请码不存在"));
    }
}
