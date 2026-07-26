package com.lumoren.agentchat.tools;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.lumoren.agentchat.ai.GameDataAccess;
import com.lumoren.agentchat.i18n.I18nHelper;
import com.lumoren.agentchat.i18n.I18nKeys;
import com.lumoren.agentchat.model.ActionCard;
import com.lumoren.agentchat.model.AssistantAction;
import com.lumoren.agentchat.model.InventoryItem;
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
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Tool that gives the AI full automatic control over the active project.
 */
public class ProjectTool implements GameTool {

    private static final String NAME = "manage_project";
    private static final String DESCRIPTION =
            "Manage the active project: create, inspect, update task progress, edit tasks, cancel, or calculate missing materials.";

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
        JsonObject action = new JsonObject();
        action.addProperty("type", "string");
        action.addProperty("description",
                "Action to run: create_project, get_status, mark_done, mark_blocked, "
                        + "mark_in_progress, set_current_task, add_task, remove_task, rename_project, "
                        + "get_missing_materials, cancel");
        JsonArray enumValues = new JsonArray();
        for (String value : List.of("create_project", "get_status", "mark_done", "mark_blocked",
                "mark_in_progress", "set_current_task", "add_task", "remove_task", "rename_project",
                "get_missing_materials", "cancel")) {
            enumValues.add(value);
        }
        action.add("enum", enumValues);
        properties.add("action", action);

        addStringParam(properties, "project_name", "Project name for create_project or rename_project.");
        addStringParam(properties, "description", "Task description for add_task.");
        addStringParam(properties, "type", "Task type for add_task: CRAFT, GATHER, GO_TO, USE, KILL, PLAN.");
        addStringParam(properties, "note", "Optional task note for add_task.");

        JsonObject tasksParam = new JsonObject();
        tasksParam.addProperty("type", "array");
        tasksParam.addProperty("description",
                "Task list for create_project. Each task: {description, type, items:[{itemId,count}]}.");
        properties.add("tasks", tasksParam);

        JsonObject itemsParam = new JsonObject();
        itemsParam.addProperty("type", "array");
        itemsParam.addProperty("description",
                "Required items for add_task. Each item: {itemId, count}.");
        properties.add("items", itemsParam);

        JsonObject taskIndex = new JsonObject();
        taskIndex.addProperty("type", "integer");
        taskIndex.addProperty("description", "1-based task number for task update actions.");
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
                return error("No active project manager. Cannot verify or modify project state.");
            }

            return switch (action) {
                case "create_project" -> createProject(pm, args);
                case "get_status" -> getStatus(pm);
                case "mark_done" -> updateTaskStatus(pm, args, TaskStatus.DONE, true);
                case "mark_blocked" -> updateTaskStatus(pm, args, TaskStatus.BLOCKED, false);
                case "mark_in_progress", "set_current_task" -> setCurrentTask(pm, args);
                case "add_task" -> addTask(pm, args);
                case "remove_task" -> removeTask(pm, args);
                case "rename_project" -> renameProject(pm, args);
                case "get_missing_materials" -> getMissingMaterials(pm, mc);
                case "cancel" -> cancelProject(pm);
                default -> error("Unknown project action: " + action);
            };
        } catch (Exception e) {
            return error("Cannot verify project action because arguments were invalid: " + e.getMessage());
        }
    }

    private ToolResult createProject(ProjectManager pm, JsonObject args) {
        String name = stringArg(args, "project_name", "Project");
        JsonArray tasksJson = args.getAsJsonArray("tasks");
        if (tasksJson == null || tasksJson.isEmpty()) {
            return error("Cannot create project: no verified tasks were provided.");
        }

        List<Task> tasks = new ArrayList<>();
        for (var element : tasksJson) {
            if (!element.isJsonObject()) {
                continue;
            }
            JsonObject taskJson = element.getAsJsonObject();
            String desc = stringArg(taskJson, "description", "");
            if (desc.isBlank()) {
                continue;
            }
            TaskType type = parseTaskType(stringArg(taskJson, "type", "PLAN"));
            List<ItemRequirement> items = parseItems(taskJson.getAsJsonArray("items"));
            tasks.add(new Task(null, desc, type, TaskStatus.PENDING, items, null));
        }
        if (tasks.isEmpty()) {
            return error("Cannot create project: no valid task descriptions were provided.");
        }

        Project project = new Project(name);
        project.setTasks(tasks);
        pm.saveProject(project);
        return projectCard(project, "Project created", "First step: " + tasks.get(0).description());
    }

    private ToolResult getStatus(ProjectManager pm) {
        Project project = pm.getActiveProject();
        if (project == null) {
            return error("No active project. Cannot verify next step.");
        }
        return projectCard(project, "Project status", nextStepSummary(project));
    }

    private ToolResult updateTaskStatus(ProjectManager pm, JsonObject args,
                                        TaskStatus status, boolean cascadeDone) {
        Project project = requireProject(pm);
        if (project == null) {
            return error("No active project. Cannot update task status.");
        }
        int index = taskIndex(args);
        List<Task> tasks = new ArrayList<>(project.getTasks());
        if (!isValidIndex(index, tasks)) {
            return error("Cannot update task: task index " + (index + 1) + " is not valid.");
        }

        int changed = 0;
        if (cascadeDone && status == TaskStatus.DONE) {
            for (int i = 0; i <= index; i++) {
                Task task = tasks.get(i);
                if (task.status() == TaskStatus.PENDING || task.status() == TaskStatus.IN_PROGRESS) {
                    tasks.set(i, withStatus(task, TaskStatus.DONE));
                    changed++;
                }
            }
        } else {
            tasks.set(index, withStatus(tasks.get(index), status));
            changed = 1;
        }
        project.setTasks(tasks);
        pm.saveProject(project);
        return projectCard(project, "Task updated",
                changed + " task(s) changed. " + nextStepSummary(project));
    }

    private ToolResult setCurrentTask(ProjectManager pm, JsonObject args) {
        Project project = requireProject(pm);
        if (project == null) {
            return error("No active project. Cannot set current task.");
        }
        int index = taskIndex(args);
        List<Task> tasks = new ArrayList<>(project.getTasks());
        if (!isValidIndex(index, tasks)) {
            return error("Cannot set current task: task index " + (index + 1) + " is not valid.");
        }

        for (int i = 0; i < tasks.size(); i++) {
            Task task = tasks.get(i);
            if (i == index) {
                tasks.set(i, withStatus(task, TaskStatus.IN_PROGRESS));
            } else if (task.status() == TaskStatus.IN_PROGRESS) {
                tasks.set(i, withStatus(task, TaskStatus.PENDING));
            }
        }
        project.setTasks(tasks);
        pm.saveProject(project);
        return projectCard(project, "Current task set", "Now: " + tasks.get(index).description());
    }

    private ToolResult addTask(ProjectManager pm, JsonObject args) {
        Project project = requireProject(pm);
        if (project == null) {
            return error("No active project. Cannot add task.");
        }
        String desc = stringArg(args, "description", "");
        if (desc.isBlank()) {
            return error("Cannot add task: description is missing.");
        }
        TaskType type = parseTaskType(stringArg(args, "type", "PLAN"));
        List<ItemRequirement> items = parseItems(args.getAsJsonArray("items"));
        String note = stringArg(args, "note", null);
        project.addTask(new Task(null, desc, type, TaskStatus.PENDING, items, note));
        pm.saveProject(project);
        return projectCard(project, "Task added", desc);
    }

    private ToolResult removeTask(ProjectManager pm, JsonObject args) {
        Project project = requireProject(pm);
        if (project == null) {
            return error("No active project. Cannot remove task.");
        }
        int index = taskIndex(args);
        List<Task> tasks = new ArrayList<>(project.getTasks());
        if (!isValidIndex(index, tasks)) {
            return error("Cannot remove task: task index " + (index + 1) + " is not valid.");
        }
        String removed = tasks.remove(index).description();
        project.setTasks(tasks);
        pm.saveProject(project);
        return projectCard(project, "Task removed", removed);
    }

    private ToolResult renameProject(ProjectManager pm, JsonObject args) {
        Project project = requireProject(pm);
        if (project == null) {
            return error("No active project. Cannot rename project.");
        }
        String name = stringArg(args, "project_name", "");
        if (name.isBlank()) {
            return error("Cannot rename project: project_name is missing.");
        }
        project.setName(name);
        pm.saveProject(project);
        return projectCard(project, "Project renamed", name);
    }

    private ToolResult getMissingMaterials(ProjectManager pm, Minecraft mc) {
        Project project = pm.getActiveProject();
        if (project == null) {
            return error("No active project. Cannot verify missing materials.");
        }

        Map<String, Integer> needed = new HashMap<>();
        for (Task task : project.getTasks()) {
            if (task.status() == TaskStatus.DONE || task.requiredItems() == null) {
                continue;
            }
            for (ItemRequirement req : task.requiredItems()) {
                needed.merge(req.itemId(), req.needed(), Integer::sum);
            }
        }
        if (needed.isEmpty()) {
            return cardResult(new ActionCard(ActionCard.Type.MATERIALS,
                    "Missing materials", "No material requirements are tracked for open tasks.",
                    List.of("Project: " + project.getName()), List.of(), NAME));
        }

        Map<String, Integer> inventory = inventoryMap(mc);
        List<String> facts = new ArrayList<>();
        int missingKinds = 0;
        for (var entry : needed.entrySet()) {
            int have = inventory.getOrDefault(entry.getKey(), 0);
            int missing = Math.max(0, entry.getValue() - have);
            if (missing > 0) {
                missingKinds++;
            }
            facts.add(entry.getKey() + ": need " + entry.getValue() + ", have " + have + ", missing " + missing);
        }

        return cardResult(new ActionCard(ActionCard.Type.MATERIALS,
                "Missing materials",
                missingKinds == 0 ? "All tracked materials are currently available."
                        : missingKinds + " tracked material type(s) are missing.",
                facts,
                List.of(new AssistantAction(null, I18nHelper.translateToString(I18nKeys.ACTION_LABEL_SHOW_PROJECT), AssistantAction.Kind.PIN_TASK, new JsonObject())),
                NAME));
    }

    private ToolResult cancelProject(ProjectManager pm) {
        Project project = pm.getActiveProject();
        if (project == null) {
            return error("No active project. Nothing to cancel.");
        }
        String name = project.getName();
        pm.archiveCurrentProject();
        return cardResult(new ActionCard(ActionCard.Type.PROJECT,
                "Project archived", "Cancelled and archived: " + name,
                List.of("No active project after cancellation."),
                List.of(), NAME));
    }

    private ToolResult projectCard(Project project, String title, String summary) {
        List<Task> tasks = project.getTasks();
        long done = tasks.stream().filter(t -> t.status() == TaskStatus.DONE).count();
        List<String> facts = new ArrayList<>();
        facts.add("Project: " + project.getName());
        facts.add("Progress: " + done + "/" + tasks.size());
        for (int i = 0; i < tasks.size(); i++) {
            Task task = tasks.get(i);
            facts.add((i + 1) + ". [" + task.status() + "] " + task.description());
        }

        List<AssistantAction> actions = new ArrayList<>();
        int next = nextTaskIndex(project);
        if (next >= 0) {
            JsonObject payload = new JsonObject();
            payload.addProperty("task_index", next + 1);
            actions.add(new AssistantAction(null, I18nHelper.translateToString(I18nKeys.ACTION_LABEL_MARK_DONE),
                    AssistantAction.Kind.MARK_TASK_DONE, payload));
            actions.add(new AssistantAction(null, I18nHelper.translateToString(I18nKeys.ACTION_LABEL_SET_CURRENT),
                    AssistantAction.Kind.PIN_TASK, payload));
        }

        return cardResult(new ActionCard(ActionCard.Type.PROJECT, title, summary, facts, actions, NAME));
    }

    private ToolResult error(String message) {
        return cardResult(new ActionCard(ActionCard.Type.STATUS,
                "Unable to verify", message, List.of(message), List.of(), NAME));
    }

    private ToolResult cardResult(ActionCard card) {
        return new ToolResult(NAME, card.toToolResultContent());
    }

    private static void addStringParam(JsonObject properties, String name, String description) {
        JsonObject param = new JsonObject();
        param.addProperty("type", "string");
        param.addProperty("description", description);
        properties.add(name, param);
    }

    private static Project requireProject(ProjectManager pm) {
        return pm.getActiveProject();
    }

    private static Task withStatus(Task task, TaskStatus status) {
        return new Task(task.id(), task.description(), task.type(), status,
                task.requiredItems(), task.note());
    }

    private static int taskIndex(JsonObject args) {
        return args.get("task_index").getAsInt() - 1;
    }

    private static boolean isValidIndex(int index, List<Task> tasks) {
        return index >= 0 && index < tasks.size();
    }

    private static TaskType parseTaskType(String value) {
        try {
            return TaskType.valueOf(value.toUpperCase(Locale.ROOT));
        } catch (RuntimeException e) {
            return TaskType.PLAN;
        }
    }

    private static List<ItemRequirement> parseItems(JsonArray itemsJson) {
        if (itemsJson == null || itemsJson.isEmpty()) {
            return List.of();
        }
        List<ItemRequirement> items = new ArrayList<>();
        for (var element : itemsJson) {
            if (!element.isJsonObject()) {
                continue;
            }
            JsonObject item = element.getAsJsonObject();
            String itemId = stringArg(item, "itemId", "");
            if (itemId.isBlank()) {
                itemId = stringArg(item, "item_id", "");
            }
            if (itemId.isBlank()) {
                continue;
            }
            int count = item.has("count") && !item.get("count").isJsonNull()
                    ? item.get("count").getAsInt() : 1;
            items.add(new ItemRequirement(itemId, Math.max(1, count), 0));
        }
        return items;
    }

    private static String stringArg(JsonObject obj, String name, String defaultValue) {
        if (!obj.has(name) || obj.get(name).isJsonNull()) {
            return defaultValue;
        }
        return obj.get(name).getAsString();
    }

    private static int nextTaskIndex(Project project) {
        List<Task> tasks = project.getTasks();
        for (int i = 0; i < tasks.size(); i++) {
            TaskStatus status = tasks.get(i).status();
            if (status == TaskStatus.IN_PROGRESS || status == TaskStatus.PENDING || status == TaskStatus.BLOCKED) {
                return i;
            }
        }
        return -1;
    }

    private static String nextStepSummary(Project project) {
        int index = nextTaskIndex(project);
        if (index < 0) {
            return "All tasks are done.";
        }
        return "Next: " + project.getTasks().get(index).description();
    }

    private static Map<String, Integer> inventoryMap(Minecraft mc) {
        Map<String, Integer> inventory = new HashMap<>();
        if (mc == null || mc.player == null) {
            return inventory;
        }
        for (InventoryItem item : GameDataAccess.getInventorySnapshot(mc)) {
            inventory.merge(item.itemId(), item.count(), Integer::sum);
        }
        return inventory;
    }
}
