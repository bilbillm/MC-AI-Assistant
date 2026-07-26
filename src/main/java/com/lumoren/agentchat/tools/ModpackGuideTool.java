package com.lumoren.agentchat.tools;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.lumoren.agentchat.guide.GuideContext;
import com.lumoren.agentchat.guide.GuideEvidence;
import com.lumoren.agentchat.guide.GuideIntent;
import com.lumoren.agentchat.guide.ModpackGuideService;
import com.lumoren.agentchat.i18n.I18nHelper;
import com.lumoren.agentchat.i18n.I18nKeys;
import com.lumoren.agentchat.model.ActionCard;
import com.lumoren.agentchat.model.AssistantAction;
import com.lumoren.agentchat.model.Project;
import com.lumoren.agentchat.model.Task;
import com.lumoren.agentchat.model.TaskStatus;
import com.lumoren.agentchat.model.ToolDefinition;
import com.lumoren.agentchat.model.ToolResult;
import com.lumoren.agentchat.persistence.ProjectManager;

import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Produces a structured guide card for common modpack guide flows.
 */
public class ModpackGuideTool implements GameTool {
    private static final String NAME = "modpack_guide";
    private static final String DESCRIPTION =
            "Create a structured guide card for next-step, recipe planning, item help, or machine diagnosis questions. Use this when the player asks what to do next or why a machine is not working.";
    private static final Pattern ITEM_ID_PATTERN =
            Pattern.compile("[a-z0-9_.-]+:[a-z0-9/._-]+");

    private final ModpackGuideService guideService = new ModpackGuideService();
    private final java.util.function.Function<String, GuideContext> recentContextProvider;

    public ModpackGuideTool() {
        this(null);
    }

    /**
     * @param recentContextProvider supplies the guide context already assembled
     *                              for the in-flight user request, so this tool
     *                              does not re-run the whole source aggregation;
     *                              may be {@code null} (always re-aggregates)
     */
    public ModpackGuideTool(java.util.function.Function<String, GuideContext> recentContextProvider) {
        this.recentContextProvider = recentContextProvider;
    }

    @Override
    public String getName() {
        return NAME;
    }

    @Override
    public String getDescription() {
        return DESCRIPTION;
    }

    @Override
    public ToolDefinition getDefinition() {
        JsonObject parameters = new JsonObject();
        parameters.addProperty("type", "object");
        JsonObject properties = new JsonObject();

        JsonObject question = new JsonObject();
        question.addProperty("type", "string");
        question.addProperty("description", "The player's guide question.");
        properties.add("question", question);

        JsonObject intent = new JsonObject();
        intent.addProperty("type", "string");
        intent.addProperty("description", "Optional intent override.");
        JsonArray values = new JsonArray();
        for (GuideIntent value : GuideIntent.values()) {
            values.add(value.name());
        }
        intent.add("enum", values);
        properties.add("intent", intent);

        parameters.add("properties", properties);
        JsonArray required = new JsonArray();
        required.add("question");
        parameters.add("required", required);
        return new ToolDefinition(NAME, DESCRIPTION, parameters);
    }

    @Override
    public ToolResult execute(Minecraft mc, String arguments) {
        String question = "";
        GuideIntent intent = null;
        try {
            JsonObject args = JsonParser.parseString(arguments).getAsJsonObject();
            question = stringArg(args, "question", "");
            String override = stringArg(args, "intent", "");
            if (!override.isBlank()) {
                intent = GuideIntent.valueOf(override.toUpperCase(Locale.ROOT));
            }
        } catch (RuntimeException ignored) {
            // Fall back to auto detection below.
        }
        if (intent == null) {
            intent = guideService.detectIntent(question);
        }

        GuideContext context = recentContextProvider != null
                ? recentContextProvider.apply(question) : null;
        if (context == null) {
            context = guideService.buildContext(mc, question);
        }
        ActionCard card = switch (intent) {
            case WHAT_NEXT -> nextStepCard(context);
            case MACHINE_DIAGNOSIS -> diagnosisCard(mc, question, context);
            case RECIPE_PLAN -> recipePlanCard(question, context);
            case ITEM_HELP -> itemHelpCard(question, context);
            default -> sourceSummaryCard(context);
        };
        return new ToolResult(NAME, card.toToolResultContent());
    }

    private ActionCard nextStepCard(GuideContext context) {
        Project project = ProjectManager.getInstance() != null
                ? ProjectManager.getInstance().getActiveProject() : null;
        List<String> facts = sourceFacts(context);
        List<AssistantAction> actions = new ArrayList<>();
        String title = "Guide next step";
        String summary;

        if (project == null || project.getTasks().isEmpty()) {
            summary = "No active project is being tracked. Ask for a concrete goal and I can turn it into a project.";
            JsonObject payload = new JsonObject();
            payload.addProperty("query", "I want to start a modpack goal. Help me choose the next objective.");
            actions.add(new AssistantAction(null, I18nHelper.translateToString(I18nKeys.ACTION_LABEL_ASK_GOAL), AssistantAction.Kind.SEARCH_MORE, payload));
        } else {
            int index = nextTaskIndex(project);
            if (index < 0) {
                summary = "All tracked project tasks are done.";
            } else {
                Task task = project.getTasks().get(index);
                summary = task.description();
                facts.add("Project: " + project.getName());
                facts.add("Task " + (index + 1) + ": " + task.status());
                JsonObject payload = new JsonObject();
                payload.addProperty("task_index", index + 1);
                actions.add(new AssistantAction(null, I18nHelper.translateToString(I18nKeys.ACTION_LABEL_SET_CURRENT), AssistantAction.Kind.SET_CURRENT_TASK, payload));
                actions.add(new AssistantAction(null, I18nHelper.translateToString(I18nKeys.ACTION_LABEL_MARK_DONE), AssistantAction.Kind.MARK_TASK_DONE, payload));
            }
        }

        return new ActionCard(ActionCard.Type.GUIDE_STEP, title, summary, facts, actions, NAME);
    }

    private ActionCard diagnosisCard(Minecraft mc, String question, GuideContext context) {
        List<String> facts = sourceFacts(context);
        String summary = "Inspect the target block, then check inputs, outputs, energy, fluid, side configuration, and the JEI recipe.";
        if (mc == null || mc.player == null || mc.level == null) {
            facts.add("No world is active.");
            summary = "Cannot inspect a machine because the player is not in a world.";
        } else if (!(mc.hitResult instanceof BlockHitResult hit) || hit.getType() != HitResult.Type.BLOCK) {
            facts.add("No target block is selected.");
            summary = "Look directly at the machine block and ask again.";
        } else {
            ActionCard inspected = ActionCard.fromToolResultContent(
                    new InspectTargetBlockTool().execute(mc, "{}").content());
            if (inspected != null && !inspected.facts().isEmpty()) {
                facts.addAll(inspected.facts());
            } else {
                var state = mc.level.getBlockState(hit.getBlockPos());
                facts.add("Target: " + BuiltInRegistries.BLOCK.getKey(state.getBlock()));
                facts.add("Position: " + hit.getBlockPos().getX() + ", "
                        + hit.getBlockPos().getY() + ", " + hit.getBlockPos().getZ());
                facts.add("V1 caveat: mod-specific energy/fluid/internal inventories are not read yet.");
            }
        }

        JsonObject payload = new JsonObject();
        payload.addProperty("query", question == null || question.isBlank()
                ? "Minecraft modded machine not working troubleshooting" : question);
        List<AssistantAction> actions = List.of(
                new AssistantAction(null, I18nHelper.translateToString(I18nKeys.ACTION_LABEL_SEARCH_SOURCES), AssistantAction.Kind.SEARCH_MORE, payload));
        return new ActionCard(ActionCard.Type.DIAGNOSIS, "Machine diagnosis", summary, facts, actions, NAME);
    }

    private ActionCard recipePlanCard(String question, GuideContext context) {
        List<String> facts = sourceFacts(context);
        List<AssistantAction> actions = new ArrayList<>();
        String itemId = firstItemId(question);
        String summary;
        if (itemId == null) {
            summary = "Use a namespaced item ID or exact item name so I can verify the recipe with JEI or the vanilla recipe manager.";
            JsonObject payload = new JsonObject();
            payload.addProperty("query", question);
            actions.add(new AssistantAction(null, I18nHelper.translateToString(I18nKeys.ACTION_LABEL_CLARIFY_ITEM), AssistantAction.Kind.SEARCH_MORE, payload));
        } else {
            summary = "Verified item target: " + itemId + ". Open its recipe or usage card to continue.";
            JsonObject payload = new JsonObject();
            payload.addProperty("item_name", itemId);
            actions.add(new AssistantAction(null, I18nHelper.translateToString(I18nKeys.ACTION_LABEL_OPEN_RECIPE), AssistantAction.Kind.OPEN_RECIPE, payload));
            actions.add(new AssistantAction(null, I18nHelper.translateToString(I18nKeys.ACTION_LABEL_OPEN_USAGE), AssistantAction.Kind.OPEN_USAGE, payload));
        }
        return new ActionCard(ActionCard.Type.GUIDE_STEP, "Recipe plan", summary, facts, actions, NAME);
    }

    private ActionCard itemHelpCard(String question, GuideContext context) {
        List<String> facts = sourceFacts(context);
        List<AssistantAction> actions = new ArrayList<>();
        String itemId = firstItemId(question);
        String summary = itemId == null
                ? "I need a precise item ID or item name to verify usages."
                : "Use JEI/recipe data first, then web/wiki only if mechanics remain unclear.";
        if (itemId != null) {
            JsonObject payload = new JsonObject();
            payload.addProperty("item_name", itemId);
            actions.add(new AssistantAction(null, I18nHelper.translateToString(I18nKeys.ACTION_LABEL_OPEN_USAGE), AssistantAction.Kind.OPEN_USAGE, payload));
            actions.add(new AssistantAction(null, I18nHelper.translateToString(I18nKeys.ACTION_LABEL_OPEN_RECIPE), AssistantAction.Kind.OPEN_RECIPE, payload));
        }
        return new ActionCard(ActionCard.Type.SOURCE_SUMMARY, "Item guide", summary, facts, actions, NAME);
    }

    private ActionCard sourceSummaryCard(GuideContext context) {
        return new ActionCard(ActionCard.Type.SOURCE_SUMMARY,
                "Guide sources",
                "Available local and optional guide sources for this instance.",
                sourceFacts(context),
                List.of(), NAME);
    }

    private static List<String> sourceFacts(GuideContext context) {
        List<String> facts = new ArrayList<>();
        if (context.projectSummary() != null && !context.projectSummary().isBlank()) {
            facts.add("Project: " + firstLine(context.projectSummary()));
        }
        if (context.targetBlockSummary() != null && !context.targetBlockSummary().isBlank()) {
            facts.add("Target: " + firstLine(context.targetBlockSummary()));
        }
        for (GuideEvidence evidence : context.evidence()) {
            facts.add(evidence.sourceType() + ": " + evidence.title());
        }
        return facts;
    }

    private static int nextTaskIndex(Project project) {
        List<Task> tasks = project.getTasks();
        for (int i = 0; i < tasks.size(); i++) {
            if (tasks.get(i).status() != TaskStatus.DONE) {
                return i;
            }
        }
        return -1;
    }

    private static String firstItemId(String text) {
        if (text == null) {
            return null;
        }
        Matcher matcher = ITEM_ID_PATTERN.matcher(text);
        return matcher.find() ? matcher.group() : null;
    }

    private static String firstLine(String text) {
        int index = text.indexOf('\n');
        return index >= 0 ? text.substring(0, index) : text;
    }

    private static String stringArg(JsonObject obj, String name, String defaultValue) {
        if (obj == null || !obj.has(name) || obj.get(name).isJsonNull()) {
            return defaultValue;
        }
        return obj.get(name).getAsString();
    }
}
