package com.lumoren.agentchat.guide;

import net.minecraft.client.Minecraft;
import org.slf4j.Logger;
import com.mojang.logging.LogUtils;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;

/**
 * Asynchronous guide-request pipeline. Keeps the Minecraft main thread free
 * of file scans and HTTP:
 * <ol>
 *   <li>main thread: fast immutable {@link GuideSnapshot} (+ JEI/FTB evidence)</li>
 *   <li>background: resource/file knowledge sources</li>
 *   <li>background: web search with a hard timeout; local evidence proceeds without it</li>
 *   <li>main thread: single completion callback with the assembled guide prompt</li>
 * </ol>
 * Cancellation is token-based: a cancelled or superseded request never
 * invokes its callback, so stale results cannot bleed into a new
 * conversation thread or a reopened screen.
 */
public class GuideRequestCoordinator {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final long DEFAULT_WEB_TIMEOUT_MS = 4000;
    private static final long SNAPSHOT_SLOW_MS = 16;

    public enum Phase {
        COLLECTING_LOCAL,
        QUERYING_SOURCES,
        SEARCHING_WEB
    }

    public interface Callback {
        /** Called on the main thread as the pipeline advances. */
        void onPhase(Phase phase);

        /** Called once on the main thread with the assembled guide system prompt. */
        void onReady(long requestId, String guideSystemPrompt);

        /** Called once on the main thread if the pipeline failed; the caller should send without guide context. */
        void onFailed(long requestId, String reason);
    }

    /**
     * Latest assembled context, for tools (modpack_guide) that should reuse
     * the aggregation of the in-flight user request instead of rescanning.
     */
    public record CachedGuideContext(String userText, GuideContext context, long timestampMillis) {
    }

    private static final long CONTEXT_CACHE_TTL_MS = 60_000;

    private final ModpackGuideService guideService;
    private final Executor mainThreadExecutor;
    private final ExecutorService backgroundExecutor;
    private final long webTimeoutMs;
    private final AtomicLong idGenerator = new AtomicLong();
    private final Map<Long, GuideRequestState> active = new ConcurrentHashMap<>();
    private volatile CachedGuideContext latestContext;

    public GuideRequestCoordinator(ModpackGuideService guideService,
                                   Executor mainThreadExecutor,
                                   ExecutorService backgroundExecutor,
                                   long webTimeoutMs) {
        this.guideService = guideService;
        this.mainThreadExecutor = mainThreadExecutor;
        this.backgroundExecutor = backgroundExecutor;
        this.webTimeoutMs = webTimeoutMs;
    }

    public static GuideRequestCoordinator createDefault(ModpackGuideService guideService,
                                                        Executor mainThreadExecutor) {
        ExecutorService background = Executors.newFixedThreadPool(2, runnable -> {
            Thread thread = new Thread(runnable, "agentchat-guide-worker");
            thread.setDaemon(true);
            return thread;
        });
        return new GuideRequestCoordinator(guideService, mainThreadExecutor, background,
                DEFAULT_WEB_TIMEOUT_MS);
    }

    /**
     * Starts a guide request. Must be called on the Minecraft main thread.
     *
     * @return request id token; use {@link #cancel(long)} to abort
     */
    public long request(Minecraft mc, String userText, boolean guideMode, boolean webEnabled,
                        Callback callback) {
        long id = idGenerator.incrementAndGet();
        GuideRequestState state = new GuideRequestState(callback);
        active.put(id, state);

        callback.onPhase(Phase.COLLECTING_LOCAL);
        GuideSnapshot snapshot;
        long snapshotStart = System.nanoTime();
        try {
            snapshot = guideService.collectMainThreadSnapshot(mc, userText, webEnabled);
        } catch (RuntimeException e) {
            active.remove(id);
            LOGGER.warn("Guide snapshot failed; sending without guide context", e);
            callback.onFailed(id, "snapshot-failed");
            return id;
        }
        long snapshotMs = (System.nanoTime() - snapshotStart) / 1_000_000;
        if (snapshotMs > SNAPSHOT_SLOW_MS) {
            LOGGER.info("Guide snapshot took {} ms (budget {} ms) for intent {}",
                    snapshotMs, SNAPSHOT_SLOW_MS, snapshot.intent());
        }

        backgroundExecutor.execute(() -> runBackground(id, state, mc, userText, guideMode, snapshot));
        return id;
    }

    private void runBackground(long id, GuideRequestState state, Minecraft mc, String userText,
                               boolean guideMode, GuideSnapshot snapshot) {
        if (state.cancelled) {
            return;
        }
        postPhaseToMain(state, Phase.QUERYING_SOURCES);
        List<GuideEvidence> backgroundEvidence = safeQuery(
                () -> guideService.queryBackgroundSources(snapshot, mc, userText), "background");

        if (state.cancelled) {
            return;
        }
        List<GuideEvidence> webEvidence;
        if (!snapshot.webEnabled()) {
            webEvidence = guideService.webEvidence(snapshot, mc, userText, backgroundEvidence);
        } else {
            postPhaseToMain(state, Phase.SEARCHING_WEB);
            webEvidence = withTimeout(
                    () -> guideService.webEvidence(snapshot, mc, userText, backgroundEvidence),
                    webTimeoutMs, webTimeoutEvidence());
        }

        if (state.cancelled) {
            return;
        }
        GuideContext context = guideService.assembleContext(snapshot, backgroundEvidence, webEvidence);
        latestContext = new CachedGuideContext(userText, context, System.currentTimeMillis());
        String prompt = context.toSystemPrompt(guideMode, snapshot.webEnabled());
        List<GuideEvidence> finalWeb = webEvidence;
        mainThreadExecutor.execute(() -> {
            active.remove(id);
            if (!state.cancelled) {
                LOGGER.debug("Guide request {} ready: {} evidence, web={} entries",
                        id, context.evidence().size(), finalWeb.size());
                state.callback.onReady(id, prompt);
            }
        });
    }

    private void postPhaseToMain(GuideRequestState state, Phase phase) {
        if (state.cancelled) {
            return;
        }
        mainThreadExecutor.execute(() -> {
            if (!state.cancelled) {
                state.callback.onPhase(phase);
            }
        });
    }

    private List<GuideEvidence> safeQuery(Supplier<List<GuideEvidence>> query, String label) {
        try {
            List<GuideEvidence> result = query.get();
            return result == null ? List.of() : result;
        } catch (RuntimeException e) {
            LOGGER.warn("Guide {} source query failed; continuing without it", label, e);
            return List.of();
        }
    }

    private List<GuideEvidence> withTimeout(Supplier<List<GuideEvidence>> query, long timeoutMs,
                                            List<GuideEvidence> fallback) {
        Future<List<GuideEvidence>> future = backgroundExecutor.submit(query::get);
        try {
            List<GuideEvidence> result = future.get(timeoutMs, TimeUnit.MILLISECONDS);
            return result == null ? List.of() : result;
        } catch (TimeoutException e) {
            future.cancel(true);
            LOGGER.info("Guide web query exceeded {} ms; continuing with local evidence", timeoutMs);
            return fallback;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            future.cancel(true);
            return fallback;
        } catch (ExecutionException | RuntimeException e) {
            LOGGER.warn("Guide web query failed; continuing with local evidence", e);
            return fallback;
        }
    }

    private static List<GuideEvidence> webTimeoutEvidence() {
        return List.of(new GuideEvidence(GuideEvidence.SourceType.WEB,
                "Web/wiki search timed out",
                "Web search did not answer within the time budget; the answer relies on local evidence only.",
                "web_search:timeout", false));
    }

    /**
     * Cancels one request; its callback is never invoked again.
     */
    public void cancel(long requestId) {
        GuideRequestState state = active.remove(requestId);
        if (state != null) {
            state.cancelled = true;
        }
    }

    /**
     * Cancels all outstanding requests (screen close, thread switch, new send).
     */
    public void cancelAll() {
        for (GuideRequestState state : active.values()) {
            state.cancelled = true;
        }
        active.clear();
    }

    /**
     * Returns a recent assembled context for the same question, so tools do
     * not re-run the whole aggregation for the current user request.
     */
    public GuideContext latestContextFor(String userText) {
        CachedGuideContext cached = latestContext;
        if (cached == null || userText == null) {
            return null;
        }
        if (!cached.userText().equals(userText)) {
            return null;
        }
        if (System.currentTimeMillis() - cached.timestampMillis() > CONTEXT_CACHE_TTL_MS) {
            return null;
        }
        return cached.context();
    }

    private static final class GuideRequestState {
        final Callback callback;
        volatile boolean cancelled;

        GuideRequestState(Callback callback) {
            this.callback = callback;
        }
    }
}
