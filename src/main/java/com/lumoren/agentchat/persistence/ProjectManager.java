package com.lumoren.agentchat.persistence;

import com.google.gson.*;
import com.google.gson.reflect.TypeToken;
import com.lumoren.agentchat.model.ItemRequirement;
import com.lumoren.agentchat.model.Project;
import com.lumoren.agentchat.model.ProjectStatus;
import com.lumoren.agentchat.model.Task;

import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 项目持久化管理器 —— per-world 单例，负责项目数据的保存、加载、归档与重激活。
 * <p>
 * 每个 Minecraft 存档拥有独立的项目目录：
 * {@code <worldSaveDir>/agentchat-projects/projects.json}。
 * 遵循 {@link ConversationManager} 的单例生命周期模式 ——
 * {@link #initialize(Path)} 绑定存档目录，{@link #shutdown()} 保存并清理。
 * <p>
 * JSON 损坏时自动恢复：捕获 {@link JsonSyntaxException} / {@link JsonIOException}，
 * 返回空状态（无活跃项目、空归档列表），不影响游戏运行。
 * <p>
 * 归档上限：保持最近 {@value #MAX_ARCHIVED} 个项目，超量时自动删除最旧条目。
 */
public class ProjectManager {

    private static final String DIR_NAME = "agentchat-projects";
    private static final String FILE_NAME = "projects.json";

    /** 归档项目最大保留数量 */
    static final int MAX_ARCHIVED = 10;

    private static ProjectManager instance;

    private final Path saveDir;
    private final Path saveFile;
    private Project activeProject;
    private final List<Project> archivedProjects;

    // ==================== 静态单例生命周期 ====================

    /**
     * 初始化全局单例，绑定到指定存档目录。
     * <p>
     * 从已有 JSON 文件加载项目数据（若存在）；若文件不存在或损坏，则起始为空状态。
     *
     * @param worldSaveDir 当前世界的存档根目录
     */
    public static synchronized void initialize(Path worldSaveDir) {
        instance = new ProjectManager(worldSaveDir);
    }

    /**
     * 关闭当前管理器：保存活跃项目数据，清空单例引用。
     * <p>
     * 即使未调用 {@link #initialize(Path)} 或管理器已关闭，调用本方法也不会抛出异常。
     */
    public static synchronized void shutdown() {
        if (instance != null) {
            instance.save();
            instance = null;
        }
    }

    /**
     * 获取当前全局单例。
     *
     * @return 当前 ProjectManager，若尚未初始化则返回 {@code null}
     */
    public static synchronized ProjectManager getInstance() {
        return instance;
    }

    // ==================== 构造与内部状态初始化 ====================

    private ProjectManager(Path worldSaveDir) {
        this.saveDir = worldSaveDir.resolve(DIR_NAME);
        this.saveFile = saveDir.resolve(FILE_NAME);
        this.archivedProjects = new ArrayList<>();
        load();
    }

    // ==================== 公开 API ====================

    /**
     * 获取当前活跃项目。
     *
     * @return 活跃项目，若无则返回 {@code null}
     */
    public synchronized Project getActiveProject() {
        return activeProject;
    }

    /**
     * 保存（创建或更新）当前活跃项目。
     * <p>
     * 若已有活跃项目则覆盖；新项目状态为 {@link ProjectStatus#IN_PROGRESS}。
     *
     * @param project 要设为活跃的项目
     */
    public synchronized void saveProject(Project project) {
        if (project.getStatus() != ProjectStatus.IN_PROGRESS) {
            project.setStatus(ProjectStatus.IN_PROGRESS);
        }
        // 若已有活跃项目，先归档
        if (activeProject != null && !activeProject.getId().equals(project.getId())) {
            archiveExistingProject();
        }
        this.activeProject = project;
        save();
    }

    /**
     * 归档当前活跃项目：将其状态设为 {@link ProjectStatus#ARCHIVED}，
     * 移入归档列表，并清空活跃项目引用。
     * <p>
     * 若归档列表超过 {@value #MAX_ARCHIVED} 条，则自动删除最旧条目。
     */
    public synchronized void archiveCurrentProject() {
        if (activeProject == null) {
            return;
        }
        archiveExistingProject();
        save();
    }

    /**
     * 获取所有已归档项目的不可变列表副本（按归档顺序排列，旧的在前）。
     *
     * @return 归档项目列表
     */
    public synchronized List<Project> getArchivedProjects() {
        return Collections.unmodifiableList(new ArrayList<>(archivedProjects));
    }

    /**
     * 重激活指定 ID 的归档项目：从归档列表中移除，设为当前活跃项目。
     * <p>
     * 若当前已有活跃项目，先将其归档。
     *
     * @param projectId 要重激活的归档项目 ID
     * @return 重激活后的项目，若未找到则返回 {@code null}
     */
    public synchronized Project reactivateProject(String projectId) {
        for (int i = 0; i < archivedProjects.size(); i++) {
            Project p = archivedProjects.get(i);
            if (p.getId().equals(projectId)) {
                // 若已有活跃项目，先归档
                if (activeProject != null) {
                    archiveExistingProject();
                }
                archivedProjects.remove(i);
                p.setStatus(ProjectStatus.IN_PROGRESS);
                activeProject = p;
                save();
                return p;
            }
        }
        return null;
    }

    // ==================== 内部持久化 ====================

    /**
     * 将当前完整状态（活跃项目 + 归档列表）序列化到 JSON 文件。
     */
    private synchronized void save() {
        try {
            Files.createDirectories(saveDir);
            Gson gson = createGson();
            ProjectManagerData data = new ProjectManagerData(activeProject, new ArrayList<>(archivedProjects));
            String json = gson.toJson(data);
            Files.writeString(saveFile, json);
        } catch (IOException e) {
            System.err.println("[AgentChat] Failed to save projects: " + e.getMessage());
        }
    }

    /**
     * 从 JSON 文件加载项目数据。若文件不存在则保留空状态。
     * 若 JSON 损坏，打印警告并返回空状态（不崩溃）。
     */
    private void load() {
        if (!Files.exists(saveFile)) {
            return;
        }
        try {
            String json = Files.readString(saveFile);
            Gson gson = createGson();
            ProjectManagerData data = gson.fromJson(json, ProjectManagerData.class);
            if (data == null) {
                return;
            }
            // 过滤掉反序列化失败的 null 项目
            if (data.activeProject != null) {
                this.activeProject = data.activeProject;
            }
            if (data.archivedProjects != null) {
                for (Project p : data.archivedProjects) {
                    if (p != null) {
                        this.archivedProjects.add(p);
                    }
                }
            }
            // 确保不会超出上限
            while (archivedProjects.size() > MAX_ARCHIVED) {
                archivedProjects.remove(0);
            }
        } catch (IOException e) {
            System.err.println("[AgentChat] Failed to load projects: " + e.getMessage());
        } catch (JsonSyntaxException | JsonIOException e) {
            System.err.println("[AgentChat] Corrupt projects.json — starting fresh: " + e.getMessage());
        }
    }

    /**
     * 将当前活跃项目标记为已归档并移入归档列表（不触发 save）。
     * 同时检查归档上限并裁剪。
     */
    private void archiveExistingProject() {
        if (activeProject == null) {
            return;
        }
        activeProject.setStatus(ProjectStatus.ARCHIVED);
        archivedProjects.add(activeProject);
        activeProject = null;
        while (archivedProjects.size() > MAX_ARCHIVED) {
            archivedProjects.remove(0);
        }
    }

    // ==================== Gson 工厂 ====================

    private static Gson createGson() {
        return new GsonBuilder()
                .setPrettyPrinting()
                .registerTypeAdapter(Project.class, new ProjectAdapter())
                .registerTypeAdapter(Instant.class, new InstantAdapter())
                .create();
    }

    // ==================== 内部数据结构与适配器 ====================

    /**
     * JSON 顶层结构：包含活跃项目与归档项目列表。
     */
    @SuppressWarnings("unused")
    private static class ProjectManagerData {
        Project activeProject;
        List<Project> archivedProjects;

        ProjectManagerData(Project activeProject, List<Project> archivedProjects) {
            this.activeProject = activeProject;
            this.archivedProjects = archivedProjects;
        }
    }

    /**
     * Gson 类型适配器：处理 {@link Instant} 的序列化 / 反序列化。
     * <p>
     * Gson 2.10.1 不内置支持 {@code java.time.Instant}，需手动注册。
     */
    private static class InstantAdapter implements JsonSerializer<Instant>, JsonDeserializer<Instant> {
        @Override
        public JsonElement serialize(Instant src, Type typeOfSrc, JsonSerializationContext context) {
            return new JsonPrimitive(src.toString());
        }

        @Override
        public Instant deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context)
                throws JsonParseException {
            return Instant.parse(json.getAsString());
        }
    }

    /**
     * Gson 自定义适配器：处理 {@link Project} 的序列化 / 反序列化。
     * <p>
     * 序列化：逐字段写入，tasks 委托给 Gson 上下文处理（支持 Task 记录的默认序列化）。
     * <p>
     * 反序列化：对缺失或损坏的字段使用安全默认值，损坏的条目返回 {@code null} 由上层过滤。
     */
    private static class ProjectAdapter implements JsonSerializer<Project>, JsonDeserializer<Project> {

        @Override
        public JsonElement serialize(Project src, Type typeOfSrc, JsonSerializationContext context) {
            JsonObject obj = new JsonObject();
            obj.addProperty("id", src.getId());
            obj.addProperty("name", src.getName());
            obj.add("tasks", context.serialize(src.getTasks()));
            obj.addProperty("status", src.getStatus().name());
            obj.addProperty("createdAt", src.getCreatedAt().toString());
            obj.addProperty("updatedAt", src.getUpdatedAt().toString());
            return obj;
        }

        @Override
        public Project deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context)
                throws JsonParseException {
            try {
                JsonObject obj = json.getAsJsonObject();

                // ---- 必填字段验证 ----
                if (!obj.has("id") || obj.get("id").isJsonNull()) {
                    System.err.println("[AgentChat] Skipping corrupt project: missing 'id' field");
                    return null;
                }
                String id = obj.get("id").getAsString();

                // ---- 名称（可选，默认值） ----
                String name = "Unnamed Project";
                if (obj.has("name") && !obj.get("name").isJsonNull()) {
                    name = obj.get("name").getAsString();
                }

                // ---- 任务列表 ----
                List<Task> tasks = new ArrayList<>();
                if (obj.has("tasks") && !obj.get("tasks").isJsonNull()) {
                    Type listType = new TypeToken<List<Task>>() {}.getType();
                    List<Task> parsed = context.deserialize(obj.get("tasks"), listType);
                    if (parsed != null) {
                        for (Task t : parsed) {
                            if (t != null) {
                                tasks.add(t);
                            }
                        }
                    }
                }

                // ---- 状态 ----
                ProjectStatus status = ProjectStatus.CREATED;
                if (obj.has("status") && !obj.get("status").isJsonNull()) {
                    try {
                        status = ProjectStatus.valueOf(obj.get("status").getAsString());
                    } catch (IllegalArgumentException e) {
                        System.err.println("[AgentChat] Invalid project status for " + id
                                + " — using CREATED");
                    }
                }

                // ---- 时间戳 ----
                Instant createdAt = Instant.now();
                Instant updatedAt = Instant.now();
                if (obj.has("createdAt") && !obj.get("createdAt").isJsonNull()) {
                    try {
                        createdAt = Instant.parse(obj.get("createdAt").getAsString());
                    } catch (Exception e) {
                        System.err.println("[AgentChat] Invalid 'createdAt' for project " + id
                                + " — using current time");
                    }
                }
                if (obj.has("updatedAt") && !obj.get("updatedAt").isJsonNull()) {
                    try {
                        updatedAt = Instant.parse(obj.get("updatedAt").getAsString());
                    } catch (Exception e) {
                        System.err.println("[AgentChat] Invalid 'updatedAt' for project " + id
                                + " — using current time");
                    }
                }

                return new Project(id, name, tasks, status, createdAt, updatedAt);
            } catch (RuntimeException e) {
                System.err.println("[AgentChat] Skipping corrupt project: " + e.getMessage());
                return null;
            }
        }
    }
}
