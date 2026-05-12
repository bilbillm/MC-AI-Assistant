package com.lumoren.agentchat.client;

import com.lumoren.agentchat.ai.GameDataAccess;
import com.lumoren.agentchat.model.InventoryItem;
import com.lumoren.agentchat.model.ItemRequirement;
import com.lumoren.agentchat.model.Project;
import com.lumoren.agentchat.model.Task;
import com.lumoren.agentchat.model.TaskStatus;
import net.minecraft.client.Minecraft;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Tick-based inventory listener that auto-detects task completion and death/item loss.
 * <p>
 * Called every N game ticks to compare current inventory against tracked tasks.
 * When inventory satisfies all required items for a task, the task is marked {@link TaskStatus#DONE}.
 * When significant item loss is detected (likely death), affected tasks are marked {@link TaskStatus#BLOCKED}.
 */
public class ProjectProgressTracker {

    /** Cooldown between death detections to prevent duplicate triggers (30 seconds). */
    static final long DEATH_COOLDOWN_MS = 30_000;

    /** Tick counter — reset to 0 after reaching the configured interval. */
    int tickCounter;

    /** Previous inventory snapshot keyed by itemId → total count. */
    Map<String, Integer> previousInventory;

    /** Timestamp of the last death detection event. */
    long lastDeathDetection;

    /**
     * Called every game tick. Evaluates inventory changes at the configured interval.
     *
     * @param project  the project whose tasks should be evaluated
     * @param mc       the Minecraft client instance
     * @param interval number of ticks between evaluations (e.g. 20 = 1 second)
     */
    public void onClientTick(Project project, Minecraft mc, int interval) {
        if (mc.player == null) {
            return;
        }

        tickCounter++;
        if (tickCounter < interval) {
            return;
        }
        tickCounter = 0;

        List<InventoryItem> items = GameDataAccess.getInventorySnapshot(mc);
        Map<String, Integer> current = buildInventoryMap(items);

        if (previousInventory == null) {
            previousInventory = current;
            return;
        }

        // Detect death: significant item loss
        if (detectDeathItemLoss(previousInventory, current)) {
            long now = System.currentTimeMillis();
            if (now - lastDeathDetection > DEATH_COOLDOWN_MS) {
                markBlockedTasks(project, current);
                lastDeathDetection = now;
            }
        }

        // Match items against tasks
        checkTaskCompletion(project, current);

        previousInventory = current;
    }

    /**
     * Converts a list of {@link InventoryItem} to a map of itemId → total count.
     * <p>
     * Items with the same ID across different slots are summed.
     *
     * @param items the raw inventory items
     * @return a map aggregating counts by item ID
     */
    Map<String, Integer> buildInventoryMap(List<InventoryItem> items) {
        Map<String, Integer> map = new HashMap<>();
        for (InventoryItem item : items) {
            map.merge(item.itemId(), item.count(), Integer::sum);
        }
        return map;
    }

    /**
     * Checks all {@code PENDING} and {@code IN_PROGRESS} tasks against the current inventory.
     * <p>
     * If all required items for a task are present in sufficient quantity,
     * a new {@link Task} with status {@link TaskStatus#DONE} replaces the old one.
     *
     * @param project   the project containing tasks
     * @param inventory the current inventory snapshot (itemId → count)
     */
    void checkTaskCompletion(Project project, Map<String, Integer> inventory) {
        List<Task> tasks = project.getTasks();
        List<Task> updated = new ArrayList<>();
        boolean changed = false;

        for (Task task : tasks) {
            if (task.status() == TaskStatus.PENDING || task.status() == TaskStatus.IN_PROGRESS) {
                if (allItemsSatisfied(task.requiredItems(), inventory)) {
                    updated.add(new Task(task.id(), task.description(), task.type(), TaskStatus.DONE,
                            task.requiredItems(), task.note()));
                    changed = true;
                } else {
                    updated.add(task);
                }
            } else {
                updated.add(task);
            }
        }

        if (changed) {
            project.setTasks(updated);
        }
    }

    /**
     * Detects potential death by comparing total item counts between snapshots.
     * <p>
     * Returns true if the total item count has dropped by more than 50%,
     * which indicates the player likely died and lost their inventory.
     *
     * @param previous the previous inventory snapshot
     * @param current  the current inventory snapshot
     * @return true if significant item loss is detected
     */
    boolean detectDeathItemLoss(Map<String, Integer> previous, Map<String, Integer> current) {
        int prevTotal = previous.values().stream().mapToInt(Integer::intValue).sum();
        int currTotal = current.values().stream().mapToInt(Integer::intValue).sum();

        if (prevTotal == 0) {
            return false;
        }
        return currTotal < prevTotal * 0.5;
    }

    /**
     * Marks tasks as {@link TaskStatus#BLOCKED} when their required items are no longer
     * in the inventory.
     * <p>
     * Only marks tasks that were previously {@code PENDING} or {@code IN_PROGRESS}.
     * A task is blocked when ANY of its required items is completely absent (count = 0).
     *
     * @param project the project containing tasks
     * @param current the current inventory snapshot (itemId → count)
     */
    void markBlockedTasks(Project project, Map<String, Integer> current) {
        List<Task> tasks = project.getTasks();
        List<Task> updated = new ArrayList<>();
        boolean changed = false;

        for (Task task : tasks) {
            if (task.status() == TaskStatus.PENDING || task.status() == TaskStatus.IN_PROGRESS) {
                if (anyRequiredItemMissing(task.requiredItems(), current)) {
                    updated.add(new Task(task.id(), task.description(), task.type(), TaskStatus.BLOCKED,
                            task.requiredItems(), task.note()));
                    changed = true;
                } else {
                    updated.add(task);
                }
            } else {
                updated.add(task);
            }
        }

        if (changed) {
            project.setTasks(updated);
        }
    }

    /**
     * Checks whether all required items have sufficient quantity in the inventory.
     */
    private boolean allItemsSatisfied(List<ItemRequirement> requirements, Map<String, Integer> inventory) {
        for (ItemRequirement req : requirements) {
            if (inventory.getOrDefault(req.itemId(), 0) < req.needed()) {
                return false;
            }
        }
        return true;
    }

    /**
     * Checks whether any required item is completely absent from the inventory (count = 0).
     */
    private boolean anyRequiredItemMissing(List<ItemRequirement> requirements, Map<String, Integer> inventory) {
        for (ItemRequirement req : requirements) {
            if (inventory.getOrDefault(req.itemId(), 0) == 0) {
                return true;
            }
        }
        return false;
    }
}
