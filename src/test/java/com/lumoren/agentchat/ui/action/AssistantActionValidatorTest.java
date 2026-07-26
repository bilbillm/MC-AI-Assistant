package com.lumoren.agentchat.ui.action;

import com.google.gson.JsonObject;
import com.lumoren.agentchat.i18n.I18nKeys;
import com.lumoren.agentchat.model.AssistantAction;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AssistantActionValidatorTest {

    private static AssistantAction action(AssistantAction.Kind kind, JsonObject payload) {
        return new AssistantAction("id", "label", kind, payload);
    }

    private static JsonObject itemPayload() {
        JsonObject payload = new JsonObject();
        payload.addProperty("item_name", "minecraft:hopper");
        return payload;
    }

    @Test
    void openRecipeRequiresItemTarget() {
        assertTrue(AssistantActionValidator.validate(
                action(AssistantAction.Kind.OPEN_RECIPE, itemPayload())).valid());
        assertTrue(AssistantActionValidator.validate(
                action(AssistantAction.Kind.OPEN_USAGE, itemPayload())).valid());

        AssistantActionValidator.ValidationResult result = AssistantActionValidator.validate(
                action(AssistantAction.Kind.OPEN_RECIPE, new JsonObject()));
        assertFalse(result.valid());
        assertEquals(I18nKeys.ACTION_ERROR_MISSING_ITEM, result.errorKey());
    }

    @Test
    void itemTargetAcceptsKeyVariants() {
        JsonObject snake = new JsonObject();
        snake.addProperty("item_id", " minecraft:piston ");
        assertTrue(AssistantActionValidator.validate(
                action(AssistantAction.Kind.OPEN_USAGE, snake)).valid());

        JsonObject camel = new JsonObject();
        camel.addProperty("itemId", "minecraft:furnace");
        assertTrue(AssistantActionValidator.validate(
                action(AssistantAction.Kind.OPEN_USAGE, camel)).valid());

        assertEquals("minecraft:piston", AssistantActionValidator.itemTarget(snake));
        assertEquals("minecraft:furnace", AssistantActionValidator.itemTarget(camel));
    }

    @Test
    void taskActionsRequirePositiveTaskIndex() {
        JsonObject valid = new JsonObject();
        valid.addProperty("task_index", 2);
        assertTrue(AssistantActionValidator.validate(
                action(AssistantAction.Kind.MARK_TASK_DONE, valid)).valid());
        assertTrue(AssistantActionValidator.validate(
                action(AssistantAction.Kind.SET_CURRENT_TASK, valid)).valid());
        assertTrue(AssistantActionValidator.validate(
                action(AssistantAction.Kind.PIN_TASK, valid)).valid());
        assertTrue(AssistantActionValidator.validate(
                action(AssistantAction.Kind.MARK_TASK_ACTIVE, valid)).valid());

        JsonObject missing = new JsonObject();
        assertFalse(AssistantActionValidator.validate(
                action(AssistantAction.Kind.MARK_TASK_DONE, missing)).valid());

        JsonObject zero = new JsonObject();
        zero.addProperty("task_index", 0);
        assertFalse(AssistantActionValidator.validate(
                action(AssistantAction.Kind.MARK_TASK_DONE, zero)).valid());

        JsonObject garbage = new JsonObject();
        garbage.addProperty("task_index", "abc");
        AssistantActionValidator.ValidationResult result = AssistantActionValidator.validate(
                action(AssistantAction.Kind.MARK_TASK_DONE, garbage));
        assertFalse(result.valid());
        assertEquals(I18nKeys.ACTION_ERROR_MISSING_TASK, result.errorKey());
    }

    @Test
    void searchMoreRequiresQuery() {
        JsonObject valid = new JsonObject();
        valid.addProperty("query", "why machine not working");
        assertTrue(AssistantActionValidator.validate(
                action(AssistantAction.Kind.SEARCH_MORE, valid)).valid());

        JsonObject blank = new JsonObject();
        blank.addProperty("query", "   ");
        AssistantActionValidator.ValidationResult result = AssistantActionValidator.validate(
                action(AssistantAction.Kind.SEARCH_MORE, blank));
        assertFalse(result.valid());
        assertEquals(I18nKeys.ACTION_ERROR_MISSING_QUERY, result.errorKey());
    }

    @Test
    void cancelProjectNeedsNoPayload() {
        assertTrue(AssistantActionValidator.validate(
                action(AssistantAction.Kind.CANCEL_PROJECT, new JsonObject())).valid());
    }

    @Test
    void nullActionIsInvalid() {
        AssistantActionValidator.ValidationResult result = AssistantActionValidator.validate(null);
        assertFalse(result.valid());
        assertEquals(I18nKeys.ACTION_ERROR_UNKNOWN_ACTION, result.errorKey());
    }

    @Test
    void queryIsTrimmed() {
        JsonObject payload = new JsonObject();
        payload.addProperty("query", "  padded question  ");
        assertEquals("padded question", AssistantActionValidator.query(payload));
    }
}
