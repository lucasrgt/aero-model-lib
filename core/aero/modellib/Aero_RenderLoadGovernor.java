package aero.modellib;

/**
 * Previous-frame render pressure feedback shared by the platform renderers and
 * the pure render-distance math.
 *
 * <p>The animation budget controls how many BEs advance bones. Dense model
 * scenes can still be GPU/driver-bound after every animated BE has degraded to
 * at-rest lists, so this governor also shrinks the static render radius under
 * sustained frame-time pressure. It recovers slowly to avoid popping while the
 * player rotates around a heavy scene.</p>
 */
public final class Aero_RenderLoadGovernor {

    private static final boolean ENABLED =
        "true".equalsIgnoreCase(System.getProperty("aero.renderLoadGovernor"));
    private static final double MIN_SCALE =
        doubleProperty("aero.renderLoad.minScale", 0.70d, 0.25d, 1.0d);
    private static final double STEP =
        doubleProperty("aero.renderLoad.step", 0.05d, 0.001d, 0.50d);
    private static final double RECOVERY_STEP =
        doubleProperty("aero.renderLoad.recoveryStep", 0.025d, 0.001d, 0.50d);
    private static final double MIN_RADIUS =
        doubleProperty("aero.renderLoad.minRadius", 24.0d, 0.0d, 4096.0d);
    private static final double TARGET_MS =
        doubleProperty("aero.renderLoad.targetMs", 10.0d, 0.0d, 10000.0d);
    private static final double DISPLAY_STALL_MS =
        doubleProperty("aero.renderLoad.displayStallMs", 25.0d, 0.0d, 10000.0d);
    private static final double RENDER_CHUNK_STALL_MS =
        doubleProperty("aero.renderLoad.renderChunkStallMs", 20.0d, 0.0d, 10000.0d);
    private static final double THROUGHPUT_RATIO =
        doubleProperty("aero.renderLoad.throughputRatio", 1.55d, 1.01d, 100.0d);
    private static final double THROUGHPUT_MIN_MS =
        doubleProperty("aero.renderLoad.throughputMinMs", 4.0d, 0.0d, 10000.0d);
    private static final int THROUGHPUT_FRAMES =
        intProperty("aero.renderLoad.throughputFrames", 4, 1, 100000);
    private static final int HOLD_FRAMES =
        intProperty("aero.renderLoad.holdFrames", 90, 0, 100000);
    private static final int RECOVERY_FRAMES =
        intProperty("aero.renderLoad.recoveryFrames", 90, 1, 100000);

    private static int frameIndex;
    private static int holdUntilFrame;
    private static int nextRecoveryFrame;
    private static int drops;
    private static int throughputBadFrames;
    private static double fastFrameMs;
    private static double scale = 1.0d;

    private Aero_RenderLoadGovernor() {
    }

    public static boolean enabled() {
        return ENABLED;
    }

    public static void recordFramePressure(double frameMs, double displayUpdateMs,
                                           double renderChunksMs,
                                           double renderEntitiesMs,
                                           long gcTimeDeltaMs,
                                           int visibleChunks) {
        if (!ENABLED) return;
        if (visibleChunks < 0) return;
        frameIndex++;

        boolean hardPressure =
            displayUpdateMs >= DISPLAY_STALL_MS
            || renderChunksMs >= RENDER_CHUNK_STALL_MS;
        boolean targetPressure = TARGET_MS > 0.0d && frameMs >= TARGET_MS;
        boolean throughputPressure = recordThroughputPressure(frameMs);

        if (hardPressure || targetPressure || throughputPressure) {
            double next = hardPressure
                ? MIN_SCALE
                : Math.max(MIN_SCALE, scale - STEP);
            if (next < scale) {
                scale = next;
                drops++;
            }
            holdUntilFrame = frameIndex + HOLD_FRAMES;
            nextRecoveryFrame = holdUntilFrame + RECOVERY_FRAMES;
            return;
        }

        if (scale < 1.0d
            && frameIndex > holdUntilFrame
            && frameIndex >= nextRecoveryFrame) {
            scale = Math.min(1.0d, scale + RECOVERY_STEP);
            nextRecoveryFrame = frameIndex + RECOVERY_FRAMES;
        }
    }

    public static double scaleRadius(double radius) {
        if (!ENABLED || scale >= 0.999999d || radius <= 0.0d) return radius;
        double scaled = radius * scale;
        if (scaled < MIN_RADIUS) {
            scaled = Math.min(radius, MIN_RADIUS);
        }
        return scaled > radius ? radius : scaled;
    }

    public static double distanceScale() {
        return ENABLED ? scale : 1.0d;
    }

    public static int drops() {
        return drops;
    }

    public static int throughputBadFrames() {
        return throughputBadFrames;
    }

    private static boolean recordThroughputPressure(double frameMs) {
        if (frameMs <= 0.0d) return false;
        if (fastFrameMs <= 0.0d || frameMs < fastFrameMs) {
            fastFrameMs = frameMs;
            throughputBadFrames = 0;
            return false;
        }

        boolean bad = fastFrameMs > 0.0d
            && frameMs >= THROUGHPUT_MIN_MS
            && frameMs >= fastFrameMs * THROUGHPUT_RATIO;
        if (bad) {
            throughputBadFrames++;
            return throughputBadFrames >= THROUGHPUT_FRAMES;
        }

        if (throughputBadFrames > 0) throughputBadFrames--;
        fastFrameMs += (frameMs - fastFrameMs) * 0.0025d;
        return false;
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
