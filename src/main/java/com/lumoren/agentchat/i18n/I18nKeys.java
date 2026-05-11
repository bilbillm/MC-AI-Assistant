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
}
