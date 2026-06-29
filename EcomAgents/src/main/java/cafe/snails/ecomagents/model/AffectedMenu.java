package cafe.snails.ecomagents.model;

/**
 * 工单受影响菜单枚举，用于标识问题发生的前端功能区域。
 */
public enum AffectedMenu {
    /** 仪表盘。 */
    DASHBOARD,
    /** 对话页面。 */
    CHAT,
    /** Agent 列表或管理页面。 */
    AGENT_LIST,
    /** 历史会话页面。 */
    HISTORY,
    /** 我的工单页面。 */
    MY_TICKETS,
    /** 知识库页面。 */
    KNOWLEDGE_BASE,
    /** 系统或个人设置页面。 */
    SETTINGS,
    /** Token 用量页面。 */
    TOKEN_USAGE,
    /** 系统日志页面。 */
    LOGS,
    /** 用户管理页面。 */
    USER_MANAGE,
    /** 模型管理页面。 */
    MODEL_MANAGE,
    /** 工具管理页面。 */
    TOOL_MANAGE,
    /** 技能管理页面。 */
    SKILL_MANAGE,
    /** 未归类或其它区域。 */
    OTHER
}
