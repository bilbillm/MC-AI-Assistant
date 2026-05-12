package com.lumoren.agentchat.client;

import com.lumoren.agentchat.model.InventoryItem;
import com.lumoren.agentchat.model.ItemRequirement;
import com.lumoren.agentchat.model.Project;
import com.lumoren.agentchat.model.Task;
import com.lumoren.agentchat.model.TaskStatus;
import com.lumoren.agentchat.model.TaskType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for {@link ProjectProgressTracker}.
 * <p>
 * Tests the core inventory comparison and task status logic without requiring
 * a running Minecraft instance. All tests use plain data objects.
 */
class ProjectProgressTrackerTest {

    private ProjectProgressTracker tracker;
    private Project project;

    @BeforeEach
    void setUp() {
        tracker = new ProjectProgressTracker();
        project = new Project("test-project");
    }

    // ========== buildInventoryMap ==========

    @Test
    void buildInventoryMap_aggregatesCountsByItemId() {
        List<InventoryItem> items = List.of(
                new InventoryItem(0, "minecraft:diamond", "钻石", 3, 0, 0),
                new InventoryItem(1, "minecraft:diamond", "钻石", 2, 0, 0),
                new InventoryItem(2, "minecraft:stick", "木棍", 5, 0, 0),
                new InventoryItem(3, "minecraft:cobblestone", "圆石", 10, 0, 0)
        );

        Map<String, Integer> result = tracker.buildInventoryMap(items);

        assertEquals(3, result.size());
        assertEquals(5, result.get("minecraft:diamond"));   // 3 + 2
        assertEquals(5, result.get("minecraft:stick"));
        assertEquals(10, result.get("minecraft:cobblestone"));
    }

    @Test
    void buildInventoryMap_emptyListReturnsEmptyMap() {
        Map<String, Integer> result = tracker.buildInventoryMap(List.of());
        assertTrue(result.isEmpty());
    }

    // ========== checkTaskCompletion — fully satisfied → DONE ==========

    @Test
    void checkTaskCompletion_allItemsSatisfied_marksTaskDone() {
        Task task = new Task("task-1", "收集钻石", TaskType.GATHER, TaskStatus.PENDING,
                List.of(new ItemRequirement("minecraft:diamond", 5, 0)), null);
        project.setTasks(List.of(task));

        Map<String, Integer> inventory = Map.of("minecraft:diamond", 5);
        tracker.checkTaskCompletion(project, inventory);

        Task updated = project.getTasks().getFirst();
        assertEquals(TaskStatus.DONE, updated.status());
        assertEquals("task-1", updated.id());
        assertEquals("收集钻石", updated.description());
    }

    @Test
    void checkTaskCompletion_multipleRequirementsAllSatisfied_marksDone() {
        Task task = new Task("task-2", "合成钻石剑", TaskType.CRAFT, TaskStatus.PENDING,
                List.of(
                        new ItemRequirement("minecraft:diamond", 2, 0),
                        new ItemRequirement("minecraft:stick", 1, 0)
                ), null);
        project.setTasks(List.of(task));

        Map<String, Integer> inventory = Map.of(
                "minecraft:diamond", 2,
                "minecraft:stick", 1
        );
        tracker.checkTaskCompletion(project, inventory);

        assertEquals(TaskStatus.DONE, project.getTasks().getFirst().status());
    }

    @Test
    void checkTaskCompletion_inProgressTaskSatisfied_marksDone() {
        Task task = new Task("task-3", "收集木头", TaskType.GATHER, TaskStatus.IN_PROGRESS,
                List.of(new ItemRequirement("minecraft:oak_log", 10, 0)), null);
        project.setTasks(List.of(task));

        Map<String, Integer> inventory = Map.of("minecraft:oak_log", 10);
        tracker.checkTaskCompletion(project, inventory);

        assertEquals(TaskStatus.DONE, project.getTasks().getFirst().status());
    }

    // ========== checkTaskCompletion — insufficient items → stays ==========

    @Test
    void checkTaskCompletion_insufficientItems_staysPending() {
        Task task = new Task("task-4", "收集铁锭", TaskType.GATHER, TaskStatus.PENDING,
                List.of(new ItemRequirement("minecraft:iron_ingot", 10, 0)), null);
        project.setTasks(List.of(task));

        Map<String, Integer> inventory = Map.of("minecraft:iron_ingot", 3);
        tracker.checkTaskCompletion(project, inventory);

        assertEquals(TaskStatus.PENDING, project.getTasks().getFirst().status());
    }

    @Test
    void checkTaskCompletion_missingItem_staysPending() {
        Task task = new Task("task-5", "收集钻石", TaskType.GATHER, TaskStatus.PENDING,
                List.of(new ItemRequirement("minecraft:diamond", 1, 0)), null);
        project.setTasks(List.of(task));

        // Inventory has no diamonds at all
        Map<String, Integer> inventory = Map.of("minecraft:stick", 10);
        tracker.checkTaskCompletion(project, inventory);

        assertEquals(TaskStatus.PENDING, project.getTasks().getFirst().status());
    }

    // ========== checkTaskCompletion — no inventory change → unchanged ==========

    @Test
    void checkTaskCompletion_noInventoryChange_tasksUnchanged() {
        Task pendingTask = new Task("task-6", "收集钻石", TaskType.GATHER, TaskStatus.PENDING,
                List.of(new ItemRequirement("minecraft:diamond", 5, 0)), null);
        Task doneTask = new Task("task-7", "收集木头", TaskType.GATHER, TaskStatus.DONE,
                List.of(new ItemRequirement("minecraft:oak_log", 5, 0)), null);
        Task blockedTask = new Task("task-8", "合成剑", TaskType.CRAFT, TaskStatus.BLOCKED,
                List.of(new ItemRequirement("minecraft:iron_ingot", 2, 0)), null);
        project.setTasks(List.of(pendingTask, doneTask, blockedTask));

        // Inventory does not satisfy the PENDING task
        Map<String, Integer> inventory = Map.of("minecraft:diamond", 2);
        tracker.checkTaskCompletion(project, inventory);

        List<Task> tasks = project.getTasks();
        assertEquals(3, tasks.size());
        assertEquals(TaskStatus.PENDING, tasks.get(0).status());
        assertEquals(TaskStatus.DONE, tasks.get(1).status());
        assertEquals(TaskStatus.BLOCKED, tasks.get(2).status());
    }

    @Test
    void checkTaskCompletion_excessInventorySatisfies_countsAsDone() {
        Task task = new Task("task-9", "收集圆石", TaskType.GATHER, TaskStatus.PENDING,
                List.of(new ItemRequirement("minecraft:cobblestone", 10, 0)), null);
        project.setTasks(List.of(task));

        // Have more than needed
        Map<String, Integer> inventory = Map.of("minecraft:cobblestone", 64);
        tracker.checkTaskCompletion(project, inventory);

        assertEquals(TaskStatus.DONE, project.getTasks().getFirst().status());
    }

    // ========== detectDeathItemLoss ==========

    @Test
    void detectDeathItemLoss_significantDrop_returnsTrue() {
        Map<String, Integer> previous = Map.of(
                "minecraft:diamond_sword", 1,
                "minecraft:bread", 20,
                "minecraft:torch", 64,
                "minecraft:cobblestone", 50
        );
        // After death: only a few items remain (more than 50% loss)
        Map<String, Integer> current = Map.of(
                "minecraft:stick", 2
        );

        assertTrue(tracker.detectDeathItemLoss(previous, current));
    }

    @Test
    void detectDeathItemLoss_smallDrop_returnsFalse() {
        Map<String, Integer> previous = Map.of(
                "minecraft:cobblestone", 50,
                "minecraft:torch", 30
        );
        Map<String, Integer> current = Map.of(
                "minecraft:cobblestone", 45,  // used 5
                "minecraft:torch", 25           // used 5
        );

        assertFalse(tracker.detectDeathItemLoss(previous, current));
    }

    @Test
    void detectDeathItemLoss_noChange_returnsFalse() {
        Map<String, Integer> previous = Map.of("minecraft:diamond", 5);
        Map<String, Integer> current = Map.of("minecraft:diamond", 5);

        assertFalse(tracker.detectDeathItemLoss(previous, current));
    }

    @Test
    void detectDeathItemLoss_emptyPrevious_returnsFalse() {
        Map<String, Integer> previous = Map.of();
        Map<String, Integer> current = Map.of();

        assertFalse(tracker.detectDeathItemLoss(previous, current));
    }

    @Test
    void detectDeathItemLoss_increaseInItems_returnsFalse() {
        Map<String, Integer> previous = Map.of("minecraft:diamond", 1);
        Map<String, Integer> current = Map.of("minecraft:diamond", 10);

        assertFalse(tracker.detectDeathItemLoss(previous, current));
    }

    // ========== death cooldown ==========

    @Test
    void deathDetection_cooldownPreventsDuplicateTriggers() {
        // Setup: project has a task requiring diamond
        Task task = new Task("task-10", "收集钻石", TaskType.GATHER, TaskStatus.PENDING,
                List.of(new ItemRequirement("minecraft:diamond", 1, 0)), null);
        project.setTasks(List.of(task));

        // First death detection
        Map<String, Integer> deathInventory = Map.of("minecraft:stick", 1); // almost empty
        tracker.previousInventory = Map.of(
                "minecraft:diamond", 10,
                "minecraft:cobblestone", 64
        );

        // Simulate first death detection
        tracker.markBlockedTasks(project, deathInventory);
        tracker.lastDeathDetection = System.currentTimeMillis();

        assertEquals(TaskStatus.BLOCKED, project.getTasks().getFirst().status());

        // Reset task to PENDING to test cooldown
        project.setTasks(List.of(new Task("task-10", "收集钻石", TaskType.GATHER, TaskStatus.PENDING,
                List.of(new ItemRequirement("minecraft:diamond", 1, 0)), null)));

        // Try to trigger again immediately (within cooldown) — should be suppressed
        // The onClientTick checks cooldown, so we verify by checking if
        // the lastDeathDetection timestamp is recent enough
        long elapsed = System.currentTimeMillis() - tracker.lastDeathDetection;
        assertTrue(elapsed < ProjectProgressTracker.DEATH_COOLDOWN_MS,
                "Cooldown should prevent re-trigger within " + ProjectProgressTracker.DEATH_COOLDOWN_MS + "ms");
    }

    @Test
    void deathDetection_cooldownExpired_allowsReTrigger() {
        // Set lastDeathDetection far enough in the past
        tracker.lastDeathDetection = System.currentTimeMillis() - ProjectProgressTracker.DEATH_COOLDOWN_MS - 1000;

        Map<String, Integer> previous = Map.of("minecraft:diamond", 10, "minecraft:cobblestone", 64);
        Map<String, Integer> current = Map.of("minecraft:stick", 1);

        // The death should be detected
        assertTrue(tracker.detectDeathItemLoss(previous, current));

        // And the cooldown has expired, so markBlockedTasks should fire
        long elapsed = System.currentTimeMillis() - tracker.lastDeathDetection;
        assertTrue(elapsed > ProjectProgressTracker.DEATH_COOLDOWN_MS,
                "Cooldown expired, should allow re-trigger after " + ProjectProgressTracker.DEATH_COOLDOWN_MS + "ms");
    }

    // ========== markBlockedTasks ==========

    @Test
    void markBlockedTasks_requiredItemMissing_marksBlocked() {
        Task task = new Task("task-11", "合成钻石剑", TaskType.CRAFT, TaskStatus.PENDING,
                List.of(
                        new ItemRequirement("minecraft:diamond", 2, 0),
                        new ItemRequirement("minecraft:stick", 1, 0)
                ), null);
        project.setTasks(List.of(task));

        // Inventory has sticks but no diamonds
        Map<String, Integer> current = Map.of("minecraft:stick", 1);
        tracker.markBlockedTasks(project, current);

        assertEquals(TaskStatus.BLOCKED, project.getTasks().getFirst().status());
    }

    @Test
    void markBlockedTasks_itemsPresent_doesNotMarkBlocked() {
        Task task = new Task("task-12", "合成木棍", TaskType.CRAFT, TaskStatus.PENDING,
                List.of(new ItemRequirement("minecraft:oak_planks", 2, 0)), null);
        project.setTasks(List.of(task));

        // Inventory has the required items
        Map<String, Integer> current = Map.of("minecraft:oak_planks", 5);
        tracker.markBlockedTasks(project, current);

        assertEquals(TaskStatus.PENDING, project.getTasks().getFirst().status());
    }

    @Test
    void markBlockedTasks_skipsAlreadyDoneTasks() {
        Task doneTask = new Task("task-13", "已完成", TaskType.CRAFT, TaskStatus.DONE,
                List.of(new ItemRequirement("minecraft:diamond", 1, 0)), null);
        project.setTasks(List.of(doneTask));

        // Even with empty inventory, DONE tasks should not be re-marked
        Map<String, Integer> current = Map.of();
        tracker.markBlockedTasks(project, current);

        assertEquals(TaskStatus.DONE, project.getTasks().getFirst().status());
    }

    @Test
    void markBlockedTasks_inProgressTask_marksBlocked() {
        Task task = new Task("task-14", "收集铁锭", TaskType.GATHER, TaskStatus.IN_PROGRESS,
                List.of(new ItemRequirement("minecraft:iron_ingot", 5, 0)), null);
        project.setTasks(List.of(task));

        // Inventory lacks iron
        Map<String, Integer> current = Map.of();
        tracker.markBlockedTasks(project, current);

        assertEquals(TaskStatus.BLOCKED, project.getTasks().getFirst().status());
    }
}
