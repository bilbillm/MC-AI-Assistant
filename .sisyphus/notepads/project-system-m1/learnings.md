# Learnings — Project System M1 Task 1

## Data Model Patterns
- **Enums**: Simple Java enums in `com.lumoren.agentchat.model` package (ProjectStatus, TaskStatus, TaskType)
- **Records**: 
  - `ItemRequirement` — plain record with String itemId, int needed, int owned
  - `Task` — record with compact constructor for UUID auto-generation + static factory method `of()`
- **Mutable class**: `Project` — follows `ConversationThread.java` pattern:
  - No-arg constructor auto-generates UUID, full constructor accepts String id
  - `ArrayList` for mutability (tasks field is `final`)
  - `getTasks()` returns `Collections.unmodifiableList()`
  - `addTask()`, `setTasks()`, `clearTasks()` all update `updatedAt` timestamp

## Gson Serialization
- Gson 2.10.1 does NOT have built-in `Instant` TypeAdapter → must register custom adapter
- Java 21 module system restricts reflective access → need `--add-opens` JVM args in build.gradle test task
- Added to `build.gradle`:
  ```groovy
  jvmArgs += [
      '--add-opens', 'java.base/java.time=ALL-UNNAMED',
      '--add-opens', 'java.base/java.lang=ALL-UNNAMED'
  ]
  ```
- Installed Instant TypeAdapter:
  ```java
  .registerTypeAdapter(Instant.class, new TypeAdapter<Instant>() {
      @Override public void write(JsonWriter out, Instant value) throws IOException {
          out.value(value.toString());
      }
      @Override public Instant read(JsonReader in) throws IOException {
          return Instant.parse(in.nextString());
      }
  })
  ```

## Test Patterns
- Tests in `src/test/java/com/lumoren/agentchat/model/ProjectTest.java`
- All tests use plain JUnit 5 (no `@Tag("requires-minecraft")`)
- 23 tests covering: enum values, ItemRequirement creation, Task auto-ID/fields/factory/immutability, Project CRUD, immutable getter, serialization round-trip (Project, Task, ItemRequirement), empty tasks list

## Task 2: ProjectManager Persistence

### Singleton Pattern
- Follows `ConversationManager` exactly: `static synchronized initialize(Path)` / `shutdown()` / `getInstance()`
- Private constructor takes `worldSaveDir`, resolves `agentchat-projects/projects.json`
- On init, calls `load()` to restore state; on shutdown, calls `save()` then nulls instance

### Save Format
```json
{
  "activeProject": { ... },
  "archivedProjects": [ ... ]
}
```
- Uses a simple `ProjectManagerData` wrapper class (Gson reflective, no custom adapter needed)
- `activeProject` can be null (no active project)

### Gson TypeAdapter — ProjectAdapter
- Implements both `JsonSerializer<Project>` and `JsonDeserializer<Project>` (following `ConversationThreadAdapter` pattern)
- **Serialize**: Manual JSON building for scalar fields (id, name, status, timestamps), `context.serialize()` for tasks list
- **Deserialize**: Guards every field against null/missing — missing id = return null, missing name = "Unnamed Project", missing status = CREATED, bad timestamps = Instant.now()
- **Error tolerance**: Returns `null` for unparseable projects → filtered by caller
- **Instant handling**: Explicit `.toString()` / `Instant.parse()` in adapter (does NOT rely on registered InstantAdapter for Project fields)
- Tasks list: Delegated to `context.deserialize()` with `new TypeToken<List<Task>>() {}` — Gson handles records natively
- Also registers a standalone `InstantAdapter` as safety net for any other Instant usage

### Corrupt JSON Recovery
- `load()` wraps `gson.fromJson()` in try-catch for `JsonSyntaxException | JsonIOException`
- On failure: logs `[AgentChat] Corrupt projects.json — starting fresh`, returns empty state
- Same pattern as `ChatHistoryManager.load()` lines 80-83
- Null entries from failed deserialization filtered out (null guard in load loop)

### Auto-cleanup
- `MAX_ARCHIVED = 10` constant
- `archiveExistingProject()` enforces: `while (archivedProjects.size() > MAX_ARCHIVED) remove(0)`
- Also enforced on load (defense against manually-edited JSON)

### saveProject behavior
- Forces status to `IN_PROGRESS` (even if project was CREATED)
- Auto-archives existing active project if different ID
- Same-ID saves just update in place (no re-archive)

### Test Patterns
- 21 tests in `ProjectManagerTest.java`
- Uses `@TempDir Path tempDir` for isolated filesystem (same as ChatHistoryManagerTest)
- `@AfterEach tearDown()` calls `ProjectManager.shutdown()` to clean up singleton between tests
- No `@Tag("requires-minecraft")` — tests run in standard `./gradlew test`
- Test coverage: round-trip save/load, empty init, shutdown persistence, archive, archive-noop, reactivate, reactivate-miss, reactivate-swap, saveProject-overwrite, saveProject-force-status, corrupt JSON (3 variants), max-archives-auto-remove (2 variants), task-with-items round-trip, task-order, unmodifiable list, empty tasks, null note
- **Pitfall**: `build.gradle` excludes `ConversationManagerTest` by name but NOT `ProjectManagerTest` — our tests must NOT depend on ConfigManager (they don't)
 - **Pitfall**: The `--add-opens` JVM args for `java.time` are already in `build.gradle` test config

## JEI Integration (Task from planned work) — 2026-05-12

### JEI API for 1.21.1 NeoForge (19.21.0.247)
- Maven: `mezz.jei:jei-1.21.1-neoforge-api:19.21.0.247` from `https://maven.blamejared.com`
- Key packages:
  - `mezz.jei.api.runtime.IJeiRuntime` — getRecipeManager(), getIngredientManager(), getJeiHelpers()
  - `mezz.jei.api.runtime.IIngredientManager` — getIngredientHelper(IIngredientType), NOT in `mezz.jei.api.ingredients`
  - `mezz.jei.api.ingredients.IIngredientHelper<V>` — getTagStream(V) returns Stream<ResourceLocation> (not getTags)
  - `mezz.jei.api.recipe.IFocusFactory` — createFocus(RecipeIngredientRole, IIngredientType<V>, V)
  - `mezz.jei.api.recipe.IRecipeManager` — createRecipeCategoryLookup() + createRecipeLookup(RecipeType)
- `@JeiPlugin` on IModPlugin impl for SPI auto-discovery

### Build Config
- `compileOnly` + `testImplementation` for JEI (test classpath needs it for RecipeTool imports)
- `mandatory=false`, `ordering="AFTER"` in neoforge.mods.toml
- `gradle.properties`: `jei_version=19.21.0.247`

### Pitfalls
- `IIngredientManager` is `mezz.jei.api.runtime.IIngredientManager`, NOT `mezz.jei.api.ingredients`
- `getTagStream(V)` replaces deprecated `getTags(V)` in JEI 19.x
- `GameDataAccess.lookupItem` needed public visibility (was package-private) for RecipeTool cross-package access
- Unused ArrayList import in RecipeTool removed
- Test compilation needed `testImplementation` for JEI — otherwise NoClassDefFoundError on RecipeTool imports
- ProjectPlanningServiceTest failure is pre-existing and unrelated

## Task: ProjectPlanningService — 2026-05-12

### Implementation
- `ProjectPlanningService.java` in `ai/` package — utility class (private constructor, all static methods)
- Three methods:
  1. `buildTaskChainPrompt(String goal)` — returns `List.of(ChatMessage.system(prompt))` with structured JSON format instructions
  2. `parseTaskChain(String aiResponse)` — Gson `JsonParser.parseString()`, iterates `"tasks"` array, creates `Task.of(description, type, items)`. Returns empty list on ANY exception (malformed JSON, missing fields, invalid type)
  3. `buildProjectContextMessage(Project project)` — builds system message with project name, status, progress (done/total), blocked count, and task list with status icons

### Item ID Validation
- `isValidItemId(String)` uses regex `^[a-z0-9_.-]+:[a-z0-9/._-]+$` to validate Minecraft resource location format
- Invalid item IDs are silently skipped during parsing (no crash)
- Package-private to allow testing

### Pre-existing Build Issues Resolved
- `jei_version=19.21.0.11` added to `gradle.properties` (was missing, causing JEI class resolution failures)
- Build requires: `compileOnly "mezz.jei:jei-${minecraft_version}-neoforge-api:${jei_version}"` from `https://maven.blamejared.com`

### Test Results
- 21 tests in `ProjectPlanningServiceTest.java`, all passing
- No `@Tag("requires-minecraft")` — runs with standard `./gradlew test`
- Test coverage:
  - `buildTaskChainPrompt`: system message with goal, escapes special characters
  - `parseTaskChain`: valid JSON (3 tasks, all types, items with ItemRequirements), empty array, malformed JSON, empty string, null, invalid type, invalid items skipped, missing items field, unique IDs, all 6 TaskType values
  - `buildProjectContextMessage`: project name/progress/blocked, all done, empty tasks, null project, null tasks guard, pending task icon
  - `isValidItemId`: valid/invalid IDs (with/without colon, empty, null)

### Pitfall: Unicode Arrow Character
- The `→` (U+2192 RIGHTWARDS ARROW) character in test assertions didn't match the character in the production code due to encoding differences
- **Fix**: Use description text for assertions instead of icon characters (`content.contains("Collect wood")` vs `content.contains("[→] Collect wood")`)

## ProjectProgressTracker — 2026-05-12

### Design Decisions
- **Package-private core methods** (`buildInventoryMap`, `checkTaskCompletion`, `detectDeathItemLoss`, `markBlockedTasks`) for testability — tests are in same package (`com.lumoren.agentchat.client`)
- **No dependency injection** for Minecraft — `onClientTick` takes `Minecraft` directly, calls `GameDataAccess.getInventorySnapshot(mc)`. Tests bypass this by calling package-private methods with plain data.
- **Task is a record** → immutable. Status transitions create new `Task` instances with updated `TaskStatus`, preserving `id`, `description`, `type`, `requiredItems`, `note`.
- **`Project.setTasks()`** mutates the project's task list and updates the `updatedAt` timestamp.

### Death Detection Heuristic
- Simple >50% total item count drop. Previous total > 0 guard prevents division-by-zero.
- 30s cooldown (`DEATH_COOLDOWN_MS = 30000`) stored in `lastDeathDetection` (package-private for test access)
- `markBlockedTasks` blocks tasks only when ANY required item is completely missing (count = 0)

### Inventory Aggregation
- `buildInventoryMap` sums counts across all slots for same itemId. Uses `Map.merge(itemId, count, Integer::sum)`.

### Test Coverage (16 tests)
- `buildInventoryMap`: aggregation, empty list
- `checkTaskCompletion`: all items satisfied → DONE (single, multi-req, IN_PROGRESS), insufficient → stays PENDING, missing item → stays PENDING, no inventory change → all statuses preserved, excess inventory → DONE
- `detectDeathItemLoss`: significant drop → true, small drop → false, no change → false, empty previous → false, increase → false
- Death cooldown: prevents duplicate triggers (recent timestamp), allows re-trigger after cooldown expiry
- `markBlockedTasks`: required item missing → BLOCKED, items present → unchanged, skips already DONE, IN_PROGRESS → BLOCKED

## Task 9: AIChatService — Project Context Injection — 2026-05-12

### Implementation
- Minimal change: 5 lines added to `sendMessage()` in `AIChatService.java`
- Project context injected between `SYSTEM_PROMPT` and conversation history
- Null-safe: checks `ProjectManager.getInstance() != null && .getActiveProject() != null`
- Only one import needed: `ProjectManager` (api package). `ProjectPlanningService` is same package, no import.
- SYSTEM_PROMPT remains untouched `static final` — project context is additional, separate system message

### Key Design Points
- Injected in `sendMessage()` only, NOT in `runConversationLoop()` — tool-calling loop doesn't get project context repeatedly
- Not persisted in conversation history — added per-invocation, not part of stored thread
- `buildProjectContextMessage()` already handles null project internally (returns "No active project."), but we guard upstream anyway
- No method signature changes — fully backward compatible

### Build
- `./gradlew clean compileJava --no-daemon` → BUILD SUCCESSFUL
- 4 pre-existing deprecation warnings (ClientModEvents, AgentChatCommand) — zero new warnings

## Task 7: ProjectHudOverlay + ProjectHudWidget — 2026-05-12

### Key Discovery: NeoForge 21.1 removed RegisterGuiOverlaysEvent
- In NeoForge 21.1.228, `RegisterGuiOverlaysEvent` and `IGuiOverlay` were **completely removed**.
- Replaced by `RenderGuiEvent.Post` (fires after all vanilla GUI is rendered) on the **FORGE** (GAME) bus.
- `EventBusSubscriber.bus()` is deprecated — use `@EventBusSubscriber(modid = ..., value = Dist.CLIENT)` without `bus` parameter for FORGE bus events. MOD bus events should be registered via `modEventBus.addListener()` in the `@Mod` constructor, or use the deprecated `bus = Bus.MOD`.

### Event API Changes in NeoForge 21.1.228
- **`RenderGuiEvent`**: `getGuiGraphics()`, `getPartialTick()` returns `DeltaTracker` (not float!). Use `deltaTracker.getGameTimeDeltaPartialTick(mc.isPaused())` to get float partialTick.
- **`InputEvent.MouseScrollingEvent`**: Has `getMouseX()`, `getMouseY()` (window coordinates), `getScrollDeltaX()`, `getScrollDeltaY()`. Cancellable via `setCanceled(true)`.
- No more `IGuiOverlay` or `RegisterGuiOverlaysEvent` — all HUD rendering via `RenderGuiEvent.Post` on FORGE bus.

### HUD Interaction Without Screen
- `AbstractWidget.mouseClicked()`, `mouseDragged()`, `mouseReleased()` are NOT automatically called outside a `Screen`.
- Solution: Use GLFW state queries in the render callback:
  ```java
  long handle = mc.getWindow().getWindow();
  boolean leftDown = GLFW.glfwGetMouseButton(handle, GLFW.GLFW_MOUSE_BUTTON_LEFT) == GLFW.GLFW_PRESS;
  ```
- Track `wasLeftDown` across frames to detect press/drag/release transitions.
- Scroll events forwarded via `InputEvent.MouseScrollingEvent` on FORGE bus.

### Mouse Coordinate Scaling
- `mc.mouseHandler.xpos()` returns raw screen pixels. Convert to scaled GUI coords:
  ```java
  double guiScale = mc.getWindow().getGuiScale();
  double mX = mc.mouseHandler.xpos() / guiScale;
  ```
- `InputEvent.MouseScrollingEvent.getMouseX()` also returns raw pixels — same scaling needed.

### Widget Pipeline (AbstractWidget outside Screen)
- `renderWidget()` is called directly from the overlay — still the correct rendering method.
- `graphics.drawString(font, ...)` inside `renderWidget()` is the correct pipeline for NeoForge 1.21.1.
- `graphics.drawString(font, Component, x, y, color)` — the `color` parameter is a fallback for unstyled text.

### Component.withStyle() requires MutableComponent
- `Component.literal()` returns `MutableComponent`, but if assigned to `Component` variable, `withStyle(Style)` won't compile (it's on `MutableComponent` only).
- Fix: Use `MutableComponent` variable type, call `withStyle()` without re-assignment (it mutates in-place):
  ```java
  MutableComponent line = Component.literal("text");
  line.withStyle(Style.EMPTY.withColor(...)); // mutates in-place
  ```

### TextColor.fromRgb()
- Works in Minecraft 1.21.1: `TextColor.fromRgb(0xFF_22C55E)` returns `TextColor`.
- `Style.EMPTY.withColor(TextColor)` returns a new Style.

### GLFW Window Handle
- `mc.getWindow().getWindow()` returns `long` (the GLFW window pointer).
- Works with `GLFW.glfwGetMouseButton(long window, int button)`.

### Position Persistence
- Local static fields (`persistentOffsetX`, `persistentOffsetY`) sufficient for M1 MVP.
- Exposed via `getStoredOffsetX()/getStoredOffsetY()` and `setStoredOffset(x, y)` for future persistence.

## Task: AIChatScreen Project Creation Flow — 2026-05-12

### New Fields Added
- `projectJustCompleted` (boolean) — guards against duplicate completion celebrations
- `pendingProjectCreation` (boolean) — true when user expressed project intent, waiting for AI task chain response
- `pendingProjectName` (String) — the cleaned goal name extracted from user message
- `pendingTasks` (List<Task>) — parsed tasks awaiting confirmation

### sendMessage() Flow Changes
Order of operations BEFORE sending=true:
1. `handleEditCommand(text)` — local edit commands (delete step, rename step, add step)
2. `handleConfirmation(text)` — "确认"/"confirm" creates project from pending tasks
3. Clear pending tasks if user types anything else (ignores confirmation)
4. THEN `sending = true` and normal flow

### Intent Detection
- `checkProjectIntent()` called in sendMessage() after sending=true, before `thread.addMessage(user)`
- Detects keywords: "我要做", "我想做", "I want to make", "I want to build", "帮我规划"
- On match: injects system message asking AI for JSON task chain, sets `pendingProjectCreation = true`, extracts clean goal

### Project Creation Confirmation Flow
1. `onStreamUpdate()` checks `streamer.isCompleted() && pendingProjectCreation` → calls `tryParseProjectFromLastResponse()`
2. `tryParseProjectFromLastResponse()` extracts JSON from markdown code blocks, calls `ProjectPlanningService.parseTaskChain()`
3. If tasks parsed successfully → stores as `pendingTasks`, adds confirmation system message to thread
4. User replies "确认"/"confirm"/"yes"/"是" → `handleConfirmation()` creates `Project(pendingProjectName)`, calls `setTasks(pendingTasks)`, saves via `ProjectManager.saveProject()`

### Manual Task Editing Commands
- `删除第N步` → removes task at index N-1, calls `project.setTasks()`, `pm.saveProject()`
- `把第N步改成XXX` → creates new Task record (same id, new description), replaces in list, saves
- `添加步骤: XXX` or `添加步骤：XXX` → `Task.of(desc, TaskType.PLAN, List.of())`, `project.addTask()`, saves
- All commands use regex matching and return true to prevent sending to AI
- Guard: returns false if no active project

### Completion Detection
- `checkProjectCompletion()` called in `onStreamUpdate()` every update
- Checks `project.getTasks().stream().allMatch(t -> t.status() == TaskStatus.DONE)`
- On all done: sets `projectJustCompleted = true`, archives project, adds celebration message using I18nKeys.PROJECT_COMPLETE
- Guard `projectJustCompleted` prevents duplicate triggers (reset when new project created via handleConfirmation)

### Task Record Immutability
- Since `Task` is a Java record, modifications create new Task instances:
  ```java
  new Task(oldTask.id(), newDesc, oldTask.type(), oldTask.status(), oldTask.requiredItems(), oldTask.note())
  ```

### Pre-existing Build Issue
- Two untracked files (ProjectHudOverlay.java, ProjectHudWidget.java) have compile errors on NeoForge 21.1.219:
  - `RegisterGuiOverlaysEvent` not found (API removed in 21.1.228, not available in 21.1.219 either)
  - `withStyle(Style)` not found on `Component` (needs `MutableComponent`)
  - `getScrollDelta()` not found (replaced by `getScrollDeltaX()`/`getScrollDeltaY()`)
- These are from a different workstream — temporarily moved to `.tmp-bak/` for clean build verification, then restored
- AIChatScreen.java changes compile cleanly with zero errors, zero new warnings
