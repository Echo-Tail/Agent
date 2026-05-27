package cafe.snails.ecomagents.config;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Locale;

/**
 * 模型定价配置枚举，记录各模型每百万 Token 的美元单价。
 * <p>通过 {@link #match(String)} 按 modelName 前缀匹配定价，未匹配返回 null（按免费处理）。</p>
 *
 * <pre>
 * 价格来源：docs/token价格.md（2026 年最新大模型换算矩阵）
 * </pre>
 */
public enum ModelPriceConfig {

    // ===== OpenAI =====
    GPT55("GPT-5.5", "openai", new BigDecimal("5.00"), new BigDecimal("15.00")),
    GPT54("GPT-5.4", "openai", new BigDecimal("5.00"), new BigDecimal("15.00")),
    GPT54_MINI("GPT-5.4 Mini", "openai", new BigDecimal("0.75"), new BigDecimal("4.50")),

    // ===== Anthropic / Claude =====
    CLAUDE_OPUS("Claude Opus", "anthropic", new BigDecimal("5.00"), new BigDecimal("25.00")),
    CLAUDE_SONNET("Claude Sonnet", "anthropic", new BigDecimal("3.00"), new BigDecimal("15.00")),

    // ===== DeepSeek =====
    DEEPSEEK_PRO("DeepSeek V4 Pro", "deepseek", new BigDecimal("1.74"), new BigDecimal("3.48")),
    DEEPSEEK_FLASH("DeepSeek V4 Flash", "deepseek", new BigDecimal("0.14"), new BigDecimal("0.28"));

    private final String key;           // 用于前缀匹配的标识
    private final String provider;      // 供应商
    private final BigDecimal inputPrice;   // 每百万 Token 输入价格（美元）
    private final BigDecimal outputPrice;  // 每百万 Token 输出价格（美元）

    ModelPriceConfig(String key, String provider, BigDecimal inputPrice, BigDecimal outputPrice) {
        this.key = key;
        this.provider = provider;
        this.inputPrice = inputPrice;
        this.outputPrice = outputPrice;
    }

    public String getKey() { return key; }
    public String getProvider() { return provider; }
    public BigDecimal getInputPrice() { return inputPrice; }
    public BigDecimal getOutputPrice() { return outputPrice; }

    /**
     * 按 modelName 前缀匹配定价。例如 "GPT-5.5" 匹配 "GPT-5.5" 开头的任何模型名。
     * 长 key 优先匹配。
     *
     * @param modelName 完整模型名
     * @return 匹配的定价枚举，未匹配返回 null
     */
    public static ModelPriceConfig match(String modelName) {
        if (modelName == null || modelName.isBlank()) return null;
        String name = modelName.trim();
        ModelPriceConfig best = null;
        int bestLen = 0;
        for (ModelPriceConfig cfg : values()) {
            if (name.toLowerCase(Locale.ROOT).startsWith(cfg.key.toLowerCase(Locale.ROOT))) {
                if (cfg.key.length() > bestLen) {
                    best = cfg;
                    bestLen = cfg.key.length();
                }
            }
        }
        return best;
    }

    /**
     * 计算单次调用的 CNY 费用。
     *
     * @param promptTokens     输入 token 数
     * @param completionTokens 输出 token 数
     * @param usdCnyRate       美元兑人民币汇率
     * @return CNY 费用，保留 2 位小数
     */
    public BigDecimal calculateCost(int promptTokens, int completionTokens, BigDecimal usdCnyRate) {
        BigDecimal promptCost = BigDecimal.valueOf(promptTokens)
                .divide(BigDecimal.valueOf(1_000_000), 10, RoundingMode.HALF_UP)
                .multiply(inputPrice);
        BigDecimal completionCost = BigDecimal.valueOf(completionTokens)
                .divide(BigDecimal.valueOf(1_000_000), 10, RoundingMode.HALF_UP)
                .multiply(outputPrice);
        return promptCost.add(completionCost)
                .multiply(usdCnyRate)
                .setScale(2, RoundingMode.HALF_UP);
    }
}
