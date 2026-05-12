package com.lumoren.agentchat.config;

import net.neoforged.neoforge.common.ModConfigSpec;

public class Config {
    public static final ModConfigSpec SPEC;

    // 6 config items with defaults and ranges
    public static final ModConfigSpec.ConfigValue<String> BASE_URL;
    public static final ModConfigSpec.ConfigValue<String> MODEL;
    public static final ModConfigSpec.DoubleValue TEMPERATURE;
    public static final ModConfigSpec.IntValue MAX_TOKENS;
    public static final ModConfigSpec.IntValue MAX_HISTORY;
    public static final ModConfigSpec.IntValue TIMEOUT;
    public static final ModConfigSpec.IntValue PROJECT_TRACK_INTERVAL;

    static {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();

        builder.comment("MC-AI-Assistant Configuration").push("agentchat");

        BASE_URL = builder
                .comment("OpenAI-compatible API endpoint URL")
                .define("baseUrl", "https://api.openai.com/v1");

        MODEL = builder
                .comment("Model name to use for completions")
                .define("model", "gpt-4o-mini");

        TEMPERATURE = builder
                .comment("Sampling temperature (0.0-2.0). Higher = more creative")
                .defineInRange("temperature", 0.7, 0.0, 2.0);

        MAX_TOKENS = builder
                .comment("Maximum tokens in AI response (100-4096)")
                .defineInRange("maxTokens", 1024, 100, 4096);

        MAX_HISTORY = builder
                .comment("Maximum conversation turns to keep in history (10-1000)")
                .defineInRange("maxHistory", 200, 10, 1000);

        TIMEOUT = builder
                .comment("HTTP request timeout in seconds (5-120)")
                .defineInRange("timeout", 30, 5, 120);

        PROJECT_TRACK_INTERVAL = builder
                .comment("Tick interval for project progress tracking (5-100). Default 20 = 1 second")
                .defineInRange("projectTrackInterval", 20, 5, 100);

        builder.pop();
        SPEC = builder.build();
    }

    /** Persist current in-memory config values to the TOML file on disk. */
    public static void save() {
        SPEC.save();
    }
}
