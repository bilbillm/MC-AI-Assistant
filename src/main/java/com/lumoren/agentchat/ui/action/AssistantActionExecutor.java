package com.lumoren.agentchat.ui.action;

import com.google.gson.JsonObject;
import com.lumoren.agentchat.ai.GameDataAccess;
import com.lumoren.agentchat.i18n.I18nHelper;
import com.lumoren.agentchat.i18n.I18nKeys;
import com.lumoren.agentchat.model.AssistantAction;
import com.lumoren.agentchat.tools.OpenItemRecipeTool;
import com.lumoren.agentchat.tools.OpenItemUsageTool;
import com.lumoren.agentchat.tools.ProjectTool;
import net.minecraft.client.Minecraft;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

import java.util.HashMap;
import java.util.Map;
import java.util.function.LongSupplier;
import java.util.function.Supplier;

/**
 * Single entry point for executing {@link AssistantAction}s from cards.
 * Keeps action execution out of the chat screen and enforces:
 * <ul>
 *   <li>fail-closed validation per kind</li>
 *   <li>JEI GUI opens on the Minecraft main thread</li>
 *   <li>a double-click guard so the same action cannot fire twice quickly</li>
 * </ul>
 */
public class AssistantActionExecutor {

    private static final long DOUBLE_CLICK_GUARD_MS = 750;

    /**
     * Side effects the executor may perform on the chat UI.
     */
    public interface ActionContext {
        /** Append a tool-result message (usually an ActionCard) to the current thread. */
        void appendToolResult(String content);

        /** Send a follow-up user message through the normal send pipeline. */
        void sendFollowUp(String query);

        /** Append a localized system message to the current thread. */
        void showSystemMessage(String message);
    }

    /**
     * JEI GUI capability, injectable for tests.
     */
    public interface JeiGui {
        boolean isAvailable();

        boolean showRecipes(String itemId);

        boolean showUsages(String itemId);
    }

    /**
     * Resolves a player-facing item name to an existing item id, injectable
     * for tests. Returns the namespaced id when the item exists, otherwise
     * {@code null}.
     */
    public interface ItemResolver {
        String resolveExistingItemId(String itemName);
    }

    private final Supplier<Minecraft> mcSupplier;
    private final JeiGui jeiGui;
    private final ItemResolver itemResolver;
    private final LongSupplier clock;
    private final Map<String, Long> recentExecutions = new HashMap<>();

    public AssistantActionExecutor(Supplier<Minecraft> mcSupplier, JeiGui jeiGui) {
        this(mcSupplier, jeiGui, gameDataItemResolver(), System::currentTimeMillis);
    }

    public AssistantActionExecutor(Supplier<Minecraft> mcSupplier, JeiGui jeiGui,
                                   ItemResolver itemResolver, LongSupplier clock) {
        this.mcSupplier = mcSupplier;
        this.jeiGui = jeiGui;
        this.itemResolver = itemResolver;
        this.clock = clock;
    }

    /**
     * Production {@link ItemResolver} backed by the vanilla item registry.
     * Must run where registries are available (client runtime).
     */
    public static ItemResolver gameDataItemResolver() {
        return itemName -> {
            try {
                net.minecraft.resources.ResourceLocation itemId = GameDataAccess.resolveItemId(itemName);
                Item item = GameDataAccess.lookupItem(itemId);
                return (item == null || item == Items.AIR) ? null : itemId.toString();
            } catch (RuntimeException e) {
                return null;
            }
        };
    }

    /**
     * Executes an action. Must be called on the Minecraft main thread.
     *
     * @return explicit result; never throws for malformed input
     */
    public ActionExecutionResult execute(AssistantAction action, ActionContext context) {
        if (action == null || action.kind() == null) {
            return ActionExecutionResult.ignored(
                    I18nHelper.translateToString(I18nKeys.ACTION_ERROR_UNKNOWN_ACTION));
        }
        AssistantActionValidator.ValidationResult validation = AssistantActionValidator.validate(action);
        if (!validation.valid()) {
            return ActionExecutionResult.failure(
                    I18nHelper.translateToString(validation.errorKey()));
        }
        if (!claim(action)) {
            return ActionExecutionResult.ignored(
                    I18nHelper.translateToString(I18nKeys.ACTION_STATUS_IGNORED_DUPLICATE));
        }

        return switch (action.kind()) {
            case OPEN_RECIPE -> openItem(action, context, true);
            case OPEN_USAGE -> openItem(action, context, false);
            case MARK_TASK_DONE -> runProjectAction(context, "mark_done", action.payload());
            case MARK_TASK_ACTIVE, SET_CURRENT_TASK, PIN_TASK ->
                    runProjectAction(context, "set_current_task", action.payload());
            case CANCEL_PROJECT -> {
                context.showSystemMessage(
                        I18nHelper.translateToString(I18nKeys.STATUS_CANCEL_PROJECT_CARD_BLOCKED));
                yield ActionExecutionResult.ignored(
                        I18nHelper.translateToString(I18nKeys.STATUS_CANCEL_PROJECT_CARD_BLOCKED));
            }
            case SEARCH_MORE -> {
                context.sendFollowUp(AssistantActionValidator.query(action.payload()));
                yield ActionExecutionResult.success("");
            }
        };
    }

    private boolean claim(AssistantAction action) {
        long now = clock.getAsLong();
        Long last = recentExecutions.get(action.id());
        if (last != null && now - last < DOUBLE_CLICK_GUARD_MS) {
            return false;
        }
        recentExecutions.put(action.id(), now);
        return true;
    }

    private ActionExecutionResult openItem(AssistantAction action, ActionContext context, boolean recipe) {
        String itemName = AssistantActionValidator.itemTarget(action.payload());
        Minecraft mc = mcSupplier.get();

        String itemId = itemResolver.resolveExistingItemId(itemName);
        if (itemId == null) {
            return ActionExecutionResult.failure(
                    I18nHelper.translateToString(I18nKeys.ACTION_ERROR_UNKNOWN_ITEM, itemName));
        }

        if (jeiGui.isAvailable()) {
            boolean opened = recipe ? jeiGui.showRecipes(itemId) : jeiGui.showUsages(itemId);
            if (opened) {
                return ActionExecutionResult.success(
                        I18nHelper.translateToString(I18nKeys.ACTION_STATUS_JEI_OPENED, itemId));
            }
            // JEI present but failed to open — fall through to local card.
            context.appendToolResult(fallbackCard(mc, itemName, recipe));
            return ActionExecutionResult.success(
                    I18nHelper.translateToString(I18nKeys.ACTION_STATUS_JEI_FAILED_FALLBACK));
        }

        context.appendToolResult(fallbackCard(mc, itemName, recipe));
        return ActionExecutionResult.success(
                I18nHelper.translateToString(I18nKeys.ACTION_STATUS_JEI_UNAVAILABLE_FALLBACK));
    }

    private static String fallbackCard(Minecraft mc, String itemName, boolean recipe) {
        JsonObject args = new JsonObject();
        args.addProperty("item_name", itemName);
        return recipe
                ? new OpenItemRecipeTool().execute(mc, args.toString()).content()
                : new OpenItemUsageTool().execute(mc, args.toString()).content();
    }

    private ActionExecutionResult runProjectAction(ActionContext context, String actionName, JsonObject payload) {
        JsonObject args = projectActionArgs(actionName, payload);
        context.appendToolResult(new ProjectTool().execute(mcSupplier.get(), args.toString()).content());
        return ActionExecutionResult.success("");
    }

    static JsonObject projectActionArgs(String actionName, JsonObject payload) {
        JsonObject args = new JsonObject();
        if (payload != null) {
            for (var entry : payload.entrySet()) {
                if (!"action".equals(entry.getKey())) {
                    args.add(entry.getKey(), entry.getValue().deepCopy());
                }
            }
        }
        args.addProperty("action", actionName);
        return args;
    }
}
