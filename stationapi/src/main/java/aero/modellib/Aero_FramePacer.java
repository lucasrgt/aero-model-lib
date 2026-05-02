package aero.modellib;

import java.util.concurrent.locks.LockSupport;
import org.lwjgl.opengl.Display;

/**
 * Optional dense-scene frame pacing.
 *
 * <p>Uncapped Beta can submit hundreds of tiny display-list calls at 300+
 * FPS, then stall unpredictably when the old GL driver catches up. This
 * opt-in pacer only sleeps when the previous frame actually used Aero dense
 * rendering, keeping normal gameplay/menu behaviour untouched.
 */
public final class Aero_FramePacer {

    public static final boolean ENABLED =
        "true".equalsIgnoreCase(System.getProperty("aero.framePacing"));

    private static final int TARGET_FPS =
        intProperty("aero.framePacing.fps", 180, 30, 1000);
    private static final int MIN_FPS =
        intProperty("aero.framePacing.minFps", 60, 30, TARGET_FPS);
    private static final int DROP_STEP =
        intProperty("aero.framePacing.dropStep", 30, 1, 1000);
    private static final int RECOVERY_STEP =
        intProperty("aero.framePacing.recoveryStep", 15, 1, 1000);
    private static final int HOLD_FRAMES =
        intProperty("aero.framePacing.holdFrames", 180, 0, 100000);
    private static final int RECOVERY_FRAMES =
        intProperty("aero.framePacing.recoveryFrames", 240, 1, 100000);
    private static final double DISPLAY_STALL_MS =
        doubleProperty("aero.framePacing.displayStallMs", 30.0d, 0.0d, 10000.0d);
    private static final double FRAME_RATIO =
        doubleProperty("aero.framePacing.frameRatio", 2.25d, 1.0d, 100.0d);
    private static final boolean ADAPTIVE =
        !"false".equalsIgnoreCase(System.getProperty("aero.framePacing.adaptive"));
    private static final boolean SYNC_DISPLAY =
        !"false".equalsIgnoreCase(System.getProperty("aero.framePacing.syncDisplay"));
    private static final boolean ONLY_DENSE =
        !"false".equalsIgnoreCase(System.getProperty("aero.framePacing.onlyDense"));
    private static final int MIN_AERO_WORK =
        intProperty("aero.framePacing.minAeroWork", 32, 0, 100000);

    private static long lastFrameNs;
    private static long sleptLastFrameNs;
    private static int frameIndex;
    private static int holdUntilFrame;
    private static int nextRecoveryFrame;
    private static int dynamicTargetFps = TARGET_FPS;

    private Aero_FramePacer() {}

    public static void beforeFrame() {
        if (!ENABLED) {
            lastFrameNs = 0L;
            sleptLastFrameNs = 0L;
            return;
        }
        if (SYNC_DISPLAY) {
            sleptLastFrameNs = 0L;
            return;
        }

        long now = System.nanoTime();
        boolean pace = !ONLY_DENSE || previousFrameWasDense();
        if (pace && lastFrameNs != 0L) {
            long elapsed = now - lastFrameNs;
            long remaining = targetNanos() - elapsed;
            if (remaining > 0L) {
                sleepNanos(remaining);
                long after = System.nanoTime();
                sleptLastFrameNs = after - now;
                lastFrameNs = after;
                return;
            }
        }
        sleptLastFrameNs = 0L;
        lastFrameNs = now;
    }

    public static void beforeDisplayUpdate() {
        if (!ENABLED || !SYNC_DISPLAY) return;
        if (ONLY_DENSE && !currentOrPreviousFrameWasDense()) {
            sleptLastFrameNs = 0L;
            return;
        }
        long start = System.nanoTime();
        Display.sync(dynamicTargetFps);
        sleptLastFrameNs = System.nanoTime() - start;
    }

    public static void recordFramePressure(double frameMs, double displayUpdateMs) {
        if (!ENABLED || !ADAPTIVE) return;
        frameIndex++;
        double targetMs = 1000.0d / (double) dynamicTargetFps;
        boolean pressure =
            (DISPLAY_STALL_MS > 0.0d && displayUpdateMs >= DISPLAY_STALL_MS)
            || (frameMs >= targetMs * FRAME_RATIO);
        if (pressure) {
            int next = Math.max(MIN_FPS, dynamicTargetFps - DROP_STEP);
            if (next < dynamicTargetFps) {
                dynamicTargetFps = next;
            }
            holdUntilFrame = frameIndex + HOLD_FRAMES;
            nextRecoveryFrame = holdUntilFrame + RECOVERY_FRAMES;
            return;
        }
        if (dynamicTargetFps < TARGET_FPS
            && frameIndex > holdUntilFrame
            && frameIndex >= nextRecoveryFrame) {
            dynamicTargetFps = Math.min(TARGET_FPS, dynamicTargetFps + RECOVERY_STEP);
            nextRecoveryFrame = frameIndex + RECOVERY_FRAMES;
        }
    }

    public static double sleptLastFrameMs() {
        return sleptLastFrameNs / 1000000.0d;
    }

    public static int targetFps() {
        return ENABLED ? dynamicTargetFps : 0;
    }

    private static boolean previousFrameWasDense() {
        int work =
            Aero_AnimatedBatcher.queuedThisFrame()
            + Aero_BECellRenderer.queuedLastFrame()
            + Aero_BECellRenderer.pageCallsThisFrame()
            + Aero_MeshRenderer.atRestListCallsThisFrame();
        return work >= MIN_AERO_WORK;
    }

    private static boolean currentOrPreviousFrameWasDense() {
        return previousFrameWasDense()
            || Aero_AnimatedBatcher.flushedInstancesThisFrame()
            + Aero_BECellRenderer.queuedLastFrame()
            + Aero_BECellRenderer.pageCallsThisFrame()
            + Aero_MeshRenderer.atRestListCallsThisFrame() >= MIN_AERO_WORK;
    }

    private static long targetNanos() {
        return 1000000000L / Math.max(1, dynamicTargetFps);
    }

    private static void sleepNanos(long nanos) {
        long deadline = System.nanoTime() + nanos;
        while (true) {
            long remaining = deadline - System.nanoTime();
            if (remaining <= 0L) return;
            if (remaining > 2000000L) {
                try {
                    Thread.sleep((remaining / 1000000L) - 1L);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                }
            } else {
                LockSupport.parkNanos(remaining);
            }
        }
    }

    private static int intProperty(String name, int fallback, int min, int max) {
        String raw = System.getProperty(name);
        if (raw == null) return fallback;
        try {
            int parsed = Integer.parseInt(raw.trim());
            return parsed >= min && parsed <= max ? parsed : fallback;
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private static double doubleProperty(String name, double fallback,
                                         double min, double max) {
        String raw = System.getProperty(name);
        if (raw == null) return fallback;
        try {
            double parsed = Double.parseDouble(raw.trim());
            return parsed >= min && parsed <= max ? parsed : fallback;
        } catch (NumberFormatException e) {
            return fallback;
        }
    }
}
