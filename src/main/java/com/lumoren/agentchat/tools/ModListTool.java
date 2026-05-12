package com.lumoren.agentchat.tools;

import com.google.gson.JsonObject;
import com.lumoren.agentchat.model.ToolDefinition;
import com.lumoren.agentchat.model.ToolResult;
import net.minecraft.client.Minecraft;
import net.neoforged.fml.ModList;

import java.util.ArrayList;
import java.util.List;

/**
 * Tool to list all installed mods.
 * <p>
 * Returns mod ID, display name, and version for each installed mod,
 * excluding Minecraft and NeoForge itself.
 */
public class ModListTool implements GameTool {

    private static final String NAME = "list_mods";
    private static final String DESCRIPTION =
            "List all installed mods in the current Minecraft instance.";

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
        return new ToolDefinition(NAME, DESCRIPTION, parameters);
    }

    @Override
    public ToolResult execute(Minecraft mc, String arguments) {
        try {
            List<?> mods = ModList.get().getMods();
            if (mods.isEmpty()) {
                return new ToolResult(NAME, "No mods found.");
            }

            // Collect mod info into strings, filtering out Minecraft/NeoForge
            List<String> modEntries = new ArrayList<>();
            for (Object mod : mods) {
                String id = getModId(mod);
                if ("minecraft".equals(id) || "neoforge".equals(id)) {
                    continue;
                }

                StringBuilder entry = new StringBuilder();
                entry.append(id);

                String displayName = getDisplayName(mod);
                if (displayName != null && !displayName.isBlank() && !displayName.equals(id)) {
                    entry.append(" - ").append(displayName);
                }

                Object version = getVersion(mod);
                if (version != null) {
                    String versionStr = version.toString();
                    if (!versionStr.isBlank()) {
                        entry.append(" (v").append(versionStr).append(")");
                    }
                }

                modEntries.add(entry.toString());
            }

            // Sort entries alphabetically
            modEntries.sort(String.CASE_INSENSITIVE_ORDER);

            // Format output
            StringBuilder sb = new StringBuilder();
            sb.append("Installed mods (").append(modEntries.size()).append("):\n\n");
            for (int i = 0; i < modEntries.size(); i++) {
                sb.append(i + 1).append(". ").append(modEntries.get(i)).append("\n");
            }

            return new ToolResult(NAME, sb.toString().trim());

        } catch (Exception e) {
            return new ToolResult(NAME, "Error listing mods: " + e.getMessage());
        }
    }

    private static String getModId(Object mod) {
        try {
            return (String) mod.getClass().getMethod("getModId").invoke(mod);
        } catch (Exception e) {
            return "Unknown";
        }
    }

    private static String getDisplayName(Object mod) {
        try {
            return (String) mod.getClass().getMethod("getDisplayName").invoke(mod);
        } catch (Exception e) {
            return null;
        }
    }

    private static Object getVersion(Object mod) {
        try {
            return mod.getClass().getMethod("getVersion").invoke(mod);
        } catch (Exception e) {
            return null;
        }
    }
}
