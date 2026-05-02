package aero.modellib;

import net.minecraft.client.render.chunk.ChunkBuilder;
import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;

/**
 * Per-frame set of "visible" chunks — those that vanilla's
 * {@code WorldRenderer.cullChunks} has flagged as in-frustum this frame.
 *
 * <h2>Why</h2>
 * <p>The {@code Aero_RenderDistance.blockEntityDistanceFrom} cull stack
 * (cone + 6-plane + occlusion + distance) decides per BE whether to
 * render. The first three layers are SOLID for far / behind-camera
 * cases but their results flip when the camera moves a fraction of a
 * block — causing on-jump flicker where BEs near the line-of-sight
 * boundary pop in/out as the player's eye level changes.
 *
 * <p>Chunk-level frustum visibility doesn't have this problem.
 * {@code WorldRenderer.cullChunks} runs once per frame and produces a
 * stable yes/no for each chunk; the result only changes when the
 * camera enters/exits a chunk's view cone, not on per-frame jiggle.
 * Vanilla already uses this for its OWN chunk render skip; we mirror
 * the same decision for BE rendering.
 *
 * <h2>How it works</h2>
 * <p>{@code Aero_RenderDistance.beginRenderFrame()} (called from
 * {@code GameRendererMixin} HEAD on {@code GameRenderer.renderWorld})
 * snapshots the {@code inFrustum} flag from every loaded
 * {@code ChunkBuilder} into a {@link LongOpenHashSet} keyed by
 * (chunkX, chunkZ) packed into a long. {@code blockEntityDistanceFrom}
 * looks up the BE's chunk in this set and returns
 * {@link Double#POSITIVE_INFINITY} if absent — vanilla's BE dispatcher
 * treats Infinity as out-of-range, skipping the BE entirely.
 *
 * <p><strong>Timing</strong>: vanilla's {@code cullChunks} runs early in
 * {@code renderWorld} (before chunk + entity render passes). Our snapshot
 * runs on entry to {@code renderWorld}, so it reads the PREVIOUS frame's
 * frustum decisions. One frame of staleness is invisible at 60+ FPS and
 * avoids needing a TAIL injection on cullChunks (which has had
 * descriptor-stability issues in earlier StationAPI internal classes).
 *
 * <h2>Toggle</h2>
 * <p>{@code -Daero.chunkvisibility=false} disables the cull (everything
 * passes). On by default — chunk-level cull is conservative (the BE has to
 * be in a chunk vanilla agrees is invisible to be skipped) and produces
 * no flicker because chunk-frustum decisions stabilize when the camera
 * isn't actively moving.
 */
public final class Aero_ChunkVisibility {

    public static final boolean ENABLED =
        !"false".equalsIgnoreCase(System.getProperty("aero.chunkvisibility"));
    public static final boolean HONOR_OCCLUSION_QUERY =
        "true".equalsIgnoreCase(System.getProperty("aero.chunkvisibility.hoq"));

    private static final int HOLD_FRAMES =
        intProperty("aero.chunkvisibility.holdFrames", 6, 0, 120);
    private static final double RADIUS_PADDING_BLOCKS =
        doubleProperty("aero.chunkvisibility.radiusPad", 2.0d, 0.0d, 32.0d);

    /**
     * Visible chunk set keyed by packed {@code (chunkX, chunkZ)}. Reused
     * across frames; cleared and rebuilt by {@link #snapshot}. Allocated
     * with capacity matching typical render-distance chunk counts so
     * the rebuild rarely needs to grow.
     */
    private static final LongOpenHashSet VISIBLE = new LongOpenHashSet(2048);
    private static final Long2IntOpenHashMap RECENT_UNTIL = new Long2IntOpenHashMap(4096);

    /**
     * Whether the snapshot has been populated for the current frame.
     * Until set true, lookups return true (no cull) so we don't false-
     * cull during init or before the first {@code beginRenderFrame}.
     */
    private static boolean snapshotValid = false;

    // Last-hit cache for chunk visibility queries. Beta's BlockEntity
    // dispatcher iterates BEs in chunk-clustered order — when a chunk
    // holds 100+ BEs (mega/factory tower), the cache hits 99% of the
    // time and elides the LongOpenHashSet.contains call. Reset on every
    // snapshot so stale results never leak across frames.
    private static int lastQueryChunkX;
    private static int lastQueryChunkZ;
    private static boolean lastQueryResult;
    private static boolean lastQueryValid;
    private static int frameIndex;

    static {
        RECENT_UNTIL.defaultReturnValue(Integer.MIN_VALUE);
    }

    private Aero_ChunkVisibility() {}

    /**
     * Builds the visible-chunk set from {@code WorldRenderer.chunks[]}.
     * Each {@code ChunkBuilder} with {@code inFrustum=true} contributes
     * its (chunkX, chunkZ) to the set. Y is collapsed because Beta 1.7.3
     * worlds are flat-Y (single chunk column from y=0..127); a BE
     * column is fully covered by 8 ChunkBuilders all sharing the same
     * (chunkX, chunkZ).
     */
    public static void snapshot(ChunkBuilder[] chunks) {
        frameIndex++;
        if (!ENABLED) {
            snapshotValid = false;
            lastQueryValid = false;
            RECENT_UNTIL.clear();
            return;
        }
        VISIBLE.clear();
        lastQueryValid = false;
        if (chunks == null) {
            snapshotValid = false;
            return;
        }
        for (int i = 0; i < chunks.length; i++) {
            ChunkBuilder cb = chunks[i];
            if (cb == null || !cb.inFrustum) continue;
            // Hardware-occlusion-query check: vanilla Beta issues HOQ
            // per chunk via the "Advanced OpenGL" option. If queries are
            // running and the result says "no samples passed" (chunk is
            // entirely behind solid terrain), skip the BEs in that chunk.
            // Otherwise (query not ready yet, or HOQ disabled, or chunk
            // is genuinely visible) — pass through. This is the GPU-
            // accurate version of the raycast occlusion we tried earlier;
            // no flicker because GPU samples are stable per-frame, no
            // false-cull because we only act on a confirmed "0 samples"
            // result.
            if (HONOR_OCCLUSION_QUERY && cb.occlusionQueryReady && !cb.unoccluded) continue;
            // ChunkBuilder.x/y/z is the BLOCK-coord origin of this chunk
            // builder. Beta chunks are 16x16x16 in render terms (the
            // chunk-builder grid; world chunks are 16×128×16 split into
            // 8 builder slices). We collapse Y — BE columns share x/z.
            long key = packChunkKey(cb.x >> 4, cb.z >> 4);
            VISIBLE.add(key);
            if (HOLD_FRAMES > 0) {
                RECENT_UNTIL.put(key, frameIndex + HOLD_FRAMES);
            }
        }
        snapshotValid = true;
        if ((frameIndex & 63) == 0) {
            sweepRecentChunks();
        }
    }

    /**
     * Returns true if any chunk-builder slice at {@code (chunkX, chunkZ)}
     * was in-frustum during the most recent {@link #snapshot} call. When
     * the snapshot hasn't run yet (early init), returns true — fail-open
     * so the user never gets a black world due to a misconfigured cull.
     */
    public static boolean isChunkVisible(int chunkX, int chunkZ) {
        if (!ENABLED || !snapshotValid) return true;
        // Last-hit cache — BE dispatcher iterates in chunk-clustered order
        // so 100+ BEs in the same chunk produce 1 set lookup + N-1 cache
        // hits. Reset by snapshot() at frame boundaries.
        if (lastQueryValid
            && chunkX == lastQueryChunkX
            && chunkZ == lastQueryChunkZ) {
            return lastQueryResult;
        }
        long key = packChunkKey(chunkX, chunkZ);
        boolean result = VISIBLE.contains(key) || isRecentlyVisible(key);
        lastQueryChunkX = chunkX;
        lastQueryChunkZ = chunkZ;
        lastQueryResult = result;
        lastQueryValid = true;
        return result;
    }

    /**
     * Convenience: takes BE world coords (block-aligned) and returns the
     * chunk-level visibility result. Equivalent to
     * {@code isChunkVisible(beX >> 4, beZ >> 4)}.
     */
    public static boolean isBlockChunkVisible(int beX, int beZ) {
        return isChunkVisible(beX >> 4, beZ >> 4);
    }

    /**
     * BE visibility with a horizontal radius. This avoids false-culling large
     * models whose origin chunk left vanilla's chunk frustum while part of
     * their mesh is still visible in a neighboring chunk.
     */
    public static boolean isBlockChunkVisible(int beX, int beZ,
                                              double visualRadiusBlocks) {
        if (!ENABLED || !snapshotValid) return true;
        double radius = visualRadiusBlocks > 0.0d ? visualRadiusBlocks : 0.5d;
        radius += RADIUS_PADDING_BLOCKS;
        if (radius <= 0.0d) return isBlockChunkVisible(beX, beZ);

        double centerX = beX + 0.5d;
        double centerZ = beZ + 0.5d;
        int minChunkX = floorToChunk(centerX - radius);
        int maxChunkX = floorToChunk(centerX + radius);
        int minChunkZ = floorToChunk(centerZ - radius);
        int maxChunkZ = floorToChunk(centerZ + radius);
        for (int cz = minChunkZ; cz <= maxChunkZ; cz++) {
            for (int cx = minChunkX; cx <= maxChunkX; cx++) {
                if (isChunkVisible(cx, cz)) return true;
            }
        }
        return false;
    }

    public static boolean isBlockAreaChunkVisible(int minBlockX, int minBlockZ,
                                                  int maxBlockX, int maxBlockZ) {
        if (!ENABLED || !snapshotValid) return true;
        int minChunkX = floorToChunk(minBlockX);
        int maxChunkX = floorToChunk(maxBlockX);
        int minChunkZ = floorToChunk(minBlockZ);
        int maxChunkZ = floorToChunk(maxBlockZ);
        for (int cz = minChunkZ; cz <= maxChunkZ; cz++) {
            for (int cx = minChunkX; cx <= maxChunkX; cx++) {
                if (isChunkVisible(cx, cz)) return true;
            }
        }
        return false;
    }

    /** Packs (chunkX, chunkZ) into a 64-bit key. */
    private static long packChunkKey(int chunkX, int chunkZ) {
        return ((long) chunkX << 32) | (chunkZ & 0xFFFFFFFFL);
    }

    /** Used by tests / debug to expose the snapshot size. */
    public static int visibleChunkCount() {
        return snapshotValid ? VISIBLE.size() : -1;
    }

    public static int recentChunkCount() {
        return RECENT_UNTIL.size();
    }

    private static boolean isRecentlyVisible(long key) {
        return HOLD_FRAMES > 0 && RECENT_UNTIL.get(key) >= frameIndex;
    }

    private static void sweepRecentChunks() {
        if (RECENT_UNTIL.isEmpty()) return;
        long[] keys = RECENT_UNTIL.keySet().toLongArray();
        for (int i = 0; i < keys.length; i++) {
            long key = keys[i];
            if (RECENT_UNTIL.get(key) < frameIndex) {
                RECENT_UNTIL.remove(key);
            }
        }
    }

    private static int floorToChunk(double blockCoord) {
        return floorToInt(blockCoord) >> 4;
    }

    private static int floorToInt(double value) {
        int i = (int) value;
        return value < (double) i ? i - 1 : i;
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

    private static double doubleProperty(String name, double fallback,
                                         double min, double max) {
        String raw = System.getProperty(name);
        if (raw == null) return fallback;
        try {
            double parsed = Double.parseDouble(raw.trim());
            if (Double.isNaN(parsed) || Double.isInfinite(parsed)) return fallback;
            if (parsed < min) return min;
            if (parsed > max) return max;
            return parsed;
        } catch (NumberFormatException e) {
            return fallback;
        }
    }
}
