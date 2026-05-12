package com.lumoren.agentchat.config;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class SecretsConfig {
    private static final String CONFIG_PATH = "config/agentchat-secrets.json";
    private static final String TEMPLATE = """
        {
          "_comment": "MC-AI-Assistant API Key Configuration",
          "_help": "Get your API key from the OpenAI dashboard or your provider",
          "apiKey": "sk-your-api-key-here"
        }""";

    private final Path secretsPath;
    private String apiKey = "";

    public SecretsConfig() {
        this(Paths.get(CONFIG_PATH));
    }

    SecretsConfig(Path secretsPath) {
        this.secretsPath = secretsPath;
        if (!Files.exists(secretsPath)) {
            createTemplate(secretsPath);
        }
    }

    public void load() {
        try {
            String content = Files.readString(secretsPath);
            JsonObject json = JsonParser.parseString(content).getAsJsonObject();
            if (json.has("apiKey")) {
                apiKey = json.get("apiKey").getAsString();
            }
        } catch (IOException | JsonSyntaxException e) {
            System.err.println("Failed to load secrets config: " + e.getMessage());
            apiKey = "";
        }
    }

    public String getApiKey() { return apiKey; }

    public boolean hasApiKey() {
        return apiKey != null && !apiKey.isBlank();
    }

    public Path getSecretsPath() {
        return secretsPath;
    }

    public void setApiKey(String key) {
        saveApiKey(key);
    }

    public void saveApiKey(String key) {
        if (key == null) {
            key = "";
        }
        try {
            String content = Files.readString(secretsPath);
            JsonObject json = JsonParser.parseString(content).getAsJsonObject();
            json.addProperty("apiKey", key);
            String prettyJson = new GsonBuilder().setPrettyPrinting().create().toJson(json);
            Files.writeString(secretsPath, prettyJson);
            this.apiKey = key;
        } catch (IOException | JsonSyntaxException e) {
            System.err.println("Failed to save API key: " + e.getMessage());
        }
    }

    private void createTemplate(Path path) {
        try {
            Files.createDirectories(path.getParent());
            Files.writeString(path, TEMPLATE);
        } catch (IOException e) {
            System.err.println("Failed to create secrets template: " + e.getMessage());
        }
    }
}
