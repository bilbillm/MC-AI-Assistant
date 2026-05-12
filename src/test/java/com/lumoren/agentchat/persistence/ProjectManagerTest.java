package com.lumoren.agentchat.persistence;

import com.lumoren.agentchat.model.ItemRequirement;
import com.lumoren.agentchat.model.Project;
import com.lumoren.agentchat.model.ProjectStatus;
import com.lumoren.agentchat.model.Task;
import com.lumoren.agentchat.model.TaskStatus;
import com.lumoren.agentchat.model.TaskType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * ProjectManager 单元测试。
 * <p>
 * 验证项目持久化、归档、重激活、JSON 损坏恢复、归档上限裁剪等核心行为。
 * 所有测试使用临时目录，不依赖 Minecraft 运行时。
 */
class ProjectManagerTest {

    @TempDir
    Path tempDir;

    @AfterEach
    void tearDown() {
        ProjectManager.shutdown();
    }

    // ==================== 基本保存/加载 ====================

    @Test
    void saveAndLoadRoundTrip() {
        Path worldDir = tempDir.resolve("world1");
        ProjectManager.initialize(worldDir);
        ProjectManager mgr = ProjectManager.getInstance();
        assertNotNull(mgr);

        Project project = new Project("test-id", "Test Project",
                List.of(Task.of("Mine stone", TaskType.GATHER,
                        List.of(new ItemRequirement("minecraft:stone", 64, 0)))),
                ProjectStatus.IN_PROGRESS,
                java.time.Instant.parse("2026-05-01T10:00:00Z"),
                java.time.Instant.parse("2026-05-01T12:00:00Z"));

        mgr.saveProject(project);
        assertEquals("test-id", mgr.getActiveProject().getId());
        assertEquals("Test Project", mgr.getActiveProject().getName());
        assertEquals(ProjectStatus.IN_PROGRESS, mgr.getActiveProject().getStatus());

        // Simulate shutdown + reinitialize
        ProjectManager.shutdown();
        ProjectManager.initialize(worldDir);

        mgr = ProjectManager.getInstance();
        assertNotNull(mgr);
        Project loaded = mgr.getActiveProject();
        assertNotNull(loaded, "Re-initialized manager should have active project");
        assertEquals("test-id", loaded.getId());
        assertEquals("Test Project", loaded.getName());
        assertEquals(1, loaded.getTasks().size());
        assertEquals("Mine stone", loaded.getTasks().get(0).description());
        assertEquals(ProjectStatus.IN_PROGRESS, loaded.getStatus());
        assertEquals(java.time.Instant.parse("2026-05-01T10:00:00Z"), loaded.getCreatedAt());
        assertEquals(java.time.Instant.parse("2026-05-01T12:00:00Z"), loaded.getUpdatedAt());
    }

    @Test
    void initializeWithNoFileReturnsEmptyState() {
        Path worldDir = tempDir.resolve("empty_world");
        ProjectManager.initialize(worldDir);
        ProjectManager mgr = ProjectManager.getInstance();

        assertNotNull(mgr);
        assertNull(mgr.getActiveProject());
        assertTrue(mgr.getArchivedProjects().isEmpty());
    }

    @Test
    void shutdownSavesAndClearsInstance() {
        Path worldDir = tempDir.resolve("world_shutdown");
        ProjectManager.initialize(worldDir);
        ProjectManager mgr = ProjectManager.getInstance();

        Project project = new Project("shutdown-test", "Shutdown Project",
                List.of(), ProjectStatus.IN_PROGRESS,
                java.time.Instant.now(), java.time.Instant.now());
        mgr.saveProject(project);

        ProjectManager.shutdown();
        assertNull(ProjectManager.getInstance());

        // Reinitialize and verify data persisted
        ProjectManager.initialize(worldDir);
        mgr = ProjectManager.getInstance();
        assertNotNull(mgr);
        assertNotNull(mgr.getActiveProject());
        assertEquals("shutdown-test", mgr.getActiveProject().getId());
    }

    // ==================== 归档 ====================

    @Test
    void archiveCurrentProjectMovesToArchive() {
        Path worldDir = tempDir.resolve("world_archive");
        ProjectManager.initialize(worldDir);
        ProjectManager mgr = ProjectManager.getInstance();

        Project project = new Project("archive-me", "Archive Project",
                List.of(), ProjectStatus.IN_PROGRESS,
                java.time.Instant.now(), java.time.Instant.now());
        mgr.saveProject(project);
        assertNotNull(mgr.getActiveProject());

        mgr.archiveCurrentProject();
        assertNull(mgr.getActiveProject());
        List<Project> archived = mgr.getArchivedProjects();
        assertEquals(1, archived.size());
        assertEquals("archive-me", archived.get(0).getId());
        assertEquals(ProjectStatus.ARCHIVED, archived.get(0).getStatus());
    }

    @Test
    void archiveCurrentProjectWithNoActiveIsNoop() {
        Path worldDir = tempDir.resolve("world_noop_archive");
        ProjectManager.initialize(worldDir);
        ProjectManager mgr = ProjectManager.getInstance();

        assertNull(mgr.getActiveProject());
        mgr.archiveCurrentProject(); // should not throw
        assertNull(mgr.getActiveProject());
        assertTrue(mgr.getArchivedProjects().isEmpty());
    }

    // ==================== 重激活 ====================

    @Test
    void reactivateProjectRestoresFromArchive() {
        Path worldDir = tempDir.resolve("world_reactivate");
        ProjectManager.initialize(worldDir);
        ProjectManager mgr = ProjectManager.getInstance();

        Project project = new Project("reactivate-me", "Reactivate Project",
                List.of(), ProjectStatus.IN_PROGRESS,
                java.time.Instant.now(), java.time.Instant.now());
        mgr.saveProject(project);
        String projectId = project.getId();
        mgr.archiveCurrentProject();

        assertNull(mgr.getActiveProject());
        assertEquals(1, mgr.getArchivedProjects().size());

        Project restored = mgr.reactivateProject(projectId);
        assertNotNull(restored);
        assertEquals(projectId, restored.getId());
        assertEquals(ProjectStatus.IN_PROGRESS, restored.getStatus());
        assertEquals(restored, mgr.getActiveProject());
        assertTrue(mgr.getArchivedProjects().isEmpty());
    }

    @Test
    void reactivateNonExistentProjectReturnsNull() {
        Path worldDir = tempDir.resolve("world_reactivate_miss");
        ProjectManager.initialize(worldDir);
        ProjectManager mgr = ProjectManager.getInstance();

        Project result = mgr.reactivateProject("nonexistent-id");
        assertNull(result);
    }

    @Test
    void reactivateArchivesExistingActiveProject() {
        Path worldDir = tempDir.resolve("world_reactivate_swap");
        ProjectManager.initialize(worldDir);
        ProjectManager mgr = ProjectManager.getInstance();

        // Create and archive two projects
        Project p1 = new Project("p1", "Project 1", List.of(),
                ProjectStatus.IN_PROGRESS, java.time.Instant.now(), java.time.Instant.now());
        Project p2 = new Project("p2", "Project 2", List.of(),
                ProjectStatus.IN_PROGRESS, java.time.Instant.now(), java.time.Instant.now());

        mgr.saveProject(p1);
        String p1Id = p1.getId();
        mgr.archiveCurrentProject();

        mgr.saveProject(p2);
        String p2Id = p2.getId();

        // Now active is p2, archived has p1
        assertEquals(p2Id, mgr.getActiveProject().getId());
        assertEquals(1, mgr.getArchivedProjects().size());

        // Reactivate p1 → should archive p2, activate p1
        Project restored = mgr.reactivateProject(p1Id);
        assertNotNull(restored);
        assertEquals(p1Id, mgr.getActiveProject().getId());
        assertEquals(1, mgr.getArchivedProjects().size());
        assertEquals(ProjectStatus.ARCHIVED, mgr.getArchivedProjects().get(0).getStatus());
    }

    // ==================== saveProject ====================

    @Test
    void saveProjectOverwritesExistingActive() {
        Path worldDir = tempDir.resolve("world_overwrite");
        ProjectManager.initialize(worldDir);
        ProjectManager mgr = ProjectManager.getInstance();

        Project p1 = new Project("first", "First Project",
                List.of(), ProjectStatus.IN_PROGRESS,
                java.time.Instant.now(), java.time.Instant.now());
        mgr.saveProject(p1);
        assertEquals("first", mgr.getActiveProject().getId());

        Project p2 = new Project("second", "Second Project",
                List.of(), ProjectStatus.IN_PROGRESS,
                java.time.Instant.now(), java.time.Instant.now());
        mgr.saveProject(p2);

        // New project should be active, old archived
        assertEquals("second", mgr.getActiveProject().getId());
        List<Project> archived = mgr.getArchivedProjects();
        assertEquals(1, archived.size());
        assertEquals("first", archived.get(0).getId());
        assertEquals(ProjectStatus.ARCHIVED, archived.get(0).getStatus());
    }

    @Test
    void saveProjectForcesInProgressStatus() {
        Path worldDir = tempDir.resolve("world_force_status");
        ProjectManager.initialize(worldDir);
        ProjectManager mgr = ProjectManager.getInstance();

        Project project = new Project("status-test", "Status Test",
                List.of(), ProjectStatus.CREATED,
                java.time.Instant.now(), java.time.Instant.now());
        mgr.saveProject(project);

        assertEquals(ProjectStatus.IN_PROGRESS, mgr.getActiveProject().getStatus());
    }

    // ==================== JSON 损坏恢复 ====================

    @Test
    void corruptJsonFileReturnsEmptyState() throws Exception {
        Path worldDir = tempDir.resolve("world_corrupt");
        Path projectsDir = worldDir.resolve("agentchat-projects");
        Files.createDirectories(projectsDir);
        Path projectsFile = projectsDir.resolve("projects.json");
        Files.writeString(projectsFile, "this is not valid JSON {{{");

        // Should not throw, should return empty state
        ProjectManager.initialize(worldDir);
        ProjectManager mgr = ProjectManager.getInstance();

        assertNotNull(mgr);
        assertNull(mgr.getActiveProject());
        assertTrue(mgr.getArchivedProjects().isEmpty());
    }

    @Test
    void partiallyCorruptJsonSkipsBadProject() throws Exception {
        Path worldDir = tempDir.resolve("world_partial_corrupt");
        Path projectsDir = worldDir.resolve("agentchat-projects");
        Files.createDirectories(projectsDir);
        Path projectsFile = projectsDir.resolve("projects.json");

        // Valid JSON but activeProject is corrupt (missing required 'id')
        String json = """
                {
                  "activeProject": {
                    "name": "No ID Project",
                    "tasks": [],
                    "status": "IN_PROGRESS",
                    "createdAt": "2026-05-01T10:00:00Z",
                    "updatedAt": "2026-05-01T10:00:00Z"
                  },
                  "archivedProjects": [
                    {
                      "id": "valid-archived",
                      "name": "Valid Archived",
                      "tasks": [],
                      "status": "ARCHIVED",
                      "createdAt": "2026-05-01T08:00:00Z",
                      "updatedAt": "2026-05-01T08:00:00Z"
                    }
                  ]
                }""";
        Files.writeString(projectsFile, json);

        ProjectManager.initialize(worldDir);
        ProjectManager mgr = ProjectManager.getInstance();

        assertNotNull(mgr);
        // Corrupt active project skipped → null
        assertNull(mgr.getActiveProject());
        // Valid archived project loaded
        List<Project> archived = mgr.getArchivedProjects();
        assertEquals(1, archived.size());
        assertEquals("valid-archived", archived.get(0).getId());
    }

    @Test
    void corruptTimestampsUseCurrentTime() throws Exception {
        Path worldDir = tempDir.resolve("world_corrupt_time");
        Path projectsDir = worldDir.resolve("agentchat-projects");
        Files.createDirectories(projectsDir);
        Path projectsFile = projectsDir.resolve("projects.json");

        String json = """
                {
                  "activeProject": {
                    "id": "time-test",
                    "name": "Time Test",
                    "tasks": [],
                    "status": "IN_PROGRESS",
                    "createdAt": "not-a-timestamp",
                    "updatedAt": 12345
                  },
                  "archivedProjects": []
                }""";
        Files.writeString(projectsFile, json);

        ProjectManager.initialize(worldDir);
        ProjectManager mgr = ProjectManager.getInstance();

        assertNotNull(mgr.getActiveProject());
        // Timestamps should be fallback to current time, not null
        assertNotNull(mgr.getActiveProject().getCreatedAt());
        assertNotNull(mgr.getActiveProject().getUpdatedAt());
    }

    @Test
    void missingStatusFieldDefaultsToCreated() throws Exception {
        Path worldDir = tempDir.resolve("world_missing_status");
        Path projectsDir = worldDir.resolve("agentchat-projects");
        Files.createDirectories(projectsDir);
        Path projectsFile = projectsDir.resolve("projects.json");

        String json = """
                {
                  "activeProject": {
                    "id": "no-status",
                    "name": "No Status Project",
                    "tasks": [],
                    "createdAt": "2026-05-01T10:00:00Z",
                    "updatedAt": "2026-05-01T10:00:00Z"
                  },
                  "archivedProjects": []
                }""";
        Files.writeString(projectsFile, json);

        ProjectManager.initialize(worldDir);
        ProjectManager mgr = ProjectManager.getInstance();

        assertNotNull(mgr.getActiveProject());
        assertEquals(ProjectStatus.CREATED, mgr.getActiveProject().getStatus());
    }

    // ==================== 归档上限裁剪 ====================

    @Test
    void moreThanMaxArchivesRemovesOldest() {
        Path worldDir = tempDir.resolve("world_max_archives");
        ProjectManager.initialize(worldDir);
        ProjectManager mgr = ProjectManager.getInstance();

        // Create 12 projects, each overwriting the previous (auto-archiving)
        // After 12 saves: 1 active + 11 archived → oldest gets auto-removed
        for (int i = 0; i < 12; i++) {
            Project p = new Project("proj-" + i, "Project " + i,
                    List.of(), ProjectStatus.IN_PROGRESS,
                    java.time.Instant.now(), java.time.Instant.now());
            mgr.saveProject(p);
        }

        // Active is the last one
        assertEquals("proj-11", mgr.getActiveProject().getId());

        // Archived should have at most 10 (proj-1 through proj-10, proj-0 removed)
        List<Project> archived = mgr.getArchivedProjects();
        assertEquals(10, archived.size());
        // The oldest (proj-0) should have been removed
        assertFalse(archived.stream().anyMatch(p -> "proj-0".equals(p.getId())),
                "Oldest archived project should be auto-removed");
        // proj-1 through proj-10 should be present
        for (int i = 1; i <= 10; i++) {
            String expectedId = "proj-" + i;
            assertTrue(archived.stream().anyMatch(p -> expectedId.equals(p.getId())),
                    "Project " + expectedId + " should be in archive");
        }
    }

    @Test
    void archiveCurrentProjectEnforcesMaxLimit() {
        Path worldDir = tempDir.resolve("world_archive_limit");
        ProjectManager.initialize(worldDir);
        ProjectManager mgr = ProjectManager.getInstance();

        // Manually populate archived list with 10 projects
        for (int i = 0; i < 10; i++) {
            Project p = new Project("arch-" + i, "Archived " + i,
                    List.of(), ProjectStatus.IN_PROGRESS,
                    java.time.Instant.now(), java.time.Instant.now());
            // Save then archive
            mgr.saveProject(p);
            mgr.archiveCurrentProject();
        }

        assertEquals(10, mgr.getArchivedProjects().size());

        // Add one more active, then archive → should trigger removal of oldest
        Project p11 = new Project("arch-10", "Eleventh",
                List.of(), ProjectStatus.IN_PROGRESS,
                java.time.Instant.now(), java.time.Instant.now());
        mgr.saveProject(p11);
        mgr.archiveCurrentProject();

        assertEquals(10, mgr.getArchivedProjects().size());
        assertFalse(mgr.getArchivedProjects().stream().anyMatch(p -> "arch-0".equals(p.getId())),
                "Oldest (arch-0) should be removed");
        assertTrue(mgr.getArchivedProjects().stream().anyMatch(p -> "arch-10".equals(p.getId())),
                "Newest (arch-10) should be present");
    }

    // ==================== 任务恢复 ====================

    @Test
    void tasksWithItemRequirementsRoundTrip() {
        Path worldDir = tempDir.resolve("world_tasks");
        ProjectManager.initialize(worldDir);
        ProjectManager mgr = ProjectManager.getInstance();

        Task task = new Task(null, "Craft diamond sword",
                TaskType.CRAFT, TaskStatus.PENDING,
                List.of(
                        new ItemRequirement("minecraft:diamond", 2, 0),
                        new ItemRequirement("minecraft:stick", 1, 0)),
                "Need to mine diamonds first");

        Project project = new Project("task-test", "Task Round Trip",
                List.of(task), ProjectStatus.IN_PROGRESS,
                java.time.Instant.now(), java.time.Instant.now());
        mgr.saveProject(project);

        ProjectManager.shutdown();
        ProjectManager.initialize(worldDir);
        mgr = ProjectManager.getInstance();

        Project loaded = mgr.getActiveProject();
        assertNotNull(loaded);
        assertEquals(1, loaded.getTasks().size());

        Task loadedTask = loaded.getTasks().get(0);
        assertEquals("Craft diamond sword", loadedTask.description());
        assertEquals(TaskType.CRAFT, loadedTask.type());
        assertEquals(TaskStatus.PENDING, loadedTask.status());
        assertEquals("Need to mine diamonds first", loadedTask.note());
        assertEquals(2, loadedTask.requiredItems().size());

        ItemRequirement diamond = loadedTask.requiredItems().get(0);
        assertEquals("minecraft:diamond", diamond.itemId());
        assertEquals(2, diamond.needed());
        assertEquals(0, diamond.owned());

        ItemRequirement stick = loadedTask.requiredItems().get(1);
        assertEquals("minecraft:stick", stick.itemId());
        assertEquals(1, stick.needed());
        assertEquals(0, stick.owned());
    }

    @Test
    void multipleTasksPreserveOrder() {
        Path worldDir = tempDir.resolve("world_task_order");
        ProjectManager.initialize(worldDir);
        ProjectManager mgr = ProjectManager.getInstance();

        List<Task> tasks = List.of(
                Task.of("Step 1: Gather", TaskType.GATHER, List.of()),
                Task.of("Step 2: Craft", TaskType.CRAFT, List.of()),
                Task.of("Step 3: Use", TaskType.USE, List.of())
        );

        Project project = new Project("order-test", "Order Test",
                tasks, ProjectStatus.IN_PROGRESS,
                java.time.Instant.now(), java.time.Instant.now());
        mgr.saveProject(project);

        ProjectManager.shutdown();
        ProjectManager.initialize(worldDir);
        mgr = ProjectManager.getInstance();

        List<Task> loaded = mgr.getActiveProject().getTasks();
        assertEquals(3, loaded.size());
        assertEquals("Step 1: Gather", loaded.get(0).description());
        assertEquals("Step 2: Craft", loaded.get(1).description());
        assertEquals("Step 3: Use", loaded.get(2).description());
    }

    // ==================== getArchivedProjects 不可变性 ====================

    @Test
    void getArchivedProjectsReturnsUnmodifiableCopy() {
        Path worldDir = tempDir.resolve("world_immutable");
        ProjectManager.initialize(worldDir);
        ProjectManager mgr = ProjectManager.getInstance();

        Project p = new Project("immutable-test", "Test",
                List.of(), ProjectStatus.IN_PROGRESS,
                java.time.Instant.now(), java.time.Instant.now());
        mgr.saveProject(p);
        mgr.archiveCurrentProject();

        List<Project> archived = mgr.getArchivedProjects();
        assertEquals(1, archived.size());

        // Attempting to modify returned list should throw
        assertThrows(UnsupportedOperationException.class, () -> archived.add(
                new Project("should-fail")));
    }

    // ==================== 空任务列表和空 note 持久化 ====================

    @Test
    void projectWithEmptyTasksRoundTrip() {
        Path worldDir = tempDir.resolve("world_empty_tasks");
        ProjectManager.initialize(worldDir);
        ProjectManager mgr = ProjectManager.getInstance();

        Project project = new Project("empty-tasks", "Empty Tasks",
                List.of(), ProjectStatus.IN_PROGRESS,
                java.time.Instant.now(), java.time.Instant.now());
        mgr.saveProject(project);

        ProjectManager.shutdown();
        ProjectManager.initialize(worldDir);
        mgr = ProjectManager.getInstance();

        Project loaded = mgr.getActiveProject();
        assertNotNull(loaded);
        assertTrue(loaded.getTasks().isEmpty());
    }

    @Test
    void taskWithNullNoteRoundTrip() {
        Path worldDir = tempDir.resolve("world_null_note");
        ProjectManager.initialize(worldDir);
        ProjectManager mgr = ProjectManager.getInstance();

        Task task = new Task(null, "Task without note",
                TaskType.PLAN, TaskStatus.PENDING,
                List.of(), null);

        Project project = new Project("null-note-test", "Null Note",
                List.of(task), ProjectStatus.IN_PROGRESS,
                java.time.Instant.now(), java.time.Instant.now());
        mgr.saveProject(project);

        ProjectManager.shutdown();
        ProjectManager.initialize(worldDir);
        mgr = ProjectManager.getInstance();

        Task loaded = mgr.getActiveProject().getTasks().get(0);
        assertNull(loaded.note());
    }
}
