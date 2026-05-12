package com.lumoren.agentchat.tools;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.lumoren.agentchat.model.ItemRequirement;
import com.lumoren.agentchat.model.Project;
import com.lumoren.agentchat.model.Task;
import com.lumoren.agentchat.model.TaskStatus;
import com.lumoren.agentchat.model.TaskType;
import com.lumoren.agentchat.model.ToolDefinition;
import com.lumoren.agentchat.model.ToolResult;
import com.lumoren.agentchat.persistence.ProjectManager;
import net.minecraft.client.Minecraft;

import java.util.ArrayList;
import java.util.List;

/**
 * Tool that gives the AI full CRUD control over the project system.
 * <p>
 * Supports five actions via the required "action" parameter:
 * <ul>
 *   <li>{@code create_project} — create a new project with tasks (requires {@code project_name}, {@code tasks})</li>
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
                "Action: 'create_project' to create a project with tasks, 'get_status' to see progress, "
                        + "'mark_done' to complete a task, 'mark_blocked' to block a task, "
                        + "'cancel' to archive the project");
        JsonArray enumValues = new JsonArray();
        enumValues.add("create_project");
        enumValues.add("get_status");
        enumValues.add("mark_done");
        enumValues.add("mark_blocked");
        enumValues.add("cancel");
        action.add("enum", enumValues);
        properties.add("action", action);

        // project_name: for create_project
        JsonObject projectName = new JsonObject();
        projectName.addProperty("type", "string");
        projectName.addProperty("description",
                "Project name. Required for 'create_project'");
        properties.add("project_name", projectName);

        // tasks: JSON array of {description, type, items:[{itemId, count}]} for create_project
        JsonObject tasksParam = new JsonObject();
        tasksParam.addProperty("type", "array");
        tasksParam.addProperty("description",
                "Task list as JSON array. Each task: {description: string, type: CRAFT|GATHER|GO_TO|USE|KILL|PLAN, items: [{itemId: string, count: int}]}. Required for 'create_project'");
        properties.add("tasks", tasksParam);

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
                case "create_project" -> createProject(pm, args);
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

    private ToolResult createProject(ProjectManager pm, JsonObject args) {
        String name = args.has("project_name") ? args.get("project_name").getAsString() : "Project";
        JsonArray tasksJson = args.getAsJsonArray("tasks");
        if (tasksJson == null || tasksJson.isEmpty()) {
            return new ToolResult(NAME, "No tasks provided");
        }

        List<Task> tasks = new ArrayList<>();
        for (int i = 0; i < tasksJson.size(); i++) {
            JsonObject t = tasksJson.get(i).getAsJsonObject();
            String desc = t.get("description").getAsString();
            String typeStr = t.has("type") ? t.get("type").getAsString() : "PLAN";
            TaskType type;
            try { type = TaskType.valueOf(typeStr.toUpperCase()); }
            catch (IllegalArgumentException e) { type = TaskType.PLAN; }

            List<ItemRequirement> items = new ArrayList<>();
            if (t.has("items") && !t.get("items").isJsonNull()) {
                JsonArray itemsJson = t.getAsJsonArray("items");
                for (int j = 0; j < itemsJson.size(); j++) {
                    JsonObject item = itemsJson.get(j).getAsJsonObject();
                    String itemId = item.get("itemId").getAsString();
                    int count = item.has("count") ? item.get("count").getAsInt() : 1;
                    items.add(new ItemRequirement(itemId, count, 0));
                }
            }
            tasks.add(new Task(null, desc, type, TaskStatus.PENDING, items, null));
        }

        Project project = new Project(name);
        project.setTasks(tasks);
        pm.saveProject(project);

        return new ToolResult(NAME,
                "Project '" + name + "' created with " + tasks.size() + " steps. "
                + "First step: " + tasks.get(0).description());
    }

    // ==================== Existing Action Implementations ====================

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
