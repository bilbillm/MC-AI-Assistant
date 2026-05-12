package com.lumoren.agentchat.ai;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.lumoren.agentchat.model.*;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * AI prompt builder for task decomposition and response parser.
 * <p>
 * This service constructs prompts that ask the AI to decompose a goal into
 * concrete Minecraft tasks, and parses the structured JSON responses back
 * into {@link Task} objects. It also builds project context messages for
 * injection into the AI conversation.
 * <p>
 * This class does NOT make HTTP/AI API calls — it only builds prompts and
 * parses responses.
 */
public final class ProjectPlanningService {

    private ProjectPlanningService() {
        // utility class
    }

    /** Regex matching Minecraft resource location format: {@code namespace:path}. */
    private static final Pattern ITEM_ID_PATTERN =
            Pattern.compile("^[a-z0-9_.-]+:[a-z0-9/._-]+$");

    // ==================== Prompt Building ====================

    /**
     * Build a system prompt instructing the AI to decompose a goal into
     * a structured JSON task chain.
     *
     * @param goal the player's high-level goal
     * @return a singleton list containing the system {@link ChatMessage}
     */
    public static List<ChatMessage> buildTaskChainPrompt(String goal) {
        String prompt = """
                You are a Minecraft task planner. Break down this goal into 3-8 concrete steps: "%s"

                Rules:
                - Each step must be a single actionable task
                - For tasks requiring items, specify the Minecraft item ID (e.g. "minecraft:stick")
                - Return ONLY valid JSON, no markdown, no explanation

                Format:
                {
                  "tasks": [
                    {
                      "description": "what the player needs to do",
                      "type": "CRAFT|GATHER|GO_TO|USE|KILL|PLAN",
                      "items": [
                        {"itemId": "minecraft:stick", "count": 4}
                      ]
                    }
                  ]
                }
                """.formatted(goal);
        return List.of(ChatMessage.system(prompt));
    }

    // ==================== Response Parsing ====================

    /**
     * Parse an AI-generated JSON response into a list of {@link Task} objects.
     * <p>
     * On any parsing failure (malformed JSON, missing fields, etc.), this
     * method returns an empty list rather than throwing an exception.
     *
     * @param aiResponse the raw AI response string (expected to be JSON)
     * @return list of parsed tasks, or an empty list on failure
     */
    public static List<Task> parseTaskChain(String aiResponse) {
        try {
            JsonObject obj = JsonParser.parseString(aiResponse).getAsJsonObject();
            var tasksArray = obj.getAsJsonArray("tasks");
            if (tasksArray == null || tasksArray.isEmpty()) {
                return List.of();
            }

            List<Task> tasks = new ArrayList<>();
            for (var element : tasksArray) {
                JsonObject taskObj = element.getAsJsonObject();

                String description = taskObj.get("description").getAsString();
                TaskType type = TaskType.valueOf(taskObj.get("type").getAsString());

                List<ItemRequirement> items = parseItems(taskObj);
                tasks.add(Task.of(description, type, items));
            }
            return tasks;
        } catch (Exception e) {
            return List.of();
        }
    }

    /**
     * Extract and validate the "items" array from a task JSON object.
     *
     * @param taskObj the JSON object representing a single task
     * @return list of validated {@link ItemRequirement}, may be empty
     */
    private static List<ItemRequirement> parseItems(JsonObject taskObj) {
        List<ItemRequirement> items = new ArrayList<>();
        if (!taskObj.has("items") || taskObj.get("items").isJsonNull()) {
            return items;
        }
        var itemsArray = taskObj.getAsJsonArray("items");
        for (var itemElem : itemsArray) {
            try {
                JsonObject itemObj = itemElem.getAsJsonObject();
                String itemId = itemObj.get("itemId").getAsString();
                int count = itemObj.get("count").getAsInt();

                if (isValidItemId(itemId)) {
                    items.add(new ItemRequirement(itemId, count, 0));
                }
            } catch (Exception ignored) {
                // skip malformed item entries
            }
        }
        return items;
    }

    /**
     * Validate that an item ID conforms to the Minecraft resource-location
     * format ({@code namespace:path}). At runtime this could be augmented
     * with {@code ResourceLocation.tryParse(itemId)} for stricter validation.
     *
     * @param itemId the item ID to validate (e.g. {@code "minecraft:stick"})
     * @return {@code true} if the format is valid
     */
    static boolean isValidItemId(String itemId) {
        return itemId != null && ITEM_ID_PATTERN.matcher(itemId).matches();
    }

    // ==================== Project Context ====================

    /**
     * Build a system message that summarises the current project state
     * for injection into the AI conversation.
     *
     * @param project the current project (may be null)
     * @return a system {@link ChatMessage} with project context
     */
    public static ChatMessage buildProjectContextMessage(Project project) {
        if (project == null || project.getTasks() == null) {
            return ChatMessage.system("No active project.");
        }

        long doneCount = project.getTasks().stream()
                .filter(t -> t.status() == TaskStatus.DONE).count();
        long blockedCount = project.getTasks().stream()
                .filter(t -> t.status() == TaskStatus.BLOCKED).count();
        long total = project.getTasks().size();

        StringBuilder sb = new StringBuilder();
        sb.append("Current project: \"").append(project.getName()).append("\"\n");
        sb.append("Status: ").append(project.getStatus()).append("\n");
        sb.append("Progress: ").append(doneCount).append("/").append(total)
                .append(" tasks completed");
        if (blockedCount > 0) {
            sb.append(", ").append(blockedCount).append(" blocked");
        }
        sb.append("\n\n");

        if (!project.getTasks().isEmpty()) {
            sb.append("Tasks:\n");
            for (Task t : project.getTasks()) {
                String statusIcon = switch (t.status()) {
                    case DONE -> "[✓]";
                    case BLOCKED -> "[!]";
                    case IN_PROGRESS -> "[→]";
                    default -> "[ ]";
                };
                sb.append(statusIcon).append(" ").append(t.description()).append("\n");
            }
        }

        return ChatMessage.system(sb.toString().strip());
    }
}
