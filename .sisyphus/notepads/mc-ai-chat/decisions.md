# mc-ai-chat Decisions

## Architecture Decisions
1. **Client-only mod**: Uses only client-side data (no ServerPlayer/ServerLevel access)
2. **Java 21 HttpClient**: Instead of OkHttp to avoid classloader conflicts
3. **ModConfigSpec + JSON secrets**: API Key in separate JSON file (gitignored), config in standard NeoForge config
4. **i18n via NeoForge**: Use Minecraft's built-in i18n system with lang JSON files
5. **TDD**: All core logic has JUnit 5 tests
6. **Per-save persistence**: Chat history stored in `.minecraft/saves/<world>/agentchat/`
7. **Screen injection**: Via ScreenEvent.Init.Post hook (same pattern as JEI)
8. **Git**: Initialize separate git repo in Agent-chat-in-MC directory
