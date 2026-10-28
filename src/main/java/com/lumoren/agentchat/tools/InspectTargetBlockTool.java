package com.lumoren.agentchat.tools;

import com.google.gson.JsonArray;
import com.lumoren.agentchat.model.ActionCard;
import com.lumoren.agentchat.model.ToolDefinition;
import com.lumoren.agentchat.model.ToolResult;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

import java.util.ArrayList;
import java.util.List;

/**
 * Read-only inspection of the block the player is looking at.
 */
public class InspectTargetBlockTool implements GameTool {
    private static final String NAME = "inspect_target_block";
    private static final String DESCRIPTION =
            "Inspect the block the player is looking at: block ID, name, position, dimension, block entity type, and basic block state properties.";

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
        com.google.gson.JsonObject parameters = new com.google.gson.JsonObject();
        parameters.addProperty("type", "object");
        return new ToolDefinition(NAME, DESCRIPTION, parameters);
    }

    @Override
    public ToolResult execute(Minecraft mc, String arguments) {
        if (mc == null || mc.player == null || mc.level == null) {
            return card("No world", "Cannot inspect a target block because the player is not in a world.",
                    List.of("Not in a world."));
        }
        if (!(mc.hitResult instanceof BlockHitResult hit) || hit.getType() != HitResult.Type.BLOCK) {
            return card("No target block", "Look at a block and ask again.",
                    List.of("Current hit result is not a block."));
        }

        BlockPos pos = hit.getBlockPos();
        BlockState state = mc.level.getBlockState(pos);
        ResourceLocation blockId = BuiltInRegistries.BLOCK.getKey(state.getBlock());
        List<String> facts = new ArrayList<>();
        facts.add("Block ID: " + blockId);
        facts.add("Name: " + state.getBlock().getName().getString());
        facts.add("Position: " + pos.getX() + ", " + pos.getY() + ", " + pos.getZ());
        facts.add("Dimension: " + mc.level.dimension().location());

        JsonArray properties = new JsonArray();
        for (Property<?> property : state.getProperties()) {
            properties.add(property.getName() + "=" + state.getValue(property));
        }
        if (!properties.isEmpty()) {
            facts.add("State: " + join(properties));
        }

        BlockEntity blockEntity = mc.level.getBlockEntity(pos);
        if (blockEntity != null) {
            ResourceLocation typeId = BuiltInRegistries.BLOCK_ENTITY_TYPE.getKey(blockEntity.getType());
            facts.add("Block entity type: " + (typeId != null ? typeId : "unknown"));
        } else {
            facts.add("Block entity type: none");
        }

        facts.add("V1 caveat: energy, fluid, inventory, and mod-specific NBT internals are not inspected.");
        return card("Target block inspected",
                "Use these verified block facts to diagnose inputs, outputs, energy/fluid supply, side configuration, and relevant JEI recipe/usage.",
                facts);
    }

    private ToolResult card(String title, String summary, List<String> facts) {
        ActionCard card = new ActionCard(ActionCard.Type.DIAGNOSIS, title, summary,
                new ArrayList<>(facts), List.of(), NAME);
        return new ToolResult(NAME, card.toToolResultContent());
    }

    private static String join(JsonArray values) {
        List<String> parts = new ArrayList<>();
        for (var value : values) {
            parts.add(value.getAsString());
        }
        return String.join(", ", parts);
    }
}
