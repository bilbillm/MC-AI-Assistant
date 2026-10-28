package com.lumoren.agentchat.tools;

import com.lumoren.agentchat.model.ActionCard;
import com.lumoren.agentchat.model.AssistantAction;
import com.lumoren.agentchat.model.Project;
import com.lumoren.agentchat.model.TaskStatus;
import com.lumoren.agentchat.persistence.ProjectManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class ProjectToolTest {

    @TempDir
    Path tempDir;

    @AfterEach
    void tearDown() {
        ProjectManager.shutdown();
    }

    @Test
    void createProject_returnsProjectActionCardAndSavesActiveProject() {
        ProjectManager.initialize(tempDir);
        ProjectTool tool = new ProjectTool();

        var result = tool.execute(null, """
                {
                  "action": "create_project",
                  "project_name": "Iron Farm",
                  "tasks": [
                    {"description": "Gather beds", "type": "GATHER", "items": [{"itemId": "minecraft:white_bed", "count": 3}]},
                    {"description": "Build platform", "type": "PLAN", "items": []}
                  ]
                }
                """);

        ActionCard card = ActionCard.fromToolResultContent(result.content());
        assertNotNull(card);
        assertEquals(ActionCard.Type.PROJECT, card.type());
        assertEquals("Project created", card.title());
        assertTrue(card.actions().stream()
                .anyMatch(a -> a.kind() == AssistantAction.Kind.MARK_TASK_DONE));
        assertTrue(card.actions().stream()
                .noneMatch(a -> a.kind() == AssistantAction.Kind.CANCEL_PROJECT));

        Project active = ProjectManager.getInstance().getActiveProject();
        assertNotNull(active);
        assertEquals("Iron Farm", active.getName());
        assertEquals(2, active.getTasks().size());
        assertEquals("Gather beds", active.getTasks().get(0).description());
    }

    @Test
    void addRemoveAndSetCurrentTask_updatesProject() {
        ProjectManager.initialize(tempDir);
        ProjectTool tool = new ProjectTool();
        tool.execute(null, """
                {"action":"create_project","project_name":"Base","tasks":[{"description":"Gather logs","type":"GATHER","items":[]}]}
                """);

        tool.execute(null, """
                {"action":"add_task","description":"Craft chests","type":"CRAFT","items":[{"itemId":"minecraft:chest","count":4}]}
                """);
        tool.execute(null, "{\"action\":\"set_current_task\",\"task_index\":2}");

        Project active = ProjectManager.getInstance().getActiveProject();
        assertEquals(2, active.getTasks().size());
        assertEquals(TaskStatus.IN_PROGRESS, active.getTasks().get(1).status());

        tool.execute(null, "{\"action\":\"remove_task\",\"task_index\":1}");
        active = ProjectManager.getInstance().getActiveProject();
        assertEquals(1, active.getTasks().size());
        assertEquals("Craft chests", active.getTasks().get(0).description());
    }

    @Test
    void getMissingMaterials_withNoMinecraftReturnsVerifiedMissingCard() {
        ProjectManager.initialize(tempDir);
        ProjectTool tool = new ProjectTool();
        tool.execute(null, """
                {
                  "action": "create_project",
                  "project_name": "Storage",
                  "tasks": [
                    {"description": "Craft chests", "type": "CRAFT", "items": [{"itemId": "minecraft:chest", "count": 4}]}
                  ]
                }
                """);

        var result = tool.execute(null, "{\"action\":\"get_missing_materials\"}");
        ActionCard card = ActionCard.fromToolResultContent(result.content());

        assertNotNull(card);
        assertEquals(ActionCard.Type.MATERIALS, card.type());
        assertTrue(card.summary().contains("missing"));
        assertTrue(card.facts().get(0).contains("minecraft:chest"));
    }

    @Test
    void invalidAction_returnsStatusCard() {
        ProjectManager.initialize(tempDir);
        ProjectTool tool = new ProjectTool();

        var result = tool.execute(null, "{\"action\":\"unknown\"}");
        ActionCard card = ActionCard.fromToolResultContent(result.content());

        assertNotNull(card);
        assertEquals(ActionCard.Type.STATUS, card.type());
        assertTrue(card.summary().contains("Unknown project action"));
    }
}
