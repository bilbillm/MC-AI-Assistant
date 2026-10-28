package com.lumoren.agentchat.tools;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.lumoren.agentchat.ai.GameDataAccess;
import com.lumoren.agentchat.integration.XaeroWaypointBridge;
import com.lumoren.agentchat.model.ToolDefinition;
import com.lumoren.agentchat.model.ToolResult;
import net.minecraft.client.Minecraft;

/**
 * Tool to search for a specific biome around the player using spiral sampling.
 * <p>
 * Supports both exact biome names (e.g. "desert", "minecraft:taiga") and
 * fuzzy type keywords (e.g. "cold", "warm", "ocean", "forest", "mountain").
 * <p>
 * When Xaero's Minimap is installed, automatically creates a waypoint
 * at the nearest matching biome location.
 */
public class FindBiomeTool implements GameTool {

    private static final String NAME = "find_biome";
    private static final String DESCRIPTION =
            "Search for a specific biome around the player. Use biome name (e.g. \"desert\", " +
            "\"minecraft:taiga\") or type keyword (\"cold\", \"warm\", \"ocean\", \"jungle\", " +
            "\"swamp\", \"mountain\", \"plains\", \"forest\", \"taiga\", \"mushroom\"). " +
            "Uses spiral sampling to find the nearest match within a configurable radius. " +
            "Returns direction, distance, and position of the nearest matching biome.";

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
        JsonObject params = new JsonObject();
        params.addProperty("type", "object");

        JsonObject props = new JsonObject();

        JsonObject biomeProp = new JsonObject();
        biomeProp.addProperty("type", "string");
        biomeProp.addProperty("description",
                "Biome name (e.g. \"desert\", \"minecraft:taiga\") or type keyword " +
                "(cold/warm/ocean/jungle/swamp/mountain/plains/forest/taiga/mushroom/desert/savanna/badlands)");
        props.add("biome", biomeProp);

        JsonObject radiusProp = new JsonObject();
        radiusProp.addProperty("type", "integer");
        radiusProp.addProperty("description", "Maximum search radius in blocks (32-1024, default 512)");
        radiusProp.addProperty("default", 512);
        props.add("max_radius", radiusProp);

        params.add("properties", props);

        com.google.gson.JsonArray reqArr = new com.google.gson.JsonArray();
        reqArr.add("biome");
        params.add("required", reqArr);

        return new ToolDefinition(NAME, DESCRIPTION, params);
    }

    @Override
    public ToolResult execute(Minecraft mc, String arguments) {
        try {
            JsonObject args = JsonParser.parseString(arguments).getAsJsonObject();
            if (!args.has("biome")) {
                return new ToolResult(NAME,
                        "{\"error\": \"Missing required parameter: biome\"}");
            }
            String biome = args.get("biome").getAsString();
            int maxRadius = args.has("max_radius") ? args.get("max_radius").getAsInt() : 512;

            String result = GameDataAccess.findBiome(mc, biome, maxRadius);

            // If Xaero is available and biome was found, create a waypoint
            JsonObject resultObj = JsonParser.parseString(result).getAsJsonObject();
            if (XaeroWaypointBridge.isAvailable()
                    && resultObj.has("found") && resultObj.get("found").getAsBoolean()
                    && resultObj.has("nearest")) {

                JsonObject nearest = resultObj.getAsJsonObject("nearest");
                String biomeName = nearest.get("biome").getAsString();
                int wx = nearest.getAsJsonObject("pos").get("x").getAsInt();
                int wz = nearest.getAsJsonObject("pos").get("z").getAsInt();

                // Use player's current Y as reasonable waypoint height
                int wy = mc.player != null ? mc.player.blockPosition().getY() : 64;
                String displayName = "Biome: " + formatBiomeName(biomeName);
                XaeroWaypointBridge.addWaypoint(mc, wx, wy, wz, displayName, "B", "cyan");
            }

            return new ToolResult(NAME, result);
        } catch (Exception e) {
            return new ToolResult(NAME,
                    "{\"error\": \"Failed to search biome: " + e.getMessage() + "\"}");
        }
    }

    /**
     * Format a biome ResourceLocation into a human-readable name.
     * Example: "minecraft:dark_forest" → "Dark Forest"
     */
    private static String formatBiomeName(String biomeId) {
        String name = biomeId.contains(":") ? biomeId.substring(biomeId.indexOf(':') + 1) : biomeId;
        // Replace underscores with spaces and capitalize words
        String[] parts = name.split("_");
        StringBuilder sb = new StringBuilder();
        for (String part : parts) {
            if (!part.isEmpty()) {
                if (!sb.isEmpty()) sb.append(' ');
                sb.append(Character.toUpperCase(part.charAt(0)));
                if (part.length() > 1) {
                    sb.append(part.substring(1));
                }
            }
        }
        return sb.toString();
    }
}
