package com.lumoren.agentchat.client;

import com.lumoren.agentchat.tools.ToolRegistry;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertTrue;

class ClientServiceManagerToolRegistryTest {
    @Test
    void createToolRegistry_exposesV1GuideToolsToAi() throws Exception {
        Method method = ClientServiceManager.class.getDeclaredMethod("createToolRegistry");
        method.setAccessible(true);

        ToolRegistry registry = (ToolRegistry) method.invoke(null);
        Set<String> names = registry.getAll().stream()
                .map(tool -> tool.getName())
                .collect(Collectors.toSet());

        assertTrue(names.contains("modpack_guide"));
        assertTrue(names.contains("inspect_target_block"));
        assertTrue(names.contains("open_item_recipe"));
        assertTrue(names.contains("open_item_usage"));
        assertTrue(names.contains("web_search"));
        assertTrue(names.contains("read_webpage"));
        assertTrue(names.contains("list_mods"));
        assertTrue(names.contains("calculate_materials"));
    }
}
