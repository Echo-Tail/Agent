package cafe.snails.ecomagents.service;

import com.knuddels.jtokkit.Encodings;
import com.knuddels.jtokkit.api.Encoding;
import com.knuddels.jtokkit.api.EncodingRegistry;
import com.knuddels.jtokkit.api.EncodingType;
import org.springframework.stereotype.Component;

/**
 * Token 计数器，使用 jtokkit 对文本做精确分词计数。
 * <p>
 * 根据模型名自动选择编码，未知模型 fallback 到 cl100k_base。
 * </p>
 */
@Component
public class TokenCounter {

    /** jtokkit 编码注册表。 */
    private static final EncodingRegistry REGISTRY = Encodings.newDefaultEncodingRegistry();

    /**
     * 计算文本的 token 数。
     *
     * @param modelName 模型名，如 gpt-4o、deepseek-chat
     * @param text      要计算的文本
     * @return token 数
     */
    public int count(String modelName, String text) {
        if (text == null || text.isBlank()) return 0;
        Encoding encoding = REGISTRY.getEncoding(getEncodingType(modelName));
        return encoding.countTokens(text);
    }

    /**
     * 根据模型名选择编码类型。
     * <ul>
     *   <li>gpt-4o 系列 → o200k_base</li>
     *   <li>其他（含 deepseek、qwen 等）→ cl100k_base</li>
     * </ul>
     */
    static EncodingType getEncodingType(String modelName) {
        if (modelName == null) return EncodingType.CL100K_BASE;
        String lower = modelName.toLowerCase();
        if (lower.startsWith("gpt-4o") || lower.startsWith("gpt-4.5")) {
            return EncodingType.O200K_BASE;
        }
        return EncodingType.CL100K_BASE;
    }
}
