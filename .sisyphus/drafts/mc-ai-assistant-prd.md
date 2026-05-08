# MC-AI-Assistant — 产品需求文档 (PRD)

> **版本**: v1.0 | **日期**: 2026-05-09 | **作者**: lumoren
> **协议**: GNU LGPL v3 | **仓库**: GitHub — `lumoren/MC-AI-Assistant`

---

## 1. 产品概述

### 1.1 一句话描述

在 Minecraft 中嵌入一个 AI 对话助手——像 JEI 一样在物品栏界面显示，玩家可以用自然语言询问游戏相关问题，AI 实时查
询游戏数据并给出准确回答。

### 1.2 产品愿景

让每个 Minecraft 玩家都有一个随身的"游戏顾问"。不需要切出游戏查 Wiki、翻配方、算坐标——直接问 AI，它知道你的
背包有什么、你在哪里、钻石剑怎么做、现在是什么时间。

### 1.3 核心价值主张

| 痛点 | 解决方案 |
|------|---------|
| 查合成配方需要切出游戏 | AI 直接告诉你配方，还能根据背包材料判断是否可合成 |
| 背包太乱找不到东西 | AI 快速列出背包内容，筛选特定物品 |
| 新手不知道下一步该做什么 | AI 根据当前进度（装备、位置、时间）给出建议 |
| 忘记坐标/维度/时间 | AI 即时查询，无需手动记 |

---

## 2. 目标用户

### 2.1 用户画像

| 画像 | 特征 | 核心需求 |
|------|------|---------|
| **新手矿工** | 刚入坑 MC，不熟悉合成表和游戏机制 | "钻石剑怎么做？""铁矿石哪里找？" |
| **生存玩家** | 熟悉基础但经常需要查配方、管理背包 | "背包里有几种食物？""哪些材料可以做床？" |
| **模组玩家** | 玩大量 Mod，配方和物品记不住 | "这个 Mod 加了什么新东西？""机械动力的齿轮怎么做？"（第二期） |
| **技术探索者** | 对 AI 感兴趣，想体验 AI+游戏结合 | "试试让 AI 帮我分析一下我现在的装备怎么样" |

### 2.2 用户场景

**场景 1：新手求助**
> 小明天黑前想做一把石剑防身。打开物品栏，右侧 AI 面板输入"石剑怎么做？"。AI 回答："需要 2 个圆石 + 1 个木
棍。你的背包里有 12 个圆石但缺少木棍——用 2 个木板合成 4 个木棍吧。"

**场景 2：背包管理**
> 小红挖矿回来背包满了。打开物品栏问"我背包里有哪些值钱的东西？"。AI 列出钻石、金锭、青金石等珍贵物品，建议
丢弃圆石和泥土。

**场景 3：进度查询**
> 小刚想确认游戏进度。打开物品栏问"我现在的装备怎么样？"。AI 回答："你穿着铁套（铁胸甲、铁护腿），但头盔是皮
的，靴子是空的。建议优先合成铁靴——你有 5 个铁锭，够用了。"

---

## 3. 功能需求

### 3.1 V1 核心功能（本次交付）

#### F1：JEI 式侧边栏

- **触发方式**：打开物品栏（E 键）时自动显示在界面右侧
- **位置**：屏幕右侧，宽度 30%（可配置 20%-50%），最小 200px
- **组件**：标题栏 + 消息列表（可滚动） + 输入框 + 发送按钮 + 新对话按钮
- **行为**：关闭物品栏时自动隐藏，正常游玩时不显示任何 HUD 元素
- **配置**：侧边栏位置（LEFT/RIGHT）、宽度可在配置中调整

#### F2：全屏聊天模式

- **触发方式**：按 ` 键（反引号，可自定义）
- **界面**：独立全屏聊天页面，更大显示区域
- **行为**：不暂停游戏（`isPauseScreen() = false`），ESC 关闭

#### F3：自由对话 + Function Calling

- 玩家用自然语言提问
- AI 自动判断是否需要查询游戏数据
- 如需查询→调用对应工具→获取数据→给出回答
- 支持流式输出（逐字打字效果）

#### F4：游戏数据查询工具（5 个）

| 工具 | 功能 | 示例问题 |
|------|------|---------|
| `get_inventory` | 查询玩家背包所有物品 | "我背包里有什么？""有哪些食物？" |
| `lookup_recipe` | 查询物品合成/熔炼配方 | "钻石剑怎么做？""铁矿石能烧吗？" |
| `get_player_status` | 查询坐标、维度、生命值、饥饿值 | "我在哪？""我的坐标是多少？" |
| `item_info` | 查询物品详细属性 | "钻石剑有多少耐久？""面包能回复多少饥饿？" |
| `get_world_state` | 查询时间、天气、难度 | "现在几点了？""会不会下雨？" |

#### F5：存档隔离的对话历史

- 每个 Minecraft 存档独立的对话历史
- 每个存档可创建多个对话线程
- 线程支持新建、切换、删除、重命名
- 历史自动保存（每 3 分钟 + 退出时）
- 消息上限：默认 200 条/线程（可配置）

#### F6：配置系统

- **API Key**：支持指令设置、GUI 设置、直接编辑 JSON 三选一
- **模型配置**：Base URL、Model、Temperature、Max Tokens 通过 TOML 配置
- **UI 配置**：侧边栏位置、宽度
- **配置指令**：`/agentchat config`、`/agentchat key set <key>`
- **配置 GUI**：通过 Mod Menu 集成或指令打开

#### F7：国际化（i18n）

- 默认语言：根据 Minecraft 客户端语言自动选择
- 首期支持：中文（简体）、英文
- 后续开放社区贡献更多语言

### 3.2 V2 规划（明确不在 V1）

| 功能 | 说明 |
|------|------|
| **Mod 互操作** | 读取小地图 Mod、机械动力等第三方 Mod 数据 |
| **可读写操作** | AI 可帮玩家合成、切换装备（需确认） |
| **多渠道分发** | CurseForge、Modrinth 自动发布 |
| **语音输入** | Whisper API 集成 |
| **截图分析** | AI 可"看懂"当前游戏画面 |
| **多 AI 提供商原生支持** | Claude、Gemini 等独立 API 适配 |

---

## 4. 非功能需求

### 4.1 性能

| 指标 | 要求 |
|------|------|
| **FPS 影响** | 物品栏界面打开时 FPS 降低 < 5%，正常游玩时 0% 影响 |
| **API 响应** | 首次 token 延迟 < 2s（网络正常时） |
| **UI 渲染** | 侧边栏渲染 < 1ms/frame |
| **内存占用** | < 50MB 额外内存（含聊天历史） |
| **启动影响** | Mod 加载 < 1s（不含 API 预热） |

### 4.2 可靠性

| 指标 | 要求 |
|------|------|
| **API 故障** | 网络错误/超时时显示明确提示，不崩溃 |
| **API Key 未配置** | 显示引导提示，不白屏 |
| **速率限制** | 自动识别 429 错误，指数退避重试 |
| **存档损坏** | 历史 JSON 损坏时重置为空，不阻止 Mod 加载 |

### 4.3 安全性

| 要求 | 说明 |
|------|------|
| **API Key 不泄露** | 不在日志、崩溃报告、截图中暴露 |
| **纯只读** | 不可通过 AI 执行任何游戏操作 |
| **本地存储** | 聊天历史仅存本地，不上传 |
| **请求体控制** | 限制单次请求体 < 32KB，避免 token 浪费 |

### 4.4 兼容性

| 项目 | 要求 |
|------|------|
| **MC 版本** | NeoForge 1.21.1 |
| **Java 版本** | Java 21+ |
| **操作系统** | Windows / macOS / Linux |
| **JEI 兼容** | 与 JEI 同时安装无 UI 冲突（侧边栏默认右侧，可改为左侧避开） |
| **其他 Mod** | 不修改原版物品栏布局，无侵入性 Hook |

---

## 5. UI/UX 规范

### 5.1 侧边栏布局

```
┌───────────────────┬─────────────────────┐
│                   │  🤖 AI 助手          │ ← 标题栏
│                   │  ──────────────────  │
│   物品栏区域      │  ┌─────────────────┐ │
│   (原版不变)      │  │ AI: 你好！      │ │ ← 消息气泡
│                   │  │ 我能帮你什么？  │ │
│                   │  └─────────────────┘ │
│                   │  ┌─────────────────┐ │
│                   │  │     用户: 我背包  │ │
│                   │  │     里有什么？  │ │
│                   │  └─────────────────┘ │
│                   │  ──────────────────  │
│                   │  [向 AI 提问...    ] │ ← 输入框
│                   │  [发送] [新对话]     │ ← 按钮
└───────────────────┴─────────────────────┘
```

### 5.2 设计原则

- **不抢戏**：面板是辅助工具，不遮挡核心游戏内容
- **即看即走**：打开物品栏就能用，关闭立即消失
- **渐进加载**：消息按需渲染，不一次性加载全部历史
- **一致视觉**：颜色、字体、间距与 Minecraft 原生 UI 风格一致

### 5.3 交互规范

- **输入激活**：打开物品栏后自动聚焦输入框（可选关闭）
- **发送消息**：Enter 发送，Shift+Enter 换行
- **滚动**：鼠标滚轮滚动消息列表，新消息到达自动滚到底部
- **状态反馈**：输入中（蓝色动画）、查询工具中（"正在查询..."）、错误（红色提示）
- **快捷键**：` 键（反引号）打开全屏模式。物品栏内 Esc 先关闭侧边栏焦点。Controls 菜单可自定义。

### 5.4 错误状态

| 状态 | UI 表现 |
|------|---------|
| **无 API Key** | 蓝色引导卡片："🔑 需要配置 API Key。输入 `/agentchat key set <key>` 或打开配置界面" |
| **网络错误** | 红色提示："⚠️ 网络连接失败，请检查网络。点击重试" |
| **API 401** | 红色提示："❌ API Key 无效，请重新设置" |
| **API 429** | 黄色提示："⏳ 请求频繁，稍后自动重试..."
| **加载中** | 三个点动画 + "AI 正在思考..." |
| **工具执行中** | 灰色提示："🔍 正在查询背包..." |

---

## 6. 技术架构

### 6.1 技术栈

| 层 | 技术选择 | 理由 |
|----|---------|------|
| **运行平台** | NeoForge 1.21.1 | 现代、活跃维护的 MC Mod 平台 |
| **开发语言** | Java 21 | MC 1.21 最低要求 |
| **构建工具** | Gradle + ModDevGradle | NeoForge 标准构建 |
| **UI 渲染** | Minecraft GuiGraphics / Screen | 原生 API，零额外依赖 |
| **HTTP 客户端** | Java 21 java.net.http.HttpClient | 内置，无 OkHttp 冲突 |
| **JSON 解析** | Jackson (via simple-openai) | AI API 响应解析 |
| **配置存储** | NeoForge ModConfigSpec (TOML) + 独立 JSON | 标准 + 安全分离 |
| **持久化** | Gson → JSON 文件 | 轻量，易调试 |
| **测试** | JUnit 5 + Mockito + GameTest | 标准 Java 测试栈 |
| **AI API** | OpenAI 兼容协议 | 支持 OpenAI / DeepSeek / 通义千问 等 |

### 6.2 模块架构

```
mcaiassistant/
├── AgentChat.java              # @Mod 主类
├── client/
│   └── ClientModEvents.java    # 客户端事件注册
├── config/
│   ├── Config.java             # ModConfigSpec (TOML 配置)
│   ├── SecretsConfig.java      # API Key JSON 读写
│   └── ConfigManager.java      # 统一配置门面
├── model/
│   ├── ChatMessage.java        # 消息模型
│   ├── ToolDefinition.java     # 工具定义
│   ├── ConversationThread.java # 对话线程
│   └── ...                     # 其他数据模型
├── data/
│   └── GameDataAccess.java     # 游戏数据访问层
├── ai/
│   ├── OpenAICompatClient.java # HTTP 客户端
│   ├── ToolCallDispatcher.java # Function Calling 调度
│   └── AIChatService.java      # 高层 AI 服务
├── tools/
│   ├── GameTool.java           # 工具接口
│   ├── ToolRegistry.java       # 工具注册表
│   ├── InventoryTool.java
│   ├── RecipeTool.java
│   ├── PositionTool.java
│   ├── ItemEncyclopediaTool.java
│   └── WorldStateTool.java
├── ui/
│   ├── ChatMessageWidget.java  # 消息气泡组件
│   ├── MessageListWidget.java  # 可滚动消息列表
│   ├── AIChatSidebarPanel.java # 侧边栏面板
│   ├── AIChatScreen.java       # 全屏聊天
│   ├── ConfigScreen.java       # 配置 GUI
│   └── MarkdownRenderer.java   # Markdown 解析
├── persistence/
│   ├── ChatHistoryManager.java # 历史持久化
│   └── ConversationManager.java # 多会话管理
└── i18n/
    ├── I18nKeys.java           # 翻译键常量
    └── I18nHelper.java         # 翻译辅助
```

### 6.3 数据流

```
玩家输入文字
    │
    ▼
AIChatService.sendMessage()
    │
    ├─► 构建 system prompt + 历史消息 + tools 定义
    │
    ▼
OpenAICompatClient.chatCompletionStreaming()
    │
    ├─► HTTP POST → AI API（流式 SSE）
    │
    ▼
解析 delta（SSE 事件）
    │
    ├── delta.content → onToken() → UI 增量渲染（打字机效果）
    │
    └── delta.tool_calls → ToolCallDispatcher
            │
            ├── 解析 tool_calls[]
            ├── ToolRegistry.executeTool(name, args)
            │       │
            │       └── GameDataAccess.getXxx()
            │               │
            │               └── Minecraft.getInstance().player/level
            │
            └── 结果作为 tool 消息回传 AI
                    │
                    └── AI 继续生成 → onToken() → 最终回答
```

---

## 7. 成功指标

### 7.1 质量指标

| 指标 | 目标 |
|------|------|
| **编译通过** | `./gradlew build` 零错误零警告 |
| **测试覆盖** | ≥30 个 JUnit 测试，全部通过 |
| **Momus 审查** | OKAY（已通过 ✅） |
| **代码行数** | < 5000 行 Java（不含构建文件） |

### 7.2 体验指标

| 指标 | 目标 |
|------|------|
| **首次配置时间** | < 2 分钟（从启动到发出第一条消息） |
| **消息响应** | < 5 秒（含工具调用）|
| **崩溃率** | API 故障时 0 崩溃 |

---

## 8. 里程碑

| 阶段 | 内容 | 预估 |
|------|------|------|
| **M1: 脚手架** | GitHub 仓库 + Gradle + Mod 主类 + 配置 + 测试 | Wave 1 (7 tasks) |
| **M2: 核心模块** | 数据访问 + AI 客户端 + 5 个工具 + Function Calling | Wave 2 (9 tasks) |
| **M3: UI 实现** | 侧边栏 + 全屏 + 快捷键 + 流式渲染 + Markdown | Wave 3 (6 tasks) |
| **M4: 持久化** | 聊天历史 + 多会话 + 配置 GUI + 指令 + 错误处理 | Wave 4 (5 tasks) |
| **M5: 审查发布** | 4 项验证审查 → 用户确认 → GitHub Release | Final Wave (4 tasks) |

---

## 9. 风险与缓解

| 风险 | 概率 | 影响 | 缓解措施 |
|------|------|------|---------|
| **OpenAI API 变更** | 低 | 高 | 使用兼容协议，非 OpenAI 专有 API |
| **OkHttp 类加载器冲突** | 中 | 高 | 使用 Java 21 内置 HttpClient，避免 OkHttp 依赖 |
| **Minecraft 版本更新** | 中 | 中 | 锁定 1.21.1，后续版本单独适配 |
| **JEI 侧边栏冲突** | 低 | 中 | 默认右侧（JEI 占右侧时改为左侧），位置可配置 |
| **API 成本失控** | 低 | 中 | 速率限制（2s 最小间隔）+ 最大并发请求限制 + 配置化最大 token |
| **API Key 泄露** | 中 | 高 | 独立 JSON 文件 + .gitignore 排除 + 日志中遮罩 + GUI 中遮罩 |
| **游戏内输入法冲突** | 中 | 中 | 使用原版 EditBox 组件（已有 IME 支持） |
| **存档持久化损坏** | 低 | 低 | JSON 格式 + 读取失败时重置为干净状态 + 版本号标记 |

---

## 10. 附录

### A. 术语表

| 术语 | 说明 |
|------|------|
| **JEI** | Just Enough Items，流行的 MC 物品列表 Mod，在物品栏右侧显示物品浏览器 |
| **Function Calling** | OpenAI API 功能，允许 AI 模型调用预定义的函数来获取外部数据 |
| **SSE** | Server-Sent Events，一种流式 HTTP 协议，用于逐 token 推送 AI 回复 |
| **DeferredRegister** | NeoForge 的注册系统，用于注册物品、方块、实体等内容 |
| **ModConfigSpec** | NeoForge 的配置规范 API，用于定义 Mod 配置项 |
| **Screen** | Minecraft 的 UI 屏幕类，控制一个完整的 UI 界面 |
| **GuiLayer** | NeoForge 的 HUD 层概念，允许在游戏 HUD 上叠加自定义内容 |

### B. 参考项目

| 项目 | 参考价值 |
|------|---------|
| [gemini-minecraft](https://github.com/aaronaalmendarez/gemini-minecraft) | 工具定义、MCP 桥、上下文快照 |
| [Steve AI](https://github.com/YuvDwi/Steve) | 智能体循环设计 |
| [GameQuery](https://github.com/YeeticusFinch/GameQuery) | 游戏数据 JSON 查询模式 |
| [JEI](https://github.com/mezz/JustEnoughItems) | 侧边栏 UI 集成模式 |
| [Create](https://github.com/Creators-of-Create/Create) | NeoForge HUD 覆盖层实现参考 |

### C. 变更记录

| 版本 | 日期 | 变更 |
|------|------|------|
| v1.0 | 2026-05-09 | 初始版本，基于 Prometheus 访谈生成 |
