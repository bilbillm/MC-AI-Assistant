package com.lumoren.agentchat.tools;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.lumoren.agentchat.integration.XaeroWaypointBridge;
import com.lumoren.agentchat.model.ActionCard;
import com.lumoren.agentchat.model.AssistantAction;
import com.lumoren.agentchat.model.ToolDefinition;
import com.lumoren.agentchat.model.ToolResult;
import net.minecraft.client.Minecraft;

import java.util.List;

/**
 * Action tool that creates a waypoint at explicit coordinates or the player's current position.
 */
public class CreateWaypointTool implements GameTool {

    private static final String NAME = "create_waypoint";
    private static final String DESCRIPTION =
            "Create a minimap waypoint using Xaero's Minimap when available. If coordinates are omitted, uses the player's current position.";

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
        addString(properties, "name", "Waypoint display name, e.g. Home or Village.");
        addString(properties, "symbol", "1-2 character waypoint symbol. Defaults to first letter of name.");
        addString(properties, "color", "Waypoint color name such as green, red, blue, yellow.");
        addInteger(properties, "x", "Optional block X coordinate.");
        addInteger(properties, "y", "Optional block Y coordinate.");
        addInteger(properties, "z", "Optional block Z coordinate.");
        parameters.add("properties", properties);
        JsonArray required = new JsonArray();
        required.add("name");
        parameters.add("required", required);
        return new ToolDefinition(NAME, DESCRIPTION, parameters);
    }

    @Override
    public ToolResult execute(Minecraft mc, String arguments) {
        try {
            JsonObject args = JsonParser.parseString(arguments).getAsJsonObject();
            String name = stringArg(args, "name", "Waypoint");
            String symbol = stringArg(args, "symbol", name.isBlank() ? "W" : name.substring(0, 1));
            String color = stringArg(args, "color", "green");

            Coordinates coordinates = coordinates(args, mc);
            if (coordinates == null) {
                return card("Waypoint unavailable",
                        "Cannot verify waypoint position because no coordinates were provided and player position is unavailable.",
                        List.of("No waypoint was created."), false);
            }

            boolean available = XaeroWaypointBridge.isAvailable();
            if (available) {
                XaeroWaypointBridge.addWaypoint(mc, coordinates.x(), coordinates.y(), coordinates.z(),
                        name, symbol, color);
            }

            String summary = available
                    ? "Waypoint created: " + name
                    : "Xaero's Minimap is not available; waypoint was not created.";
            return card("Waypoint", summary,
                    List.of("Name: " + name,
                            "Position: " + coordinates.x() + ", " + coordinates.y() + ", " + coordinates.z(),
                            "Xaero available: " + available),
                    available);
        } catch (Exception e) {
            return card("Waypoint unavailable",
                    "Cannot verify waypoint action because arguments were invalid: " + e.getMessage(),
                    List.of("No waypoint was created."), false);
        }
    }

    private ToolResult card(String title, String summary, List<String> facts, boolean created) {
        JsonObject payload = new JsonObject();
        payload.addProperty("created", created);
        ActionCard card = new ActionCard(ActionCard.Type.WAYPOINT, title, summary, facts,
                List.of(new AssistantAction(null, "Show project",
                        AssistantAction.Kind.PIN_TASK, payload)),
                NAME);
        return new ToolResult(NAME, card.toToolResultContent());
    }

    private static Coordinates coordinates(JsonObject args, Minecraft mc) {
        if (args.has("x") && args.has("y") && args.has("z")) {
            return new Coordinates(args.get("x").getAsInt(), args.get("y").getAsInt(), args.get("z").getAsInt());
        }
        if (mc == null || mc.player == null) {
            return null;
        }
        return new Coordinates(mc.player.getBlockX(), mc.player.getBlockY(), mc.player.getBlockZ());
    }

    private static String stringArg(JsonObject obj, String name, String defaultValue) {
        if (!obj.has(name) || obj.get(name).isJsonNull()) {
            return defaultValue;
        }
        return obj.get(name).getAsString();
    }

    private static void addString(JsonObject properties, String name, String description) {
        JsonObject param = new JsonObject();
        param.addProperty("type", "string");
        param.addProperty("description", description);
        properties.add(name, param);
    }

    private static void addInteger(JsonObject properties, String name, String description) {
        JsonObject param = new JsonObject();
        param.addProperty("type", "integer");
        param.addProperty("description", description);
        properties.add(name, param);
    }

    private record Coordinates(int x, int y, int z) {}
}
