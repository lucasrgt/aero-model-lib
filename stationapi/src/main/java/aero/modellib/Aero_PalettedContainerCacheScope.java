package aero.modellib;

/**
 * Per-thread scope used by the StationAPI PalettedContainer cache mixin.
 *
 * <p>The old {@code -Daero.palettedcache=true} mode enables the cache on every
 * {@code PalettedContainer.get(int)} call. That proved too expensive in steady
 * state. This scope lets the cache activate only while vanilla is rebuilding a
 * chunk display list, which is the workload that originally showed locality.
 */
public final class Aero_PalettedContainerCacheScope {

    private static final boolean CHUNK_SCOPE_ENABLED =
        "true".equalsIgnoreCase(System.getProperty("aero.palettedcache.chunkScope"));

    private static final ThreadLocal DEPTH = new ThreadLocal() {
        @Override
        protected Object initialValue() {
            return Integer.valueOf(0);
        }
    };

    private Aero_PalettedContainerCacheScope() {
    }

    public static void beginChunkBuild() {
        if (!CHUNK_SCOPE_ENABLED) return;
        int depth = ((Integer) DEPTH.get()).intValue();
        DEPTH.set(Integer.valueOf(depth + 1));
    }

    public static void endChunkBuild() {
        if (!CHUNK_SCOPE_ENABLED) return;
        int depth = ((Integer) DEPTH.get()).intValue();
        if (depth <= 1) {
            DEPTH.set(Integer.valueOf(0));
        } else {
            DEPTH.set(Integer.valueOf(depth - 1));
        }
    }

    public static boolean isActive() {
        return CHUNK_SCOPE_ENABLED && ((Integer) DEPTH.get()).intValue() > 0;
    }
}
