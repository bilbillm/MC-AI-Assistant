package com.lumoren.agentchat.ai;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Per-session logger for raw API interactions. Writes independent log files
 * to {@code session_logs/<threadId>.log} under the conversation directory.
 * <p>
 * Thread-safe: all writes are synchronized per session.
 */
public final class SessionLogger {

    private static final DateTimeFormatter TS = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static Path logRoot;
    private static final Map<String, BufferedWriter> writers = new ConcurrentHashMap<>();

    private SessionLogger() {}

    /** Initialize with the conversation save directory. Call once per world load. */
    public static void init(Path conversationDir) {
        closeAll();
        logRoot = conversationDir.resolve("session_logs");
        try { Files.createDirectories(logRoot); } catch (IOException ignored) {}
    }

    /** Clean up all open writers. Call on world unload. */
    public static void closeAll() {
        for (BufferedWriter w : writers.values()) {
            try { w.close(); } catch (IOException ignored) {}
        }
        writers.clear();
    }

    /** Log the user's raw input message. */
    public static void logUser(String threadId, String rawText) {
        write(threadId, "USER: " + rawText);
    }

    /** Log an API request body (non-streaming tool-call round). */
    public static void logApiRequest(String threadId, String body) {
        write(threadId, "REQUEST: " + body);
    }

    /** Log an API response body (non-streaming). */
    public static void logApiResponse(String threadId, String body) {
        write(threadId, "RESPONSE: " + body);
    }

    /** Log the accumulated streaming response when it completes. */
    public static void logStreamComplete(String threadId, String fullText) {
        write(threadId, "STREAM_COMPLETE: " + fullText);
    }

    private static void write(String threadId, String line) {
        if (logRoot == null) return;
        try {
            BufferedWriter w = writers.computeIfAbsent(threadId, id -> {
                try {
                    Path f = logRoot.resolve(sanitize(id) + ".log");
                    return Files.newBufferedWriter(f, StandardCharsets.UTF_8,
                            java.nio.file.StandardOpenOption.CREATE,
                            java.nio.file.StandardOpenOption.APPEND);
                } catch (IOException e) {
                    return null;
                }
            });
            if (w != null) {
                synchronized (w) {
                    w.write("[" + TS.format(LocalDateTime.now()) + "] " + line);
                    w.newLine();
                    w.flush();
                }
            }
        } catch (IOException ignored) {}
    }

    /** Replace filesystem-unsafe characters in thread IDs. */
    private static String sanitize(String id) {
        return id.replaceAll("[^a-zA-Z0-9._-]", "_");
    }
}
