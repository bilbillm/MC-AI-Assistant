package com.lumoren.agentchat.config;

public class ConfigManager {
    private static volatile ConfigManager instance;
    private final SecretsConfig secrets;

    private ConfigManager() {
        this.secrets = new SecretsConfig();
        this.secrets.load();
    }

    public static ConfigManager getInstance() {
        if (instance == null) {
            instance = new ConfigManager();
        }
        return instance;
    }

    public String getBaseUrl() { return Config.BASE_URL.get(); }
    public String getModel() { return Config.MODEL.get(); }
    public double getTemperature() { return Config.TEMPERATURE.get(); }
    public int getMaxTokens() { return Config.MAX_TOKENS.get(); }
    public int getMaxHistory() { return Config.MAX_HISTORY.get(); }
    public int getTimeout() { return Config.TIMEOUT.get(); }
    public int getProjectTrackInterval() { return Config.PROJECT_TRACK_INTERVAL.get(); }
    public String getApiKey() { return secrets.getApiKey(); }
    public boolean hasApiKey() { return secrets.getApiKey() != null && !secrets.getApiKey().isBlank(); }
    public SecretsConfig getSecrets() { return secrets; }
}
