package cafe.snails.ecomagents.controller;

import cafe.snails.ecomagents.dto.ApiResponse;
import cafe.snails.ecomagents.dto.BatchInviteRequest;
import cafe.snails.ecomagents.model.InviteCode;
import cafe.snails.ecomagents.service.InviteCodeService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 邀请码管理控制器，支持列表查看、批量生成和删除。
 */
@RestController
@RequestMapping("/v1")
@RequiredArgsConstructor
public class InviteCodeController {

    private final InviteCodeService inviteCodeService;

    /** 获取所有邀请码 */
    @GetMapping("/invite-codes")
    public ApiResponse<List<InviteCode>> listCodes() {
        return inviteCodeService.listCodes();
    }

    /** 批量生成邀请码 */
    @PostMapping("/invite-codes/batch")
    public ApiResponse<List<InviteCode>> batchGenerate(@Valid @RequestBody BatchInviteRequest request) {
        return inviteCodeService.batchGenerate(request.getCount());
    }

    /** 删除指定邀请码 */
    @DeleteMapping("/invite-codes/{code}")
    public ApiResponse<Void> deleteCode(@PathVariable("code") String code) {
        return inviteCodeService.deleteCode(code);
    }
}
