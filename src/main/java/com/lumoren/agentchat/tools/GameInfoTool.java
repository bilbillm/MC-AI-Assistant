package com.lumoren.agentchat.tools;

import com.google.gson.JsonObject;
import com.lumoren.agentchat.model.ToolDefinition;
import com.lumoren.agentchat.model.ToolResult;
import net.minecraft.client.Minecraft;
import net.neoforged.fml.ModList;
import net.neoforged.fml.loading.FMLLoader;
import net.minecraft.SharedConstants;

/**
 * Tool to get Minecraft version and environment information.
 * <p>
 * Returns Minecraft version, loader type, mod count, and Java version.
 */
public class GameInfoTool implements GameTool {

    private static final String NAME = "game_info";
    private static final String DESCRIPTION =
            "Get Minecraft version and environment information.";

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
            StringBuilder sb = new StringBuilder();
            sb.append("Game Info:\n");

            // Minecraft version
            String mcVersion;
            try {
                mcVersion = SharedConstants.getCurrentVersion().getName();
            } catch (Exception e) {
                mcVersion = "Unknown";
            }
            sb.append("  Minecraft Version: ").append(mcVersion).append("\n");

            // Loader type
            String loader = detectLoader();
            sb.append("  Loader: ").append(loader).append("\n");

            // Mod count
            int modCount = ModList.get().getMods().size();
            // Exclude Minecraft and NeoForge from count
            int thirdPartyCount = (int) ModList.get().getMods().stream()
                    .filter(m -> !"minecraft".equals(m.getModId()) && !"neoforge".equals(m.getModId()))
                    .count();
            sb.append("  Mods: ").append(thirdPartyCount).append(" installed (")
                    .append(modCount).append(" total)\n");

            // Java version
            String javaVersion = System.getProperty("java.version");
            String javaVendor = System.getProperty("java.vendor");
            sb.append("  Java: ").append(javaVersion);
            if (javaVendor != null) {
                sb.append(" (").append(javaVendor).append(")");
            }
            sb.append("\n");

            // Operating system
            String osName = System.getProperty("os.name");
            String osArch = System.getProperty("os.arch");
            sb.append("  OS: ").append(osName).append(" (").append(osArch).append(")\n");

            return new ToolResult(NAME, sb.toString().trim());

        } catch (Exception e) {
            return new ToolResult(NAME, "Error getting game info: " + e.getMessage());
        }
    }

    /**
     * Detect the mod loader type.
     */
    private String detectLoader() {
        try {
            // Check if NeoForge is loaded
            Class.forName("net.neoforged.fml.loading.FMLLoader");
            try {
                return "NeoForge " + FMLLoader.versionInfo().neoForgeVersion();
            } catch (Exception e) {
                return "NeoForge";
            }
        } catch (ClassNotFoundException e) {
            // Not NeoForge
        }
        try {
            Class.forName("net.minecraftforge.fml.loading.FMLLoader");
            return "Forge";
        } catch (ClassNotFoundException e) {
            // Not Forge
        }
        try {
            Class.forName("net.fabricmc.loader.api.FabricLoader");
            return "Fabric";
        } catch (ClassNotFoundException e) {
            // Not Fabric
        }
        return "Vanilla";
    }
}
