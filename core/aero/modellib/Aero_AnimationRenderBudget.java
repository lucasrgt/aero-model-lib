package aero.modellib;

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
        Integer.getInteger("aero.maxAnimatedBE", 128).intValue();

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

    private static final HashMap<Object, Integer> HELD_UNTIL_FRAME =
        new HashMap<Object, Integer>();

    private static double focalPx;
    private static int cachedDisplayHeight = -1;
    private static int frameIndex;
    private static int acceptedThisFrame;
    private static int rejectedThisFrame;
    private static int criticalAcceptedThisFrame;
    private static int hysteresisAcceptedThisFrame;
    private static int priorityRejectedThisFrame;

    private Aero_AnimationRenderBudget() {}

    /** Resets counters at the start of a visual render frame. */
    public static void beginFrame() {
        frameIndex++;
        acceptedThisFrame = 0;
        rejectedThisFrame = 0;
        criticalAcceptedThisFrame = 0;
        hysteresisAcceptedThisFrame = 0;
        priorityRejectedThisFrame = 0;
        if ((frameIndex & 31) == 0) {
            expireHolds();
        }
    }

    /** Updates the projection math used by priority scoring. */
    public static void updateFromDisplayHeight(int displayHeight) {
        if (displayHeight <= 0 || displayHeight == cachedDisplayHeight) return;
        focalPx = displayHeight / (2.0d * TAN_HALF_VFOV);
        cachedDisplayHeight = displayHeight;
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
        if (!ENABLED || MAX_ANIMATED < 0) return lod;
        double distSq = x * x + y * y + z * z;
        double projectedPx = projectedDiameterPx(distSq, visualRadiusBlocks);
        if (isHeld(hysteresisKey) && acceptedThisFrame < hysteresisLimit()) {
            accept(hysteresisKey, true, false);
            return lod;
        }
        if (isCritical(distSq, projectedPx) && acceptedThisFrame < criticalLimit()) {
            accept(hysteresisKey, false, true);
            return lod;
        }

        if (shouldPriorityReject(projectedPx)) {
            rejectedThisFrame++;
            priorityRejectedThisFrame++;
            return Aero_RenderLod.STATIC;
        }

        if (acceptedThisFrame < MAX_ANIMATED) {
            accept(hysteresisKey, false, false);
            return lod;
        }
        rejectedThisFrame++;
        return Aero_RenderLod.STATIC;
    }

    private static boolean isCritical(double distSq, double projectedPx) {
        if (projectedPx >= CRITICAL_PX) return true;
        return NEAR_BLOCKS > 0.0d && distSq <= NEAR_BLOCKS * NEAR_BLOCKS;
    }

    private static boolean shouldPriorityReject(double projectedPx) {
        if (MAX_ANIMATED <= 0) return true;
        int lowLimit = Math.max(1, MAX_ANIMATED / 2);
        int midLimit = Math.max(lowLimit, (MAX_ANIMATED * 3) / 4);
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

    private static void rememberHold(Object key) {
        if (key == null || HYSTERESIS_FRAMES <= 0) return;
        HELD_UNTIL_FRAME.put(key, Integer.valueOf(frameIndex + HYSTERESIS_FRAMES));
    }

    private static void expireHolds() {
        if (HELD_UNTIL_FRAME.isEmpty()) return;
        Iterator<Map.Entry<Object, Integer>> it = HELD_UNTIL_FRAME.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<Object, Integer> entry = it.next();
            Integer until = entry.getValue();
            if (until == null || until.intValue() < frameIndex) {
                it.remove();
            }
        }
    }

    private static int hysteresisLimit() {
        return MAX_ANIMATED + Math.max(0, HYSTERESIS_EXTRA);
    }

    private static int criticalLimit() {
        return MAX_ANIMATED + Math.max(0, HYSTERESIS_EXTRA) + Math.max(0, CRITICAL_EXTRA);
    }

    private static int defaultCriticalExtra() {
        return MAX_ANIMATED > 0 ? Math.max(8, MAX_ANIMATED / 4) : 0;
    }

    private static int defaultHysteresisExtra() {
        return MAX_ANIMATED > 0 ? Math.max(4, MAX_ANIMATED / 8) : 0;
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
}
