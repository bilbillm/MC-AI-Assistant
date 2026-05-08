# MC-AI-Assistant — 工作计划

> **Mod ID**: `mcaiassistant` | **包名**: `com.lumoren.mcaiassistant`
> **GitHub 描述**: *An AI chat assistant mod for Minecraft (NeoForge 1.21.1) — ask questions, look up recipes, and get real-time game info without leaving the game.*

## TL;DR

> **简要说明**：为 Minecraft NeoForge 1.21.1 开发一个 AI 聊天助手 Mod，像 JEI 一样在打开物品栏时显示侧边栏对话窗口。AI 通过 OpenAI 兼容 API 接入，可调用工具查询游戏内数据（背包、配方、坐标、世界状态等）。纯只读，存档隔离历史。
>
> **产出物**：
> - AI 聊天侧边栏（物品栏界面左侧面板）
> - 全屏聊天模式（快捷键 ` 键 打开）
> - OpenAI 兼容 API 客户端（支持流式输出和 Function Calling）
> - 5 个游戏数据查询工具（背包、配方、坐标、物品百科、世界状态）
> - 存档隔离的多会话聊天历史
> - i18n 国际化支持
> - 配置系统（指令 + GUI + JSON 三选一）
>
> **预估工作量**：Large（31 个任务：1 初始化 + 26 实现 + 4 验证）
> **并行执行**：YES — 5 个并行波次
> **关键路径**：GitHub 仓库 → 项目脚手架 → 数据访问层 → AI 客户端 → UI 集成 → 持久化 → 最终验证

---

## Context

### 原始需求
在 Minecraft 中嵌入 AI 对话窗口，类似 JEI 侧边栏风格，可通过快捷键打开，AI 可调用各种工具获取游戏内信息，为玩家提供帮助。开源项目，发布到 GitHub。

### 访谈摘要
**关键决策**：
- **平台**：NeoForge 1.21.1，Java 21，Gradle 构建
- **AI 后端**：OpenAI 兼容协议（OpenAI / DeepSeek / 通义千问 等）
- **交互模式**：混合模式 — 自由对话 + AI 自动识别意图调用工具
- **操作权限**：V1 纯只读（不可执行游戏操作），读写是未来 TODO
- **AI 可查数据**：玩家背包/装备、合成配方、玩家状态/环境、物品/方块百科、世界状态
- **UI 行为**：正常游玩时隐藏，打开物品栏时左侧显示（类 JEI），` 键可打开独立全屏模式
- **聊天历史**：存档隔离，每存档可开多个独立对话线程
- **语言**：i18n 国际化
- **测试**：TDD（测试驱动开发）
- **协议**：GNU LGPL v3
- **分发**：GitHub Releases

**未来 TODO（不在 V1 范围）**：
- Mod 互操作（读取小地图、机械动力等第三方 Mod）
- AI 可读写模式（合成、切换装备等，需玩家确认）
- 多渠道分发（CurseForge / Modrinth）

### Metis 审查
**已解决的缺口**：
- **客户端 vs 服务端**：确认纯客户端，只用客户端已有数据
- **NeoForge 子版本**：锁定 1.21.1（最稳定，API: LayeredDraw.Layer）
- **流式输出**：确认使用 SSE 流式逐字输出
- **UI 与 GUI 冲突**：类 JEI 模式 — 仅在物品栏界面显示，平时不占屏幕
- **OpenAI 库选型**：采用 Java 21 内置 HttpClient + 轻量 JSON 解析，避免 OkHttp 类加载器冲突

---

## Work Objectives

### 核心目标
构建一个无缝嵌入 Minecraft 物品栏界面的 AI 对话助手，玩家可以在整理背包时向 AI 询问游戏相关问题，AI 通过实时查询游戏数据给出准确回答。

### 具体产出
- `AgentChat` Mod 主类与注册系统
- `AIChatPanel` 侧边栏面板（物品栏左侧附加 UI）
- `AIChatScreen` 全屏聊天界面
- `OpenAICompatClient` HTTP 客户端（流式 + Function Calling）
- `ToolRegistry` 工具注册与调度系统
- 5 个游戏数据工具实现（InventoryTool, RecipeTool, PositionTool, ItemEncyclopediaTool, WorldStateTool）
- `ChatHistoryManager` 存档隔离的多会话存储
- 配置系统（API Key、模型、温度等）
- i18n lang 文件（中文 + 英文）
- JUnit + GameTest 测试套件

### 完成定义
- [ ] `./gradlew build` 成功（编译 + 测试全通过）
- [ ] 在 MC 1.21.1 客户端中，打开物品栏时左侧出现 AI 聊天面板
- [ ] 输入"我背包里有什么"后，AI 能正确列出背包物品
- [ ] 输入"钻石剑怎么做"后，AI 能给出合成配方
- [ ] 按 ` 键可打开独立全屏聊天界面
- [ ] 切换存档后，聊天历史正确隔离

### Must Have
- 物品栏界面集成（类 JEI 左侧面板）
- OpenAI 兼容 API 调用（流式输出）
- Function Calling 工具调用
- 5 个游戏数据查询工具
- ` 键全屏模式
- 存档隔离聊天历史
- 配置指令 `/agentchat config`
- i18n（中英双语）
- TDD 测试覆盖核心逻辑

### Must NOT Have（护栏）
- 任何游戏状态修改操作（破坏方块、合成、移动物品等）
- Mod 互操作接口（IModDataProvider 等）
- AI 主动行为/提醒（纯被动响应）
- web 搜索工具
- 语音输入
- 多 AI 提供商原生支持（仅 OpenAI 兼容协议）
- 在玩家正常游玩时（非物品栏界面）显示任何 HUD 元素

---

## Verification Strategy

> **零人工干预** — 所有验证由 Agent 执行。不接受"手动测试确认"类验收条件。

### 测试决策
- **测试基础设施存在**：NO（全新项目，需要搭建）
- **自动化测试**：TDD（测试驱动开发）
- **框架**：JUnit 5（单元测试）+ NeoForge GameTest（集成测试）
- **TDD 流程**：每个任务遵循 RED（失败测试）→ GREEN（最小实现）→ REFACTOR

### QA 策略
每个任务必须包含 Agent 可执行的 QA 场景（见下方 TODO 模板）。证据保存到 `.sisyphus/evidence/task-{N}-{scenario-slug}.{ext}`。

- **API/后端**：使用 Bash（curl 测试 OpenAI 兼容端点）/ JUnit 测试运行
- **构建/编译**：使用 Bash（`./gradlew build`、`./gradlew test`）
- **UI/前端**：使用 Playwright（如果配置页面为 Web 则用，否则用 Gradle runClient 测试）
- **库/模块**：使用 JUnit + Mockito（隔离测试）

---

## Execution Strategy

### 并行执行波次

```
Wave 1（立即启动 — 基础设施 + 脚手架）：
├── Task 0: GitHub 仓库初始化 + README + LICENSE [quick]
├── Task 1: Gradle 项目搭建 + NeoForge MDK [quick]
├── Task 2: Mod 主类 + DeferredRegister 注册 [quick]
├── Task 3: 配置系统（ModConfigSpec + JSON secrets） [quick]
├── Task 4: 测试基础设施搭建 [quick]
├── Task 5: 数据模型 / 类型定义 [quick]
└── Task 6: i18n 框架 + 中英 lang 文件 [quick]

Wave 2（Wave 1 后 — 核心模块，最大并行）：
├── Task 7: 游戏数据访问层（客户端侧） [deep]
├── Task 8: OpenAI 兼容 HTTP 客户端 [deep]
├── Task 9: Tool 工具注册表 + 接口定义 [quick]
├── Task 10: InventoryTool 背包查询工具 [medium]
├── Task 11: RecipeTool 配方查询工具 [deep]
├── Task 12: PositionTool 玩家状态工具 [quick]
├── Task 13: ItemEncyclopediaTool 物品百科工具 [medium]
├── Task 14: WorldStateTool 世界状态工具 [quick]
└── Task 15: Function Calling 调度器 [deep]

Wave 3（Wave 2 后 — UI 实现）：
├── Task 16: AI 聊天 UI 组件（消息气泡 + 滚动） [visual-engineering]
├── Task 17: 物品栏 Screen 集成（侧边栏面板） [visual-engineering]
├── Task 18: 全屏 AIChatScreen [visual-engineering]
├── Task 19: 快捷键注册（` 键） [quick]
├── Task 20: 流式渲染更新 [visual-engineering]
└── Task 21: Markdown/格式化文本渲染 [quick]

Wave 4（Wave 3 后 — 持久化 + 配置界面）：
├── Task 22: 聊天历史持久化（存档隔离） [deep]
├── Task 23: 多会话管理 [medium]
├── Task 24: 配置 GUI 界面 [visual-engineering]
├── Task 25: `/agentchat` 指令 [quick]
└── Task 26: 错误处理与状态提示 [medium]

Wave FINAL（所有任务后 — 4 项并行审查，需用户确认）：
├── Task F1: 计划合规审计（oracle）
├── Task F2: 代码质量审查
├── Task F3: 手工 QA 执行
└── Task F4: 范围保真度检查
```

**关键路径**：Task 1 → Task 7 → Task 15 → Task 16 → Task 17 → Task 22 → Task F1-F4
**并行加速**：比顺序执行快约 60%

### Agent 调度摘要

- **Wave 1**: 6 — T1-T4 → `quick`, T5 → `quick`, T6 → `quick`
- **Wave 2**: 9 — T7 → `deep`, T8 → `deep`, T9 → `quick`, T10-T12 → `quick`/`medium`, T13 → `medium`, T14 → `quick`, T15 → `deep`
- **Wave 3**: 6 — T16-T18 → `visual-engineering`, T19 → `quick`, T20 → `visual-engineering`, T21 → `quick`
- **Wave 4**: 5 — T22 → `deep`, T23 → `medium`, T24 → `visual-engineering`, T25 → `quick`, T26 → `medium`
- **FINAL**: 4 — F1 → `oracle`, F2-F3 → `unspecified-high`, F4 → `deep`

---

## TODOs

- [ ] 0. GitHub 仓库初始化 + README + LICENSE

  **What to do**：
  - 在 `Agent-chat-in-MC` 目录初始化 Git 仓库：`git init`
  - 创建 `.gitignore`：
    ```
    # Gradle
    .gradle/
    build/
    !gradle/wrapper/gradle-wrapper.jar
    # IDE
    .idea/
    *.iml
    .vscode/
    # MC runs
    runs/
    # OS
    .DS_Store
    Thumbs.db
    # Secrets (API keys!)
    run/config/mcaiassistant-secrets.json
    # Logs
    logs/
    *.log
    ```
  - 创建 `README.md`：
    - 项目名称：MC-AI-Assistant
    - GitHub 描述：*An AI chat assistant mod for Minecraft (NeoForge 1.21.1) — ask questions, look up recipes, and get real-time game info without leaving the game.*
    - 特性列表：JEI 式侧边栏、OpenAI 兼容、Function Calling、5 个游戏数据工具、存档隔离历史
    - 安装说明、配置说明（API Key 设置）、截图占位
    - 开源协议：GNU LGPL v3
  - 创建 `LICENSE` 文件：复制 GNU LGPL v3 协议全文
  - 初始化提交：`git add . && git commit -m "chore: init MC-AI-Assistant project"`
  - （可选）创建 GitHub 仓库 `lumoren/MC-AI-Assistant` 并推送

  **Must NOT do**：
  - 不要提交 `run/config/mcaiassistant-secrets.json`（含 API Key）
  - 不要在 README 中暴露示例 API Key

  **Recommended Agent Profile**：
  - **Category**: `quick` — 纯文件创建 + git 操作
  - **Skills**: [`git-master`] — 需要创建规范的初始化提交
  - **Skills Evaluated but Omitted**: 无

  **Parallelization**：
  - **Can Run In Parallel**: YES
  - **Parallel Group**: Wave 1 (with Tasks 1, 2, 3, 4, 5, 6)
  - **Blocks**: None
  - **Blocked By**: None (可立即启动)

  **References**：
  - `https://www.gnu.org/licenses/lgpl-3.0.txt` — LGPL v3 协议全文
  - `https://github.com/github/gitignore/blob/main/Gradle.gitignore` — Gradle gitignore 模板
  - `https://github.com/github/gitignore/blob/main/Java.gitignore` — Java gitignore 模板

  **Acceptance Criteria**：
  - [ ] `.gitignore` 包含 Gradle/IDE/MC runs/secrets 排除规则
  - [ ] `README.md` 包含项目名称、描述、特性、安装说明
  - [ ] `LICENSE` 为完整的 LGPL v3 文本
  - [ ] `git log` 显示至少 1 个初始化提交

  **QA Scenarios**：

  ```
  Scenario: Git 仓库初始化验证
    Tool: Bash
    Preconditions: 在 Agent-chat-in-MC 目录
    Steps:
      1. git status
      2. 验证 .gitignore 存在且包含 secrets 排除规则
      3. 验证 README.md 存在且描述正确
      4. 验证 LICENSE 文件存在
    Expected Result: 三个文件存在，git status 干净（或仅显示未跟踪的计划文件）
    Failure Indicators: 文件缺失、.gitignore 未排除 secrets
    Evidence: .sisyphus/evidence/task-0-git-init.txt
  ```

  **Commit**: YES（即初始化提交本身）
  - Message: `chore: init MC-AI-Assistant project with README, LICENSE, and .gitignore`
  - Files: `README.md`, `LICENSE`, `.gitignore`

- [ ] 1. Gradle 项目搭建 + NeoForge MDK

  **What to do**：
  - 基于 NeoForge MDK-1.21-ModDevGradle 模板搭建项目骨架
  - 配置 `gradle.properties`：`mod_id=agentchat`, `minecraft_version=1.21.1`, `neo_version=21.1.x`
  - 配置 `build.gradle`：ModDevGradle 插件 `2.0.141+`, Java 21 toolchain, Parchment mappings
  - 配置 `settings.gradle`：插件仓库
  - 创建 `src/main/templates/META-INF/neoforge.mods.toml` 模板
  - 添加依赖：`simple-openai`（OpenAI 兼容客户端，含 Jackson）+ JUnit 5 + Mockito
  - 创建源码包结构：`com.lumoren.agentchat.{client,ai,config,tools,ui,persistence}`
  - 创建资源目录：`src/main/resources/assets/agentchat/lang/`
  - 验证 `./gradlew build` 成功（生成空 Mod JAR）

  **Must NOT do**：
  - 不要添加任何 Minecraft 内容注册（物品、方块、实体）
  - 不要引入 Kotlin 依赖
  - 不要使用官方 `openai-java`（会引入 OkHttp 冲突）

  **Recommended Agent Profile**：
  - **Category**: `quick`
    - Reason: 纯项目脚手架，标准化 Gradle 配置，无复杂逻辑
  - **Skills**: []
  - **Skills Evaluated but Omitted**: 无

  **Parallelization**：
  - **Can Run In Parallel**: YES
  - **Parallel Group**: Wave 1 (with Tasks 2, 3, 4, 5, 6)
  - **Blocks**: Task 7, Task 8, Task 19（所有后续任务依赖项目结构）
  - **Blocked By**: None (可立即启动)

  **References**：
  - `https://github.com/NeoForgeMDKs/MDK-1.21-ModDevGradle` — 官方 MDK 模板，参考 `build.gradle` 和 `gradle.properties` 结构
  - `https://docs.neoforged.net/` — NeoForge 官方文档
  - `https://github.com/sashirestela/simple-openai` — simple-openai 库，参考 Maven/Gradle 依赖坐标

  **Acceptance Criteria**：
  - [ ] `./gradlew build` 执行成功，无编译错误
  - [ ] 生成的 JAR 中包含 `neoforge.mods.toml`
  - [ ] `./gradlew runClient` 能启动 MC 1.21.1 客户端

  **QA Scenarios**：

  ```
  Scenario: 项目构建成功
    Tool: Bash
    Preconditions: Java 21 已安装，Gradle wrapper 可执行
    Steps:
      1. cd C:\Users\lumoren\Documents\GitHub\Agent-chat-in-MC
      2. ./gradlew build --no-daemon
      3. 检查退出码为 0
    Expected Result: BUILD SUCCESSFUL，输出中包含 `agentchat-1.0.0.jar`
    Failure Indicators: 编译错误、依赖解析失败、退出码非 0
    Evidence: .sisyphus/evidence/task-1-build-success.txt

  Scenario: MC 客户端可启动
    Tool: Bash
    Preconditions: Task 1 构建成功
    Steps:
      1. cd C:\Users\lumoren\Documents\GitHub\Agent-chat-in-MC
      2. timeout 30 ./gradlew runClient --no-daemon 2>&1
      3. 检查日志中无 FATAL 或 crash 信息
    Expected Result: 客户端启动日志中出现 "agentchat" mod 加载成功
    Failure Indicators: Crash report、mod 加载失败、FATAL 错误
    Evidence: .sisyphus/evidence/task-1-runclient.log
  ```

  **Commit**: YES
  - Message: `chore: init NeoForge 1.21.1 project with Gradle and simple-openai`
  - Files: `build.gradle`, `settings.gradle`, `gradle.properties`, `gradle/wrapper/`, `src/main/templates/META-INF/neoforge.mods.toml`, 包结构目录

- [ ] 2. Mod 主类 + DeferredRegister 注册

  **What to do**：
  - 创建 `AgentChat.java` Mod 主类（`@Mod` 注解，MODID = `"agentchat"`）
  - 在构造函数中接收 `IEventBus modEventBus` 和 `ModContainer modContainer`
  - 注册配置到 modContainer：`modContainer.registerConfig(ModConfig.Type.CLIENT, Config.SPEC)`
  - 创建 `ClientModEvents.java`：`@Mod.EventBusSubscriber(modid, bus=Bus.MOD, value=Dist.CLIENT)`
    - 订阅 `RegisterKeyMappingsEvent`（预留占位，后续 Task 19 实现）
  - 创建日志：`public static final Logger LOGGER = LogUtils.getLogger()`
  - 编写 JUnit 测试：验证 MODID 常量、Mod 类可被实例化

  **Must NOT do**：
  - 不要注册任何 DeferredRegister（本 Mod 无需物品/方块/实体注册）
  - 不要在 Mod 主类中放业务逻辑

  **Recommended Agent Profile**：
  - **Category**: `quick`
    - Reason: Mod 入口点 + 事件总线注册，标准模板化代码
  - **Skills**: []
  - **Skills Evaluated but Omitted**: `git-master`（暂时不需要）

  **Parallelization**：
  - **Can Run In Parallel**: YES
  - **Parallel Group**: Wave 1 (with Tasks 1, 3, 4, 5, 6)
  - **Blocks**: Task 7, Task 19
  - **Blocked By**: Task 1 (需要项目结构)

  **References**：
  - `https://github.com/NeoForgeMDKs/MDK-1.21-ModDevGradle/blob/main/src/main/java/com/example/examplemod/ExampleMod.java` — Mod 主类模板，`@Mod` + 构造函数签名
  - `https://github.com/NeoForgeMDKs/MDK-1.21-ModDevGradle/blob/main/src/main/java/com/example/examplemod/Config.java` — Config 类模板
  - `https://docs.neoforged.net/docs/concepts/events/` — 事件系统文档

  **Acceptance Criteria**：
  - [ ] `AgentChat` 类带有 `@Mod(AgentChat.MODID)` 注解
  - [ ] `ClientModEvents` 类带有 `@Mod.EventBusSubscriber` + `value=Dist.CLIENT`
  - [ ] JUnit 测试 `AgentChatTest.testModId()` 通过：`assertEquals("agentchat", AgentChat.MODID)`
  - [ ] `./gradlew build` 成功

  **QA Scenarios**：

  ```
  Scenario: Mod 加载验证
    Tool: Bash
    Preconditions: Task 1 构建成功
    Steps:
      1. cd C:\Users\lumoren\Documents\GitHub\Agent-chat-in-MC
      2. ./gradlew build --no-daemon
      3. grep "agentchat" build/libs/*.jar (检查 jar 中包含 mod 标识)
    Expected Result: JAR 中包含 agentchat 类文件，构建成功
    Failure Indicators: 构建失败、类文件缺失
    Evidence: .sisyphus/evidence/task-2-build.log

  Scenario: Mod 主类单元测试
    Tool: Bash (./gradlew test)
    Preconditions: Task 1 构建成功
    Steps:
      1. cd C:\Users\lumoren\Documents\GitHub\Agent-chat-in-MC
      2. ./gradlew test --tests "com.lumoren.agentchat.AgentChatTest" --no-daemon
      3. 检查测试输出全部 PASS
    Expected Result: 1 test passed, 0 failures
    Failure Indicators: 测试失败、编译错误
    Evidence: .sisyphus/evidence/task-2-test-results.txt
  ```

  **Commit**: YES
  - Message: `feat: add AgentChat mod main class with event bus registration`
  - Files: `src/main/java/com/lumoren/agentchat/AgentChat.java`, `src/main/java/com/lumoren/agentchat/client/ClientModEvents.java`, `src/test/java/com/lumoren/agentchat/AgentChatTest.java`

- [ ] 3. 配置系统（ModConfigSpec + JSON secrets）

  **What to do**：
  - 创建 `Config.java`：使用 `ModConfigSpec.Builder` 定义所有配置项：
    - `baseUrl`：API 端点地址，默认 `https://api.openai.com/v1`
    - `model`：模型名称，默认 `gpt-4o-mini`
    - `temperature`：温度参数，默认 `0.7`，范围 `0.0-2.0`
    - `maxTokens`：最大输出 token，默认 `1024`，范围 `100-4096`
    - `maxHistory`：最大对话轮数，默认 `200`，范围 `10-1000`
    - `sidebarPosition`：侧边栏位置（LEFT/RIGHT），默认 `RIGHT`
    - `sidebarWidth`：侧边栏宽度（屏幕百分比），默认 `30`，范围 `20-50`
    - `timeout`：请求超时（秒），默认 `30`，范围 `5-120`
  - 创建 `SecretsConfig.java`：独立 JSON 文件读取 API Key（`config/agentchat-secrets.json`）
    - 首次运行生成模板文件
    - 支持读取 `apiKey` 字段
  - 创建 `ConfigManager.java`：统一配置访问门面
  - 编写 JUnit 测试：验证配置默认值、范围约束、JSON 序列化/反序列化

  **Must NOT do**：
  - 不要把 API Key 放在 TOML ModConfig 中
  - 不要 log API Key

  **Recommended Agent Profile**：
  - **Category**: `quick`
    - Reason: 配置定义 + JSON 读写，标准化代码
  - **Skills**: []
  - **Skills Evaluated but Omitted**: 无

  **Parallelization**：
  - **Can Run In Parallel**: YES
  - **Parallel Group**: Wave 1 (with Tasks 1, 2, 4, 5, 6)
  - **Blocks**: Task 8, Task 24
  - **Blocked By**: Task 1, Task 2

  **References**：
  - `https://github.com/NeoForgeMDKs/MDK-1.21-ModDevGradle/blob/main/src/main/java/com/example/examplemod/Config.java` — ModConfigSpec.Builder 模式
  - `https://docs.neoforged.net/docs/misc/config/` — 配置系统文档，Config.Type 说明

  **Acceptance Criteria**：
  - [ ] `Config.java` 定义 8 个配置项，全部有默认值和注释
  - [ ] `SecretsConfig.java` 能从 `config/agentchat-secrets.json` 读取 API Key
  - [ ] 首次运行自动生成 `agentchat-secrets.json` 模板
  - [ ] JUnit 测试 `ConfigTest.testDefaults()`：所有默认值正确
  - [ ] JUnit 测试 `SecretsConfigTest.testLoadAndSave()`：读写正确

  **QA Scenarios**：

  ```
  Scenario: 配置文件生成验证
    Tool: Bash
    Preconditions: 项目构建成功，config 目录为空
    Steps:
      1. cd C:\Users\lumoren\Documents\GitHub\Agent-chat-in-MC
      2. 删除 run/config/agentchat-secrets.json（如果存在）
      3. ./gradlew runClient (启动后立即退出)
      4. 检查 run/config/agentchat-secrets.json 存在
      5. 检查文件内容包含 apiKey 字段和注释
    Expected Result: 文件自动生成，包含模板结构
    Failure Indicators: 文件不存在、JSON 格式错误
    Evidence: .sisyphus/evidence/task-3-secrets-template.json

  Scenario: 配置默认值单元测试
    Tool: Bash (./gradlew test)
    Preconditions: 代码已编写
    Steps:
      1. cd C:\Users\lumoren\Documents\GitHub\Agent-chat-in-MC
      2. ./gradlew test --tests "com.lumoren.agentchat.config.ConfigTest" --no-daemon
    Expected Result: 所有测试通过：model=gpt-4o-mini, temperature=0.7, maxTokens=1024
    Failure Indicators: 测试失败
    Evidence: .sisyphus/evidence/task-3-test-results.txt
  ```

  **Commit**: YES
  - Message: `feat: add configuration system with ModConfigSpec and JSON secrets`
  - Files: `src/main/java/com/lumoren/agentchat/config/Config.java`, `SecretsConfig.java`, `ConfigManager.java`, 测试文件

- [ ] 4. 测试基础设施搭建

  **What to do**：
  - 配置 JUnit 5 + Mockito 在 `build.gradle` 中（testImplementation）
  - 创建测试工具类 `TestUtils.java`：提供 Mock `Minecraft`、`LocalPlayer`、`Inventory` 等常用对象
  - 创建 `MockGameData.java`：预置测试用的物品栏数据、配方数据、坐标数据
  - 配置 GameTest：创建 `src/test/java/com/lumoren/agentchat/gametest/` 目录
  - 在 `build.gradle` 中添加 `gameTestServer` run config
  - 编写冒烟测试：验证测试框架可正常运行（`assertTrue(true)`）

  **Must NOT do**：
  - 不要为 Minecraft 内部类编写 Mock（使用真实 GameTest 代替）
  - 不要在 GameTest 中测试尚未实现的功能

  **Recommended Agent Profile**：
  - **Category**: `quick`
    - Reason: 测试基础设施配置，无业务逻辑
  - **Skills**: []
  - **Skills Evaluated but Omitted**: 无

  **Parallelization**：
  - **Can Run In Parallel**: YES
  - **Parallel Group**: Wave 1 (with Tasks 1, 2, 3, 5, 6)
  - **Blocks**: 所有后续任务的测试部分
  - **Blocked By**: Task 1

  **References**：
  - `https://docs.neoforged.net/docs/misc/gametest/` — GameTest 文档
  - `https://github.com/neoforged/NeoForge/blob/26.1.x/tests/` — NeoForge 官方测试示例
  - `https://junit.org/junit5/docs/current/user-guide/` — JUnit 5 用户指南

  **Acceptance Criteria**：
  - [ ] `./gradlew test` 执行成功（即使无测试）
  - [ ] Mockito 依赖可用（`Mockito.mock()` 可调用）
  - [ ] `TestUtils.createMockPlayer()` 返回非 null Mock 对象
  - [ ] GameTest 冒烟测试通过

  **QA Scenarios**：

  ```
  Scenario: 测试框架可用性验证
    Tool: Bash (./gradlew test)
    Preconditions: 项目构建成功
    Steps:
      1. cd C:\Users\lumoren\Documents\GitHub\Agent-chat-in-MC
      2. ./gradlew test --no-daemon
      3. 检查输出中无 FAILED 标记
    Expected Result: BUILD SUCCESSFUL, 所有测试通过
    Failure Indicators: 测试配置错误、依赖缺失
    Evidence: .sisyphus/evidence/task-4-test-smoke.txt
  ```

  **Commit**: YES
  - Message: `test: set up JUnit 5 + Mockito + GameTest infrastructure`
  - Files: `build.gradle`（test 依赖部分），测试工具类，冒烟测试

- [ ] 5. 数据模型 / 类型定义

  **What to do**：
  - 创建数据模型包 `com.lumoren.agentchat.model`，定义以下 record/class：
    - `ChatMessage`：role (system/user/assistant/tool) + content + toolCalls/toolCallId
    - `ToolDefinition`：name + description + parameters (JsonObject schema)
    - `ToolResult`：toolCallId + content
    - `ConversationThread`：id + name + messages (List<ChatMessage>) + createdAt
    - `GameContext`：playerName + position (x/y/z/dimension) + inventory + health + hunger
    - `InventoryItem`：slot + itemId + displayName + count + durability
    - `RecipeResult`：recipeId + input (List) + output + type (crafting/smelting/etc)
    - `WorldState`：dayTime + weather + difficulty + biome
  - 所有模型类使用 Java 21 record 或稳定的 POJO
  - 确保所有类支持 JSON 序列化/反序列化（与 OpenAI API 兼容）
  - 编写 JUnit 测试：序列化循环测试（object → JSON → object）

  **Must NOT do**：
  - 不要引入 Lombok（避免依赖膨胀）
  - 不要在这些模型中放业务逻辑

  **Recommended Agent Profile**：
  - **Category**: `quick`
    - Reason: 纯数据模型定义，无复杂逻辑
  - **Skills**: []
  - **Skills Evaluated but Omitted**: 无

  **Parallelization**：
  - **Can Run In Parallel**: YES
  - **Parallel Group**: Wave 1 (with Tasks 1, 2, 3, 4, 6)
  - **Blocks**: Task 7, Task 8, Task 9, Task 15, Task 16, Task 22
  - **Blocked By**: None

  **References**：
  - `https://platform.openai.com/docs/api-reference/chat/create` — OpenAI Chat Completions API 消息格式
  - `https://platform.openai.com/docs/guides/function-calling` — Function Calling 的 tool 定义格式
  - `https://github.com/sashirestela/simple-openai` — simple-openai 的数据类设计参考

  **Acceptance Criteria**：
  - [ ] 所有 8 个模型类定义完成
  - [ ] `ChatMessage` 能正确表示 OpenAI API 的四种消息角色
  - [ ] `ToolDefinition` 能序列化为 OpenAI tool JSON schema
  - [ ] JUnit 测试 `ChatMessageTest.testSerialization()`：序列化后反序列化一致

  **QA Scenarios**：

  ```
  Scenario: ChatMessage JSON 序列化循环测试
    Tool: Bash (./gradlew test)
    Preconditions: 数据模型代码完成
    Steps:
      1. cd C:\Users\lumoren\Documents\GitHub\Agent-chat-in-MC
      2. ./gradlew test --tests "com.lumoren.agentchat.model.ChatMessageTest" --no-daemon
      3. 验证序列化/反序列化后内容一致
    Expected Result: All tests passed: ChatMessage, ToolDefinition, ToolResult
    Failure Indicators: JSON 字段名不匹配、类型转换异常
    Evidence: .sisyphus/evidence/task-5-model-tests.txt
  ```

  **Commit**: YES
  - Message: `feat: define data models for chat messages, tools, and game context`
  - Files: `src/main/java/com/lumoren/agentchat/model/*.java`, 测试文件

- [ ] 6. i18n 框架 + 中英 lang 文件

  **What to do**：
  - 创建 `src/main/resources/assets/agentchat/lang/zh_cn.json`：
    - 所有 UI 文本的翻译键（侧边栏标题、输入框占位符、发送按钮、加载提示、错误消息等）
  - 创建 `src/main/resources/assets/agentchat/lang/en_us.json`：
    - 对应的英文翻译
  - 创建 `I18nHelper.java`：静态辅助方法 `translate(key, args...)`
  - 创建 `I18nKeys.java`：常量类，定义所有翻译键避免硬编码字符串
  - 翻译键至少包含：
    - `agentchat.title`：AI 助手
    - `agentchat.input.placeholder`：向 AI 提问...
    - `agentchat.button.send`：发送
    - `agentchat.error.no_api_key`：未配置 API Key！
    - `agentchat.error.network`：网络错误，请检查连接
    - `agentchat.error.rate_limit`：请求过于频繁，请稍候
    - `agentchat.empty_conversation`：开始一段新对话吧！
    - `agentchat.thread.new`：新对话
    - `agentchat.config.title`：配置
    - `agentchat.key.category`：AgentChat Mod
    - `agentchat.key.open_chat`：打开 AI 聊天

  **Must NOT do**：
  - 不要在代码中硬编码中文或英文字符串（始终使用翻译键）

  **Recommended Agent Profile**：
  - **Category**: `quick`
    - Reason: 纯资源文件 + 辅助类，无复杂逻辑
  - **Skills**: []
  - **Skills Evaluated but Omitted**: 无

  **Parallelization**：
  - **Can Run In Parallel**: YES
  - **Parallel Group**: Wave 1 (with Tasks 1, 2, 3, 4, 5)
  - **Blocks**: Task 16, Task 17, Task 18, Task 24（所有 UI 任务）
  - **Blocked By**: Task 1

  **References**：
  - `https://docs.neoforged.net/docs/misc/internationalization/` — NeoForge i18n 文档
  - Minecraft 原版 `zh_cn.json` — 翻译文件格式参考

  **Acceptance Criteria**：
  - [ ] `zh_cn.json` 包含 ≥20 个翻译键
  - [ ] `en_us.json` 包含相同键的英文翻译
  - [ ] `I18nKeys.java` 定义所有键常量
  - [ ] `I18nHelper.translate(I18nKeys.TITLE)` 返回正确翻译
  - [ ] JUnit 测试验证所有键在两个语言文件中都存在

  **QA Scenarios**：

  ```
  Scenario: 翻译键完整性验证
    Tool: Bash (./gradlew test)
    Preconditions: lang 文件和 I18nKeys 完成
    Steps:
      1. cd C:\Users\lumoren\Documents\GitHub\Agent-chat-in-MC
      2. ./gradlew test --tests "com.lumoren.agentchat.i18n.I18nTest" --no-daemon
      3. 检查所有 I18nKeys 常量在两个语言文件中都存在
    Expected Result: 0 missing keys
    Failure Indicators: 缺失翻译键
    Evidence: .sisyphus/evidence/task-6-i18n-test.txt
  ```

  **Commit**: YES
  - Message: `feat: add i18n framework with Chinese and English lang files`
   - Files: `src/main/resources/assets/agentchat/lang/zh_cn.json`, `en_us.json`, `I18nKeys.java`, `I18nHelper.java`, 测试文件

- [ ] 7. 游戏数据访问层（客户端侧）

  **What to do**：
  - 创建 `GameDataAccess.java`：统一游戏数据访问门面
  - 实现以下查询方法（仅使用客户端侧数据）：
    - `getInventorySnapshot(Minecraft mc)` — 读取玩家背包，返回 `List<InventoryItem>`
    - `getPlayerContext(Minecraft mc)` — 返回坐标、维度、生命值、饥饿值
    - `getItemInfo(ResourceLocation itemId)` — 从 BuiltInRegistries 查询物品属性
    - `getWorldState(ClientLevel level)` — 返回时间、天气、难度
    - `getRecipesForOutput(ResourceLocation itemId, RecipeManager recipes)` — 查询合成配方
  - 编写 JUnit 测试：使用 Mock 验证查询逻辑（≥5 个测试）

  **Must NOT do**：不要访问服务端 API（ServerLevel/ServerPlayer），不要修改游戏状态

  **Recommended Agent Profile**：
  - **Category**: `deep` — Minecraft 客户端 API 深挖
  - **Skills**: []
  - **Parallelization**：Wave 2 | Blocks: T10-T15 | Blocked By: T1, T2, T5
  - **References**：`Minecraft.getInstance().player`, `Inventory.items`, `BuiltInRegistries.ITEM`, `ItemStack.getComponents()`
  - **Acceptance**：背包快照 ≥ 5 测试通过 | Mock 含 3 物品返回正确条目
  - **QA**：JUnit test: 3-item mock inventory → JSON 含 3 条目
  - **Evidence**: `.sisyphus/evidence/task-7-game-data-test.txt`
  - **Commit**: `feat: implement client-side game data access layer` — `GameDataAccess.java`, tests

- [ ] 8. OpenAI 兼容 HTTP 客户端

  **What to do**：
  - 创建 `OpenAICompatClient.java`：使用 Java 21 `java.net.http.HttpClient`
  - 实现非流式 `chatCompletion()` 和流式 `chatCompletionStreaming()`（SSE 解析）
  - 错误处理：401→AUTH_ERROR, 429→RATE_LIMIT（指数退避重试）, 超时→TIMEOUT
  - 结果回调到主线程：`Minecraft.getInstance().submit()`
  - 编写 JUnit 测试：Mock HTTP 响应覆盖所有错误码

  **Must NOT do**：不使用 OkHttp，不在主线程阻塞，不 log API Key

  **Recommended Agent Profile**：
  - **Category**: `deep` — HTTP + SSE + 重试 + 错误处理复杂度高
  - **Skills**: []
  - **Parallelization**：Wave 2 | Blocks: T15, T16 | Blocked By: T1, T3, T5
  - **References**：`java.net.http.HttpClient`, OpenAI Chat API, SSE format, simple-openai HTTP impl
  - **Acceptance**：流式 SSE 正确解析 token-by-token | 401/429/超时全覆盖 | ≥6 测试
  - **QA**：Mock 401 → AUTH_ERROR | Mock SSE "data:{\"choices\":[{\"delta\":{\"content\":\"你好\"}}]}" → onToken("你好")
  - **Evidence**: `.sisyphus/evidence/task-8-*.txt`
  - **Commit**: `feat: implement OpenAI-compatible HTTP client with streaming and retry` — `OpenAICompatClient.java`, tests

- [ ] 9. Tool 工具注册表 + 接口定义

  **What to do**：
  - 创建 `GameTool` 接口：getName/getDescription/getDefinition/execute
  - 创建 `ReadOnlyGameTool` 标记接口
  - 创建 `ToolRegistry`：register/getAll/getToolDefinitions/executeTool
  - 支持工具自动注册（构造函数注入）

  **Must NOT do**：不实现 ReadWriteGameTool，不放具体工具逻辑

  **Recommended Agent Profile**：`quick` — 接口 + 注册表标准模式
  **Parallelization**：Wave 2 | Blocks: T10-T15 | Blocked By: T5
  **References**：OpenAI Function Calling tool schema, gemini-minecraft tool registry
  **Acceptance**：Mock 2 tools → getAll()=2 | getToolDefinitions() 返回正确 OpenAI schema
  **QA**：JUnit test: 注册/查找/执行 mock 工具
  **Evidence**: `.sisyphus/evidence/task-9-tool-registry-test.txt`
  **Commit**: `feat: define GameTool interface and ToolRegistry` — interfaces + registry

- [ ] 10. InventoryTool 背包查询工具

  **What to do**：实现 `ReadOnlyGameTool`，name=`get_inventory`，调用 GameDataAccess 返回背包物品列表
  **Category**: `quick` | Wave 2 | Blocks: T15 | Blocked By: T7, T9
  **Acceptance**：3 物品 mock → 返回 3 条目 JSON
  **QA**：JUnit: diamond_sword x1 + bread x5 + torch x64 → 验证输出
  **Evidence**: `.sisyphus/evidence/task-10-inventory-tool-test.txt`
  **Commit**: `feat: implement InventoryTool` — `InventoryTool.java`, tests

- [ ] 11. RecipeTool 配方查询工具

  **What to do**：实现 `ReadOnlyGameTool`，name=`lookup_recipe`，参数 item 名，返回合成/熔炼配方
  **Category**: `deep` — RecipeManager API 复杂 | Wave 2 | Blocks: T15 | Blocked By: T7, T9
  **Acceptance**：diamond_sword → stick:1 + diamond:2 | 不存在的物品 → "未找到"
  **QA**：JUnit: mock RecipeManager 含 diamond_sword 配方 → 验证输出
  **Evidence**: `.sisyphus/evidence/task-11-recipe-tool-test.txt`
  **Commit**: `feat: implement RecipeTool` — `RecipeTool.java`, tests

- [ ] 12. PositionTool 玩家状态工具

  **What to do**：实现 `ReadOnlyGameTool`，name=`get_player_status`，返回坐标/维度/生命/饥饿
  **Category**: `quick` | Wave 2 | Blocks: T15 | Blocked By: T7, T9
  **Acceptance**：Mock overworld (100,64,-200) health=18 hunger=14 → 输出准确
  **QA**：JUnit: 验证坐标和状态输出 JSON
  **Evidence**: `.sisyphus/evidence/task-12-position-tool-test.txt`
  **Commit**: `feat: implement PositionTool` — `PositionTool.java`, tests

- [ ] 13. ItemEncyclopediaTool 物品百科工具

  **What to do**：实现 `ReadOnlyGameTool`，name=`item_info`，返回 maxStackSize/maxDamage/rarity/foodProps
  **Category**: `medium` — DataComponentMap API | Wave 2 | Blocks: T15 | Blocked By: T7, T9
  **Acceptance**：diamond_sword → maxDamage=1561 | bread → nutrition=5, saturation=6.0
  **QA**：JUnit: 验证物品属性输出
  **Evidence**: `.sisyphus/evidence/task-13-item-info-test.txt`
  **Commit**: `feat: implement ItemEncyclopediaTool` — `ItemEncyclopediaTool.java`, tests

- [ ] 14. WorldStateTool 世界状态工具

  **What to do**：实现 `ReadOnlyGameTool`，name=`get_world_state`，返回时间/天气/难度/生物群系
  **Category**: `quick` | Wave 2 | Blocks: T15 | Blocked By: T7, T9
  **Acceptance**：dayTime=6000 → day=0 | raining=false | difficulty=NORMAL
  **QA**：JUnit: 验证世界状态输出
  **Evidence**: `.sisyphus/evidence/task-14-world-state-test.txt`
  **Commit**: `feat: implement WorldStateTool` — `WorldStateTool.java`, tests

- [ ] 15. Function Calling 调度器

  **What to do**：
  - 创建 `ToolCallDispatcher.java`：接收 tool_calls → 执行 → 结果回传 AI
  - 完整 conversation loop：用户消息 → API(含tools) → tool_calls → 执行 → 结果回传 → AI 最终回答
  - 最多 5 轮循环（防无限循环）
  - 创建 `AIChatService.java` 高层服务：`sendMessage(userInput, history, onToken)`
  - 编写 JUnit 测试：Mock AI 返回 tool_calls，验证完整流程

  **Must NOT do**：不放具体工具逻辑，不超 5 轮循环

  **Recommended Agent Profile**：
  - **Category**: `deep` — 异步流程控制 + tool_calls 解析 + 完整循环
  - **Skills**: []
  - **Parallelization**：Sequential | Blocks: T16-T18 | Blocked By: T7-T14
  - **References**：OpenAI Function Calling 完整流程, gemini-minecraft 工具调用循环, simple-openai tool_choice
  - **Acceptance**：1 tool_call → 执行+回传 | 2 tool_calls → 并行执行+合并 | 普通消息 → 直接回调 | >5轮 → 强制停止
  - **QA**：Mock tool_call(get_inventory) → 执行 InventoryTool → 结果作为 tool 消息 → AI 最终文本通过 onToken
  - **Evidence**: `.sisyphus/evidence/task-15-dispatcher-test.txt`
  - **Commit**: `feat: implement Function Calling dispatcher with conversation loop` — `ToolCallDispatcher.java`, `AIChatService.java`, tests

- [ ] 16. AI 聊天 UI 组件（消息气泡 + 可滚动列表）

  **What to do**：
  - 创建 `ChatMessageWidget.java`：渲染单条消息气泡（用户右对齐蓝色，AI 左对齐灰色）
  - 创建 `MessageListWidget.java`：可滚动消息列表，支持鼠标滚轮和拖拽滚动条
  - 支持消息类型：用户消息、AI 文本消息、AI 加载中动画（...）、错误消息（红色）
  - 使用 `GuiGraphics.drawString()` 渲染文本，`GuiGraphics.fill()` 渲染气泡背景
  - 编写 JUnit 测试：验证消息排版、换行计算

  **Must NOT do**：不在此组件中处理网络请求或工具调用

  **Recommended Agent Profile**：
  - **Category**: `visual-engineering` — UI 渲染 + 滚动 + 气泡样式
  - **Skills**: []
  - **Parallelization**：Wave 3 | Blocks: T17, T18 | Blocked By: T5, T6, T15
  - **References**：`net.minecraft.client.gui.GuiGraphics`, `net.minecraft.client.gui.components.AbstractWidget`, JEI 面板渲染参考
  - **Acceptance**：≥3 条消息可正确渲染 | 滚动功能正常 | 长文本自动换行
  - **QA**：Playwright/Gradle runClient: 验证消息渲染和滚动
  - **Evidence**: `.sisyphus/evidence/task-16-chat-ui.png`
  - **Commit**: `feat: implement chat message bubble and scrollable message list` — chat UI files

- [ ] 17. 物品栏 Screen 集成（侧边栏面板）

  **What to do**：
  - 创建 `AIChatSidebarPanel.java`：在物品栏界面左侧渲染 AI 聊天面板
  - 通过 `ScreenEvent.Init.Post` 钩子注入到 `InventoryScreen`
  - 面板包含：标题栏 + 消息列表 + 输入框 + 发送按钮 + 新对话按钮
  - 面板宽度：屏幕宽度的 30%（可配置），最小 200px
  - 使用 `ScreenEvent.Render.Post` 确保面板渲染在物品栏之上
  - 支持拖拽调整面板宽度

  **Must NOT do**：不要修改原版物品栏布局（物品格位置不变），不要覆盖 JEI 面板

  **Recommended Agent Profile**：
  - **Category**: `visual-engineering` — Screen 钩子 + 面板布局 + 交互逻辑
  - **Skills**: []
  - **Parallelization**：Wave 3 | Blocks: None | Blocked By: T16
  - **References**：`net.minecraft.client.gui.screens.inventory.InventoryScreen`, `ScreenEvent.Init.Post/Render.Post`, JEI 源码 Screen 注入模式
  - **Acceptance**：打开物品栏 → 左侧出现 AI 面板 | 关闭物品栏 → 面板消失 | 面板宽度可拖拽调整
  - **QA**：Gradle runClient: 截图验证物品栏 + 面板共存，无布局冲突
  - **Evidence**: `.sisyphus/evidence/task-17-sidebar-inventory.png`
  - **Commit**: `feat: integrate AI chat sidebar into inventory screen (JEI-style)` — sidebar panel files

- [ ] 18. 全屏 AIChatScreen

  **What to do**：
  - 创建 `AIChatScreen.java`：独立全屏聊天界面（继承 `Screen`）
  - 更大面积的消息显示区域，适合长对话
  - 复用 Task 16 的 MessageListWidget
  - 支持 `isPauseScreen() = false`（不暂停游戏）
  - 通过 `Minecraft.getInstance().setScreen()` 打开

  **Must NOT do**：不在此屏幕中重复实现聊天逻辑（复用 AIChatService）

  **Recommended Agent Profile**：
  - **Category**: `visual-engineering` — 全屏 Screen 实现
  - **Skills**: []
  - **Parallelization**：Wave 3 | Blocks: None | Blocked By: T16
  - **References**：`net.minecraft.client.gui.screens.Screen`, `extractRenderState()`, vanilla ChatScreen
  - **Acceptance**：全屏聊天正确渲染 | ESC 关闭 | 消息能正常发送和显示
  - **QA**：Gradle runClient: 截图全屏聊天界面
  - **Evidence**: `.sisyphus/evidence/task-18-fullscreen-chat.png`
  - **Commit**: `feat: implement full-screen AI chat screen` — `AIChatScreen.java`

- [ ] 19. 快捷键注册（` 键）

  **What to do**：
  - 在 `ClientModEvents` 中注册快捷键：`GLFW.GLFW_KEY_GRAVE_ACCENT`（反引号 ` 键）
  - 快捷键行为：
    - 在物品栏界面中 → 切换输入框焦点
    - 在其他界面/游戏中 → 打开全屏 AIChatScreen
  - 支持玩家在 Controls 菜单中自定义快捷键
  - 编写测试：验证按键事件处理逻辑

  **Must NOT do**：不要与 MC 聊天框（T 键）冲突，不要在 GUI 中拦截所有输入

  **Recommended Agent Profile**：`quick` — 标准快捷键注册 | Wave 3 | Blocks: None | Blocked By: T2, T18
  **References**：`RegisterKeyMappingsEvent`, `ClientTickEvent.Post`, `KeyMapping.consumeClick()`
  **Acceptance**：按 ` 键打开全屏聊天 | Controls 菜单可见快捷键配置
  **QA**：Gradle runClient: 验证快捷键打开/关闭行为
  **Evidence**: `.sisyphus/evidence/task-19-keybinding.txt`
  **Commit**: `feat: register backtick keybinding for AI chat` — keybinding registration

- [ ] 20. 流式渲染更新

  **What to do**：
  - 实现 AI 流式响应时的逐字渲染
  - 收到新 token → 追加到当前 AI 消息 → 触发重绘
  - 处理 tool_calls 的增量渲染（显示"正在查询 [工具名]..."）
  - 流式完成时标记消息为"完成"状态
  - 确保渲染更新不阻塞主线程

  **Must NOT do**：不要在渲染循环中发起 HTTP 请求

  **Recommended Agent Profile**：
  - **Category**: `visual-engineering` — 增量渲染 + 状态管理 | Wave 3 | Blocks: None | Blocked By: T15, T16
  **References**：OpenAI SSE streaming format, Minecraft `Minecraft.getInstance().submit()` 线程调度
  **Acceptance**：流式响应逐字出现（打字机效果）| tool_calls 时显示工具名称 | 渲染不卡顿
  **QA**：Gradle runClient: 发送查询 → 观察逐字输出效果
  **Evidence**: `.sisyphus/evidence/task-20-streaming.txt`
  **Commit**: `feat: implement streaming token-by-token chat rendering` — rendering updates

- [ ] 21. Markdown/格式化文本渲染

  **What to do**：
  - 实现轻量 Markdown 解析器（仅支持常用格式）：
    - `**粗体**` → 加粗
    - `*斜体*` → 斜体
    - `` `代码` `` → 代码样式
    - `- 列表项` → 列表
    - 代码块 ` ```...``` ` → 等宽字体
    - 换行和段落
  - 将解析后的 Markdown 转换为 Minecraft `Component` 对象
  - 编写 JUnit 测试：验证各种 Markdown 格式解析正确

  **Must NOT do**：不要引入完整 CommonMark 库（太重）

  **Recommended Agent Profile**：`quick` — 轻量 Markdown 解析 | Wave 3 | Blocks: None | Blocked By: T16
  **References**：Minecraft `Component.literal()`, `Style.EMPTY.withBold(true)`, MC chat formatting
  **Acceptance**：`**粗体**` → 加粗 Component | ``` `代码` ``` → 等宽字体 | 3 种格式测试通过
  **QA**：JUnit: testBold/testItalic/testCodeBlock → 验证 Component 样式
  **Evidence**: `.sisyphus/evidence/task-21-markdown-test.txt`
  **Commit**: `feat: implement lightweight Markdown-to-Component renderer` — `MarkdownRenderer.java`, tests

- [ ] 22. 聊天历史持久化（存档隔离）

  **What to do**：
  - 创建 `ChatHistoryManager.java`：管理每个存档的对话历史
  - 存储路径：`.minecraft/saves/<世界名>/agentchat/conversations.json`
  - 使用 Gson/Jackson 序列化 `List<ConversationThread>` 到 JSON
  - 实现：save/load/delete/createThread
  - 自动保存：每 3 分钟或退出游戏时
  - 大小限制：每线程最多保留配置的 `maxHistory` 条消息
  - 编写 JUnit 测试：save → load 一致性验证

  **Must NOT do**：不要使用 NBT 格式（JSON 更易调试和迁移），不要跨存档共享历史

  **Recommended Agent Profile**：
  - **Category**: `deep` — 文件 I/O + 序列化 + 存档发现
  - **Skills**: []
  - **Parallelization**：Wave 4 | Blocks: None | Blocked By: T5
  - **References**：Minecraft `Minecraft.getInstance().getSingleplayerServer().getWorldPath()`, Gson, simple-openai JSON
  - **Acceptance**：save 3 条消息 → reload → 恢复 3 条 | 不同存档历史隔离 | 超限自动裁剪
  - **QA**：JUnit: 写入 3 条消息 → 读取 → 验证内容一致 → 裁剪到 200 条验证
  - **Evidence**: `.sisyphus/evidence/task-22-persistence-test.txt`
  - **Commit**: `feat: implement per-save conversation history persistence` — `ChatHistoryManager.java`, tests

- [ ] 23. 多会话管理

  **What to do**：
  - 创建 `ConversationManager.java`：管理多个对话线程
  - 支持：新建线程、切换线程、删除线程、重命名线程
  - 每个线程独立的 `List<ChatMessage>` 历史
  - 线程名默认为"新对话 #1, #2..."，可重命名
  - 编写 JUnit 测试：创建/切换/删除线程

  **Must NOT do**：不要在此组件中处理 AI 通信

  **Recommended Agent Profile**：`medium` — 多线程状态管理 | Wave 4 | Blocks: None | Blocked By: T22
  **References**：ChatMessage model (T5), ChatHistoryManager (T22)
  **Acceptance**：创建 3 个线程 → 切换线程历史正确隔离 → 删除线程后不可恢复
  **QA**：JUnit: createThread 3 次 → switchTo thread 2 → 消息隔离验证
  **Evidence**: `.sisyphus/evidence/task-23-multi-thread-test.txt`
  **Commit**: `feat: implement multi-conversation thread management` — `ConversationManager.java`, tests

- [ ] 24. 配置 GUI 界面

  **What to do**：
  - 创建 `ConfigScreen.java`：图形化配置界面
  - 字段：API Key（密码遮罩）、Base URL、Model、Temperature（滑块）、Max Tokens、Sidebar Position（下拉）、Sidebar Width（滑块）
  - 通过 Mod Menu 集成或 `/agentchat config` 指令打开
  - 保存时写入 `Config.java` 和 `SecretsConfig.java`
  - `neoforge.mods.toml` 中注册 `IConfigScreenFactory` 扩展点

  **Must NOT do**：不要在 GUI 中明文显示完整 API Key（仅显示前 4 位 + 后 4 位，中间用 * 替代）

  **Recommended Agent Profile**：
  - **Category**: `visual-engineering` — 表单 GUI + 滑块 + 下拉
  - **Skills**: []
  - **Parallelization**：Wave 4 | Blocks: None | Blocked By: T3, T6
  - **References**：`IConfigScreenFactory`, `net.minecraft.client.gui.components.EditBox`, `Button`, `SliderButton`
  - **Acceptance**：打开配置 → 修改 model → 保存 → 下次 API 调用使用新 model | API Key 遮罩显示
  - **QA**：Gradle runClient: 打开配置界面 → 截图验证
  - **Evidence**: `.sisyphus/evidence/task-24-config-gui.png`
  - **Commit**: `feat: implement GUI configuration screen` — `ConfigScreen.java`

- [ ] 25. `/agentchat` 指令

  **What to do**：
  - 注册客户端指令 `/agentchat`：
    - `/agentchat config` → 打开配置界面
    - `/agentchat key set <key>` → 设置 API Key
    - `/agentchat clear` → 清除当前对话历史
    - `/agentchat threads` → 列出所有对话线程
    - `/agentchat thread new <name>` → 新建线程
    - `/agentchat thread switch <id>` → 切换线程
  - 通过 `RegisterCommandsEvent` 在客户端注册
  - 使用 `CommandDispatcher` 构建命令树

  **Must NOT do**：不要在服务端注册命令（客户端 Mod）

  **Recommended Agent Profile**：`quick` — 标准命令注册 | Wave 4 | Blocks: None | Blocked By: T2, T23, T24
  **References**：`RegisterCommandsEvent`, `Commands.literal()`, NeoForge 命令文档
  **Acceptance**：`/agentchat config` 打开配置界面 | `/agentchat key set sk-xxx` 保存 Key
  **QA**：Gradle runClient: 输入 `/agentchat config` → 验证界面打开
  **Evidence**: `.sisyphus/evidence/task-25-commands.txt`
  **Commit**: `feat: add /agentchat command for config and thread management` — command registration

- [ ] 26. 错误处理与状态提示

  **What to do**：
  - 创建 `StatusBanner.java`：统一的状态提示组件
    - 绿色：成功状态（"已连接到 API"）
    - 黄色：警告（"API Key 未配置"）
    - 红色：错误（"网络错误：连接超时"）
    - 蓝色：加载中（"AI 正在思考..."）
  - 在所有 UI 组件中集成错误提示（侧边栏 + 全屏）
  - 处理首次启动无 API Key 的场景：显示引导提示
  - 处理所有工具执行中的异常：捕获并格式化为用户可读的错误消息

  **Must NOT do**：不要在错误消息中暴露敏感信息（API Key、请求体）

  **Recommended Agent Profile**：`medium` — 错误处理 + UI 状态管理 | Wave 4 | Blocks: None | Blocked By: T16, T17, T18
  **References**：Minecraft `Component.literal().withStyle(ChatFormatting.RED)`, i18n 错误键
  **Acceptance**：无 API Key → 显示引导 | 网络错误 → 红色提示 | API 成功 → 绿色状态
  **QA**：Gradle runClient: 不配 API Key → 验证引导提示 | 断网 → 验证错误提示
  **Evidence**: `.sisyphus/evidence/task-26-error-states.txt`
  **Commit**: `feat: add unified error handling and status banner` — `StatusBanner.java`

---

## Final Verification Wave

> 4 个审查代理**并行**执行。所有审查必须 **APPROVE**。汇总结果呈现给用户，**等待用户明确 "okay"** 后才算完成。

- [ ] F1. **Plan Compliance Audit** — `oracle`
  Read the plan end-to-end. For each "Must Have": verify implementation exists (read file, check classes). For each "Must NOT Have": search codebase for forbidden patterns — reject with file:line if found. Check evidence files exist in `.sisyphus/evidence/`. Compare deliverables against plan.
  Output: `Must Have [N/N] | Must NOT Have [N/N] | Tasks [N/N] | VERDICT: APPROVE/REJECT`

- [ ] F2. **Code Quality Review** — `unspecified-high`
  Run `./gradlew build` + `./gradlew test`. Review all changed files for: Java warnings, empty catch blocks, `System.out.println` 残留, commented-out code, unused imports. Check AI slop: excessive comments, over-abstraction, generic names (data/result/item/temp). Detect AI-slop anti-patterns from guardrails.
  Output: `Build [PASS/FAIL] | Tests [N pass/N fail] | Files [N clean/N issues] | VERDICT`

- [ ] F3. **Real Manual QA** — `unspecified-high`
  Start from clean state. Execute ALL QA scenarios from ALL tasks — follow exact steps, capture evidence. Test cross-task integration: 
  - Open inventory → see sidebar → type "what's in my inventory" → AI calls get_inventory → streams answer
  - Type "how to craft diamond sword" → AI calls lookup_recipe → streams recipe
  - Press ` key → full-screen chat opens
  - Create two worlds → verify history isolation
  Test edge cases: no API key, network off, empty inventory, rapid typing.
  Save to `.sisyphus/evidence/final-qa/`.
  Output: `Scenarios [N/N pass] | Integration [N/N] | Edge Cases [N tested] | VERDICT`

- [ ] F4. **Scope Fidelity Check** — `deep`
  For each task: read "What to do", read actual diff (git log/diff). Verify 1:1 — everything in spec was built (no missing), nothing beyond spec was built (no creep). Check "Must NOT do" compliance. Detect cross-task contamination. Flag unaccounted changes.
  Output: `Tasks [N/N compliant] | Contamination [CLEAN/N issues] | Unaccounted [CLEAN/N files] | VERDICT`

---

## Commit Strategy

| Wave | Tasks | Commit Pattern |
|------|-------|---------------|
| 1 | 1-6 | Individual commits per task (6 commits) |
| 2 | 7-15 | Individual commits per task (9 commits) |
| 3 | 16-21 | Individual commits per task (6 commits) |
| 4 | 22-26 | Individual commits per task (5 commits) |
| FINAL | F1-F4 | No commits (review only) |

**Commit message format**: `type(scope): description`
- `chore:` — build, deps, config
- `feat:` — new feature/module
- `test:` — test-only changes
- Example: `feat(ai): implement OpenAI-compatible HTTP client with streaming`

---

## Success Criteria

### Verification Commands

```bash
# Build and test
cd C:\Users\lumoren\Documents\GitHub\Agent-chat-in-MC
./gradlew build          # Expected: BUILD SUCCESSFUL
./gradlew test           # Expected: All tests passed (≥30 tests)
./gradlew runClient      # Expected: MC starts with mod loaded
```

### Final Checklist
- [ ] All 26 implementation tasks completed
- [ ] All 4 final verification tasks APPROVED
- [ ] All "Must Have" present (5 game data tools, sidebar, full-screen, streaming, history, i18n)
- [ ] All "Must NOT Have" absent (no game state modification, no OkHttp, no Mod interop interfaces)
- [ ] `./gradlew build` → BUILD SUCCESSFUL
- [ ] `./gradlew test` → All ≥30 tests PASS
- [ ] Evidence files exist for all tasks in `.sisyphus/evidence/`
- [ ] User explicitly confirms "okay" after Final Verification presentation

