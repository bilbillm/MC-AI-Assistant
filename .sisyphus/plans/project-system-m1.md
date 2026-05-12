
# Project System M1 — AI 驱动的项目规划与进度追踪

## TL;DR

> **Quick Summary**: 为 MC-AI-Assistant 添加项目/目标层——玩家通过自然语言设定目标，AI 拆解为 3-8 步的任务链，HUD 叠加层实时追踪进度，背包物品检测自动标记完成。

> **Deliverables**:
> - Project/Task 数据模型 + JSON 持久化
> - AI 项目规划服务（自然语言 → 结构化任务链）
> - 游戏画面 HUD 叠加层（可拖动、可折叠）
> - 背包变化 tick 监听器 + 自动进度检测
> - 系统提示词动态注入项目上下文
> - 手动任务编辑（重排/删除/备注）

> **Estimated Effort**: Medium
> **Parallel Execution**: YES — 3 waves
> **Critical Path**: Task 1 → Task 4 → Task 5 → Task 7 → Task 10 → Task 12

---

## Context

### Original Request
用户希望 MC-AI-Assistant 不只做问答工具，而是成为能主动规划、追踪进度的 AI 伙伴。M1 先做单项目 MVP。

### Interview Summary
**Key Discussions**:
- **创建方式**: 自然语言 + 手动修正，AI 生成初版任务链 → 用户可调顺序/删任务/加备注
- **UI 位置**: 游戏主画面 HUD 叠加层（NeoForge IGuiOverlay），紧凑模式 + 点击展开，可拖动
- **进度检测**: 本地轻量算法 — tick 监听背包变化 → 对比 Task itemId+count
- **项目 scope**: Per-world，跟随 ConversationManager 生命周期
- **单项目模式**: 新项目覆盖旧项目，旧项目自动归档
- **死亡处理**: AI 自动重评估，标记相关任务 BLOCKED
- **测试**: TDD for non-MC code + Agent QA for HUD

### Metis Review
**Identified Gaps** (addressed):
- HUD 渲染机制: 选定 RegisterGuiOverlaysEvent (IGuiOverlay)
- Persistence scope: 明确 per-world
- Inventory matching: 本地轻量算法
- Tick interval: 20 ticks (1s)，可配置
- Edge cases: 空背包、维度切换、NBT 忽略(M1)、CJK 字体、快速死亡去重

---

## Work Objectives

### Core Objective
为 MC-AI-Assistant 添加基于 AI 的项目规划与进度追踪系统（M1 MVP）

### Concrete Deliverables
- `model/Project.java`, `Task.java`, `ItemRequirement.java` — 数据模型
- `persistence/ProjectManager.java` — per-world JSON 持久化
- `ai/ProjectPlanningService.java` — AI 提示词构建 + 响应解析
- `client/ProjectProgressTracker.java` — tick 背包监听 + 进度检测
- `ui/ProjectHudOverlay.java` — NeoForge IGuiOverlay HUD 叠加层
- `ui/ProjectHudWidget.java` — 可拖动/折叠的 HUD widget
- 修改 `AIChatService.java` — 系统提示词动态注入
- 修改 `AIChatScreen.java` — 项目创建对话流
- 修改 `ClientEventHandler.java` — tick hook 注册
- 修改 `Config.java` — 新增 `projectTrackInterval` 配置

### Definition of Done
- [ ] `./gradlew build --no-daemon` 通过
- [ ] 所有非 MC 模型/持久化单元测试通过
- [ ] 玩家说"/我要做钻石剑/"→ AI 生成 3-8 步任务链 → 确认后出现在 HUD
- [ ] 背包变化 → 自动检测完成 → HUD 更新进度
- [ ] 项目完成后自动归档，聊天显示庆祝消息
- [ ] HUD 可拖动，位置持久化

### Must Have
- 单项目模式
- AI 生成结构化任务链（可解析的 JSON）
- 本地进度检测（itemId + count 匹配）
- NeoForge IGuiOverlay HUD（可拖动/折叠）
- Per-world 持久化
- 系统提示词动态注入

### Must NOT Have (Guardrails)
- 多项目并行支持
- AI API 调用在 tick 线程上
- `graphics.drawString()` 直接调用 — 全部走 AbstractWidget
- 项目导出/导入/分享
- 复杂 NBT 匹配（M1 只做 itemId + count）
- 成就弹窗/音效 — 完成庆祝仅一条聊天消息

---

## Verification Strategy

> **ZERO HUMAN INTERVENTION** - ALL verification is agent-executed.

### Test Decision
- **Infrastructure exists**: YES (Gradle + JUnit)
- **Automated tests**: TDD for non-MC code; Agent QA for HUD
- **Framework**: JUnit (existing) + Agent-executed manual scenarios

### QA Policy
- **非 MC 代码**: `./gradlew test --tests "..."` 验证
- **HUD 代码**: Agent 通过 `./gradlew runClient` 启动游戏，用 tmux 交互验证

---

## Execution Strategy

### Parallel Execution Waves

```
Wave 2 (After Wave 1 — AI + detection + JEI):
├── [x] Task 4: ProjectPlanningService AI 提示词 + 响应解析 [unspecified-high]
├── [x] Task 5: ProjectProgressTracker tick 监听 + 背包 diff [unspecified-high]
├── [x] Task 6: Config 扩展 — projectTrackInterval [quick]
├── [x] Task 12: JEI API 集成 — RecipeTool 重构 [unspecified-high]

Wave 3 (After Wave 2 — UI + integration):
├── [x] Task 7: ProjectHudOverlay + ProjectHudWidget (NeoForge overlay) [visual-engineering]
├── [x] Task 8: ClientEventHandler — tick hook 注册 [quick]
├── [x] Task 9: AIChatService — 系统提示词动态注入 [unspecified-high]
├── [x] Task 10: AIChatScreen — 项目创建对话流 + 完成庆祝 [unspecified-high]
├── [x] Task 11: ProjectManager 归档 + 重激活逻辑 (done in Task 2) [quick]

Wave FINAL:
├── Task F1: Plan compliance audit (oracle)
├── Task F2: Code quality review (unspecified-high)
├── Task F3: Real QA — runClient 端到端测试 (unspecified-high)
└── Task F4: Scope fidelity check (deep)
```

---

## TODOs

- [x] 12. JEI API 集成 — RecipeTool 重构

  **What to do**:
  - 创建 `client/JeiBridge.java`: implements `IModPlugin`
    - `getPluginUid()` 返回 `ResourceLocation("agentchat", "jei_bridge")`
    - `onRuntimeAvailable(IJeiRuntime)` → 缓存 runtime 到 `AgentChat.jeiRuntime`
  - 在 `AgentChat.java` 添加 `public static IJeiRuntime jeiRuntime = null;`
  - 在 `AgentChat.java` 添加 `public static boolean hasJei() { return jeiRuntime != null; }`
  - 重构 `tools/RecipeTool.java`:
    - 保持现有 `GameDataAccess.getRecipesForOutput()` 作为兜底
    - 新增 JEI 查询路径:
      - `lookupRecipes(ItemStack)`: `IFocusFactory.createFocus(OUTPUT, ITEM_STACK, stack)` → `createRecipeCategoryLookup().limitFocus(focus).get()`
      - `lookupUsages(ItemStack)`: `IFocusFactory.createFocus(INPUT, ITEM_STACK, stack)` → 同上
    - `getTags(ItemStack)`: `IIngredientHelper.getTags(stack)` → 返回 `Set<ResourceLocation>`
    - 统一输出格式: 无论 JEI 还是原版，都返回 `RecipeResult` 列表
  - 在 `build.gradle` 添加:
    ```groovy
    repositories { maven { url "https://maven.blamejared.com" } }
    dependencies { compileOnly("mezz.jei:jei-${mc_version}-neoforge-api:${jei_version}") }
    ```
  - 在 `neoforge.mods.toml` 添加可选依赖:
    ```toml
    [[dependencies.agentchat]]
        modId = "jei"
        mandatory = false
    ```
  - 在 `gradle.properties` 添加 `jei_version=19.21.0.247`

  **Must NOT do**:
  - 不要硬依赖 JEI — compileOnly + optional mod dependency
  - 不要删除现有原版 RecipeManager 查询逻辑

  **Recommended Agent Profile**:
  - **Category**: `unspecified-high`
  - **Skills**: []

  **Parallelization**:
  - **Parallel Group**: Wave 2 (with Tasks 4, 5, 6)
  - **Blocks**: None
  - **Blocked By**: None (RecipeTool already exists, refactoring it)

  **References**:
  - 现有 RecipeTool: `tools/RecipeTool.java` — execute() and GameDataAccess.getRecipesForOutput()
  - 现有 GameDataAccess: `ai/GameDataAccess.java:150-198` — getRecipesForOutput vanilla path
  - JEI API 参考: `mezz.jei.api.runtime.IJeiRuntime` — getRecipeManager(), getJeiHelpers()
  - JEI API 参考: `mezz.jei.api.recipe.IFocusFactory` — createFocus(role, type, ingredient)
  - JEI API 参考: `mezz.jei.api.recipe.RecipeIngredientRole` — OUTPUT, INPUT
  - JEI 入口点: `AgentChat.java` — mod 主类，添加静态字段

  **Acceptance Criteria**:
  - [ ] `./gradlew build --no-daemon` → PASS（JEI API compileOnly）
  - [ ] 无 JEI 运行时: RecipeTool 走原版兜底，不崩溃
  - [ ] 有 JEI 运行时: `lookupRecipes` 返回全模组全配方类型
  - [ ] `lookupUsages(diamond)` 返回所有用到钻石的配方
  - [ ] `getTags(iron_ingot)` 返回包含 `forge:ingots/iron` 的标签集

  **QA Scenarios**:
  ```
  Scenario: JEI forward lookup returns multi-mod recipes
    Tool: interactive_bash (tmux)
    Preconditions: Game running with JEI + some mods installed
    Steps:
      1. ./gradlew runClient --no-daemon
      2. Verify JEI loaded (inventory screen shows JEI panel)
      3. /agentchat → send message "What recipes make iron ingots?"
      4. AI calls lookup_recipe("iron_ingot") → verify response includes smelting + crafting + mod recipes
    Expected Result: Multiple recipe types returned, not just crafting table
    Evidence: .sisyphus/evidence/task-12-jei-forward.txt

  Scenario: Fallback works without JEI
    Tool: Bash (gradle)
    Steps:
      1. Run build without JEI at runtime
      2. RecipeTool.lookupRecipes(diamond_sword) → verify returns only crafting table recipes
      3. Assert no ClassNotFoundException or crash
    Expected Result: Graceful fallback to vanilla RecipeManager
    Evidence: .sisyphus/evidence/task-12-fallback.txt
  ```

  **Commit**: YES
  - Message: `feat(tools): integrate JEI API as primary recipe source with vanilla fallback`
  - Files: `client/JeiBridge.java`, `AgentChat.java`, `tools/RecipeTool.java`, `build.gradle`, `gradle.properties`, `neoforge.mods.toml`

- [ ] 7. ProjectHudOverlay + ProjectHudWidget (NeoForge IGuiOverlay)

  **What to do**:
  - 创建 `ui/ProjectHudWidget.java`: extends `AbstractWidget`
    - 紧凑模式: 显示项目名 + 进度分数 (2/5) + 当前步骤（截断到 30 字符）
    - 点击展开: 显示完整任务清单，每行一个 task，DONE 划线，当前高亮
    - 支持拖动: 记录 offsetX/offsetY，mouseDragged 更新位置
    - 位置持久化: 存在 ProjectManager 的 overlayConfig 中
    - 退出 MC 时存储位置，进世界时恢复
    - 空状态: 无活跃项目时显示 "No active project"
  - 创建 `ui/ProjectHudOverlay.java`: implements `IGuiOverlay`
    - 通过 `RegisterGuiOverlaysEvent` 注册到 NeoForge
    - render 时检查 mc.player != null → 创建/更新 ProjectHudWidget
    - 从 ProjectManager 获取当前活跃项目
  - 颜色: 在 `ChatColors.java` 添加项目相关颜色常量
    - HUD_BG, HUD_BORDER, TASK_DONE, TASK_BLOCKED

  **Must NOT do**:
  - 不要直接调 `graphics.drawString()` — 全部走 AbstractWidget
  - 不要在 render 里做 AI 调用或文件 I/O

  **Recommended Agent Profile**:
  - **Category**: `visual-engineering`
  - **Skills**: []

  **Parallelization**:
  - **Parallel Group**: Wave 3 (with Tasks 8, 9, 10, 11)
  - **Blocks**: None
  - **Blocked By**: Task 1, Task 2, Task 3

  **References**:
  - Widget 模式: `ui/LabelWidget.java` — AbstractWidget 最小实现 (full file)
  - Overlay 注册: NeoForge `RegisterGuiOverlaysEvent` — 标准 IGuiOverlay 注册
  - 拖动模式: `ui/MessageListWidget.java` — scrollbar drag 交互 (lines 100-130)
  - 颜色模式: `ui/theme/ChatColors.java` — 0xAARRGGBB int constants
  - Widget 裁剪: `ui/ThreadListWidget.java:97-106` — scissor pattern for overflow

  **Acceptance Criteria**:
  - [ ] `./gradlew build --no-daemon` → PASS
  - [ ] 游戏内 HUD 叠加层可见，不遮挡原版 UI
  - [ ] 可拖动到不同位置，重启后位置保持
  - [ ] 点击展开/折叠切换正常
  - [ ] mc.player == null 时 HUD 隐藏，不崩溃

  **QA Scenarios**:
  ```
  Scenario: HUD visible with active project
    Tool: interactive_bash (tmux)
    Preconditions: Game running with active project
    Steps:
      1. ./gradlew runClient --no-daemon
      2. Wait for world load
      3. Create project via chat command
      4. Verify HUD widget visible at default position showing project name + "0/N"
    Expected Result: HUD visible with project info, FPS stable
    Evidence: .sisyphus/evidence/task-7-visible.txt

  Scenario: HUD hides when no project
    Tool: interactive_bash (tmux)
    Preconditions: No active project
    Steps:
      1. Verify HUD shows "No active project" or is hidden
    Expected Result: No crash, minimal screen space used
    Evidence: .sisyphus/evidence/task-7-no-project.txt
  ```

  **Commit**: YES
  - Message: `feat(ui): add ProjectHudOverlay and ProjectHudWidget for game-screen project display`
  - Files: `ui/ProjectHudOverlay.java`, `ui/ProjectHudWidget.java`, `ui/theme/ChatColors.java`

- [ ] 8. ClientEventHandler — tick hook 注册 + ProjectManager 生命周期

  **What to do**:
  - 修改 `client/ClientEventHandler.java`:
    - `onLevelLoad`: 在 `ConversationManager.initialize` 后添加 `ProjectManager.initialize(saveDir)`
    - `onLevelUnload`: 在 `ConversationManager.shutdown` 前添加 `ProjectManager.shutdown()`
    - `onClientTick`: 添加 tick 计数器 → 达到 `Config.projectTrackInterval` 时调用 `ProjectProgressTracker.onClientTick()`
    - 计数器每 tick 递增，超过 interval 时归零
  - 确保 tick 监听和 keybind 检查不冲突

  **Must NOT do**:
  - 不要在 tick 线程上阻塞
  - 不要破坏现有的 keybind 逻辑

  **Recommended Agent Profile**:
  - **Category**: `quick`
  - **Skills**: []

  **Parallelization**:
  - **Parallel Group**: Wave 3 (with Tasks 7, 9, 10, 11)
  - **Blocks**: None
  - **Blocked By**: Task 2, Task 5, Task 6

  **References**:
  - 事件模式: `client/ClientEventHandler.java` — onLevelLoad (lines 32-38), onClientTick (lines 25-29)
  - 生命周期: `persistence/ConversationManager.java` — initialize/shutdown (lines 31-52)

  **Acceptance Criteria**:
  - [ ] `./gradlew build --no-daemon` → PASS
  - [ ] 进入世界 → ProjectManager 初始化 → 加载已有项目
  - [ ] 每 20 tick 触发一次 tracker.onClientTick()
  - [ ] 退出世界 → ProjectManager.shutdown() 保存数据

  **QA Scenarios**:
  ```
  Scenario: ProjectManager lifecycle follows world load/unload
    Tool: Bash (gradle)
    Steps:
      1. ./gradlew test --tests "com.lumoren.agentchat.client.ClientEventHandlerTest" --no-daemon
    Expected Result: Tests verify onLevelLoad initializes ProjectManager, onLevelUnload shuts it down
    Evidence: .sisyphus/evidence/task-8-lifecycle.txt
  ```

  **Commit**: YES
  - Message: `feat(client): integrate ProjectManager lifecycle and tick-based progress tracking`
  - Files: `client/ClientEventHandler.java`

- [ ] 9. AIChatService — 系统提示词动态注入

  **What to do**:
  - 修改 `ai/AIChatService.java`:
    - `sendMessage()` 方法中，在 `conversation.add(ChatMessage.system(SYSTEM_PROMPT))` **之后**、`conversation.addAll(history)` **之前**
    - 如果 `ProjectManager.getActiveProject() != null` → 插入一条 `ChatMessage.system(projectContext)`
    - `projectContext` 由 `ProjectPlanningService.buildProjectContextMessage()` 生成
    - 注意: 只在 chat 对话中注入，不要在工具调用链中重复注入
  - SYSTEM_PROMPT 保持 `static final`，不变 — 只追加项目上下文

  **Must NOT do**:
  - 不要把项目上下文持久化到 ConversationThread
  - 不要在 tool-calling 循环中重复注入

  **Recommended Agent Profile**:
  - **Category**: `unspecified-high`
  - **Skills**: []

  **Parallelization**:
  - **Parallel Group**: Wave 3 (with Tasks 7, 8, 10, 11)
  - **Blocks**: None
  - **Blocked By**: Task 4

  **References**:
  - 注入点: `ai/AIChatService.java:73-80` — sendMessage conversation building
  - 系统提示词: `ai/AIChatService.java:32-35` — SYSTEM_PROMPT 常量

  **Acceptance Criteria**:
  - [ ] `./gradlew test --tests "com.lumoren.agentchat.ai.AIChatServiceTest"` → PASS
  - [ ] 活跃项目存在 → 对话中包含项目上下文 system 消息
  - [ ] 无活跃项目 → 对话中只有基础 SYSTEM_PROMPT

  **QA Scenarios**:
  ```
  Scenario: Project context injected when active
    Tool: Bash (gradle)
    Steps:
      1. Test: create active project, call sendMessage
      2. Verify conversation list contains 2 system messages (base + project context)
    Expected Result: Project context present in conversation
    Evidence: .sisyphus/evidence/task-9-injection.txt

  Scenario: No injection when no active project
    Tool: Bash (gradle)
    Steps:
      1. Test: no active project, call sendMessage
      2. Verify conversation list contains only 1 system message (base only)
    Expected Result: No extra system messages
    Evidence: .sisyphus/evidence/task-9-no-injection.txt
  ```

  **Commit**: YES
  - Message: `feat(ai): inject active project context into system prompt for contextual AI responses`
  - Files: `ai/AIChatService.java`

- [ ] 10. AIChatScreen — 项目创建对话流 + 完成庆祝

  **What to do**:
  - 修改 `ui/AIChatScreen.java`:
    - `onStreamUpdate()` 或 `sendMessage()` 中添加意图检测: 
      - 简单关键词匹配 "我要做"/"我想做"/"I want to make" → 触发项目创建流
    - 项目创建流:
      1. 用户说 "我要做钻石剑"
      2. AI 返回任务链预览（非流式显示）
      3. 聊天中显示 "创建项目? [确认] [修改] [取消]"
      4. 用户确认 → 调用 `ProjectManager.saveProject()` → HUD 更新
    - 完成庆祝:
      - 在 `onStreamUpdate()` 中检测所有 task 变为 DONE
      - 调用 `ProjectManager.archiveProject()`
      - 聊天中添加消息: "🎉 项目「去末地」已完成！"
    - 用户可以在聊天中手动编辑: "删除第3步" / "把第2步改成..." → 解析命令更新 tasks

  **Must NOT do**:
  - 不要为项目创建开新 Screen — 保持在聊天流中
  - 不要修改现有的 sendMessage 核心逻辑

  **Recommended Agent Profile**:
  - **Category**: `unspecified-high`
  - **Skills**: []

  **Parallelization**:
  - **Parallel Group**: Wave 3 (with Tasks 7, 8, 9, 11)
  - **Blocks**: None
  - **Blocked By**: Task 4, Task 5

  **References**:
  - 聊天流: `ui/AIChatScreen.java` — sendMessage, onStreamUpdate (lines 248-331)
  - 自动命名: `ui/AIChatScreen.java:249-283` — auto-rename callback pattern

  **Acceptance Criteria**:
  - [ ] 用户说 "我要做钻石剑" → AI 生成任务链 → 显示确认 UI
  - [ ] 确认后 → HUD 出现项目 → 可以追踪进度
  - [ ] 全部任务完成 → 聊天显示庆祝消息 → 项目归档

  **QA Scenarios**:
  ```
  Scenario: Project creation via chat
    Tool: interactive_bash (tmux)
    Preconditions: Game running, AIChatScreen open
    Steps:
      1. Type "我要做钻石剑" and send
      2. Wait for AI response with task chain
      3. Verify confirmation prompt appears
      4. Confirm → verify HUD shows project
    Expected Result: Project created, visible in HUD
    Evidence: .sisyphus/evidence/task-10-create.txt

  Scenario: Manual task editing
    Tool: interactive_bash (tmux)
    Steps:
      1. With active project, type "删除第2步"
      2. Verify task removed from HUD
      3. Type "添加步骤: 合成附魔台"
      4. Verify new task appears in HUD
    Expected Result: Tasks updated, persisted after reload
    Evidence: .sisyphus/evidence/task-10-edit.txt
  ```

  **Commit**: YES
  - Message: `feat(ui): add project creation flow and completion celebration in AIChatScreen`
  - Files: `ui/AIChatScreen.java`

- [ ] 11. ProjectManager 归档 + 重激活逻辑

  **What to do**:
  - 在 `persistence/ProjectManager.java` 中完善:
    - `archiveCurrentProject()`: 当前活跃项目 → 标记 ARCHIVED → 移动到归档列表
    - `getArchivedProjects()`: 返回所有归档项目（用于历史浏览）
    - `reactivateProject(String id)`: 归档项目 → 恢复为 IN_PROGRESS → 设为当前活跃
    - `deleteArchivedProject(String id)`: 永久删除归档项目
    - 自动清理: 归档项目超过 maxArchivedProjects (10) → 删除最旧的
  - 编写单元测试覆盖归档/重激活/清理

  **Must NOT do**:
  - 不要创建复杂的归档浏览器 UI — M1 只需要数据层

  **Recommended Agent Profile**:
  - **Category**: `quick`
  - **Skills**: []

  **Parallelization**:
  - **Parallel Group**: Wave 3 (with Tasks 7, 8, 9, 10)
  - **Blocks**: None
  - **Blocked By**: Task 2

  **References**:
  - 归档模式: `persistence/ConversationManager.java` — 线程生命周期管理 (full file)

  **Acceptance Criteria**:
  - [ ] `./gradlew test --tests "com.lumoren.agentchat.persistence.ProjectManagerTest"` → PASS
  - [ ] 归档后 active=null, archived.size()=1
  - [ ] 重激活后 archived.size()=0, active != null
  - [ ] 超过 10 个归档 → 最旧的自动删除

  **QA Scenarios**:
  ```
  Scenario: Archive and reactivate project
    Tool: Bash (gradle)
    Steps:
      1. Create project "test" → archive → assert active=null
      2. Reactivate "test" → assert active.name="test"
      3. Assert archived list is empty
    Expected Result: Round-trip works correctly
    Evidence: .sisyphus/evidence/task-11-archive.txt
  ```

  **Commit**: YES
  - Message: `feat(persistence): add project archival, reactivation, and auto-cleanup`
  - Files: `persistence/ProjectManager.java`, test file

---

## Final Verification Wave (MANDATORY — after ALL implementation tasks)

> 4 review agents run in PARALLEL. ALL must APPROVE.

- [ ] F1. **Plan Compliance Audit** — `oracle`
  For each "Must Have": verify implementation exists. For each "Must NOT Have": search for forbidden patterns. Check evidence files.
  Output: `Must Have [N/N] | Must NOT Have [N/N] | Tasks [in N] | VERDICT: APPROVE/REJECT`

- [ ] F2. **Code Quality Review** — `unspecified-high`
  Run `./gradlew build --no-daemon`. Review changed files for empty catches, unused imports, AI slop.
  Output: `Build [PASS/FAIL] | Tests [N pass/N fail] | VERDICT`

- [ ] F3. **Real QA** — `unspecified-high`
  Execute EVERY QA scenario, capture evidence. Test cross-task integration.
  Output: `Scenarios [N/N pass] | Integration [N/N] | VERDICT`

- [ ] F4. **Scope Fidelity Check** — `unspecified-high`
  Verify 1:1 — everything in spec built, nothing beyond. Check "Must NOT do" compliance.
  Output: `Tasks [N/N compliant] | Contamination [CLEAN/N issues] | VERDICT`

---

## Commit Strategy

- **1**: `feat(model): add Project, Task, ItemRequirement data models` — model/*.java + tests
- **2**: `feat(persistence): add ProjectManager for project CRUD` — persistence/ProjectManager.java + test
- **3**: `feat(i18n): add project system translation keys` — i18n/* + lang/*.json
- **4**: `feat(ai): add ProjectPlanningService` — ai/ProjectPlanningService.java + test
- **5**: `feat(client): add ProjectProgressTracker` — client/ProjectProgressTracker.java + test
- **6**: `feat(config): add projectTrackInterval` — config/Config.java + ConfigManager.java
- **12**: `feat(tools): integrate JEI API as primary recipe source` — JeiBridge.java, AgentChat.java, RecipeTool.java, build.gradle
- **7**: `feat(ui): add ProjectHudOverlay and ProjectHudWidget` — ui/*.java + theme/ChatColors.java
- **8**: `feat(client): integrate ProjectManager lifecycle` — client/ClientEventHandler.java
- **9**: `feat(ai): inject project context into system prompt` — ai/AIChatService.java
- **10**: `feat(ui): add project creation flow in AIChatScreen` — ui/AIChatScreen.java
- **11**: `feat(persistence): add project archival and reactivation` — persistence/ProjectManager.java + test

---

## Success Criteria

### Verification Commands
```bash
./gradlew build --no-daemon  # Expected: BUILD SUCCESSFUL
./gradlew test --no-daemon   # Expected: all non-MC tests pass
```

### Final Checklist
- [ ] All "Must Have" present
- [ ] All "Must NOT Have" absent
- [ ] HUD overlay visible on game screen
- [ ] Project creation via chat works
- [ ] Inventory auto-detection works
- [ ] Project persists across world reloads





