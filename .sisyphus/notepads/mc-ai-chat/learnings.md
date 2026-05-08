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
