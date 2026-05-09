package com.lumoren.agentchat.config;

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
        // Read apiKey from JSON file
        // Parse JSON manually (lightweight, no extra dep)
        try {
            String content = Files.readString(secretsPath);
            apiKey = extractJsonValue(content, "apiKey");
            // NEVER log the API key
        } catch (IOException e) {
            System.err.println("Failed to load secrets config: " + e.getMessage());
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
        if (key == null) {
            this.apiKey = "";
        } else {
            saveApiKey(key);
        }
    }

    public void saveApiKey(String key) {
        try {
            String content = Files.readString(secretsPath);
            // Replace value while preserving structure
            int keyIdx = content.indexOf("\"apiKey\"");
            if (keyIdx >= 0) {
                int startQuote = content.indexOf('"', content.indexOf(':', keyIdx) + 1);
                int endQuote = content.indexOf('"', startQuote + 1);
                String updated = content.substring(0, startQuote + 1) + key + content.substring(endQuote);
                Files.writeString(secretsPath, updated);
            }
            this.apiKey = key;
        } catch (IOException e) {
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

    private static String extractJsonValue(String json, String key) {
        // Simple JSON string value extractor
        String search = "\"" + key + "\"";
        int keyIdx = json.indexOf(search);
        if (keyIdx < 0) return "";
        int colonIdx = json.indexOf(':', keyIdx + search.length());
        if (colonIdx < 0) return "";
        int startQuote = json.indexOf('"', colonIdx + 1);
        if (startQuote < 0) return "";
        int endQuote = json.indexOf('"', startQuote + 1);
        if (endQuote < 0) return "";
        return json.substring(startQuote + 1, endQuote);
    }
}
