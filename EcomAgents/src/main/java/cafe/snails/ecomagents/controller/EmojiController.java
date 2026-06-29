package cafe.snails.ecomagents.controller;

import cafe.snails.ecomagents.dto.ApiResponse;
import cafe.snails.ecomagents.model.EmojiPack;
import cafe.snails.ecomagents.repository.EmojiPackRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 表情包控制器。
 */
@RestController
@RequestMapping("/v1/emoji")
@RequiredArgsConstructor
public class EmojiController {

    private final EmojiPackRepository emojiPackRepository;

    /** 获取所有内置表情包 */
    @GetMapping("/packs")
    public ApiResponse<List<EmojiPack>> listEmojiPacks() {
        return ApiResponse.success(emojiPackRepository.findAllByOrderByCreatedAtAsc());
    }
}
