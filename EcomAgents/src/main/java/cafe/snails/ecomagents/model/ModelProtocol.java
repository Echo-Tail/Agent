package cafe.snails.ecomagents.model;

/**
 * 调用 AI 模型时使用的接口协议。
 */
public enum ModelProtocol {
    OPENAI_CHAT,
    OPENAI_EMBEDDING,
    OLLAMA_EMBEDDING,
    OPENAI_IMAGE,
    BAILIAN_IMAGE
}
