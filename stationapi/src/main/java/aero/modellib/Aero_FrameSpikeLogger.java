package aero.modellib;

import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.lang.management.ThreadMXBean;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.List;

import aero.modellib.render.Aero_AnimationRenderBudget;
import aero.modellib.render.Aero_AnimationTickBudget;
import aero.modellib.render.Aero_FrustumCull;
import aero.modellib.render.Aero_RenderLoadGovernor;

/**
 * Opt-in frame spike logger for dense BE scenes. It samples once per render
 * frame and prints a compact diagnostic line when the previous frame exceeded
 * the configured threshold.
 */
public final class Aero_FrameSpikeLogger {

    public static final boolean ENABLED =
        "true".equalsIgnoreCase(System.getProperty("aero.spikelog"));
    private static final boolean TIMING_ENABLED =
        ENABLED
        || Aero_AnimationRenderBudget.framePressureThrottleEnabled()
        || Aero_RenderLoadGovernor.enabled();

    private static final double THRESHOLD_MS =
        doubleProperty("aero.spikelog.ms", 25.0d, 1.0d, 10000.0d);
    private static final long MIN_INTERVAL_NS = (long)
        (doubleProperty("aero.spikelog.intervalMs", 0.0d, 0.0d, 60000.0d) * 1000000.0d);
    private static final long HEARTBEAT_NS = (long)
        (doubleProperty("aero.spikelog.heartbeatMs", 5000.0d, 0.0d, 600000.0d) * 1000000.0d);
    private static final double FLUSH_THRESHOLD_MS =
        doubleProperty("aero.spikelog.flushMs", 4.0d, 0.0d, 10000.0d);
    private static final boolean LOG_GC =
        !"false".equalsIgnoreCase(System.getProperty("aero.spikelog.gc"));
    private static final boolean SYNC_WRITES =
        "true".equalsIgnoreCase(System.getProperty("aero.spikelog.sync"));
    private static final String LOG_FILE =
        System.getProperty("aero.spikelog.file", "aero-frame-spikes.log");

    private static long lastFrameStartNs;
    private static long lastLogNs;
    private static long lastHeartbeatNs;
    private static long lastGcCount = -1L;
    private static long lastGcTimeMs = -1L;
    private static long lastThreadCpuNs = -1L;
    private static long lastThreadAllocBytes = -1L;
    private static long lastFrameCpuNs;
    private static long lastFrameAllocBytes;
    private static long gameRendererUpdateStartNs;
    private static long gameRendererUpdateStartAllocBytes;
    private static long lastGameRendererUpdateNs;
    private static long lastGameRendererUpdateAllocBytes;
    private static long renderWorldStartNs;
    private static long renderWorldStartAllocBytes;
    private static long lastRenderWorldNs;
    private static long lastRenderWorldAllocBytes;
    private static long aeroRenderPrepStartNs;
    private static long lastAeroRenderPrepNs;
    private static long renderEntitiesStartNs;
    private static long renderEntitiesStartAllocBytes;
    private static long lastRenderEntitiesNs;
    private static long lastRenderEntitiesAllocBytes;
    private static long clientTickStartNs;
    private static long clientTickStartAllocBytes;
    private static long lastClientTickNs;
    private static long lastClientTickAllocBytes;
    private static long displayUpdateStartNs;
    private static long displayUpdateStartAllocBytes;
    private static long lastDisplayUpdateNs;
    private static long lastDisplayUpdateAllocBytes;
    private static long profilerChartStartNs;
    private static long lastProfilerChartNs;
    private static long worldSaveStartNs;
    private static long worldSaveStartAllocBytes;
    private static long lastWorldSaveNs;
    private static long lastWorldSaveAllocBytes;
    private static long worldSaveSkipped;
    private static long chunkCompileStartNs;
    private static long chunkCompileStartAllocBytes;
    private static long lastChunkCompileNs;
    private static long lastChunkCompileAllocBytes;
    private static long lastChunkCompileMaxNs;
    private static long chunkCompileCalls;
    private static long chunkCompileSkipped;
    private static long slowChunkCompiles;
    private static long renderChunksStartNs;
    private static long renderChunksStartAllocBytes;
    private static long lastRenderChunksNs;
    private static long lastRenderChunksAllocBytes;
    private static long lastRenderChunksMaxNs;
    private static long renderChunksCalls;
    private static long worldFlushStartAllocBytes;
    private static long lastWorldFlushNs;
    private static long lastWorldFlushAllocBytes;
    private static long slowWorldFlushes;
    private static final List GC_BEANS = ManagementFactory.getGarbageCollectorMXBeans();
    private static final ThreadMXBean THREAD_BEAN =
        ManagementFactory.getThreadMXBean();
    private static final boolean THREAD_CPU_SUPPORTED =
        THREAD_BEAN.isCurrentThreadCpuTimeSupported();
    private static final com.sun.management.ThreadMXBean ALLOC_BEAN =
        ManagementFactory.getPlatformMXBean(com.sun.management.ThreadMXBean.class);
    private static final boolean THREAD_ALLOC_SUPPORTED =
        ALLOC_BEAN != null && ALLOC_BEAN.isThreadAllocatedMemorySupported();
    private static PrintWriter fileLog;
    private static boolean fileLogFailed;

    static {
        if (THREAD_ALLOC_SUPPORTED && !ALLOC_BEAN.isThreadAllocatedMemoryEnabled()) {
            try {
                ALLOC_BEAN.setThreadAllocatedMemoryEnabled(true);
            } catch (UnsupportedOperationException ignored) {
                // Allocation counters are diagnostic-only; timing still works.
            }
        }
    }

    private Aero_FrameSpikeLogger() {}

    public static void beginFrame() {
        if (!TIMING_ENABLED) return;
        long now = System.nanoTime();
        long gcCount = gcCollectionCount();
        long gcTimeMs = gcCollectionTimeMs();
        long threadCpuNs = currentThreadCpuTimeNs();
        long threadAllocBytes = currentThreadAllocatedBytes();
        if (lastGcCount < 0L) {
            lastGcCount = gcCount;
            lastGcTimeMs = gcTimeMs;
            lastThreadCpuNs = threadCpuNs;
            lastThreadAllocBytes = threadAllocBytes;
        }
        if (lastFrameStartNs != 0L) {
            double frameMs = (now - lastFrameStartNs) / 1000000.0d;
            long gcCountDelta = positiveDelta(gcCount, lastGcCount);
            long gcTimeDelta = positiveDelta(gcTimeMs, lastGcTimeMs);
            lastFrameCpuNs = threadCpuNs >= 0L && lastThreadCpuNs >= 0L
                ? positiveDelta(threadCpuNs, lastThreadCpuNs)
                : -1L;
            lastFrameAllocBytes = threadAllocBytes >= 0L && lastThreadAllocBytes >= 0L
                ? positiveDelta(threadAllocBytes, lastThreadAllocBytes)
                : -1L;
            Aero_AnimationRenderBudget.recordFramePressure(frameMs,
                lastDisplayUpdateNs / 1000000.0d,
                lastRenderChunksNs / 1000000.0d,
                gcTimeDelta);
            Aero_RenderLoadGovernor.recordFramePressure(frameMs,
                lastDisplayUpdateNs / 1000000.0d,
                lastRenderChunksNs / 1000000.0d,
                lastRenderEntitiesNs / 1000000.0d,
                gcTimeDelta,
                Aero_ChunkVisibility.visibleChunkCount());
            Aero_FramePacer.recordFramePressure(frameMs,
                lastDisplayUpdateNs / 1000000.0d);
            if (ENABLED && frameMs >= THRESHOLD_MS
                && (MIN_INTERVAL_NS == 0L || now - lastLogNs >= MIN_INTERVAL_NS)) {
                lastLogNs = now;
                logSpike(frameMs, gcCountDelta, gcTimeDelta);
            } else if (ENABLED && LOG_GC && gcCountDelta > 0L) {
                logEvent("GC", frameMs, gcCountDelta, gcTimeDelta);
            } else if (ENABLED && HEARTBEAT_NS > 0L && now - lastHeartbeatNs >= HEARTBEAT_NS) {
                lastHeartbeatNs = now;
                logEvent("Pulse", frameMs, gcCountDelta, gcTimeDelta);
            }
        }
        resetFrameStageCounters();
        lastFrameStartNs = now;
        lastGcCount = gcCount;
        lastGcTimeMs = gcTimeMs;
        lastThreadCpuNs = threadCpuNs;
        lastThreadAllocBytes = threadAllocBytes;
        gameRendererUpdateStartNs = now;
        gameRendererUpdateStartAllocBytes = threadAllocBytes;
        Aero_AnimatedBatcher.beginFrameCounters();
        Aero_MeshRenderer.beginFrameCounters();
    }

    public static void endGameRendererUpdate() {
        if (!TIMING_ENABLED || gameRendererUpdateStartNs == 0L) return;
        lastGameRendererUpdateNs = System.nanoTime() - gameRendererUpdateStartNs;
        lastGameRendererUpdateAllocBytes =
            allocDeltaSince(gameRendererUpdateStartAllocBytes);
        gameRendererUpdateStartNs = 0L;
        gameRendererUpdateStartAllocBytes = -1L;
    }

    public static void beginRenderWorld() {
        if (!TIMING_ENABLED) return;
        renderWorldStartNs = System.nanoTime();
        renderWorldStartAllocBytes = currentThreadAllocatedBytes();
    }

    public static void endRenderWorld() {
        if (!TIMING_ENABLED || renderWorldStartNs == 0L) return;
        lastRenderWorldNs = System.nanoTime() - renderWorldStartNs;
        lastRenderWorldAllocBytes = allocDeltaSince(renderWorldStartAllocBytes);
        renderWorldStartNs = 0L;
        renderWorldStartAllocBytes = -1L;
    }

    public static long beginAeroRenderPrep() {
        return TIMING_ENABLED ? System.nanoTime() : 0L;
    }

    public static void endAeroRenderPrep(long startNs) {
        if (!TIMING_ENABLED || startNs == 0L) return;
        lastAeroRenderPrepNs = System.nanoTime() - startNs;
    }

    public static void beginRenderEntities() {
        if (!TIMING_ENABLED) return;
        renderEntitiesStartNs = System.nanoTime();
        renderEntitiesStartAllocBytes = currentThreadAllocatedBytes();
    }

    public static void endRenderEntitiesBeforeAeroFlush() {
        if (!TIMING_ENABLED || renderEntitiesStartNs == 0L) return;
        lastRenderEntitiesNs = System.nanoTime() - renderEntitiesStartNs;
        lastRenderEntitiesAllocBytes = allocDeltaSince(renderEntitiesStartAllocBytes);
        renderEntitiesStartNs = 0L;
        renderEntitiesStartAllocBytes = -1L;
    }

    public static void beginClientTick() {
        if (!TIMING_ENABLED) return;
        clientTickStartNs = System.nanoTime();
        clientTickStartAllocBytes = currentThreadAllocatedBytes();
    }

    public static void endClientTick() {
        if (!TIMING_ENABLED || clientTickStartNs == 0L) return;
        lastClientTickNs = System.nanoTime() - clientTickStartNs;
        lastClientTickAllocBytes = allocDeltaSince(clientTickStartAllocBytes);
        clientTickStartNs = 0L;
        clientTickStartAllocBytes = -1L;
    }

    public static void beginDisplayUpdate() {
        if (!TIMING_ENABLED) return;
        displayUpdateStartNs = System.nanoTime();
        displayUpdateStartAllocBytes = currentThreadAllocatedBytes();
    }

    public static void endDisplayUpdate() {
        if (!TIMING_ENABLED || displayUpdateStartNs == 0L) return;
        lastDisplayUpdateNs = System.nanoTime() - displayUpdateStartNs;
        lastDisplayUpdateAllocBytes = allocDeltaSince(displayUpdateStartAllocBytes);
        displayUpdateStartNs = 0L;
        displayUpdateStartAllocBytes = -1L;
    }

    public static void beginProfilerChart() {
        if (!TIMING_ENABLED) return;
        profilerChartStartNs = System.nanoTime();
    }

    public static void endProfilerChart() {
        if (!TIMING_ENABLED || profilerChartStartNs == 0L) return;
        lastProfilerChartNs = System.nanoTime() - profilerChartStartNs;
        profilerChartStartNs = 0L;
    }

    public static void beginWorldSave() {
        if (!TIMING_ENABLED) return;
        worldSaveStartNs = System.nanoTime();
        worldSaveStartAllocBytes = currentThreadAllocatedBytes();
    }

    public static void endWorldSave() {
        if (!TIMING_ENABLED || worldSaveStartNs == 0L) return;
        lastWorldSaveNs = System.nanoTime() - worldSaveStartNs;
        lastWorldSaveAllocBytes = allocDeltaSince(worldSaveStartAllocBytes);
        worldSaveStartNs = 0L;
        worldSaveStartAllocBytes = -1L;
    }

    public static void skipWorldSave() {
        if (!TIMING_ENABLED) return;
        worldSaveSkipped++;
    }

    public static void beginChunkCompile() {
        if (!TIMING_ENABLED) return;
        chunkCompileStartNs = System.nanoTime();
        chunkCompileStartAllocBytes = currentThreadAllocatedBytes();
    }

    public static void skipChunkCompile() {
        if (!TIMING_ENABLED) return;
        chunkCompileSkipped++;
    }

    public static void endChunkCompile() {
        if (!TIMING_ENABLED || chunkCompileStartNs == 0L) return;
        long elapsedNs = System.nanoTime() - chunkCompileStartNs;
        lastChunkCompileNs += elapsedNs;
        if (elapsedNs > lastChunkCompileMaxNs) {
            lastChunkCompileMaxNs = elapsedNs;
        }
        chunkCompileCalls++;
        long allocBytes = allocDeltaSince(chunkCompileStartAllocBytes);
        if (allocBytes > 0L) lastChunkCompileAllocBytes += allocBytes;
        chunkCompileStartNs = 0L;
        chunkCompileStartAllocBytes = -1L;
        if (elapsedNs >= (long) (FLUSH_THRESHOLD_MS * 1000000.0d)) {
            slowChunkCompiles++;
        }
    }

    public static void beginRenderChunks() {
        if (!TIMING_ENABLED) return;
        renderChunksStartNs = System.nanoTime();
        renderChunksStartAllocBytes = currentThreadAllocatedBytes();
    }

    public static void endRenderChunks() {
        if (!TIMING_ENABLED || renderChunksStartNs == 0L) return;
        long elapsedNs = System.nanoTime() - renderChunksStartNs;
        lastRenderChunksNs += elapsedNs;
        if (elapsedNs > lastRenderChunksMaxNs) {
            lastRenderChunksMaxNs = elapsedNs;
        }
        renderChunksCalls++;
        long allocBytes = allocDeltaSince(renderChunksStartAllocBytes);
        if (allocBytes > 0L) lastRenderChunksAllocBytes += allocBytes;
        renderChunksStartNs = 0L;
        renderChunksStartAllocBytes = -1L;
    }

    public static long beginWorldFlush() {
        if (TIMING_ENABLED) {
            worldFlushStartAllocBytes = currentThreadAllocatedBytes();
        }
        return TIMING_ENABLED ? System.nanoTime() : 0L;
    }

    public static void endWorldFlush(long startNs) {
        if (!TIMING_ENABLED || startNs == 0L) return;
        long elapsedNs = System.nanoTime() - startNs;
        lastWorldFlushNs = elapsedNs;
        lastWorldFlushAllocBytes = allocDeltaSince(worldFlushStartAllocBytes);
        worldFlushStartAllocBytes = -1L;
        double elapsedMs = elapsedNs / 1000000.0d;
        if (ENABLED && elapsedMs >= FLUSH_THRESHOLD_MS) {
            slowWorldFlushes++;
            logEvent("WorldFlush", elapsedMs, 0L, 0L);
        }
    }

    private static void logSpike(double frameMs, long gcCountDelta, long gcTimeDelta) {
        logEvent("FrameSpike", frameMs, gcCountDelta, gcTimeDelta);
    }

    private static void resetFrameStageCounters() {
        lastFrameCpuNs = 0L;
        lastFrameAllocBytes = 0L;
        lastGameRendererUpdateNs = 0L;
        lastGameRendererUpdateAllocBytes = 0L;
        lastRenderWorldNs = 0L;
        lastRenderWorldAllocBytes = 0L;
        lastAeroRenderPrepNs = 0L;
        lastRenderEntitiesNs = 0L;
        lastRenderEntitiesAllocBytes = 0L;
        lastClientTickNs = 0L;
        lastClientTickAllocBytes = 0L;
        lastDisplayUpdateNs = 0L;
        lastDisplayUpdateAllocBytes = 0L;
        lastProfilerChartNs = 0L;
        lastWorldSaveNs = 0L;
        lastWorldSaveAllocBytes = 0L;
        worldSaveSkipped = 0L;
        lastChunkCompileNs = 0L;
        lastChunkCompileAllocBytes = 0L;
        lastChunkCompileMaxNs = 0L;
        chunkCompileCalls = 0L;
        chunkCompileSkipped = 0L;
        slowChunkCompiles = 0L;
        lastRenderChunksNs = 0L;
        lastRenderChunksAllocBytes = 0L;
        lastRenderChunksMaxNs = 0L;
        renderChunksCalls = 0L;
        lastWorldFlushNs = 0L;
        lastWorldFlushAllocBytes = 0L;
        slowWorldFlushes = 0L;
    }

    private static void logEvent(String kind, double frameMs, long gcCountDelta, long gcTimeDelta) {
        Runtime rt = Runtime.getRuntime();
        long usedMb = (rt.totalMemory() - rt.freeMemory()) >> 20;
        long totalMb = rt.totalMemory() >> 20;
        long maxMb = rt.maxMemory() >> 20;
        String line = "[Aero_" + kind + "] frameMs=" + round1(frameMs)
            + " frameCpuMs=" + round1(lastFrameCpuNs / 1000000.0d)
            + " frameAllocMB=" + round2(bytesToMb(lastFrameAllocBytes))
            + " gameUpdateMs=" + round1(lastGameRendererUpdateNs / 1000000.0d)
            + " gameAllocMB=" + round2(bytesToMb(lastGameRendererUpdateAllocBytes))
            + " renderWorldMs=" + round1(lastRenderWorldNs / 1000000.0d)
            + " renderWorldAllocMB=" + round2(bytesToMb(lastRenderWorldAllocBytes))
            + " aeroPrepMs=" + round1(lastAeroRenderPrepNs / 1000000.0d)
            + " renderEntitiesMs=" + round1(lastRenderEntitiesNs / 1000000.0d)
            + " renderEntitiesAllocMB=" + round2(bytesToMb(lastRenderEntitiesAllocBytes))
            + " clientTickMs=" + round1(lastClientTickNs / 1000000.0d)
            + " clientTickAllocMB=" + round2(bytesToMb(lastClientTickAllocBytes))
            + " displayUpdateMs=" + round1(lastDisplayUpdateNs / 1000000.0d)
            + " displayAllocMB=" + round2(bytesToMb(lastDisplayUpdateAllocBytes))
            + " profilerChartMs=" + round1(lastProfilerChartNs / 1000000.0d)
            + " worldSaveMs=" + round1(lastWorldSaveNs / 1000000.0d)
            + " worldSaveAllocMB=" + round2(bytesToMb(lastWorldSaveAllocBytes))
            + " worldSaveSkipped=" + worldSaveSkipped
            + " compileChunksMs=" + round1(lastChunkCompileNs / 1000000.0d)
            + " compileChunksAllocMB=" + round2(bytesToMb(lastChunkCompileAllocBytes))
            + " compileChunksMaxMs=" + round1(lastChunkCompileMaxNs / 1000000.0d)
            + " compileChunksCalls=" + chunkCompileCalls
            + " compileChunksSkipped=" + chunkCompileSkipped
            + " compileBudgetSkipped=" + Aero_ChunkCompileBudget.skippedLastFrame()
            + " slowChunkCompiles=" + slowChunkCompiles
            + " renderChunksMs=" + round1(lastRenderChunksNs / 1000000.0d)
            + " renderChunksAllocMB=" + round2(bytesToMb(lastRenderChunksAllocBytes))
            + " renderChunksMaxMs=" + round1(lastRenderChunksMaxNs / 1000000.0d)
            + " renderChunksCalls=" + renderChunksCalls
            + " heap=" + usedMb + "/" + totalMb + "/" + maxMb + "MB"
            + " gcCountDelta=" + gcCountDelta
            + " gcTimeDeltaMs=" + gcTimeDelta
            + " worldFlushMs=" + round1(lastWorldFlushNs / 1000000.0d)
            + " worldFlushAllocMB=" + round2(bytesToMb(lastWorldFlushAllocBytes))
            + " slowWorldFlushes=" + slowWorldFlushes
            + " animLimit=" + Aero_AnimationRenderBudget.effectiveMaxAnimatedThisFrame()
            + " animPressureLimit=" + Aero_AnimationRenderBudget.framePressureLimitThisFrame()
            + " animPressureDrops=" + Aero_AnimationRenderBudget.framePressureDrops()
            + " animThroughputBad=" + Aero_AnimationRenderBudget.throughputBadFrames()
            + " animTickSeen=" + Aero_AnimationTickBudget.seenLastTick()
            + " animTicked=" + Aero_AnimationTickBudget.tickedLastTick()
            + " animTickStride=" + Aero_AnimationTickBudget.denseStrideThisTick()
            + " renderScale=" + round1(Aero_RenderLoadGovernor.distanceScale() * 100.0d)
            + " renderDrops=" + Aero_RenderLoadGovernor.drops()
            + " renderThroughputBad=" + Aero_RenderLoadGovernor.throughputBadFrames()
            + " paceFps=" + Aero_FramePacer.targetFps()
            + " paceSleepMs=" + round1(Aero_FramePacer.sleptLastFrameMs())
            + " animAccepted=" + Aero_AnimationRenderBudget.acceptedThisFrame()
            + " animRejected=" + Aero_AnimationRenderBudget.rejectedThisFrame()
            + " animPriorityRejected=" + Aero_AnimationRenderBudget.priorityRejectedThisFrame()
            + " animHysteresis=" + Aero_AnimationRenderBudget.hysteresisAcceptedThisFrame()
            + " batchQueued=" + Aero_AnimatedBatcher.queuedThisFrame()
            + " batchFlushed=" + Aero_AnimatedBatcher.flushedInstancesThisFrame()
            + " batchBatches=" + Aero_AnimatedBatcher.flushedBatchesThisFrame()
            + " batchBonePageDrain=" + Aero_AnimatedBatcher.bonePageDrainedInstancesThisFrame()
            + " batchImmediate=" + Aero_AnimatedBatcher.immediateRendersThisFrame()
            + " beViewCulled=" + Aero_FrustumCull.beViewCulledThisFrame()
            + " beViewFastHold=" + Aero_FrustumCull.beViewFastTurnHoldFrames()
            + " beViewHistory=" + Aero_FrustumCull.beViewHistoryAcceptedThisFrame()
            + " atRestRenders=" + Aero_MeshRenderer.atRestRendersThisFrame()
            + " atRestListCalls=" + Aero_MeshRenderer.atRestListCallsThisFrame()
            + " atRestFallbacks=" + Aero_MeshRenderer.atRestTessFallbacksThisFrame()
            + " cellQueued=" + Aero_BECellRenderer.queuedLastFrame()
            + " cellCalls=" + Aero_BECellRenderer.pageCallsThisFrame()
            + " cellRebuilds=" + Aero_BECellRenderer.pageRebuildsThisFrame()
            + " cellDirect=" + Aero_BECellRenderer.directFallbacksThisFrame()
            + " cellCached=" + Aero_BECellRenderer.cachedPageCount()
            + " dlLive=" + Aero_DisplayListBudget.liveLists()
            + " dlPeak=" + Aero_DisplayListBudget.peakLiveLists()
            + " dlDenied=" + Aero_DisplayListBudget.deniedAllocations()
            + " dlFailed=" + Aero_DisplayListBudget.failedAllocations()
            + " prewarmDrained=" + Aero_Prewarm.drainedThisFrame()
            + " prewarmQueued=" + Aero_Prewarm.queuedModelCount()
            + " visibleChunks=" + Aero_ChunkVisibility.visibleChunkCount()
            + " recentChunks=" + Aero_ChunkVisibility.recentChunkCount();
        System.out.println(line);
        writeFile(line);
    }

    private static void writeFile(String line) {
        if (LOG_FILE == null || LOG_FILE.length() == 0 || fileLogFailed) return;
        PrintWriter out = fileLog;
        if (out == null) {
            try {
                out = new PrintWriter(new FileWriter(LOG_FILE, true));
                fileLog = out;
                out.println("# Aero frame spike log");
                Runtime.getRuntime().addShutdownHook(new Thread(new Runnable() {
                    public void run() {
                        PrintWriter log = fileLog;
                        if (log != null) {
                            log.flush();
                            log.close();
                        }
                    }
                }, "AeroFrameSpikeLogFlush"));
            } catch (IOException e) {
                fileLogFailed = true;
                return;
            }
        }
        out.println(line);
        if (SYNC_WRITES) out.flush();
    }

    private static long gcCollectionCount() {
        long total = 0L;
        for (int i = 0; i < GC_BEANS.size(); i++) {
            long value = ((GarbageCollectorMXBean) GC_BEANS.get(i)).getCollectionCount();
            if (value > 0L) total += value;
        }
        return total;
    }

    private static long currentThreadCpuTimeNs() {
        if (!THREAD_CPU_SUPPORTED) return -1L;
        try {
            return THREAD_BEAN.getCurrentThreadCpuTime();
        } catch (UnsupportedOperationException e) {
            return -1L;
        }
    }

    private static long currentThreadAllocatedBytes() {
        if (!THREAD_ALLOC_SUPPORTED || !ALLOC_BEAN.isThreadAllocatedMemoryEnabled()) return -1L;
        try {
            return ALLOC_BEAN.getThreadAllocatedBytes(Thread.currentThread().getId());
        } catch (UnsupportedOperationException e) {
            return -1L;
        }
    }

    private static long allocDeltaSince(long startBytes) {
        long now = currentThreadAllocatedBytes();
        if (now < 0L || startBytes < 0L) return -1L;
        return positiveDelta(now, startBytes);
    }

    private static long gcCollectionTimeMs() {
        long total = 0L;
        for (int i = 0; i < GC_BEANS.size(); i++) {
            long value = ((GarbageCollectorMXBean) GC_BEANS.get(i)).getCollectionTime();
            if (value > 0L) total += value;
        }
        return total;
    }

    private static long positiveDelta(long current, long previous) {
        return current >= previous ? current - previous : 0L;
    }

    private static String round1(double value) {
        return String.valueOf(Math.round(value * 10.0d) / 10.0d);
    }

    private static String round2(double value) {
        return String.valueOf(Math.round(value * 100.0d) / 100.0d);
    }

    private static double bytesToMb(long bytes) {
        return bytes >= 0L ? bytes / 1048576.0d : -1.0d;
    }

    private static double doubleProperty(String name, double fallback,
                                         double min, double max) {
        String raw = System.getProperty(name);
        if (raw == null) return fallback;
        try {
            double value = Double.parseDouble(raw.trim());
            if (Double.isNaN(value) || Double.isInfinite(value)) return fallback;
            if (value < min) return min;
            if (value > max) return max;
            return value;
        } catch (NumberFormatException e) {
            return fallback;
        }
    }
}
