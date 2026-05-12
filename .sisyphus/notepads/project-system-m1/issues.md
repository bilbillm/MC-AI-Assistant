# Issues Found During F2 Code Quality Review

## Critical/High
1. **I18nKeys.java:125** — Typo: `PROJET_TASK_BLOCKED` should be `PROJECT_TASK_BLOCKED`
2. **ProjectHudWidget.java:202-204** — Empty if-block (unfinished code draft artifact)
3. **ProjectHudOverlay.java:49-66** — Raw GLFW mouse handling instead of NeoForge `InputEvent` system

## Medium
4. **ProjectHudWidget.java:123** — Dead parameters `mx, my` in `renderCompact()`
5. **ConfigScreen.java `onSave()`** — Double `doSave()` call on Save button click
6. **Project.java:98-100** — `setId()` allows mutation of auto-generated UUID

## Low
7. **OpenAICompatClient.java:6** — Unused import `com.google.gson.JsonNull`
8. **AIChatScreen.java:62** — Dead field `selectedMessageUuid` (write-only)
9. **ConfigScreen.java:202** — Dead method `onApiKeyChanged()`
10. **MessageListWidget.java:60-61** — Dead setter/getter for `selectedMessageUuid`
11. **OpenAICompatClient.java:372** — Empty catch block
12. **ConfigScreen.java:177** — Empty catch block for NumberFormatException

## Style/AI Artifacts
- Section comment dividers (em-dash `// ──`) in ProjectHudWidget (9x), ProjectManager (6x)
- Useless JavaDoc on obvious getters/trivial methods
- Default in exhaustive enum switch (ProjectPlanningService:174)
- DUPLICATE jei_version in gradle.properties (pre-existing)
- DRY: archive cap enforcement duplicated in ProjectManager.load() and archiveExistingProject()
