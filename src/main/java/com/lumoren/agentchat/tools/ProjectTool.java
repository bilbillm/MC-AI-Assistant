package com.lumoren.agentchat.tools;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.lumoren.agentchat.model.Project;
import com.lumoren.agentchat.model.Task;
import com.lumoren.agentchat.model.TaskStatus;
import com.lumoren.agentchat.model.ToolDefinition;
import com.lumoren.agentchat.model.ToolResult;
import com.lumoren.agentchat.persistence.ProjectManager;
import net.minecraft.client.Minecraft;

import java.util.ArrayList;
import java.util.List;

/**
 * Tool that gives the AI full CRUD control over the project system.
 * <p>
 * Supports four actions via the required "action" parameter:
 * <ul>
 *   <li>{@code get_status} — view active project progress and task list</li>
 *   <li>{@code mark_done} — mark a task as complete (requires {@code task_index})</li>
 *   <li>{@code mark_blocked} — mark a task as blocked (requires {@code task_index})</li>
 *   <li>{@code cancel} — archive the active project</li>
 * </ul>
 */
public class ProjectTool implements GameTool {

    private static final String NAME = "manage_project";
    private static final String DESCRIPTION =
            "Manage the active project: get status, update task progress, or cancel.";

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

        // action: what to do
        JsonObject action = new JsonObject();
        action.addProperty("type", "string");
        action.addProperty("description",
                "Action: 'get_status' to see project progress, 'mark_done' to mark a task as complete, "
                        + "'mark_blocked' to mark a task as blocked, 'cancel' to archive the project");
        JsonArray enumValues = new JsonArray();
        enumValues.add("get_status");
        enumValues.add("mark_done");
        enumValues.add("mark_blocked");
        enumValues.add("cancel");
        action.add("enum", enumValues);
        properties.add("action", action);

        // task_index: which task (1-based, for mark_done/mark_blocked)
        JsonObject taskIndex = new JsonObject();
        taskIndex.addProperty("type", "integer");
        taskIndex.addProperty("description",
                "Task number (1-based) to update. Required for 'mark_done' and 'mark_blocked'");
        properties.add("task_index", taskIndex);

        parameters.add("properties", properties);

        JsonArray required = new JsonArray();
        required.add("action");
        parameters.add("required", required);

        return new ToolDefinition(NAME, DESCRIPTION, parameters);
    }

    @Override
    public ToolResult execute(Minecraft mc, String arguments) {
        try {
            JsonObject args = JsonParser.parseString(arguments).getAsJsonObject();
            String action = args.get("action").getAsString();
            ProjectManager pm = ProjectManager.getInstance();

            if (pm == null) {
                return new ToolResult(NAME, "No active project manager");
            }

            return switch (action) {
                case "get_status" -> getStatus(pm);
                case "mark_done" -> markDone(pm, args);
                case "mark_blocked" -> markBlocked(pm, args);
                case "cancel" -> cancelProject(pm);
                default -> new ToolResult(NAME, "Unknown action: " + action);
            };
        } catch (Exception e) {
            return new ToolResult(NAME, "Error: " + e.getMessage());
        }
    }

    // ==================== Action Implementations ====================

    private ToolResult getStatus(ProjectManager pm) {
        Project p = pm.getActiveProject();
        if (p == null) {
            return new ToolResult(NAME, "No active project");
        }

        StringBuilder sb = new StringBuilder();
        sb.append("Project: ").append(p.getName()).append("\n");
        sb.append("Status: ").append(p.getStatus()).append("\n");
        List<Task> tasks = p.getTasks();
        long done = tasks.stream().filter(t -> t.status() == TaskStatus.DONE).count();
        sb.append("Progress: ").append(done).append("/").append(tasks.size()).append("\n\n");
        for (int i = 0; i < tasks.size(); i++) {
            Task t = tasks.get(i);
            String icon = switch (t.status()) {
                case DONE -> "[✓]";
                case BLOCKED -> "[!]";
                case IN_PROGRESS -> "[→]";
                default -> "[ ]";
            };
            sb.append(i + 1).append(". ").append(icon).append(" ").append(t.description()).append("\n");
        }
        return new ToolResult(NAME, sb.toString());
    }

    private ToolResult markDone(ProjectManager pm, JsonObject args) {
        Project p = pm.getActiveProject();
        if (p == null) {
            return new ToolResult(NAME, "No active project");
        }
        int index = args.get("task_index").getAsInt() - 1;
        List<Task> tasks = new ArrayList<>(p.getTasks());
        if (index < 0 || index >= tasks.size()) {
            return new ToolResult(NAME, "Invalid task index: " + (index + 1));
        }

        int cascadeCount = 0;

        // Cascade: mark ALL previous PENDING tasks as done too
        // (player clearly has the end product, dependencies were satisfied)
        for (int i = 0; i <= index; i++) {
            Task t = tasks.get(i);
            if (t.status() == TaskStatus.PENDING || t.status() == TaskStatus.IN_PROGRESS) {
                Task updated = new Task(t.id(), t.description(), t.type(),
                        TaskStatus.DONE, t.requiredItems(), t.note());
                tasks.set(i, updated);
                cascadeCount++;
            }
            // Do NOT touch BLOCKED tasks — those were explicitly blocked
        }

        p.setTasks(tasks);
        pm.saveProject(p);
        return new ToolResult(NAME,
                "Tasks 1-" + (index + 1) + " marked done (" + cascadeCount + " completed). "
                + "Next: " + (index + 2 > tasks.size() ? "all done!" : tasks.get(index + 1).description()));
    }

    private ToolResult markBlocked(ProjectManager pm, JsonObject args) {
        return updateTaskStatus(pm, args, TaskStatus.BLOCKED, "blocked");
    }

    private ToolResult updateTaskStatus(ProjectManager pm, JsonObject args,
                                         TaskStatus newStatus, String label) {
        Project p = pm.getActiveProject();
        if (p == null) {
            return new ToolResult(NAME, "No active project");
        }
        int index = args.get("task_index").getAsInt() - 1;
        List<Task> tasks = new ArrayList<>(p.getTasks());
        if (index < 0 || index >= tasks.size()) {
            return new ToolResult(NAME, "Invalid task index: " + (index + 1));
        }
        Task old = tasks.get(index);
        Task updated = new Task(old.id(), old.description(), old.type(),
                newStatus, old.requiredItems(), old.note());
        tasks.set(index, updated);
        p.setTasks(tasks);
        pm.saveProject(p);
        return new ToolResult(NAME,
                "Task " + (index + 1) + " marked " + label + ": " + old.description());
    }

    private ToolResult cancelProject(ProjectManager pm) {
        Project p = pm.getActiveProject();
        if (p == null) {
            return new ToolResult(NAME, "No active project");
        }
        String name = p.getName();
        pm.archiveCurrentProject();
        return new ToolResult(NAME, "Project '" + name + "' cancelled and archived");
    }
}
