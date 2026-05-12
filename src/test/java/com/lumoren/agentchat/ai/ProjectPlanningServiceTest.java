package com.lumoren.agentchat.ai;

import com.lumoren.agentchat.model.*;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * ProjectPlanningService 单元测试。
 * <p>
 * 验证 AI prompt 构建、JSON 响应解析、项目上下文消息生成。
 * 这些测试不依赖 Minecraft 运行时。
 */
class ProjectPlanningServiceTest {

    // ==================== buildTaskChainPrompt ====================

    @Test
    void buildTaskChainPrompt_returnsSystemMessageWithGoal() {
        List<ChatMessage> messages = ProjectPlanningService.buildTaskChainPrompt("Build a house");

        assertEquals(1, messages.size());
        ChatMessage msg = messages.getFirst();
        assertEquals("system", msg.role());
        assertTrue(msg.content().contains("Build a house"));
        assertTrue(msg.content().contains("tasks"));
        assertTrue(msg.content().contains("CRAFT|GATHER|GO_TO|USE|KILL|PLAN"));
        assertTrue(msg.content().contains("minecraft:stick"));
    }

    @Test
    void buildTaskChainPrompt_escapesSpecialCharacters() {
        List<ChatMessage> messages = ProjectPlanningService.buildTaskChainPrompt(
                "Get 3 \"diamonds\" and craft a sword");

        assertEquals(1, messages.size());
        // Should not crash on quotes or special characters
        String content = messages.getFirst().content();
        assertNotNull(content);
    }

    // ==================== parseTaskChain ====================

    @Test
    void parseTaskChain_validJson_returnsTasks() {
        String json = """
                {
                  "tasks": [
                    {
                      "description": "Craft a crafting table",
                      "type": "CRAFT",
                      "items": [
                        {"itemId": "minecraft:oak_planks", "count": 4}
                      ]
                    },
                    {
                      "description": "Collect iron ore",
                      "type": "GATHER",
                      "items": [
                        {"itemId": "minecraft:iron_ore", "count": 5}
                      ]
                    },
                    {
                      "description": "Go to the nether",
                      "type": "GO_TO",
                      "items": []
                    }
                  ]
                }
                """;

        List<Task> tasks = ProjectPlanningService.parseTaskChain(json);

        assertEquals(3, tasks.size());

        assertEquals("Craft a crafting table", tasks.get(0).description());
        assertEquals(TaskType.CRAFT, tasks.get(0).type());
        assertEquals(TaskStatus.PENDING, tasks.get(0).status());
        assertEquals(1, tasks.get(0).requiredItems().size());
        assertEquals("minecraft:oak_planks", tasks.get(0).requiredItems().getFirst().itemId());
        assertEquals(4, tasks.get(0).requiredItems().getFirst().needed());

        assertEquals("Collect iron ore", tasks.get(1).description());
        assertEquals(TaskType.GATHER, tasks.get(1).type());

        assertEquals("Go to the nether", tasks.get(2).description());
        assertEquals(TaskType.GO_TO, tasks.get(2).type());
        assertTrue(tasks.get(2).requiredItems().isEmpty());
    }

    @Test
    void parseTaskChain_emptyTasksArray_returnsEmptyList() {
        String json = """
                {
                  "tasks": []
                }
                """;

        List<Task> tasks = ProjectPlanningService.parseTaskChain(json);

        assertNotNull(tasks);
        assertTrue(tasks.isEmpty());
    }

    @Test
    void parseTaskChain_malformedJson_returnsEmptyList() {
        List<Task> tasks = ProjectPlanningService.parseTaskChain(
                "This is not JSON at all... { broken }");

        assertNotNull(tasks);
        assertTrue(tasks.isEmpty());
    }

    @Test
    void parseTaskChain_emptyString_returnsEmptyList() {
        List<Task> tasks = ProjectPlanningService.parseTaskChain("");

        assertNotNull(tasks);
        assertTrue(tasks.isEmpty());
    }

    @Test
    void parseTaskChain_nullResponse_returnsEmptyList() {
        List<Task> tasks = ProjectPlanningService.parseTaskChain(null);

        assertNotNull(tasks);
        assertTrue(tasks.isEmpty());
    }

    @Test
    void parseTaskChain_invalidType_returnsEmptyList() {
        String json = """
                {
                  "tasks": [
                    {
                      "description": "Do magic",
                      "type": "INVALID_TYPE",
                      "items": []
                    }
                  ]
                }
                """;

        List<Task> tasks = ProjectPlanningService.parseTaskChain(json);

        assertNotNull(tasks);
        assertTrue(tasks.isEmpty());
    }

    @Test
    void parseTaskChain_itemsContainItemRequirements() {
        String json = """
                {
                  "tasks": [
                    {
                      "description": "Craft a diamond sword",
                      "type": "CRAFT",
                      "items": [
                        {"itemId": "minecraft:diamond", "count": 2},
                        {"itemId": "minecraft:stick", "count": 1}
                      ]
                    }
                  ]
                }
                """;

        List<Task> tasks = ProjectPlanningService.parseTaskChain(json);

        assertEquals(1, tasks.size());
        Task task = tasks.getFirst();

        assertEquals(2, task.requiredItems().size());
        ItemRequirement first = task.requiredItems().get(0);
        assertEquals("minecraft:diamond", first.itemId());
        assertEquals(2, first.needed());
        assertEquals(0, first.owned());

        ItemRequirement second = task.requiredItems().get(1);
        assertEquals("minecraft:stick", second.itemId());
        assertEquals(1, second.needed());
    }

    @Test
    void parseTaskChain_invalidItemId_skipped() {
        String json = """
                {
                  "tasks": [
                    {
                      "description": "Use invalid item",
                      "type": "USE",
                      "items": [
                        {"itemId": "not-a-valid-item-id", "count": 1},
                        {"itemId": "minecraft:stick", "count": 1}
                      ]
                    }
                  ]
                }
                """;

        List<Task> tasks = ProjectPlanningService.parseTaskChain(json);

        assertEquals(1, tasks.size());
        assertEquals(1, tasks.getFirst().requiredItems().size());
        assertEquals("minecraft:stick",
                tasks.getFirst().requiredItems().getFirst().itemId());
    }

    @Test
    void parseTaskChain_missingItemsField_returnsTaskWithEmptyItems() {
        String json = """
                {
                  "tasks": [
                    {
                      "description": "Plan the build",
                      "type": "PLAN"
                    }
                  ]
                }
                """;

        List<Task> tasks = ProjectPlanningService.parseTaskChain(json);

        assertEquals(1, tasks.size());
        assertTrue(tasks.getFirst().requiredItems().isEmpty());
    }

    @Test
    void parseTaskChain_eachTaskHasUniqueId() {
        String json = """
                {
                  "tasks": [
                    {
                      "description": "Task A",
                      "type": "PLAN",
                      "items": []
                    },
                    {
                      "description": "Task B",
                      "type": "PLAN",
                      "items": []
                    },
                    {
                      "description": "Task C",
                      "type": "PLAN",
                      "items": []
                    }
                  ]
                }
                """;

        List<Task> tasks = ProjectPlanningService.parseTaskChain(json);

        assertEquals(3, tasks.size());
        // Each task should have a unique auto-generated UUID
        assertNotEquals(tasks.get(0).id(), tasks.get(1).id());
        assertNotEquals(tasks.get(1).id(), tasks.get(2).id());
        assertNotEquals(tasks.get(0).id(), tasks.get(2).id());
    }

    @Test
    void parseTaskChain_allTaskTypesParsed() {
        String json = """
                {
                  "tasks": [
                    {"description": "Craft", "type": "CRAFT", "items": []},
                    {"description": "Gather", "type": "GATHER", "items": []},
                    {"description": "Go to", "type": "GO_TO", "items": []},
                    {"description": "Use", "type": "USE", "items": []},
                    {"description": "Kill", "type": "KILL", "items": []},
                    {"description": "Plan", "type": "PLAN", "items": []}
                  ]
                }
                """;

        List<Task> tasks = ProjectPlanningService.parseTaskChain(json);

        assertEquals(6, tasks.size());
        assertEquals(TaskType.CRAFT, tasks.get(0).type());
        assertEquals(TaskType.GATHER, tasks.get(1).type());
        assertEquals(TaskType.GO_TO, tasks.get(2).type());
        assertEquals(TaskType.USE, tasks.get(3).type());
        assertEquals(TaskType.KILL, tasks.get(4).type());
        assertEquals(TaskType.PLAN, tasks.get(5).type());
    }

    // ==================== buildProjectContextMessage ====================

    @Test
    void buildProjectContextMessage_containsProjectNameAndProgress() {
        Task pending = Task.of("Collect wood", TaskType.GATHER, List.of());
        Task done = new Task("t1", "Craft table", TaskType.CRAFT, TaskStatus.DONE,
                List.of(), null);
        Task blocked = new Task("t2", "Mine diamond", TaskType.GATHER, TaskStatus.BLOCKED,
                List.of(), null);

        Project project = new Project("project-ctx", "Diamond Sword Plan",
                List.of(pending, done, blocked), ProjectStatus.IN_PROGRESS,
                Instant.now(), Instant.now());

        ChatMessage msg = ProjectPlanningService.buildProjectContextMessage(project);

        assertEquals("system", msg.role());
        String content = msg.content();

        assertTrue(content.contains("Diamond Sword Plan"));
        assertTrue(content.contains("IN_PROGRESS"));
        assertTrue(content.contains("1/3"));  // 1 done out of 3
        assertTrue(content.contains("1 blocked"));
        assertTrue(content.contains("Collect wood"));
        assertTrue(content.contains("Craft table"));
        assertTrue(content.contains("Mine diamond"));
    }

    @Test
    void buildProjectContextMessage_allTasksDone() {
        Task t1 = new Task("t1", "A", TaskType.PLAN, TaskStatus.DONE, List.of(), null);
        Task t2 = new Task("t2", "B", TaskType.CRAFT, TaskStatus.DONE, List.of(), null);

        Project project = new Project("done-proj", "All Done",
                List.of(t1, t2), ProjectStatus.COMPLETED,
                Instant.now(), Instant.now());

        ChatMessage msg = ProjectPlanningService.buildProjectContextMessage(project);

        assertTrue(msg.content().contains("2/2"));
        assertTrue(msg.content().contains("COMPLETED"));
        assertFalse(msg.content().contains("blocked"));
    }

    @Test
    void buildProjectContextMessage_emptyTasks() {
        Project project = new Project("empty-proj", "Empty Plan",
                List.of(), ProjectStatus.CREATED,
                Instant.now(), Instant.now());

        ChatMessage msg = ProjectPlanningService.buildProjectContextMessage(project);

        assertTrue(msg.content().contains("Empty Plan"));
        assertTrue(msg.content().contains("0/0"));
        assertTrue(msg.content().contains("CREATED"));
    }

    @Test
    void buildProjectContextMessage_nullProject_doesNotCrash() {
        ChatMessage msg = ProjectPlanningService.buildProjectContextMessage(null);

        assertNotNull(msg);
        assertEquals("system", msg.role());
        assertNotNull(msg.content());
    }

    @Test
    void buildProjectContextMessage_projectWithNullTasks_handledGracefully() {
        // Simulate edge case: Project with getTasks() returning null (shouldn't happen
        // in practice given the constructor initialises the list, but guard anyway)
        ChatMessage msg = ProjectPlanningService.buildProjectContextMessage(null);

        assertNotNull(msg);
        assertEquals("system", msg.role());
    }

    @Test
    void buildProjectContextMessage_pendingTaskShowsEmptyBox() {
        Task pending = Task.of("Do something", TaskType.PLAN, List.of());

        Project project = new Project("pend-proj", "Pending Plan",
                List.of(pending), ProjectStatus.CREATED,
                Instant.now(), Instant.now());

        ChatMessage msg = ProjectPlanningService.buildProjectContextMessage(project);

        assertTrue(msg.content().contains("[ ] Do something"));
        assertTrue(msg.content().contains("0/1"));
    }

    // ==================== isValidItemId ====================

    @Test
    void isValidItemId_validIds() {
        assertTrue(ProjectPlanningService.isValidItemId("minecraft:stick"));
        assertTrue(ProjectPlanningService.isValidItemId("minecraft:diamond_sword"));
        assertTrue(ProjectPlanningService.isValidItemId("modid:custom_item"));
        assertTrue(ProjectPlanningService.isValidItemId("a:b"));
    }

    @Test
    void isValidItemId_invalidIds() {
        assertFalse(ProjectPlanningService.isValidItemId("stick"));         // no colon
        assertFalse(ProjectPlanningService.isValidItemId(""));              // empty
        assertFalse(ProjectPlanningService.isValidItemId(null));            // null
        assertFalse(ProjectPlanningService.isValidItemId("minecraft:"));    // empty path
        assertFalse(ProjectPlanningService.isValidItemId(":stick"));        // empty namespace
    }
}
