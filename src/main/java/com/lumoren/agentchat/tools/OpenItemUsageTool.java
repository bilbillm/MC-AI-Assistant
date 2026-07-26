package com.lumoren.agentchat.tools;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.lumoren.agentchat.AgentChat;
import com.lumoren.agentchat.ai.GameDataAccess;
import com.lumoren.agentchat.i18n.I18nHelper;
import com.lumoren.agentchat.i18n.I18nKeys;
import com.lumoren.agentchat.model.ActionCard;
import com.lumoren.agentchat.model.AssistantAction;
import com.lumoren.agentchat.model.RecipeResult;
import com.lumoren.agentchat.model.ToolDefinition;
import com.lumoren.agentchat.model.ToolResult;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;

import java.util.List;

/**
 * Action-oriented usage lookup. Uses JEI data when available and falls back to vanilla usage lookup.
 */
public class OpenItemUsageTool implements GameTool {

    private static final String NAME = "open_item_usage";
    private static final String DESCRIPTION =
            "Open or show usages for an item. Uses JEI data when available; otherwise returns a usage action card.";

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
        JsonObject itemName = new JsonObject();
        itemName.addProperty("type", "string");
        itemName.addProperty("description", "Item name or namespaced item ID to show usages for.");
        properties.add("item_name", itemName);
        parameters.add("properties", properties);
        JsonArray required = new JsonArray();
        required.add("item_name");
        parameters.add("required", required);
        return new ToolDefinition(NAME, DESCRIPTION, parameters);
    }

    @Override
    public ToolResult execute(Minecraft mc, String arguments) {
        try {
            JsonObject args = JsonParser.parseString(arguments).getAsJsonObject();
            String itemName = args.get("item_name").getAsString();
            ResourceLocation itemId = GameDataAccess.resolveItemId(itemName);

            String source = AgentChat.hasJei() ? "JEI" : "Vanilla recipe manager";
            String summary;
            if (AgentChat.hasJei()) {
                String jei = RecipeTool.lookupUsagesJEI(itemId);
                summary = jei != null && !jei.isBlank() ? compact(jei) : "JEI found no verified usage for " + itemId;
            } else {
                List<RecipeResult> usages = GameDataAccess.getUsagesForInput(mc, itemId);
                summary = usages.isEmpty()
                        ? "No verified vanilla usage found for " + itemId
                        : RecipeResult.toJsonArray(usages).toString();
            }

            JsonObject payload = new JsonObject();
            payload.addProperty("item_name", itemId.toString());
            ActionCard card = new ActionCard(ActionCard.Type.RECIPE,
                    "Usage: " + itemId,
                    summary,
                    List.of("Source: " + source, "JEI available: " + AgentChat.hasJei()),
                    List.of(new AssistantAction(null, I18nHelper.translateToString(I18nKeys.ACTION_LABEL_OPEN_RECIPE),
                            AssistantAction.Kind.OPEN_RECIPE, payload)),
                    NAME);
            return new ToolResult(NAME, card.toToolResultContent());
        } catch (Exception e) {
            ActionCard card = new ActionCard(ActionCard.Type.STATUS,
                    "Unable to verify usage",
                    "Cannot verify usage because item lookup failed: " + e.getMessage(),
                    List.of("Use a more precise item name or a namespaced item ID."),
                    List.of(), NAME);
            return new ToolResult(NAME, card.toToolResultContent());
        }
    }

    private static String compact(String text) {
        String value = text.strip();
        return value.length() > 1200 ? value.substring(0, 1197) + "..." : value;
    }
}
