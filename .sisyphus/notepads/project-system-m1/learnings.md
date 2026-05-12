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
