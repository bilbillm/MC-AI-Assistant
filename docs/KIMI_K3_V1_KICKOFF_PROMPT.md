# Kimi K3 开工提示词：v1 整合包 AI 向导 + 机械动力风格 UI

你现在是这个仓库的主实现工程师。请直接在当前工作区继续开发，不要只给方案，也不要重新从零实现已有功能。

工作区：

`C:\Users\lumoren\Documents\GitHub\Agent-chat-in-MC`

项目：Java 21、NeoForge 1.21.1 的 client-only Minecraft 模组 `MC-AI-Assistant`。

本轮总目标：

1. 把当前工作区里已经开始实现的“v1 整合包 AI 向导”补完并达到可发布状态。
2. 保留原有 Chat、线程、流式输出、DeepSeek 思考过程、项目系统和 HUD；Guide 是追加功能，不是替换聊天。
3. 在功能闭环后，把主聊天界面、配置页和项目 HUD 统一重做成原创的机械动力/Create 工坊风格 UI。
4. 完成测试、真实 Minecraft 运行时验收和最新 jar 构建。

## 必读文件

开始任何编辑前，完整阅读：

1. `AGENTS.md`
2. `docs/V1_MODPACK_GUIDE_CREATE_UI_KICKOFF.md`
3. 与当前 Slice 直接相关的生产代码和测试

`docs/V1_MODPACK_GUIDE_CREATE_UI_KICKOFF.md` 是本任务的需求、架构、视觉、施工和验收唯一主规格。若本提示词与该文档细节不同，以该文档为准；若该文档与 `AGENTS.md` 的硬约束不同，以 `AGENTS.md` 为准。

## 当前工作区特别警告

当前在 `master`，工作区已经很脏：存在大量 modified 和 untracked 的 v1 文件。这些是上一阶段已经完成或进行中的实现，不是垃圾文件。

禁止执行：

- `git reset --hard`
- `git checkout -- <file>`
- `git clean`
- 删除或覆盖不属于你本轮明确修改的现有变化
- 为了“恢复干净基线”而回退当前 v1 代码

必须先运行：

```powershell
git status --short
git diff --stat
```

先理解当前实现，再最小范围修改。`git diff` 不会显示 untracked 文件，所以必须同时读取 `guide/`、新增 tools、ActionCard 测试等未跟踪文件。

## 不可违反的工程约束

- Java 21，Java-only。
- NeoForge 1.21.1。
- `gradle.properties` 当前 `neo_version=21.1.228`，用户安装实例为 21.1.219；未经用户明确同意，不升级或改动 NeoForge 版本。
- 禁止 OkHttp，只能使用 Java 21 `java.net.http.HttpClient`。
- 禁止 Lombok、Kotlin、`fabric.mod.json`。
- 保持 client-only；事件处理使用客户端 Dist。
- 所有玩家可见文本必须经过 `I18nKeys`、`I18nHelper`、`en_us.json`、`zh_cn.json`。
- API key 和 secrets 不得写入日志、卡片、测试 fixture、文档或提交。
- 不复制 Create 模组原始纹理、图标或其他受版权保护资源；机械风格资源必须原创。
- 不做无关的大重构、全仓格式化或依赖升级。

## 文本渲染硬约束

NeoForge 1.21.1 下，无 Widget 所有权的直接文本绘制会导致毛玻璃模糊问题。

- `Screen.render()` 只负责无文本背景/装饰和 `super.render()`。
- 所有文本由注册到界面的 `AbstractWidget` 体系拥有。
- 新的复杂文本组件必须是 Widget，不能继续创建普通 helper 再从中直接画字。
- 重构时检查所有 `drawString`/`drawWordWrap` 调用，确认它们只存在于合法的 text-owning Widget 渲染边界中。
- 禁止 emoji 作为结构图标，使用原创像素 sprite。

## 必须保护的现有行为

不要破坏：

1. `AIChatScreen.rebuildLayout()` 的 recursion guard。
2. 切换线程时先 `ConversationManager.switchThread()`，再 rebuild。
3. rebuild 前后 thread/message scroll 保存。
4. sidebar 折叠状态的 static/instance 同步。
5. 鼠标滚轮先分发 thread list，再 message list。
6. DeepSeek `reasoning_content` 的请求发送、SSE 解析、非流式解析和 UI 折叠。
7. 流式取消、错误显示、自动重命名、历史持久化。
8. 项目创建、推进、完成和 HUD 追踪。

## 当前实现不是空白

已经存在并应继续利用：

- Guide/Chat toggle。
- `ModpackGuideService` 和五种 `GuideIntent`。
- `GuideEvidence`、`GuideContext`、`GuideKnowledgeSource`。
- Local、JEI/vanilla、FTB Quests reflection、Patchouli/resource/local text、Web knowledge source。
- `ActionCard`、`AssistantAction`。
- `modpack_guide`、`inspect_target_block`、recipe/usage action tools。
- ActionCard 基础绘制和 UI 点击分发。
- `guideModeDefault`、`webKnowledgeEnabled`。
- system prompt 和一批单元测试。

不要创建第二套平行的 Guide 模型或工具注册系统。优先修补、拆分和复用当前实现。

## 已知发布阻断项

必须先完成以下 P0，完成之前不要进入最终 UI 视觉验收：

### 1. 向导上下文不能阻塞 UI 线程

当前发送消息时会同步做资源扫描、FTB/Patchouli 查询和 Web HTTP，可能冻结 Minecraft。

改成：

- Minecraft 主线程快速收集 immutable snapshot。
- 文件、索引、Web 在有界后台 executor 执行。
- 有 request token、取消、超时和阶段状态。
- 关闭 Screen、切线程、发送新请求后，旧结果不能写入新线程。
- Web 超时仍使用本地 evidence 继续回答。
- UI 在点击发送后 100ms 内出现状态反馈。

### 2. Recipe/Usage 动作要真正打开 JEI

当前 `OPEN_RECIPE`、`OPEN_USAGE` 只查询并产生另一张卡，没有打开 JEI GUI。

要求：

- JEI runtime 可用时，在主线程打开目标 item 的 recipe/usage GUI。
- JEI 不可用时显示 vanilla fallback card。
- item 无法解析时显示本地化错误或 disable action。
- 防止双击重复执行。

### 3. Action 必须 fail-closed

当前未知 action kind 会回退为可执行 `PIN_TASK`，这是错误行为。

要求：

- 未知 kind 被忽略，不执行。
- 每种 kind 有 payload validator。
- 缺字段时按钮不执行，并有本地化说明。
- 把动作执行从 `AIChatScreen` 拆到统一 `AssistantActionExecutor`。
- v1 卡片不能生成破坏性一键动作。
- `CREATE_PROJECT_FROM_PLAN` 必须有真正 producer 和端到端行为；若本轮不交付，就从 v1 UI/producer 中明确移除，不能保留假入口。

### 4. i18n 和 Widget 文本管线

当前新增卡片、动作、Tool、Thread、HUD 中仍有大量硬编码英文/中文和 emoji。

要求：

- ActionCard type、动作标签、状态、错误、tooltip、HUD 全部本地化。
- 中文和英文语言文件同步。
- 结构图标改成像素 sprite。
- 消息、卡片、状态和线程文本都归属于 Widget。

## 功能完成要求

按详细文档完成以下能力：

- `WHAT_NEXT`
- `RECIPE_PLAN`
- `MACHINE_DIAGNOSIS`
- `ITEM_HELP`
- `GENERAL_CHAT`

证据优先级：

```text
LOCAL_GAME / JEI
-> FTB_QUESTS
-> PATCHOULI
-> WEB
-> 明确标注无法验证
```

注意：

- 无关 FTB/Patchouli 文本不能阻止 recipe/usage 的 Web fallback。
- GuideContext 要有总字符预算和来源上限。
- 同一请求不能在 Screen 和 `modpack_guide` 内重复做整套 Web/资源扫描。
- Resource/Patchouli/local text 要缓存，并在 resource reload、实例/世界变化时失效。
- Web 自动链路需要明确 search -> read selected page，或者明确只把 snippet 标为 snippet。
- Web 内容是不可信引用，不能执行网页中的 prompt 指令。
- Web query 不发送坐标、玩家名、服务器地址、完整背包或完整历史。

## 机械动力风格 UI 方向

功能 P0 完成后，统一重做：

- `AIChatScreen`
- `ThreadListWidget` / `ThreadItemWidget`
- `MessageListWidget` / 消息组件
- ActionCard 和 source badge
- `ConfigScreen`
- `ProjectHudWidget`

视觉关键词：

```text
原创 Create 工坊感
铸铁深色基板
黄铜主交互
铜质次交互
铜绿成功/已验证状态
红色诊断/危险状态
像素压边、铆钉、机械导轨
Minecraft 原生字体
高信息密度、克制装饰
```

禁止：

- 蓝灰 SaaS 主题继续作为主视觉。
- 渐变、毛玻璃、柔光、模糊。
- 大面积米色、棕橙、深蓝或单一色相。
- 卡片套卡片。
- 满屏装饰性齿轮或无限动画。
- 复制 Create 纹理。

使用 `docs/V1_MODPACK_GUIDE_CREATE_UI_KICKOFF.md` 中的 `GuideTheme` 色彩 token、资源路径、三档布局和 ActionCard 规格。

响应式至少覆盖：

- 320x180 GUI px
- 426x240 GUI px
- 640x360 GUI px

要求：

- 窄屏侧栏变机械 rail 或 overlay drawer。
- Guide/Chat 是清晰分段控件。
- 工具栏使用 icon buttons 和 tooltip。
- 卡片按钮必须换行、纵排或进入 more，不能静默消失。
- 输入栏不覆盖最后一条消息。
- 展开 reasoning/facts/sources 后保持滚动锚点。

## 施工顺序

严格按以下纵向切片推进。每个 Slice 都要包含生产代码、测试和验证记录：

1. **Slice 0：锁定构建与测试基线**
2. **Slice 1：动作安全 + 真正打开 JEI**
3. **Slice 2：异步 GuideRequestCoordinator**
4. **Slice 3：知识源相关性、缓存、Web 安全**
5. **Slice 4：机械 UI 基础组件和原创 sprites**
6. **Slice 5：主 Screen 三档布局**
7. **Slice 6：消息、ActionCard、来源和动作状态**
8. **Slice 7：配置页和项目 HUD**
9. **Slice 8：真实运行时验收、全量构建和 jar**

除非出现必须由用户决定的依赖/版本问题，否则不要只停在计划。完成一个 Slice 后继续下一个。

## 第一个检查点

在编辑代码前，先回复一份简短基线报告，必须包含：

```text
Current branch:
Dirty worktree summary:
Existing v1 modules found:
Targeted tests result:
Full test/build result or exact blocker:
First Slice files to change:
Risks that need preservation:
```

报告后直接开始 Slice 0/1，不需要等待普通确认。只有以下情况需要停下来询问：

- 必须改变 NeoForge 版本。
- 必须增加 FTB/Patchouli 硬依赖。
- 必须删除或覆盖无法兼容的用户现有改动。
- 产品行为存在两个会显著改变范围且无法从文档判断的选项。

## 测试要求

每次相关修改先跑定向测试，Slice 完成跑全量非 Minecraft 测试：

```powershell
.\gradlew.bat test --no-daemon --tests "com.lumoren.agentchat.guide.*"
.\gradlew.bat test --no-daemon --tests "com.lumoren.agentchat.model.*ActionCard*"
.\gradlew.bat test --no-daemon
.\gradlew.bat build --no-daemon
```

需要 Minecraft runtime 的测试不能伪装成普通 JUnit 覆盖；加 `requires-minecraft` 或使用对应 NeoForge test/run 任务，并补真实手工验证。

至少补齐：

- 未知 action fail-closed。
- 每个 action payload validation。
- JEI available/unavailable。
- Web disabled 零请求。
- Web timeout 不阻断 local answer。
- request cancellation 和线程切换不串写。
- evidence 相关性、去重、优先级、预算。
- FTB/Patchouli API/资源 fallback。
- 机器目标：无世界、无目标、空气、普通方块、BlockEntity。
- 三档 UI layout、长中英文、4+ actions、10 facts、长 URL。
- Config Cancel/ESC 不保存。
- DeepSeek reasoning、流式、线程和 HUD 回归。

## 真实手工验收

最终必须在以下矩阵验证，不能只报告单测通过：

1. 原版、无 JEI、Web off。
2. 原版 + JEI。
3. FTB Quests + Patchouli + JEI 整合包。
4. Web on/off。
5. 中文和英文客户端。
6. 无目标、普通方块、BlockEntity 机器诊断。
7. 多档 GUI scale 和三档布局宽度。
8. Guide/Chat 切换、线程切换、流式 reasoning、项目 HUD。

保存截图和必要日志，检查：无卡顿、无重叠、无按钮消失、无枚举名、无 JSON 原文、无中英文混杂。

## 完成标准

不要在只完成“代码存在”时宣布完成。最终必须同时满足：

- v1 五类 intent 和 fallback 完整。
- JEI recipe/usage 真打开。
- FTB/Patchouli 可选桥有真实实例验证。
- Web 异步、安全、可关闭、失败可降级。
- 所有 action 有 producer、validator、executor、测试。
- Chat、线程、DeepSeek、项目/HUD 无回归。
- 主界面、配置页、HUD 完成统一机械主题。
- 三档布局无重叠、截断和按钮消失。
- 中英文完整、本地化无硬编码。
- `test --no-daemon` 和 `build --no-daemon` 通过。
- 生成时间与源码一致的新 jar，并在目标实例启动。

## 过程汇报格式

每个 Slice 完成后用：

```text
Slice:
Outcome:
Changed files:
Behavior verified:
Tests run and exact result:
Manual checks:
Known remaining risks:
Next slice:
```

汇报必须写测试的真实结果；没有运行就明确说没有运行。不要用“应该可以”代替验证。

## Git 规则

- 不自动回退用户改动。
- 不提交 secrets、运行日志、网页原文、临时生成文件。
- commit 使用 conventional commits：`feat:`、`fix:`、`refactor:`、`test:`、`chore:`。
- 按 Slice 做可审查的小提交，不把功能和全套 UI 压成一个提交。
- 未经要求不要重写 remote。
- GitHub push 若需要，优先使用已认证 SSH URL，不先循环重试 HTTPS。

现在开始：先阅读文件、检查脏工作区和测试基线，给出第一个检查点，然后立即进入实现。
