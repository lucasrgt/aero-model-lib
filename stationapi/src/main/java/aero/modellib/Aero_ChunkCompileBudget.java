package aero.modellib;

/**
 * Optional frame-smoothing governor for vanilla chunk display-list rebuilds.
 *
 * <p>Dense Aero scenes can leave very little frame budget for StationAPI /
 * vanilla chunk rebuilds. Those rebuilds are correct work, but they allocate
 * several MB per rebuilt chunk on old Beta render paths; when they happen on
 * consecutive frames next to hundreds of BlockEntities, the player sees a
 * rhythmic heap climb and sudden GC/driver stalls. This governor spaces that
 * work out in dense scenes instead of letting chunk rebuilds pile onto every
 * frame.
 *
 * <p>Default off because it changes vanilla terrain update latency. Consumers
 * can opt in for heavy factory-style scenes with
 * {@code -Daero.chunkCompileBudget=true}.
 */
public final class Aero_ChunkCompileBudget {

    public static final boolean ENABLED =
        "true".equalsIgnoreCase(System.getProperty("aero.chunkCompileBudget"));

    private static final int STRIDE =
        intProperty("aero.chunkCompileBudget.stride", 3, 1, 60);
    private static final int MIN_CELL_WORK =
        intProperty("aero.chunkCompileBudget.minCellWork", 96, 0, 100000);
    private static final int MIN_VISIBLE_CHUNKS =
        intProperty("aero.chunkCompileBudget.minVisibleChunks", 320, 0, 100000);
    private static final boolean SKIP_FORCED =
        "true".equalsIgnoreCase(System.getProperty("aero.chunkCompileBudget.skipForced"));

    private static int frameIndex;
    private static int skippedThisFrame;
    private static int skippedLastFrame;

    private Aero_ChunkCompileBudget() {}

    public static void beginFrame() {
        frameIndex++;
        skippedLastFrame = skippedThisFrame;
        skippedThisFrame = 0;
    }

    public static boolean shouldSkip(boolean forced) {
        if (!ENABLED) return false;
        if (forced && !SKIP_FORCED) return false;
        if (!isDenseAeroFrame()) return false;
        if (STRIDE <= 1) return false;
        boolean allowThisFrame = (frameIndex % STRIDE) == 0;
        if (allowThisFrame) return false;
        skippedThisFrame++;
        return true;
    }

    public static int skippedThisFrame() {
        return skippedThisFrame;
    }

    public static int skippedLastFrame() {
        return skippedLastFrame;
    }

    private static boolean isDenseAeroFrame() {
        int cellWork = Aero_BECellRenderer.queuedLastFrame()
            + Aero_BECellRenderer.pageCallsThisFrame()
            + Aero_MeshRenderer.atRestListCallsThisFrame()
            + Aero_AnimatedBatcher.queuedThisFrame();
        if (cellWork >= MIN_CELL_WORK) return true;
        int visibleChunks = Aero_ChunkVisibility.visibleChunkCount();
        return visibleChunks >= MIN_VISIBLE_CHUNKS && cellWork > 0;
    }

    private static int intProperty(String name, int fallback, int min, int max) {
        String raw = System.getProperty(name);
        if (raw == null) return fallback;
        try {
            int parsed = Integer.parseInt(raw.trim());
            if (parsed < min) return min;
            if (parsed > max) return max;
            return parsed;
        } catch (NumberFormatException e) {
            return fallback;
        }
    }
}
