package com.lumoren.agentchat.i18n;

/**
 * 所有国际化翻译键的常量定义。
 * <p>
 * 命名规范：常量名 = 大写字母 + 下划线，对应 lang JSON 文件中的键名。
 * 分组说明：
 * <ul>
 *   <li>UI —— 界面文字（标题、按钮、占位符等）</li>
 *   <li>LOADING —— 加载 / 等待状态</li>
 *   <li>ERROR —— 错误信息</li>
 *   <li>CONFIG —— 配置界面</li>
 *   <li>KEY —— 按键绑定</li>
 *   <li>STATUS —— 连接 / 状态提示</li>
 * </ul>
 */
public final class I18nKeys {

    private I18nKeys() {
        // 工具类，禁止实例化
    }

    // ==================== UI ====================
    /** 聊天标题 */
    public static final String TITLE = "agentchat.title";
    /** 输入框占位符 */
    public static final String INPUT_PLACEHOLDER = "agentchat.input.placeholder";
    /** 发送按钮 */
    public static final String BUTTON_SEND = "agentchat.button.send";
    /** 新对话按钮 */
    public static final String BUTTON_NEW_THREAD = "agentchat.button.new_thread";
    /** 空对话提示 */
    public static final String EMPTY_CONVERSATION = "agentchat.empty_conversation";
    /** 新对话标题 */
    public static final String THREAD_NEW = "agentchat.thread.new";
    /** 保存按钮 */
    public static final String BUTTON_SAVE = "agentchat.button.save";
    /** 取消按钮 */
    public static final String BUTTON_CANCEL = "agentchat.button.cancel";
    /** 配置按钮 */
    public static final String BUTTON_CONFIG = "agentchat.button.config";
    /** 删除按钮 */
    public static final String BUTTON_DELETE = "agentchat.button.delete";
    public static final String BUTTON_GUIDE_MODE = "agentchat.button.guide_mode";
    public static final String BUTTON_CHAT_MODE = "agentchat.button.chat_mode";

    // ==================== LOADING ====================
    /** AI 思考中 */
    public static final String LOADING_THINKING = "agentchat.loading.thinking";
    /** 正在调用工具（含参数） */
    public static final String LOADING_TOOL_CALL = "agentchat.loading.tool_call";

    // ==================== ERROR ====================
    /** 未配置 API Key */
    public static final String ERROR_NO_API_KEY = "agentchat.error.no_api_key";
    /** 网络错误 */
    public static final String ERROR_NETWORK = "agentchat.error.network";
    /** 请求频率限制 */
    public static final String ERROR_RATE_LIMIT = "agentchat.error.rate_limit";
    /** 请求超时 */
    public static final String ERROR_TIMEOUT = "agentchat.error.timeout";
    /** API 认证失败 */
    public static final String ERROR_AUTH_ERROR = "agentchat.error.auth_error";
    /** 未知错误（含参数） */
    public static final String ERROR_UNKNOWN = "agentchat.error.unknown";
    /** 工具执行错误（含参数） */
    public static final String ERROR_TOOL_EXECUTION = "agentchat.error.tool_execution";

    // ==================== CONFIG ====================
    /** 配置界面标题 */
    public static final String CONFIG_TITLE = "agentchat.config.title";
    /** API Key 配置项 */
    public static final String CONFIG_API_KEY = "agentchat.config.api_key";
    /** 模型选择 */
    public static final String CONFIG_MODEL = "agentchat.config.model";
    /** 温度参数 */
    public static final String CONFIG_TEMPERATURE = "agentchat.config.temperature";
    /** 最大 Token */
    public static final String CONFIG_MAX_TOKENS = "agentchat.config.max_tokens";
    /** API 地址 */
    public static final String CONFIG_BASE_URL = "agentchat.config.base_url";

    // ==================== CONFIG HINTS ====================
    public static final String CONFIG_HINT_API_KEY = "agentchat.config.hint.api_key";
    public static final String CONFIG_HINT_BASE_URL = "agentchat.config.hint.base_url";
    public static final String CONFIG_HINT_MODEL = "agentchat.config.hint.model";
    public static final String CONFIG_HINT_MAX_TOKENS = "agentchat.config.hint.max_tokens";
    public static final String CONFIG_GUIDE_MODE_DEFAULT = "agentchat.config.guide_mode_default";
    public static final String CONFIG_WEB_KNOWLEDGE = "agentchat.config.web_knowledge";

    // ==================== KEY BINDINGS ====================
    /** 按键分类名 */
    public static final String KEY_CATEGORY = "agentchat.key.category";
    /** 打开聊天 */
    public static final String KEY_OPEN_CHAT = "agentchat.key.open_chat";

    // ==================== COMMAND ====================
    /** 配置界面已打开 */
    public static final String COMMAND_CONFIG_OPENED = "agentchat.command.config_opened";
    /** API Key 已设置 */
    public static final String COMMAND_KEY_SET = "agentchat.command.key_set";
    /** 对话已清空 */
    public static final String COMMAND_CLEAR_SUCCESS = "agentchat.command.clear_success";
    /** 线程列表标题（含参数：数量） */
    public static final String COMMAND_THREADS_HEADER = "agentchat.command.threads_header";
    /** 线程列表条目（含参数：id前缀, 名称, 消息数） */
    public static final String COMMAND_THREADS_ENTRY = "agentchat.command.threads_entry";
    /** 线程已创建（含参数：名称, ID） */
    public static final String COMMAND_THREAD_CREATED = "agentchat.command.thread_created";
    /** 线程已切换（含参数：名称） */
    public static final String COMMAND_THREAD_SWITCHED = "agentchat.command.thread_switched";
    /** 线程未找到（含参数：ID） */
    public static final String COMMAND_THREAD_NOT_FOUND = "agentchat.command.thread_not_found";

    // ==================== STATUS ====================
    /** 已连接 */
    public static final String STATUS_CONNECTED = "agentchat.status.connected";
    /** 未连接 */
    public static final String STATUS_DISCONNECTED = "agentchat.status.disconnected";
    /** 正在发送 */
    public static final String STATUS_SENDING = "agentchat.status.sending";
    public static final String STATUS_GUIDE_ENABLED = "agentchat.status.guide_enabled";
    public static final String STATUS_CHAT_ENABLED = "agentchat.status.chat_enabled";
    public static final String STATUS_CANCEL_PROJECT_CARD_BLOCKED = "agentchat.status.cancel_project_card_blocked";

    // ==================== ACTION CARD ====================
    /** 动作按钮：查看配方 */
    public static final String ACTION_LABEL_OPEN_RECIPE = "agentchat.action.label.open_recipe";
    /** 动作按钮：查看用途 */
    public static final String ACTION_LABEL_OPEN_USAGE = "agentchat.action.label.open_usage";
    /** 动作按钮：标记完成 */
    public static final String ACTION_LABEL_MARK_DONE = "agentchat.action.label.mark_done";
    /** 动作按钮：设为当前 */
    public static final String ACTION_LABEL_SET_CURRENT = "agentchat.action.label.set_current";
    /** 动作按钮：查看项目 */
    public static final String ACTION_LABEL_SHOW_PROJECT = "agentchat.action.label.show_project";
    /** 动作按钮：询问目标 */
    public static final String ACTION_LABEL_ASK_GOAL = "agentchat.action.label.ask_goal";
    /** 动作按钮：搜索资料 */
    public static final String ACTION_LABEL_SEARCH_SOURCES = "agentchat.action.label.search_sources";
    /** 动作按钮：澄清物品 */
    public static final String ACTION_LABEL_CLARIFY_ITEM = "agentchat.action.label.clarify_item";
    /** 不支持的操作 */
    public static final String ACTION_ERROR_UNKNOWN_ACTION = "agentchat.action.error.unknown_action";
    /** 操作缺少物品目标 */
    public static final String ACTION_ERROR_MISSING_ITEM = "agentchat.action.error.missing_item";
    /** 无法识别物品（含参数） */
    public static final String ACTION_ERROR_UNKNOWN_ITEM = "agentchat.action.error.unknown_item";
    /** 操作缺少任务目标 */
    public static final String ACTION_ERROR_MISSING_TASK = "agentchat.action.error.missing_task";
    /** 操作缺少追问内容 */
    public static final String ACTION_ERROR_MISSING_QUERY = "agentchat.action.error.missing_query";
    /** 已在 JEI 中打开（含参数） */
    public static final String ACTION_STATUS_JEI_OPENED = "agentchat.action.status.jei_opened";
    /** JEI 不可用，回退本地数据 */
    public static final String ACTION_STATUS_JEI_UNAVAILABLE_FALLBACK = "agentchat.action.status.jei_unavailable_fallback";
    /** JEI 打开失败，回退本地数据 */
    public static final String ACTION_STATUS_JEI_FAILED_FALLBACK = "agentchat.action.status.jei_failed_fallback";
    /** 重复点击被忽略 */
    public static final String ACTION_STATUS_IGNORED_DUPLICATE = "agentchat.action.status.ignored_duplicate";

    // ==================== GUIDE PIPELINE ====================
    /** 向导阶段：收集本地信息 */
    public static final String GUIDE_PHASE_COLLECTING = "agentchat.guide.phase.collecting";
    /** 向导阶段：查询指南与任务书 */
    public static final String GUIDE_PHASE_QUERYING = "agentchat.guide.phase.querying";
    /** 向导阶段：搜索网络资料 */
    public static final String GUIDE_PHASE_WEB = "agentchat.guide.phase.web";

    // ==================== MECHANICAL UI ====================
    /** 模式分段：聊天 */
    public static final String MODE_CHAT = "agentchat.mode.chat";
    /** 模式分段：向导 */
    public static final String MODE_GUIDE = "agentchat.mode.guide";
    /** 工具提示：发送 */
    public static final String TOOLTIP_SEND = "agentchat.tooltip.send";
    /** 工具提示：新建对话 */
    public static final String TOOLTIP_NEW_THREAD = "agentchat.tooltip.new_thread";
    /** 工具提示：设置 */
    public static final String TOOLTIP_CONFIG = "agentchat.tooltip.config";
    /** 工具提示：删除对话 */
    public static final String TOOLTIP_DELETE_THREAD = "agentchat.tooltip.delete_thread";
    /** 工具提示：收起侧栏 */
    public static final String TOOLTIP_COLLAPSE_SIDEBAR = "agentchat.tooltip.collapse_sidebar";
    /** 工具提示：展开侧栏 */
    public static final String TOOLTIP_EXPAND_SIDEBAR = "agentchat.tooltip.expand_sidebar";
    /** 工具提示：复制 */
    public static final String TOOLTIP_COPY = "agentchat.tooltip.copy";
    /** 工具提示：编辑 */
    public static final String TOOLTIP_EDIT = "agentchat.tooltip.edit";
    /** 工具提示：撤回 */
    public static final String TOOLTIP_RECALL = "agentchat.tooltip.recall";
    /** 工具提示：关闭 */
    public static final String TOOLTIP_CLOSE = "agentchat.tooltip.close";
    /** 工具提示：对话历史 */
    public static final String TOOLTIP_HISTORY = "agentchat.tooltip.history";
    /** 侧栏标题：对话历史 */
    public static final String SIDEBAR_HISTORY = "agentchat.sidebar.history";
    /** 线程消息数（含参数） */
    public static final String THREAD_MESSAGES = "agentchat.thread.messages";
    /** 输入框占位符（向导模式） */
    public static final String INPUT_PLACEHOLDER_GUIDE = "agentchat.input.placeholder.guide";

    // ==================== PROJECT SYSTEM ====================
    /** HUD 项目进度（含参数：名称, 已完成, 总数） */
    public static final String PROJECT_HUD_PROGRESS = "agentchat.project.hud.progress";
    /** HUD 无活跃项目 */
    public static final String PROJECT_HUD_NO_PROJECT = "agentchat.project.hud.no_project";
    /** 项目完成（含参数：项目名称） */
    public static final String PROJECT_COMPLETE = "agentchat.project.complete";
    /** 创建项目确认 */
    public static final String PROJECT_CREATE_CONFIRM = "agentchat.project.create.confirm";
    /** 项目创建失败 */
    public static final String PROJECT_CREATE_FAILED = "agentchat.project.create.failed";
    /** 步骤已添加（含参数：步骤名称） */
    public static final String PROJECT_TASK_ADDED = "agentchat.project.task.added";
    /** 步骤已删除（含参数：步骤名称） */
    public static final String PROJECT_TASK_REMOVED = "agentchat.project.task.removed";
    /** 项目已归档（含参数：项目名称） */
    public static final String PROJECT_ARCHIVED = "agentchat.project.archived";
    /** 项目已恢复（含参数：项目名称） */
    public static final String PROJECT_REACTIVATED = "agentchat.project.reactivated";
    /** 步骤已阻塞（含参数：步骤名称） */
    public static final String PROJET_TASK_BLOCKED = "agentchat.project.task.blocked";
}
