# mc-ai-chat Learnings

## Project Structure
- **Mod ID**: `agentchat`
- **Package**: `com.lumoren.agentchat`
- **Platform**: NeoForge 1.21.1, Java 21
- **Build**: Gradle (ModDevGradle plugin)

## Key Conventions
- No Lombok (use Java records/POJOs)
- No OkHttp (use Java 21 built-in HttpClient)
- No Kotlin
- AI: OpenAI-compatible protocol (streaming + Function Calling)
- Tests: JUnit 5 + Mockito + GameTest
- i18n: Always use translation keys, never hardcode strings
- JSON: Gson or Jackson (via simple-openai)
- Git: Separate repo inside Agent-chat-in-MC directory

## Architecture
- Client-only mod (no server-side code)
- Sidebar panel in InventoryScreen (JEI-style)
- Full-screen chat via backtick key
- Per-save conversation history isolation
- Read-only tools (V1)

## Wave 3 UI Implementation (2026-05-09)
- Created 6 UI source files + 2 test files for Wave 3 tasks 16-21
- Key NeoForge 1.21.1 API discoveries:
  - `Screen.addRenderableWidget()` is `protected` — cannot be called from non-Screen classes
  - Use `ScreenEvent.Init.Post.addListener(GuiEventListener)` to add widgets from event handlers
  - `@EventBusSubscriber` `bus` parameter is deprecated (warning only, still works)
  - `Consumer<GuiEventListener>` bridges event `addListener` to external widget creation
- Architecture decisions:
  - `ClientServiceManager` singleton creates and holds `AIChatService` with all 5 tools registered
  - `ScreenEventHandler` on GAME bus handles InventoryScreen lifecycle
  - `ClientEventHandler` on GAME bus handles key binding ticks (separate from MOD bus `ClientModEvents`)
  - `StreamingChatRenderer` implements `AIChatService.ChatCallback` and bridges to ConversationThread
  - `MarkdownRenderer` converts markdown to Minecraft Component tree (no external dependencies)
  - `ChatMessageWidget.getHeight(Font, int)` needs font for accurate layout calculations
- Fixed pre-existing `GameDataAccess.java` RecipeType<?> compile error with explicit cast

## Task 15: Function Calling Dispatcher (2026-05-09)
- Added `ToolCallDispatcher.java` to delegate tool call execution to `ToolRegistry`
- Added `AIChatService.java` to orchestrate the full conversation loop with max 5 rounds
- Added `chatCompletionWithTools()` to `OpenAICompatClient` to parse assistant messages including `tool_calls`
- Conversation loop: non-streaming probe → detect tool_calls → execute → repeat → stream final response
- `ChatCallback` provides `onToken`, `onThinking`, `onError`, `onComplete` for UI integration
- Tests mock async behavior using `CompletableFuture` and `doAnswer` for streaming callbacks

## Git Initialization (2026-05-09)
- Initialized git repo in `C:\Users\lumoren\Documents\GitHub\Agent-chat-in-MC` with `git init`
- Created `.gitignore` for Gradle, IDE (IntelliJ, VS Code), MC runs, OS files, secrets, and logs
- Created `LICENSE` with full GNU LGPL v3 text (fetched from gnu.org)
- Created `README.md` in Chinese with project description, features, install/config guide, tech stack, and contribution guide
- Initial commit: `chore: init MC-AI-Assistant project with README, LICENSE, and .gitignore`
- Note: On Windows PowerShell, `GIT_MASTER=1` prefix does not work — env vars must be set via `$env:GIT_MASTER='1'` separately or inline chaining
- Warning: LF/CRLF warnings are expected on Windows; harmless
- 9 files tracked in initial commit (including `.sisyphus/` artifacts)

## F3 Final QA Verification (2026-05-09)
- Scenarios [10/10 PASS] | Integration [3/3 PASS] | Edge Cases [12/12 PASS] | VERDICT: APPROVE
- **Mod loading**: @Mod(AgentChat.MODID), MODID = "agentchat" — correct
- **Config**: 8 items with correct defaults/types/ranges — all match spec
- **i18n**: 38 keys in I18nKeys, both en_us.json and zh_cn.json have all 38 entries with non-empty values
- **5 tools**: InventoryTool, ItemEncyclopediaTool, PositionTool, RecipeTool, WorldStateTool — all registered
- **Tool chain**: AIChatService → ToolCallDispatcher → ToolRegistry → GameTool → GameDataAccess (clean delegation)
- **API errors**: 401→ApiAuthException, 429→retry 3x→ApiRateLimitException, 5xx→retry 1x, timeout→ApiTimeoutException
- **Dispatcher loop**: MAX_ROUNDS=5, properly limits tool-calling recursion
- **Persistence**: ChatHistoryManager serializes ConversationThread via Gson custom adapter with maxHistory cropping; per-world isolation
- **UI**: 8 components (AIChatScreen, AIChatSidebarPanel, ChatMessageWidget, MessageListWidget, ConfigScreen, StreamingChatRenderer, StatusBanner, MarkdownRenderer)
- **Key binding**: GLFW_KEY_GRAVE_ACCENT (backtick) registered via RegisterKeyMappingsEvent
- **Command**: /agentchat with 6 subcommands (config, key set, clear, threads, thread new, thread switch)
- **Minor observations**: (1) No auto-save on message add — messages only persisted on thread CRUD operations. Risk: Low. (2) SecretsConfig uses manual JSON parsing instead of Gson. Risk: Low. (3) No auto-save on screen close. Risk: Low.
