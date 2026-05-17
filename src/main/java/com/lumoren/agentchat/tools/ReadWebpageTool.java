package com.lumoren.agentchat.tools;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.lumoren.agentchat.model.ToolDefinition;
import com.lumoren.agentchat.model.ToolResult;
import net.minecraft.client.Minecraft;

public class ReadWebpageTool implements GameTool {

    private static final String NAME = "read_webpage";
    private static final String DESCRIPTION =
            "Fetch and read the content of a web page. Extracts the main text content. "
            + "Use after web_search to read specific result pages in depth.";

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
        JsonObject urlProp = new JsonObject();
        urlProp.addProperty("type", "string");
        urlProp.addProperty("description",
                "The full URL of the web page to read (e.g. 'https://mcmod.cn/item/1.html')");
        properties.add("url", urlProp);
        parameters.add("properties", properties);

        JsonArray required = new JsonArray();
        required.add("url");
        parameters.add("required", required);

        return new ToolDefinition(NAME, DESCRIPTION, parameters);
    }

    @Override
    public ToolResult execute(Minecraft mc, String arguments) {
        try {
            JsonObject args = new com.google.gson.Gson().fromJson(arguments, JsonObject.class);
            String url = args.get("url").getAsString();

            if (url == null || url.isBlank()) {
                return new ToolResult(NAME, "Error: URL is required");
            }

            // Basic URL validation
            if (!url.startsWith("http://") && !url.startsWith("https://")) {
                url = "https://" + url;
            }

            String content = WebContentFetcher.fetchAndExtract(url, 10);
            return new ToolResult(NAME, content);
        } catch (Exception e) {
            return new ToolResult(NAME, "Failed to read page: " + e.getMessage());
        }
    }
}
