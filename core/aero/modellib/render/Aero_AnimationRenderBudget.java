package aero.modellib.render;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * Per-frame admission control for expensive animated renders.
 *
 * <p>This is the first, low-risk step toward cell/page rendering for dense
 * BlockEntity scenes: keep a fixed number of nearby/visible models animated
 * each frame and let overflow render through the at-rest display-list path.
 */
public final class Aero_AnimationRenderBudget {
    public static final boolean ENABLED =
        !"false".equalsIgnoreCase(System.getProperty("aero.animBudget"));
    public static final int MAX_ANIMATED =
        Integer.getInteger("aero.maxAnimatedBE", -1).intValue();

    private static final double TAN_HALF_VFOV = Math.tan(Math.toRadians(35.0d));
    private static final double CRITICAL_PX = doubleProperty("aero.animBudget.criticalPx", 64.0d, 1.0d, 10000.0d);
    private static final double MID_PX = doubleProperty("aero.animBudget.midPx", 32.0d, 1.0d, 10000.0d);
    private static final double LOW_PX = doubleProperty("aero.animBudget.lowPx", 16.0d, 1.0d, 10000.0d);
    private static final double NEAR_BLOCKS = doubleProperty("aero.animBudget.nearBlocks", 12.0d, 0.0d, 1024.0d);
    private static final int CRITICAL_EXTRA =
        Integer.getInteger("aero.animBudget.criticalExtra", defaultCriticalExtra()).intValue();
    private static final int HYSTERESIS_FRAMES =
        intProperty("aero.animBudget.hysteresisFrames", 6, 0, 600);
    private static final int HYSTERESIS_EXTRA =
        intProperty("aero.animBudget.hysteresisExtra", defaultHysteresisExtra(), 0, 100000);
    private static final boolean HARD_CAP =
        !"false".equalsIgnoreCase(System.getProperty("aero.animBudget.hardCap"));
    private static final boolean VISIBLE_CHUNK_THROTTLE =
        "true".equalsIgnoreCase(System.getProperty("aero.animBudget.visibleChunkThrottle"));
    private static final boolean VISIBLE_CHUNK_SMOOTH =
        !"false".equalsIgnoreCase(System.getProperty("aero.animBudget.visibleChunkSmooth"));
    private static final int VISIBLE_CHUNK_MID =
        intProperty("aero.animBudget.visibleChunkMid", 350, 1, 100000);
    private static final int VISIBLE_CHUNK_HIGH =
        intProperty("aero.animBudget.visibleChunkHigh", 450, 1, 100000);
    private static final int VISIBLE_CHUNK_MID_MAX =
        intProperty("aero.animBudget.visibleChunkMidMax", defaultVisibleChunkMidMax(), 0, 100000);
    private static final int VISIBLE_CHUNK_HIGH_MAX =
        intProperty("aero.animBudget.visibleChunkHighMax", defaultVisibleChunkHighMax(), 0, 100000);
    private static final int VISIBLE_CHUNK_STEP =
        intProperty("aero.animBudget.visibleChunkStep", defaultVisibleChunkStep(), 1, 100000);
    private static final int VISIBLE_CHUNK_RECOVERY_STEP =
        intProperty("aero.animBudget.visibleChunkRecoveryStep",
            defaultVisibleChunkRecoveryStep(), 1, 100000);
    private static final boolean FRAME_PRESSURE_THROTTLE =
        "true".equalsIgnoreCase(System.getProperty("aero.animBudget.framePressure"));
    private static final double FRAME_PRESSURE_MS =
        doubleProperty("aero.animBudget.framePressureMs", 45.0d, 1.0d, 10000.0d);
    private static final double DISPLAY_STALL_MS =
        doubleProperty("aero.animBudget.displayStallMs", 35.0d, 0.0d, 10000.0d);
    private static final double RENDER_CHUNK_STALL_MS =
        doubleProperty("aero.animBudget.renderChunkStallMs", 30.0d, 0.0d, 10000.0d);
    private static final double GC_STALL_MS =
        doubleProperty("aero.animBudget.gcStallMs", 18.0d, 0.0d, 10000.0d);
    private static final int FRAME_PRESSURE_FRAMES =
        intProperty("aero.animBudget.framePressureFrames", 90, 0, 100000);
    private static final int FRAME_PRESSURE_RECOVERY_FRAMES =
        intProperty("aero.animBudget.framePressureRecoveryFrames", 90, 1, 100000);
    private static final int FRAME_PRESSURE_STEP =
        intProperty("aero.animBudget.framePressureStep", defaultFramePressureStep(), 1, 100000);
    private static final int FRAME_PRESSURE_MIN =
        intProperty("aero.animBudget.framePressureMin", defaultFramePressureMin(), 0, 100000);
    private static final boolean THROUGHPUT_THROTTLE =
        "true".equalsIgnoreCase(System.getProperty("aero.animBudget.throughputThrottle"));
    private static final double THROUGHPUT_RATIO =
        doubleProperty("aero.animBudget.throughputRatio", 1.65d, 1.01d, 100.0d);
    private static final double THROUGHPUT_MIN_MS =
        doubleProperty("aero.animBudget.throughputMinMs", 6.0d, 0.0d, 10000.0d);
    private static final int THROUGHPUT_FRAMES =
        intProperty("aero.animBudget.throughputFrames", 4, 1, 100000);

    private static final HashMap<Object, Integer> HELD_UNTIL_FRAME =
        new HashMap<Object, Integer>();
    private static final HashMap<Object, Aero_RenderLod> DECISION_BY_KEY =
        new HashMap<Object, Aero_RenderLod>();
    private static final LongIntMap HELD_UNTIL_FRAME_LONG = new LongIntMap(1024);
    private static final LongByteMap DECISION_BY_KEY_LONG = new LongByteMap(1024);
    public static final long NO_HYSTERESIS_KEY = Long.MIN_VALUE;

    private static double focalPx;
    private static int cachedDisplayHeight = -1;
    private static int frameIndex;
    private static int acceptedThisFrame;
    private static int rejectedThisFrame;
    private static int criticalAcceptedThisFrame;
    private static int hysteresisAcceptedThisFrame;
    private static int priorityRejectedThisFrame;
    private static int visibleChunkLimit = MAX_ANIMATED;
    private static int framePressureLimit = MAX_ANIMATED;
    private static int framePressureUntilFrame;
    private static int nextFramePressureRecoveryFrame;
    private static int framePressureDrops;
    private static int throughputBadFrames;
    private static double fastFrameMs;

    private Aero_AnimationRenderBudget() {}

    /** Resets counters at the start of a visual render frame. */
    public static void beginFrame() {
        frameIndex++;
        acceptedThisFrame = 0;
        rejectedThisFrame = 0;
        criticalAcceptedThisFrame = 0;
        hysteresisAcceptedThisFrame = 0;
        priorityRejectedThisFrame = 0;
        DECISION_BY_KEY.clear();
        DECISION_BY_KEY_LONG.clear();
        if ((frameIndex & 31) == 0) {
            expireHolds();
        }
        recoverFramePressureLimit();
    }

    /** Updates the projection math used by priority scoring. */
    public static void updateFromDisplayHeight(int displayHeight) {
        if (displayHeight <= 0 || displayHeight == cachedDisplayHeight) return;
        focalPx = displayHeight / (2.0d * TAN_HALF_VFOV);
        cachedDisplayHeight = displayHeight;
    }

    /**
     * Optional runtime pressure input from platform renderers. Dense terrain
     * visibility can make chunk display-list playback dominate the frame; in
     * that situation we shrink the animated-BE budget before adding more GL
     * work on top of vanilla's chunk pass.
     */
    public static void updateVisibleChunkPressure(int visibleChunks) {
        if (!VISIBLE_CHUNK_THROTTLE || MAX_ANIMATED < 0 || visibleChunks < 0) {
            visibleChunkLimit = MAX_ANIMATED;
            return;
        }
        int target;
        if (visibleChunks >= VISIBLE_CHUNK_HIGH) {
            target = Math.min(MAX_ANIMATED, VISIBLE_CHUNK_HIGH_MAX);
        } else if (visibleChunks >= VISIBLE_CHUNK_MID) {
            target = Math.min(MAX_ANIMATED, VISIBLE_CHUNK_MID_MAX);
        } else {
            target = MAX_ANIMATED;
        }
        if (!VISIBLE_CHUNK_SMOOTH) {
            visibleChunkLimit = target;
            return;
        }
        if (visibleChunkLimit <= 0 || visibleChunkLimit > MAX_ANIMATED) {
            visibleChunkLimit = MAX_ANIMATED;
        }
        if (visibleChunkLimit > target) {
            visibleChunkLimit = Math.max(target, visibleChunkLimit - VISIBLE_CHUNK_STEP);
        } else if (visibleChunkLimit < target) {
            visibleChunkLimit = Math.min(target,
                visibleChunkLimit + VISIBLE_CHUNK_RECOVERY_STEP);
        }
    }

    /**
     * Feeds previous-frame timing pressure back into the next animation
     * admission decisions. The renderer may be CPU-cheap while still causing
     * driver stalls at {@code Display.update}; this governor reacts to that
     * pressure by temporarily lowering the animated-BE cap, then recovering
     * slowly after stable frames.
     */
    public static void recordFramePressure(double frameMs, double displayUpdateMs,
                                           double renderChunksMs, long gcTimeDeltaMs) {
        if (!framePressureThrottleEnabled()) return;
        // Ignore menus/startup and non-render frames. We only throttle when
        // animation admission participated in the previous frame.
        if (acceptedThisFrame <= 0 && rejectedThisFrame <= 0) return;

        int base = baseMaxAnimatedLimit();
        if (framePressureLimit <= 0 || framePressureLimit > base) {
            framePressureLimit = base;
        }

        boolean hardDriverPressure =
            displayUpdateMs >= DISPLAY_STALL_MS
            || renderChunksMs >= RENDER_CHUNK_STALL_MS;
        boolean throughputPressure = recordThroughputPressure(frameMs);
        boolean pressure =
            hardDriverPressure
            || gcTimeDeltaMs >= GC_STALL_MS
            || frameMs >= FRAME_PRESSURE_MS
            || throughputPressure;
        if (!pressure) return;

        int current = Math.min(base, framePressureLimit);
        int next = hardDriverPressure
            ? FRAME_PRESSURE_MIN
            : Math.max(FRAME_PRESSURE_MIN, current - FRAME_PRESSURE_STEP);
        if (next < framePressureLimit) {
            framePressureLimit = next;
            framePressureDrops++;
        }
        framePressureUntilFrame = frameIndex + FRAME_PRESSURE_FRAMES;
        nextFramePressureRecoveryFrame =
            framePressureUntilFrame + FRAME_PRESSURE_RECOVERY_FRAMES;
    }

    public static boolean framePressureThrottleEnabled() {
        return ENABLED && FRAME_PRESSURE_THROTTLE && MAX_ANIMATED > 0;
    }

    private static boolean recordThroughputPressure(double frameMs) {
        if (!THROUGHPUT_THROTTLE || frameMs <= 0.0d) return false;
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
        // Let the learned baseline drift upward extremely slowly so moving
        // into a genuinely heavier area does not pin the governor forever.
        fastFrameMs += (frameMs - fastFrameMs) * 0.0025d;
        return false;
    }

    /**
     * Applies the animation budget to a distance LOD result. Non-animated
     * LODs pass through unchanged. Animated overflow degrades to STATIC so
     * the object remains visible through existing display-list caches.
     */
    public static Aero_RenderLod apply(Aero_RenderLod lod) {
        return apply(lod, 0.0d, 0.0d, 0.0d, 0.0d, null);
    }

    /**
     * Importance-aware budget admission. Because this still runs inside
     * vanilla's per-BE dispatch order, it is not a true sorted top-N. It
     * is deliberately conservative: very large/near objects can use a
     * small reserve, while low-priority objects stop consuming budget
     * early so later, more visible objects have room.
     */
    public static Aero_RenderLod apply(Aero_RenderLod lod, double x, double y, double z,
                                       double visualRadiusBlocks) {
        return apply(lod, x, y, z, visualRadiusBlocks, null);
    }

    /**
     * Same as {@link #apply(Aero_RenderLod, double, double, double, double)}
     * with a stable identity key. Passing a key enables short hold hysteresis:
     * a model that was animated recently gets a few frames of preference so it
     * does not alternate animated/static at the budget edge.
     */
    public static Aero_RenderLod apply(Aero_RenderLod lod, double x, double y, double z,
                                       double visualRadiusBlocks, Object hysteresisKey) {
        if (lod != Aero_RenderLod.ANIMATED) return lod;
        Aero_RenderLod cached = cachedDecision(hysteresisKey);
        if (cached != null) return cached;
        if (!ENABLED || MAX_ANIMATED < 0) return rememberDecision(hysteresisKey, lod);
        double distSq = x * x + y * y + z * z;
        double projectedPx = projectedDiameterPx(distSq, visualRadiusBlocks);
        if (isHeld(hysteresisKey) && acceptedThisFrame < hysteresisLimit()) {
            accept(hysteresisKey, true, false);
            return rememberDecision(hysteresisKey, lod);
        }
        if (isCritical(distSq, projectedPx) && acceptedThisFrame < criticalLimit()) {
            accept(hysteresisKey, false, true);
            return rememberDecision(hysteresisKey, lod);
        }

        if (shouldPriorityReject(projectedPx)) {
            rejectedThisFrame++;
            priorityRejectedThisFrame++;
            return rememberDecision(hysteresisKey, Aero_RenderLod.STATIC);
        }

        if (acceptedThisFrame < effectiveMaxAnimated()) {
            accept(hysteresisKey, false, false);
            return rememberDecision(hysteresisKey, lod);
        }
        rejectedThisFrame++;
        return rememberDecision(hysteresisKey, Aero_RenderLod.STATIC);
    }

    /**
     * Allocation-free variant for hot render paths that can pack their stable
     * identity into a primitive long.
     */
    public static Aero_RenderLod apply(Aero_RenderLod lod, double x, double y, double z,
                                       double visualRadiusBlocks, long hysteresisKey) {
        if (lod != Aero_RenderLod.ANIMATED) return lod;
        Aero_RenderLod cached = cachedDecision(hysteresisKey);
        if (cached != null) return cached;
        if (!ENABLED || MAX_ANIMATED < 0) return rememberDecision(hysteresisKey, lod);
        double distSq = x * x + y * y + z * z;
        double projectedPx = projectedDiameterPx(distSq, visualRadiusBlocks);
        if (isHeld(hysteresisKey) && acceptedThisFrame < hysteresisLimit()) {
            accept(hysteresisKey, true, false);
            return rememberDecision(hysteresisKey, lod);
        }
        if (isCritical(distSq, projectedPx) && acceptedThisFrame < criticalLimit()) {
            accept(hysteresisKey, false, true);
            return rememberDecision(hysteresisKey, lod);
        }

        if (shouldPriorityReject(projectedPx)) {
            rejectedThisFrame++;
            priorityRejectedThisFrame++;
            return rememberDecision(hysteresisKey, Aero_RenderLod.STATIC);
        }

        if (acceptedThisFrame < effectiveMaxAnimated()) {
            accept(hysteresisKey, false, false);
            return rememberDecision(hysteresisKey, lod);
        }
        rejectedThisFrame++;
        return rememberDecision(hysteresisKey, Aero_RenderLod.STATIC);
    }

    private static Aero_RenderLod cachedDecision(Object key) {
        if (key == null || DECISION_BY_KEY.isEmpty()) return null;
        return DECISION_BY_KEY.get(key);
    }

    private static Aero_RenderLod cachedDecision(long key) {
        if (key == NO_HYSTERESIS_KEY || DECISION_BY_KEY_LONG.isEmpty()) return null;
        int ordinal = DECISION_BY_KEY_LONG.get(key, -1);
        return ordinal >= 0 ? Aero_RenderLod.values()[ordinal] : null;
    }

    private static Aero_RenderLod rememberDecision(Object key, Aero_RenderLod lod) {
        if (key != null) {
            DECISION_BY_KEY.put(key, lod);
        }
        return lod;
    }

    private static Aero_RenderLod rememberDecision(long key, Aero_RenderLod lod) {
        if (key != NO_HYSTERESIS_KEY) {
            DECISION_BY_KEY_LONG.put(key, lod.ordinal());
        }
        return lod;
    }

    private static boolean isCritical(double distSq, double projectedPx) {
        if (projectedPx >= CRITICAL_PX) return true;
        return NEAR_BLOCKS > 0.0d && distSq <= NEAR_BLOCKS * NEAR_BLOCKS;
    }

    private static boolean shouldPriorityReject(double projectedPx) {
        int maxAnimated = effectiveMaxAnimated();
        if (maxAnimated <= 0) return true;
        int lowLimit = Math.max(1, maxAnimated / 2);
        int midLimit = Math.max(lowLimit, (maxAnimated * 3) / 4);
        if (projectedPx > 0.0d && projectedPx < LOW_PX && acceptedThisFrame >= lowLimit) {
            return true;
        }
        if (projectedPx > 0.0d && projectedPx < MID_PX && acceptedThisFrame >= midLimit) {
            return true;
        }
        return false;
    }

    private static double projectedDiameterPx(double distSq, double visualRadiusBlocks) {
        if (focalPx <= 0.0d || visualRadiusBlocks <= 0.0d || distSq <= 0.000001d) {
            return 0.0d;
        }
        return 2.0d * visualRadiusBlocks * focalPx / Math.sqrt(distSq);
    }

    private static void accept(Object hysteresisKey, boolean viaHysteresis, boolean critical) {
        acceptedThisFrame++;
        if (critical) criticalAcceptedThisFrame++;
        if (viaHysteresis) hysteresisAcceptedThisFrame++;
        rememberHold(hysteresisKey);
    }

    private static boolean isHeld(Object key) {
        if (key == null || HYSTERESIS_FRAMES <= 0 || HELD_UNTIL_FRAME.isEmpty()) return false;
        Integer until = (Integer) HELD_UNTIL_FRAME.get(key);
        return until != null && until.intValue() >= frameIndex;
    }

    private static boolean isHeld(long key) {
        if (key == NO_HYSTERESIS_KEY || HYSTERESIS_FRAMES <= 0
            || HELD_UNTIL_FRAME_LONG.isEmpty()) return false;
        return HELD_UNTIL_FRAME_LONG.get(key, Integer.MIN_VALUE) >= frameIndex;
    }

    private static void rememberHold(Object key) {
        if (key == null || HYSTERESIS_FRAMES <= 0) return;
        HELD_UNTIL_FRAME.put(key, Integer.valueOf(frameIndex + HYSTERESIS_FRAMES));
    }

    private static void rememberHold(long key) {
        if (key == NO_HYSTERESIS_KEY || HYSTERESIS_FRAMES <= 0) return;
        HELD_UNTIL_FRAME_LONG.put(key, frameIndex + HYSTERESIS_FRAMES);
    }

    private static void accept(long hysteresisKey, boolean viaHysteresis, boolean critical) {
        acceptedThisFrame++;
        if (critical) criticalAcceptedThisFrame++;
        if (viaHysteresis) hysteresisAcceptedThisFrame++;
        rememberHold(hysteresisKey);
    }

    private static void expireHolds() {
        if (!HELD_UNTIL_FRAME.isEmpty()) {
            Iterator<Map.Entry<Object, Integer>> it = HELD_UNTIL_FRAME.entrySet().iterator();
            while (it.hasNext()) {
                Map.Entry<Object, Integer> entry = it.next();
                Integer until = entry.getValue();
                if (until == null || until.intValue() < frameIndex) {
                    it.remove();
                }
            }
        }
        HELD_UNTIL_FRAME_LONG.removeValuesLessThan(frameIndex);
    }

    private static int hysteresisLimit() {
        int base = effectiveMaxAnimated();
        return HARD_CAP ? base : base + Math.max(0, HYSTERESIS_EXTRA);
    }

    private static int criticalLimit() {
        int base = effectiveMaxAnimated();
        return HARD_CAP ? base
            : base + Math.max(0, HYSTERESIS_EXTRA) + Math.max(0, CRITICAL_EXTRA);
    }

    private static int effectiveMaxAnimated() {
        if (!ENABLED || MAX_ANIMATED < 0) return MAX_ANIMATED;
        int limit = baseMaxAnimatedLimit();
        if (framePressureThrottleEnabled()) {
            if (framePressureLimit <= 0 || framePressureLimit > limit) {
                framePressureLimit = limit;
            }
            limit = Math.min(limit, Math.max(0, framePressureLimit));
        }
        return limit;
    }

    private static int baseMaxAnimatedLimit() {
        return Math.min(MAX_ANIMATED, Math.max(0, visibleChunkLimit));
    }

    private static void recoverFramePressureLimit() {
        if (!framePressureThrottleEnabled()) return;
        int base = baseMaxAnimatedLimit();
        if (framePressureLimit <= 0 || framePressureLimit > base) {
            framePressureLimit = base;
            return;
        }
        if (framePressureLimit >= base || frameIndex <= framePressureUntilFrame
            || frameIndex < nextFramePressureRecoveryFrame) {
            return;
        }
        int step = Math.max(1, FRAME_PRESSURE_STEP / 2);
        framePressureLimit = Math.min(base, framePressureLimit + step);
        nextFramePressureRecoveryFrame = frameIndex + FRAME_PRESSURE_RECOVERY_FRAMES;
    }

    private static int defaultCriticalExtra() {
        return MAX_ANIMATED > 0 ? Math.max(8, MAX_ANIMATED / 4) : 0;
    }

    private static int defaultHysteresisExtra() {
        return MAX_ANIMATED > 0 ? Math.max(4, MAX_ANIMATED / 8) : 0;
    }

    private static int defaultVisibleChunkMidMax() {
        return MAX_ANIMATED > 0 ? Math.max(8, (MAX_ANIMATED * 2) / 3) : 0;
    }

    private static int defaultVisibleChunkHighMax() {
        return MAX_ANIMATED > 0 ? Math.max(8, MAX_ANIMATED / 3) : 0;
    }

    private static int defaultVisibleChunkStep() {
        return MAX_ANIMATED > 0 ? Math.max(4, MAX_ANIMATED / 12) : 1;
    }

    private static int defaultVisibleChunkRecoveryStep() {
        return MAX_ANIMATED > 0 ? Math.max(2, MAX_ANIMATED / 24) : 1;
    }

    private static int defaultFramePressureStep() {
        return MAX_ANIMATED > 0 ? Math.max(8, MAX_ANIMATED / 2) : 1;
    }

    private static int defaultFramePressureMin() {
        if (MAX_ANIMATED <= 0) return 0;
        return Math.max(8, Math.min(16, MAX_ANIMATED / 4));
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

    private static double doubleProperty(String name, double fallback, double min, double max) {
        String raw = System.getProperty(name);
        if (raw == null) return fallback;
        try {
            double parsed = Double.parseDouble(raw.trim());
            return parsed >= min && parsed <= max ? parsed : fallback;
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    public static int acceptedThisFrame() {
        return acceptedThisFrame;
    }

    public static int rejectedThisFrame() {
        return rejectedThisFrame;
    }

    public static int criticalAcceptedThisFrame() {
        return criticalAcceptedThisFrame;
    }

    public static int hysteresisAcceptedThisFrame() {
        return hysteresisAcceptedThisFrame;
    }

    public static int priorityRejectedThisFrame() {
        return priorityRejectedThisFrame;
    }

    public static int effectiveMaxAnimatedThisFrame() {
        return effectiveMaxAnimated();
    }

    public static int framePressureLimitThisFrame() {
        return framePressureThrottleEnabled() ? Math.max(0, framePressureLimit) : MAX_ANIMATED;
    }

    public static int framePressureDrops() {
        return framePressureDrops;
    }

    public static int throughputBadFrames() {
        return throughputBadFrames;
    }

    private static final class LongIntMap {
        private long[] keys;
        private int[] values;
        private boolean[] used;
        private int size;
        private int threshold;

        LongIntMap(int capacity) {
            int n = 1;
            while (n < capacity) n <<= 1;
            keys = new long[n];
            values = new int[n];
            used = new boolean[n];
            threshold = (n * 2) / 3;
        }

        boolean isEmpty() {
            return size == 0;
        }

        int get(long key, int fallback) {
            int mask = keys.length - 1;
            int idx = mix(key) & mask;
            while (used[idx]) {
                if (keys[idx] == key) return values[idx];
                idx = (idx + 1) & mask;
            }
            return fallback;
        }

        void put(long key, int value) {
            if (size >= threshold) grow();
            putInternal(key, value);
        }

        void clear() {
            if (size == 0) return;
            for (int i = 0; i < used.length; i++) used[i] = false;
            size = 0;
        }

        void removeValuesLessThan(int minValue) {
            if (size == 0) return;
            for (int i = 0; i < used.length; i++) {
                while (used[i] && values[i] < minValue) {
                    removeAt(i);
                }
            }
        }

        private void removeAt(int index) {
            int mask = used.length - 1;
            used[index] = false;
            size--;
            int i = (index + 1) & mask;
            while (used[i]) {
                long key = keys[i];
                int value = values[i];
                used[i] = false;
                size--;
                putInternal(key, value);
                i = (i + 1) & mask;
            }
        }

        private void putInternal(long key, int value) {
            int mask = keys.length - 1;
            int idx = mix(key) & mask;
            while (used[idx]) {
                if (keys[idx] == key) {
                    values[idx] = value;
                    return;
                }
                idx = (idx + 1) & mask;
            }
            used[idx] = true;
            keys[idx] = key;
            values[idx] = value;
            size++;
        }

        private void grow() {
            long[] oldKeys = keys;
            int[] oldValues = values;
            boolean[] oldUsed = used;
            keys = new long[oldKeys.length * 2];
            values = new int[oldValues.length * 2];
            used = new boolean[oldUsed.length * 2];
            threshold = (keys.length * 2) / 3;
            size = 0;
            for (int i = 0; i < oldUsed.length; i++) {
                if (oldUsed[i]) putInternal(oldKeys[i], oldValues[i]);
            }
        }

        private static int mix(long x) {
            x ^= (x >>> 33);
            x *= 0xff51afd7ed558ccdL;
            x ^= (x >>> 33);
            return (int) x;
        }
    }

    private static final class LongByteMap {
        private final LongIntMap delegate;

        LongByteMap(int capacity) {
            delegate = new LongIntMap(capacity);
        }

        boolean isEmpty() {
            return delegate.isEmpty();
        }

        int get(long key, int fallback) {
            return delegate.get(key, fallback);
        }

        void put(long key, int value) {
            delegate.put(key, value);
        }

        void clear() {
            delegate.clear();
        }
    }
}
