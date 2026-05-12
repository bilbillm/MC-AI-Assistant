package com.lumoren.agentchat.persistence;

import com.google.gson.*;
import com.google.gson.reflect.TypeToken;
import com.lumoren.agentchat.config.ConfigManager;
import com.lumoren.agentchat.model.ChatMessage;
import com.lumoren.agentchat.model.ConversationThread;

import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * 基于 JSON 的聊天记录持久化管理器。
 * <p>
 * 每个 Minecraft 存档拥有独立的对话历史目录：
 * {@code <worldSaveDir>/agentchat/conversations.json}。
 * 使用 Gson 进行序列化 / 反序列化，写入操作同步加锁保证线程安全。
 * 保存时自动根据 {@link ConfigManager#getMaxHistory()} 裁剪消息数量。
 */
public class ChatHistoryManager {

    private static final String FILE_NAME = "conversations.json";

    private final Path saveDir;
    private final Path saveFile;

    public ChatHistoryManager(Path worldSaveDir) {
        this.saveDir = worldSaveDir.resolve("agentchat");
        this.saveFile = saveDir.resolve(FILE_NAME);
    }

    /**
     * 将对话列表持久化到 JSON 文件，并自动裁剪超出 maxHistory 的消息。
     *
     * @param threads 要保存的对话线程列表
     */
    public synchronized void save(List<ConversationThread> threads) {
        try {
            Files.createDirectories(saveDir);
            Gson gson = createGson();
            String json = gson.toJson(threads);
            Files.writeString(saveFile, json);
        } catch (IOException e) {
            throw new RuntimeException("Failed to save chat history", e);
        }
    }

    /**
     * 从 JSON 文件加载对话列表。若文件不存在则返回空列表。
     *
     * @return 已持久化的对话线程列表
     */
    public synchronized List<ConversationThread> load() {
        if (!Files.exists(saveFile)) {
            return new ArrayList<>();
        }
        try {
            String json = Files.readString(saveFile);
            Gson gson = createGson();
            Type listType = new TypeToken<List<ConversationThread>>() {}.getType();
            List<ConversationThread> threads = gson.fromJson(json, listType);
            if (threads == null) {
                return new ArrayList<>();
            }
            // Filter out threads that failed to deserialize (returned null by adapter)
            List<ConversationThread> valid = new ArrayList<>();
            for (ConversationThread t : threads) {
                if (t != null) {
                    valid.add(t);
                }
            }
            return valid;
        } catch (IOException e) {
            throw new RuntimeException("Failed to load chat history", e);
        } catch (JsonSyntaxException | JsonIOException e) {
            System.err.println("[AgentChat] Corrupt conversations.json — starting fresh: " + e.getMessage());
            return new ArrayList<>();
        }
    }

    /**
     * 删除指定 ID 的对话线程并重新保存。
     *
     * @param threadId 要删除的线程 ID
     */
    public synchronized void delete(String threadId) {
        List<ConversationThread> threads = load();
        threads.removeIf(t -> t.getId().equals(threadId));
        save(threads);
    }

    private Gson createGson() {
        int maxHistory = ConfigManager.getInstance().getMaxHistory();
        return new GsonBuilder()
                .setPrettyPrinting()
                .registerTypeAdapter(ConversationThread.class, new ConversationThreadAdapter(maxHistory))
                .create();
    }

    /**
     * Gson 自定义适配器：处理 {@link ConversationThread} 的序列化 / 反序列化，
     * 并在序列化时按 maxHistory 裁剪消息列表。
     */
    private static class ConversationThreadAdapter implements JsonSerializer<ConversationThread>, JsonDeserializer<ConversationThread> {

        private final int maxHistory;

        ConversationThreadAdapter(int maxHistory) {
            this.maxHistory = maxHistory;
        }

        @Override
        public JsonElement serialize(ConversationThread src, Type typeOfSrc, JsonSerializationContext context) {
            JsonObject obj = new JsonObject();
            obj.addProperty("id", src.getId());
            obj.addProperty("name", src.getName());

            List<ChatMessage> messages = src.getMessages();
            if (messages.size() > maxHistory) {
                messages = messages.subList(messages.size() - maxHistory, messages.size());
            }
            obj.add("messages", context.serialize(messages));

            obj.addProperty("createdAt", src.getCreatedAt().toString());
            obj.addProperty("updatedAt", src.getUpdatedAt().toString());
            return obj;
        }

        @Override
        public ConversationThread deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context) throws JsonParseException {
            try {
                JsonObject obj = json.getAsJsonObject();
                
                // Validate required fields exist and are strings
                if (!obj.has("id") || obj.get("id").isJsonNull()) {
                    System.err.println("[AgentChat] Skipping corrupt thread: missing 'id' field");
                    return null;
                }
                if (!obj.has("name") || obj.get("name").isJsonNull()) {
                    System.err.println("[AgentChat] Skipping corrupt thread: missing 'name' field");
                    return null;
                }
                
                String id = obj.get("id").getAsString();
                String name = obj.get("name").getAsString();

                List<ChatMessage> messages;
                if (obj.has("messages") && !obj.get("messages").isJsonNull()) {
                    Type listType = new TypeToken<List<ChatMessage>>() {}.getType();
                    messages = context.deserialize(obj.get("messages"), listType);
                } else {
                    messages = new ArrayList<>();
                }

                // Use defaults for missing/corrupt timestamps
                Instant createdAt = Instant.now();
                Instant updatedAt = Instant.now();
                if (obj.has("createdAt") && !obj.get("createdAt").isJsonNull()) {
                    try {
                        createdAt = Instant.parse(obj.get("createdAt").getAsString());
                    } catch (Exception e) {
                        System.err.println("[AgentChat] Invalid 'createdAt' for thread " + id + " — using current time");
                    }
                }
                if (obj.has("updatedAt") && !obj.get("updatedAt").isJsonNull()) {
                    try {
                        updatedAt = Instant.parse(obj.get("updatedAt").getAsString());
                    } catch (Exception e) {
                        System.err.println("[AgentChat] Invalid 'updatedAt' for thread " + id + " — using current time");
                    }
                }

                return new ConversationThread(id, name, messages, createdAt, updatedAt);
            } catch (RuntimeException e) {
                System.err.println("[AgentChat] Skipping corrupt thread: " + e.getMessage());
                return null;
            }
        }
    }
}
