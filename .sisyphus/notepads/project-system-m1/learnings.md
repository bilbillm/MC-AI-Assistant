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
