# AGENTS.md — MC-AI-Assistant (NeoForge 1.21.1)

Java 21 NeoForge client-only mod. AI chat sidebar with OpenAI-compatible API, tool calling, and conversation persistence.

## Build & Test

```bash
# Build (CI-safe, no daemon)
./gradlew build --no-daemon
# Output: build/libs/agentchat-1.0.0.jar

# Run only non-Minecraft tests
./gradlew test --no-daemon

# Run a specific test class
./gradlew test --no-daemon --tests "com.lumoren.agentchat.ai.OpenAICompatClientTest"
```

**Test exclusions**: Tests tagged `requires-minecraft` or listed by name in `build.gradle:72-83` are excluded from standard `./gradlew test`. These require the NeoForge FML test framework runtime (`./gradlew runGameTestServer`).

**NeoForge version**: 21.1.228 in `gradle.properties`, but the user's installed version is 21.1.219. Do NOT bump NeoForge version without asking.

## Hard Constraints

- **NO OkHttp** — use Java 21 `java.net.http.HttpClient` only
- **NO Lombok** — no `@Data`, `@Builder`, `@Slf4j`, etc.
- **NO Kotlin** — Java-only codebase
- **NO fabric.mod.json** — this is NeoForge, not Fabric. Mod metadata lives in `src/main/resources/META-INF/neoforge.mods.toml`
- **Client-only mod** — no server-side code. All event handlers use `Dist.CLIENT`.

## Text Rendering: Widget Pipeline (CRITICAL)

**NEVER call `graphics.drawString()` directly.** All text MUST be rendered through `AbstractWidget` subclasses registered via `addRenderableWidget()`. Direct `drawString` calls cause frosted-glass blur on NeoForge 1.21.1.

Available widgets:
- `LabelWidget` — non-interactive text (extends `AbstractWidget`)
- `ThreadItemWidget` — sidebar thread items
- `MessageListWidget` — scrollable chat message list
- `ThreadListWidget` — collapsible sidebar

When adding a new text element, create a widget class or use `LabelWidget`. For complex custom rendering, extend `AbstractWidget` and implement `renderWidget()`.

## Architecture

```
AgentChat.java              — @Mod entrypoint, registers config + ConfigScreen
client/
  ClientEventHandler.java    — keybind (O→AIChatScreen), ConversationManager lifecycle
  ClientServiceManager.java  — singleton: creates AIChatService with all tools
ai/
  AIChatService.java         — orchestrates tool-calling loop (max 5 rounds)
  OpenAICompatClient.java    — HTTP client (Java HttpClient), SSE streaming, retry
  ToolCallDispatcher.java    — executes tool calls, formats results
  GameDataAccess.java        — provides Minecraft data to tools
config/
  Config.java                — NeoForge ModConfigSpec (6 fields)
  ConfigManager.java         — wraps Config + secrets.json for API key
persistence/
  ConversationManager.java   — singleton, per-world thread management
  ChatHistoryManager.java    — JSON file I/O with Gson TypeAdapter
model/
  ChatMessage.java           — record (role, content, toolCallId, toolCalls, id, reasoningContent)
  ConversationThread.java    — thread (id, name, messages, timestamps)
tools/
  ToolRegistry.java          — registry of 5 GameTool implementations
ui/
  AIChatScreen.java          — main screen, dynamic layout with rebuildLayout()
  ThreadListWidget.java      — collapsible sidebar with ThreadItemWidget items
  ThreadItemWidget.java      — individual thread list item with hover feedback
  MessageListWidget.java     — scrollable message viewer with scrollbar drag
  ChatMessageWidget.java     — WeChat-style chat bubble with collapsible reasoning
  StreamingChatRenderer.java — tracks streaming state (content, reasoning, status)
  ConfigScreen.java          — config form with dynamic left-aligned labels
  LabelWidget.java           — non-interactive text widget
  theme/ChatColors.java      — centralized color constants
i18n/
  I18nKeys.java              — all translation key constants
  I18nHelper.java            — I18n resolver helper
command/
  ChatCommand.java           — /chat CLI command
```

## AIChatScreen Layout: rebuildLayout()

The screen dynamically recalculates layout on sidebar collapse/expand, thread switch, or delete. Key invariants:

1. **Recursion guard**: `rebuilding` boolean prevents `init()` → collapse → `rebuildLayout()` → `init()` loop
2. **Thread switching**: MUST call `mgr.switchThread(t.getId())` BEFORE `rebuildLayout()`. Otherwise the old thread is re-read on rebuild.
3. **Scroll preservation**: `savedScroll` is saved before `clearWidgets()`, restored after `init()`
4. **Collapse persistence**: `globalSidebarCollapsed` static + `pendingCollapsed` instance flag. Both are synced in `rebuildLayout()` (`globalSidebarCollapsed = pendingCollapsed`). Both are checked in `init()` to apply collapsed state to new widget instances.
5. **Mouse events**: `AIChatScreen.mouseClicked()` delegates to `super.mouseClicked()`. `mouseScrolled()` explicitly dispatches to `threadList` then `messageList`.

## DeepSeek Thinking Mode

For DeepSeek API (`deepseek-reasoner`), the model emits `reasoning_content` in SSE deltas alongside regular `content`. Both MUST be handled:

- **Request body**: every `ChatMessage` with `reasoningContent` must include `reasoning_content` field when sent to API (line 282 of `OpenAICompatClient`)
- **SSE parsing**: parse `delta.reasoning_content` in streaming callback (line 220)
- **Non-streaming**: parse `message.reasoning_content` in `chatCompletionWithTools()` (line 157)
- **UI display**: reasoning text shows in ChatMessageWidget as collapsible section. Auto-expanded during thinking phase, collapsed when content arrives.

## API Client

`OpenAICompatClient` uses Java 21 built-in `HttpClient`, NO OkHttp:
- Auto-retry: 429 (3 retries with exponential backoff), 5xx (1 retry)
- URL building: auto-appends `/chat/completions` if missing
- `mainThreadExecutor`: dispatches callbacks to Minecraft main thread via `Minecraft.getInstance().execute()`

## Config

- **NeoForge config**: `Config.java` — uses `ModConfigSpec` with 6 fields (baseUrl, model, temperature, maxTokens, maxHistory, timeout). Saved via `SPEC.save()`.
- **Secrets**: `run/config/mcaiassistant-secrets.json` — API key, endpoint, model. Gitignored. Read by `ConfigManager` which wraps both sources.
- ConfigScreen uses left-aligned labels calculated from the longest label width.

## Conversation Persistence

- Stored per-world: `<gameDir>/agentchat-conversations/conversations.json`
- `ConversationManager` is a static singleton, initialized on `LevelEvent.Load` (client side only)
- All mutations (create, delete, rename, switch) auto-save
- `ChatHistoryManager` uses custom Gson `TypeAdapter` for `ConversationThread` — trims messages to `maxHistory` on serialize
- `ChatMessage` is a Java `record` — immutable, with compact constructor for UUID generation

## Tool System

5 registered tools: InventoryTool, ItemEncyclopediaTool, PositionTool, RecipeTool, WorldStateTool.
- `ToolRegistry` manages registration and lookup
- `ToolCallDispatcher` executes tool calls from AI responses
- `AIChatService` runs the loop: non-streaming call → detect tool_calls → execute → repeat (max 5 rounds) → final streaming response
- System prompt is hardcoded in `AIChatService.SYSTEM_PROMPT`

## I18n

All user-visible strings MUST use translation keys defined in `I18nKeys.java`. Language files: `src/main/resources/assets/agentchat/lang/en_us.json` and `zh_cn.json`. Use `I18nHelper.translate(key)` for `Component`, `I18nHelper.translateToString(key)` for `String`.

## Git

Remote: `https://github.com/bilbillm/MC-AI-Assistant` (origin/master)
Commit style: conventional commits — `feat:`, `fix:`, `refactor:`, `chore:`
