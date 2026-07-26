package com.lumoren.agentchat.guide;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GuideRequestCoordinatorTest {

    private ExecutorService background;

    @AfterEach
    void tearDown() {
        if (background != null) {
            background.shutdownNow();
        }
    }

    private GuideRequestCoordinator coordinator(ModpackGuideService service, long webTimeoutMs,
                                                List<GuideRequestCoordinator.Phase> phases,
                                                AtomicBoolean callbackOnMainExecutor) {
        background = Executors.newFixedThreadPool(2, r -> {
            Thread t = new Thread(r, "test-guide-worker");
            t.setDaemon(true);
            return t;
        });
        return new GuideRequestCoordinator(service, runnable -> {
            if (callbackOnMainExecutor != null) {
                callbackOnMainExecutor.set(true);
            }
            runnable.run();
        }, background, webTimeoutMs);
    }

    private static GuideKnowledgeSource fakeSource(GuideEvidence.SourceType type, String title,
                                                   boolean mainThread, boolean verified) {
        return new GuideKnowledgeSource() {
            @Override
            public boolean isAvailable(net.minecraft.client.Minecraft mc) {
                return true;
            }

            @Override
            public List<GuideEvidence> query(net.minecraft.client.Minecraft mc, GuideIntent intent, String text) {
                return List.of(new GuideEvidence(type, title, "summary of " + title,
                        type.name().toLowerCase() + ":" + title, verified));
            }

            @Override
            public boolean requiresMainThread() {
                return mainThread;
            }
        };
    }

    private static final class RecordingCallback implements GuideRequestCoordinator.Callback {
        final List<GuideRequestCoordinator.Phase> phases = Collections.synchronizedList(new ArrayList<>());
        volatile String prompt;
        volatile String failure;
        final CountDownLatch done = new CountDownLatch(1);

        @Override
        public void onPhase(GuideRequestCoordinator.Phase phase) {
            phases.add(phase);
        }

        @Override
        public void onReady(long requestId, String guideSystemPrompt) {
            this.prompt = guideSystemPrompt;
            done.countDown();
        }

        @Override
        public void onFailed(long requestId, String reason) {
            this.failure = reason;
            done.countDown();
        }
    }

    @Test
    void requestAssemblesPromptAcrossPhases() throws Exception {
        ModpackGuideService service = new ModpackGuideService(List.of(
                fakeSource(GuideEvidence.SourceType.LOCAL_GAME, "LocalCtx", true, true),
                fakeSource(GuideEvidence.SourceType.PATCHOULI, "BookPage", false, true),
                new WebKnowledgeSource(() -> true, (mc, q) -> "wiki result")
        ));
        AtomicBoolean onMain = new AtomicBoolean(false);
        GuideRequestCoordinator coordinator = coordinator(service, 2000, null, onMain);
        RecordingCallback callback = new RecordingCallback();

        coordinator.request(null, "minecraft:piston 怎么做", true, true, callback);

        assertTrue(callback.done.await(5, TimeUnit.SECONDS), "request should complete");
        assertNull(callback.failure);
        assertNotNull(callback.prompt);
        assertTrue(callback.prompt.contains("LocalCtx"));
        assertTrue(callback.prompt.contains("BookPage"));
        assertTrue(callback.prompt.contains("Web/wiki search:"));
        assertEquals(List.of(
                GuideRequestCoordinator.Phase.COLLECTING_LOCAL,
                GuideRequestCoordinator.Phase.QUERYING_SOURCES,
                GuideRequestCoordinator.Phase.SEARCHING_WEB), callback.phases);
        assertTrue(onMain.get(), "callbacks must be dispatched via the main-thread executor");
    }

    @Test
    void cancelDropsCompletionCallback() throws Exception {
        CountDownLatch gate = new CountDownLatch(1);
        GuideKnowledgeSource slowBackground = new GuideKnowledgeSource() {
            @Override
            public boolean isAvailable(net.minecraft.client.Minecraft mc) {
                return true;
            }

            @Override
            public List<GuideEvidence> query(net.minecraft.client.Minecraft mc, GuideIntent intent, String text) {
                try {
                    gate.await(5, TimeUnit.SECONDS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                return List.of(new GuideEvidence(GuideEvidence.SourceType.PATCHOULI,
                        "Slow", "slow", "slow", true));
            }

            @Override
            public boolean requiresMainThread() {
                return false;
            }
        };
        ModpackGuideService service = new ModpackGuideService(List.of(slowBackground));
        GuideRequestCoordinator coordinator = coordinator(service, 2000, null, null);
        RecordingCallback callback = new RecordingCallback();

        long id = coordinator.request(null, "下一步", true, false, callback);
        coordinator.cancel(id);
        gate.countDown();

        assertFalse(callback.done.await(2, TimeUnit.SECONDS),
                "cancelled request must never invoke onReady/onFailed");
        assertNull(callback.prompt);
    }

    @Test
    void cancelAllDropsAllCallbacks() throws Exception {
        CountDownLatch gate = new CountDownLatch(1);
        GuideKnowledgeSource slow = new GuideKnowledgeSource() {
            @Override
            public boolean isAvailable(net.minecraft.client.Minecraft mc) {
                return true;
            }

            @Override
            public List<GuideEvidence> query(net.minecraft.client.Minecraft mc, GuideIntent intent, String text) {
                try {
                    gate.await(5, TimeUnit.SECONDS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                return List.of();
            }

            @Override
            public boolean requiresMainThread() {
                return false;
            }
        };
        GuideRequestCoordinator coordinator = coordinator(new ModpackGuideService(List.of(slow)), 2000, null, null);
        RecordingCallback first = new RecordingCallback();
        RecordingCallback second = new RecordingCallback();

        coordinator.request(null, "下一步", true, false, first);
        coordinator.request(null, "怎么做 minecraft:piston", true, false, second);
        coordinator.cancelAll();
        gate.countDown();

        assertFalse(first.done.await(2, TimeUnit.SECONDS));
        assertFalse(second.done.await(2, TimeUnit.SECONDS));
    }

    @Test
    void webTimeoutStillAnswersWithLocalEvidence() throws Exception {
        WebKnowledgeSource slowWeb = new WebKnowledgeSource(() -> true, (mc, q) -> {
            try {
                Thread.sleep(10_000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            return "late result that must not be used";
        });
        ModpackGuideService service = new ModpackGuideService(List.of(
                fakeSource(GuideEvidence.SourceType.LOCAL_GAME, "LocalCtx", true, true),
                slowWeb));
        GuideRequestCoordinator coordinator = coordinator(service, 100, null, null);
        RecordingCallback callback = new RecordingCallback();

        long start = System.currentTimeMillis();
        coordinator.request(null, "minecraft:piston 怎么做", true, true, callback);

        assertTrue(callback.done.await(5, TimeUnit.SECONDS), "timeout must not block the answer");
        assertTrue(System.currentTimeMillis() - start < 5000, "answer should not wait for the slow web");
        assertNotNull(callback.prompt);
        assertTrue(callback.prompt.contains("LocalCtx"));
        assertTrue(callback.prompt.contains("Web/wiki search timed out"));
        assertFalse(callback.prompt.contains("late result"));
    }

    @Test
    void webDisabledSkipsHttpAndStatesBoundary() throws Exception {
        AtomicInteger httpCalls = new AtomicInteger();
        WebKnowledgeSource web = new WebKnowledgeSource(() -> false, (mc, q) -> {
            httpCalls.incrementAndGet();
            return "should never happen";
        });
        ModpackGuideService service = new ModpackGuideService(List.of(
                fakeSource(GuideEvidence.SourceType.LOCAL_GAME, "LocalCtx", true, true),
                web));
        GuideRequestCoordinator coordinator = coordinator(service, 500, null, null);
        RecordingCallback callback = new RecordingCallback();

        coordinator.request(null, "minecraft:piston 怎么做", true, false, callback);

        assertTrue(callback.done.await(5, TimeUnit.SECONDS));
        assertEquals(0, httpCalls.get(), "web disabled must not issue HTTP");
        assertNotNull(callback.prompt);
        assertTrue(callback.prompt.contains("Web/wiki disabled"));
    }

    @Test
    void latestContextIsReusableOnlyForSameQuestion() throws Exception {
        ModpackGuideService service = new ModpackGuideService(List.of(
                fakeSource(GuideEvidence.SourceType.LOCAL_GAME, "LocalCtx", true, true)));
        GuideRequestCoordinator coordinator = coordinator(service, 500, null, null);
        RecordingCallback callback = new RecordingCallback();

        coordinator.request(null, "下一步", true, false, callback);
        assertTrue(callback.done.await(5, TimeUnit.SECONDS));

        assertNotNull(coordinator.latestContextFor("下一步"));
        assertNull(coordinator.latestContextFor("别的问题"));
        assertNull(coordinator.latestContextFor(null));
    }

    @Test
    void snapshotFailureFallsBackToPlainSend() {
        GuideKnowledgeSource broken = new GuideKnowledgeSource() {
            @Override
            public boolean isAvailable(net.minecraft.client.Minecraft mc) {
                return true;
            }

            @Override
            public List<GuideEvidence> query(net.minecraft.client.Minecraft mc, GuideIntent intent, String text) {
                throw new IllegalStateException("boom");
            }
        };
        // A source that throws during the main-thread phase: the coordinator
        // itself catches via collectMainThreadSnapshot's callers, so simulate
        // a service-level failure instead.
        ModpackGuideService service = new ModpackGuideService(List.of(broken)) {
            @Override
            public GuideSnapshot collectMainThreadSnapshot(net.minecraft.client.Minecraft mc,
                                                           String userText, boolean webEnabled) {
                throw new IllegalStateException("snapshot boom");
            }
        };
        GuideRequestCoordinator coordinator = coordinator(service, 500, null, null);
        RecordingCallback callback = new RecordingCallback();

        coordinator.request(null, "下一步", true, false, callback);

        assertEquals("snapshot-failed", callback.failure);
        assertNull(callback.prompt);
    }
}
