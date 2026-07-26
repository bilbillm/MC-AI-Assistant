package com.lumoren.agentchat.ui.action;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.lumoren.agentchat.model.ActionCard;
import com.lumoren.agentchat.model.AssistantAction;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AssistantActionExecutorTest {

    private static final class FakeJeiGui implements AssistantActionExecutor.JeiGui {
        boolean available;
        boolean openSucceeds = true;
        final List<String> recipesShown = new ArrayList<>();
        final List<String> usagesShown = new ArrayList<>();

        @Override
        public boolean isAvailable() {
            return available;
        }

        @Override
        public boolean showRecipes(String itemId) {
            recipesShown.add(itemId);
            return openSucceeds;
        }

        @Override
        public boolean showUsages(String itemId) {
            usagesShown.add(itemId);
            return openSucceeds;
        }
    }

    private static final class FakeContext implements AssistantActionExecutor.ActionContext {
        final List<String> toolResults = new ArrayList<>();
        final List<String> followUps = new ArrayList<>();
        final List<String> systemMessages = new ArrayList<>();

        @Override
        public void appendToolResult(String content) {
            toolResults.add(content);
        }

        @Override
        public void sendFollowUp(String query) {
            followUps.add(query);
        }

        @Override
        public void showSystemMessage(String message) {
            systemMessages.add(message);
        }
    }

    private static final class MutableClock implements java.util.function.LongSupplier {
        long now;

        @Override
        public long getAsLong() {
            return now;
        }

        void advance(long ms) {
            now += ms;
        }
    }

    private FakeJeiGui jei;
    private FakeContext context;
    private MutableClock clock;
    private AssistantActionExecutor executor;

    @BeforeEach
    void setUp() {
        jei = new FakeJeiGui();
        context = new FakeContext();
        clock = new MutableClock();
        executor = new AssistantActionExecutor(() -> null, jei, fakeResolver(), clock);
    }

    /**
     * Registry-free resolver: knows minecraft:stone (with or without namespace),
     * rejects anything else.
     */
    private static AssistantActionExecutor.ItemResolver fakeResolver() {
        return itemName -> {
            if (itemName == null) {
                return null;
            }
            String normalized = itemName.strip();
            if (normalized.equals("stone") || normalized.equals("minecraft:stone")) {
                return "minecraft:stone";
            }
            return null;
        };
    }

    private static AssistantAction itemAction(AssistantAction.Kind kind, String itemName) {
        JsonObject payload = new JsonObject();
        payload.addProperty("item_name", itemName);
        return new AssistantAction("id-" + kind, "label", kind, payload);
    }

    @Test
    void openRecipeShowsJeiGuiWhenAvailable() {
        jei.available = true;

        ActionExecutionResult result = executor.execute(
                itemAction(AssistantAction.Kind.OPEN_RECIPE, "minecraft:stone"), context);

        assertEquals(ActionExecutionResult.Status.SUCCESS, result.status());
        assertEquals(List.of("minecraft:stone"), jei.recipesShown);
        assertTrue(jei.usagesShown.isEmpty());
        assertTrue(context.toolResults.isEmpty());
    }

    @Test
    void openUsageShowsJeiGuiWhenAvailable() {
        jei.available = true;

        ActionExecutionResult result = executor.execute(
                itemAction(AssistantAction.Kind.OPEN_USAGE, "stone"), context);

        assertEquals(ActionExecutionResult.Status.SUCCESS, result.status());
        assertEquals(List.of("minecraft:stone"), jei.usagesShown);
        assertTrue(context.toolResults.isEmpty());
    }

    @Test
    void openRecipeFallsBackToLocalCardWithoutJei() {
        jei.available = false;

        ActionExecutionResult result = executor.execute(
                itemAction(AssistantAction.Kind.OPEN_RECIPE, "minecraft:stone"), context);

        assertEquals(ActionExecutionResult.Status.SUCCESS, result.status());
        assertTrue(jei.recipesShown.isEmpty());
        assertEquals(1, context.toolResults.size());
        ActionCard card = ActionCard.fromToolResultContent(context.toolResults.get(0));
        assertNotNull(card);
        assertEquals(ActionCard.Type.RECIPE, card.type());
    }

    @Test
    void openRecipeFallsBackToLocalCardWhenJeiOpenFails() {
        jei.available = true;
        jei.openSucceeds = false;

        ActionExecutionResult result = executor.execute(
                itemAction(AssistantAction.Kind.OPEN_RECIPE, "minecraft:stone"), context);

        assertEquals(ActionExecutionResult.Status.SUCCESS, result.status());
        assertEquals(List.of("minecraft:stone"), jei.recipesShown);
        assertEquals(1, context.toolResults.size());
    }

    @Test
    void unknownItemFailsWithoutTouchingJei() {
        jei.available = true;

        ActionExecutionResult result = executor.execute(
                itemAction(AssistantAction.Kind.OPEN_RECIPE, "minecraft:no_such_item_xyz_123"), context);

        assertEquals(ActionExecutionResult.Status.FAILURE, result.status());
        assertFalse(result.message().isBlank());
        assertTrue(jei.recipesShown.isEmpty());
        assertTrue(context.toolResults.isEmpty());
    }

    @Test
    void missingItemPayloadFailsClosed() {
        AssistantAction action = new AssistantAction("id", "label",
                AssistantAction.Kind.OPEN_RECIPE, new JsonObject());

        ActionExecutionResult result = executor.execute(action, context);

        assertEquals(ActionExecutionResult.Status.FAILURE, result.status());
        assertTrue(jei.recipesShown.isEmpty());
        assertTrue(context.toolResults.isEmpty());
    }

    @Test
    void nullActionIsIgnored() {
        ActionExecutionResult result = executor.execute(null, context);

        assertEquals(ActionExecutionResult.Status.IGNORED, result.status());
        assertTrue(context.toolResults.isEmpty());
        assertTrue(context.followUps.isEmpty());
    }

    @Test
    void markTaskDoneRunsProjectAction() {
        JsonObject payload = new JsonObject();
        payload.addProperty("task_index", 1);
        AssistantAction action = new AssistantAction("id", "label",
                AssistantAction.Kind.MARK_TASK_DONE, payload);

        ActionExecutionResult result = executor.execute(action, context);

        assertEquals(ActionExecutionResult.Status.SUCCESS, result.status());
        assertEquals(1, context.toolResults.size());
    }

    @Test
    void markTaskDoneWithoutIndexFailsClosed() {
        AssistantAction action = new AssistantAction("id", "label",
                AssistantAction.Kind.MARK_TASK_DONE, new JsonObject());

        ActionExecutionResult result = executor.execute(action, context);

        assertEquals(ActionExecutionResult.Status.FAILURE, result.status());
        assertTrue(context.toolResults.isEmpty());
    }

    @Test
    void setCurrentTaskAliasesRunProjectAction() {
        for (AssistantAction.Kind kind : List.of(AssistantAction.Kind.SET_CURRENT_TASK,
                AssistantAction.Kind.PIN_TASK, AssistantAction.Kind.MARK_TASK_ACTIVE)) {
            clock.advance(1000);
            JsonObject payload = new JsonObject();
            payload.addProperty("task_index", 1);
            AssistantAction action = new AssistantAction("id-" + kind, "label", kind, payload);

            ActionExecutionResult result = executor.execute(action, context);

            assertEquals(ActionExecutionResult.Status.SUCCESS, result.status());
        }
        assertEquals(3, context.toolResults.size());
    }

    @Test
    void searchMoreSendsFollowUp() {
        JsonObject payload = new JsonObject();
        payload.addProperty("query", " why machine not working ");
        AssistantAction action = new AssistantAction("id", "label",
                AssistantAction.Kind.SEARCH_MORE, payload);

        ActionExecutionResult result = executor.execute(action, context);

        assertEquals(ActionExecutionResult.Status.SUCCESS, result.status());
        assertEquals(List.of("why machine not working"), context.followUps);
    }

    @Test
    void cancelProjectShowsBlockedSystemMessage() {
        AssistantAction action = new AssistantAction("id", "label",
                AssistantAction.Kind.CANCEL_PROJECT, new JsonObject());

        ActionExecutionResult result = executor.execute(action, context);

        assertEquals(ActionExecutionResult.Status.IGNORED, result.status());
        assertEquals(1, context.systemMessages.size());
        assertTrue(context.systemMessages.get(0).contains("not exposed as a one-click"));
    }

    @Test
    void doubleClickGuardBlocksImmediateRepeat() {
        jei.available = true;
        AssistantAction action = itemAction(AssistantAction.Kind.OPEN_RECIPE, "minecraft:stone");

        assertEquals(ActionExecutionResult.Status.SUCCESS, executor.execute(action, context).status());
        assertEquals(ActionExecutionResult.Status.IGNORED, executor.execute(action, context).status());
        assertEquals(1, jei.recipesShown.size());

        clock.advance(1000);
        assertEquals(ActionExecutionResult.Status.SUCCESS, executor.execute(action, context).status());
        assertEquals(2, jei.recipesShown.size());
    }

    @Test
    void projectActionArgsPreservesPayloadAndOverridesAction() {
        JsonObject payload = new JsonObject();
        payload.addProperty("action", "ignored");
        payload.addProperty("project_name", "Power Start");
        JsonArray tasks = new JsonArray();
        JsonObject task = new JsonObject();
        task.addProperty("description", "Craft generator");
        tasks.add(task);
        payload.add("tasks", tasks);

        JsonObject args = AssistantActionExecutor.projectActionArgs("create_project", payload);

        assertEquals("create_project", args.get("action").getAsString());
        assertEquals("Power Start", args.get("project_name").getAsString());
        assertEquals("Craft generator",
                args.getAsJsonArray("tasks").get(0).getAsJsonObject().get("description").getAsString());
    }
}
