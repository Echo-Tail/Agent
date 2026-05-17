package cafe.snails.ecomagents.service;

import cafe.snails.ecomagents.dto.ApiResponse;
import cafe.snails.ecomagents.model.InviteCode;
import cafe.snails.ecomagents.repository.InviteCodeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * 邀请码业务逻辑，支持批量生成和删除。
 */
@Service
@RequiredArgsConstructor
public class InviteCodeService {

    private final InviteCodeRepository inviteCodeRepository;

    /** 获取所有邀请码列表 */
    public ApiResponse<List<InviteCode>> listCodes() {
        return ApiResponse.success(inviteCodeRepository.findAll());
    }

    /**
     * 批量生成邀请码。
     * 每个码由指定前缀 + 随机十六进制字符串构成。
     */
    @Transactional
    public ApiResponse<List<InviteCode>> batchGenerate(int count, String prefix) {
        List<InviteCode> codes = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            String random = Long.toHexString(Double.doubleToLongBits(Math.random())).toUpperCase();
            String code = prefix != null && !prefix.isEmpty() ? prefix + random.substring(0, 6) : random.substring(0, 8);
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
