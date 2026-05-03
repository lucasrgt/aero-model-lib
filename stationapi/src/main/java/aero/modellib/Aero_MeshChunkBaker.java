package aero.modellib;

import net.minecraft.client.render.Tessellator;
import net.minecraft.world.BlockView;

import aero.modellib.model.Aero_MeshModel;
import aero.modellib.render.Aero_RenderOptions;

/**
 * Static-mesh chunk-bake registry — the v0.2.5 fast-path for blocks whose
 * geometry never changes per-frame.
 *
 * <h2>Why</h2>
 * <p>The default modellib block path is {@code BlockEntity + BlockEntityRenderer},
 * which dispatches once per frame per visible BE — every visible block does
 * matrix push/translate, 4× display-list call (or 4× Tessellator.draw),
 * matrix pop, plus a cone-cull check. With ~200 BEs visible in the stress
 * test that's ~800 GL/JNI calls per frame just for static geometry.
 *
 * <p>Vanilla blocks avoid this entirely: their faces are baked into the
 * chunk's vertex buffer once at chunk rebuild and replayed for free until
 * the chunk is dirtied. This class provides the same fast path for modellib
 * mesh models — registered blocks have their triangles emitted directly
 * into the active chunk Tessellator during {@code BlockRenderManager.render},
 * so they cost zero per-frame work.
 *
 * <h2>API contract</h2>
 * <p>The library itself is decoupled from StationAPI's atlas API — the
 * caller supplies a {@link UvBoundsProvider} that resolves the atlas-relative
 * UV bounds for the block's texture. The provider is invoked lazily on first
 * render (since the StationAPI atlas is initialized after blocks register)
 * and the result is cached for the lifetime of the JVM.
 *
 * <p>Typical caller pattern (using StationAPI's atlas API):
 * <pre>
 * Aero_MeshChunkBaker.register(myBlock, MyModel.MESH, () -&gt; {
 *     Atlas.Sprite sprite = myBlock.getAtlas().getTexture(textureIndex);
 *     return new float[] {
 *         (float) sprite.getStartU(), (float) sprite.getEndU(),
 *         (float) sprite.getStartV(), (float) sprite.getEndV()
 *     };
 * });
 * </pre>
 *
 * <h2>Constraints (v0.2.5 first pass)</h2>
 * <ul>
 *   <li><strong>Single terrain-atlas texture per registered block.</strong>
 *       The chunk's Tessellator session has one active texture binding.</li>
 *   <li><strong>No animation / morph / IK.</strong> The bake is a flat
 *       triangle list — animated parts must stay on the BlockEntity
 *       renderer path.</li>
 *   <li><strong>BlockEntity is optional.</strong> A registered block can
 *       skip the BE entirely. If a block has BOTH chunk-bake AND a BE
 *       renderer, the BE renderer should NOT redraw the chunk-baked
 *       geometry — only the moving parts.</li>
 * </ul>
 *
 * @see aero.modellib.mixin.BlockRenderManagerChunkBakeMixin
 */
public final class Aero_MeshChunkBaker {

    private static final int INITIAL_REGISTRY_SIZE = 4096;
    private static BakedEntry[] REGISTRY = new BakedEntry[INITIAL_REGISTRY_SIZE];

    private Aero_MeshChunkBaker() {}

    /**
     * Lazy resolver for atlas-relative UV bounds. Called once per registered
     * block on first render (when the atlas is guaranteed to be initialized);
     * the result is cached forever. Implementations should call StationAPI's
     * atlas API to look up the sprite's actual coordinates — vanilla's
     * "tile = textureId / 16" heuristic is wrong because StationAPI packs
     * sprites dynamically.
     */
    public interface UvBoundsProvider {
        /** Returns {@code float[4] = {minU, maxU, minV, maxV}}. */
        float[] resolveUvBounds();
    }

    public static void register(int blockId, Aero_MeshModel model, UvBoundsProvider uvBounds) {
        register(blockId, model, uvBounds, Aero_RenderOptions.DEFAULT);
    }

    public static void register(int blockId, Aero_MeshModel model,
                                UvBoundsProvider uvBounds, Aero_RenderOptions options) {
        if (model == null) throw new IllegalArgumentException("model must not be null");
        if (uvBounds == null) throw new IllegalArgumentException("uvBounds must not be null");
        if (options == null) options = Aero_RenderOptions.DEFAULT;
        if (blockId < 0) throw new IllegalArgumentException("blockId must be >= 0");
        if (blockId >= REGISTRY.length) {
            int newSize = Math.max(REGISTRY.length * 2, blockId + 1);
            BakedEntry[] grown = new BakedEntry[newSize];
            System.arraycopy(REGISTRY, 0, grown, 0, REGISTRY.length);
            REGISTRY = grown;
        }
        REGISTRY[blockId] = new BakedEntry(model, uvBounds, options);
    }

    public static boolean isRegistered(int blockId) {
        return blockId >= 0 && blockId < REGISTRY.length && REGISTRY[blockId] != null;
    }

    /** Internal — used by the chunk-bake mixin. Returns null if not registered. */
    public static BakedEntry get(int blockId) {
        if (blockId < 0 || blockId >= REGISTRY.length) return null;
        return REGISTRY[blockId];
    }

    /**
     * Idempotent best-effort eager bake of every registered entry. Safe to
     * call repeatedly: entries already baked are skipped, entries whose
     * {@link UvBoundsProvider} is not yet ready (atlas not stitched) are
     * left for a later attempt without poisoning them. Once all entries are
     * either successfully baked or have hit a non-recoverable bake failure,
     * returns {@code true} so the caller can stop polling.
     *
     * <p>This avoids the "primeiro avistamento" frame-spike: lazy baking
     * happens inside {@code BlockRenderManager.render} during a chunk
     * rebuild, where the {@code float[4][][]} + {@code float[15]}-per-tri
     * allocations land mid-frame on the same stack as the vanilla
     * Tessellator buffers. Pre-warming once on the render thread (after
     * the atlas is ready) moves that allocation cost out of the chunk
     * compile path.
     *
     * @return true once every registered entry is either baked or failed.
     */
    public static boolean prewarmAll() {
        boolean allDone = true;
        for (int i = 0; i < REGISTRY.length; i++) {
            BakedEntry entry = REGISTRY[i];
            if (entry == null) continue;
            if (!entry.prewarmBake()) {
                allDone = false;
            }
        }
        return allDone;
    }

    /**
     * Emits a registered block's geometry into the currently-active chunk
     * Tessellator. Called from the BlockRenderManager mixin during chunk
     * rebuild — the session is already in {@code GL_QUADS} mode, so we
     * emit each triangle as a degenerate quad (last vertex doubled).
     *
     * @return true if any geometry was emitted (vanilla render contract).
     */
    public static boolean emit(BakedEntry entry, BlockView world, int x, int y, int z) {
        if (entry == null) return false;
        float[][][] tris = entry.bakedTris();
        if (tris == null) return false;

        Tessellator tess = Tessellator.INSTANCE;
        // method_1782 is the float-brightness equivalent of getLightBrightness —
        // Yarn for Beta 1.7.3 hasn't named it yet.
        float brightness = world.method_1782(x, y, z);

        boolean anyEmitted = false;
        for (int g = 0; g < 4; g++) {
            float[][] groupTris = tris[g];
            if (groupTris.length == 0) continue;
            float bright = brightness * Aero_MeshModel.BRIGHTNESS_FACTORS[g];
            tess.color(bright * entry.tintR, bright * entry.tintG,
                       bright * entry.tintB, entry.alpha);
            for (int i = 0; i < groupTris.length; i++) {
                float[] t = groupTris[i];
                // Degenerate quad: emit (v0, v1, v2, v2) so the rasterizer
                // discards the second triangle (zero area, zero pixels).
                tess.vertex(x + t[0],  y + t[1],  z + t[2],  t[3],  t[4]);
                tess.vertex(x + t[5],  y + t[6],  z + t[7],  t[8],  t[9]);
                tess.vertex(x + t[10], y + t[11], z + t[12], t[13], t[14]);
                tess.vertex(x + t[10], y + t[11], z + t[12], t[13], t[14]);
            }
            anyEmitted = true;
        }
        return anyEmitted;
    }

    /**
     * Pre-baked geometry container with lazy atlas-UV resolution. The
     * triangles are pre-multiplied by {@code model.invScale} and have
     * UVs remapped from {@code [0,1]} model space into the atlas-relative
     * coords supplied by the {@link UvBoundsProvider}.
     */
    public static final class BakedEntry {
        private final Aero_MeshModel model;
        private final UvBoundsProvider uvBounds;
        final float tintR, tintG, tintB, alpha;
        private float[][][] cachedTris;
        private boolean bakeFailed;

        BakedEntry(Aero_MeshModel model, UvBoundsProvider uvBounds, Aero_RenderOptions options) {
            this.model = model;
            this.uvBounds = uvBounds;
            this.tintR = options.tintR;
            this.tintG = options.tintG;
            this.tintB = options.tintB;
            this.alpha = options.alpha;
        }

        /** Returns the lazily-baked triangle table, or null on bake failure. */
        float[][][] bakedTris() {
            if (cachedTris != null) return cachedTris;
            if (bakeFailed) return null;
            try {
                cachedTris = bake();
                return cachedTris;
            } catch (Throwable t) {
                bakeFailed = true;
                System.err.println("[aero-model-lib] chunk-bake failed: " + t);
                return null;
            }
        }

        /**
         * Pre-warm variant for {@link Aero_MeshChunkBaker#prewarmAll()}: the
         * "atlas not yet stitched" case (UV provider returns null) is NOT
         * treated as a permanent failure — the entry stays unbaked so a
         * later prewarm pass can succeed once the atlas is ready. Real
         * bake failures still poison the entry the same way as
         * {@link #bakedTris()} so retries don't loop forever.
         *
         * @return true if the entry is in a terminal state (baked or
         *         permanently failed), false if a retry is warranted.
         */
        boolean prewarmBake() {
            if (cachedTris != null || bakeFailed) return true;
            float[] uv;
            try {
                uv = uvBounds.resolveUvBounds();
            } catch (Throwable t) {
                bakeFailed = true;
                System.err.println("[aero-model-lib] chunk-bake UV resolve failed: " + t);
                return true;
            }
            if (uv == null || uv.length != 4) {
                // Atlas not yet stitched (or provider returns malformed
                // bounds). Defer — the normal lazy path will surface a
                // hard error later if this never resolves.
                return false;
            }
            try {
                cachedTris = bake();
                return true;
            } catch (Throwable t) {
                bakeFailed = true;
                System.err.println("[aero-model-lib] chunk-bake failed: " + t);
                return true;
            }
        }

        private float[][][] bake() {
            float[] uv = uvBounds.resolveUvBounds();
            if (uv == null || uv.length != 4) {
                throw new IllegalStateException(
                    "UvBoundsProvider must return float[4] = {minU, maxU, minV, maxV}");
            }
            float minU = uv[0], maxU = uv[1], minV = uv[2], maxV = uv[3];
            float uRange = maxU - minU;
            float vRange = maxV - minV;

            float invScale = model.invScale;
            float[][][] baked = new float[4][][];
            for (int g = 0; g < 4; g++) {
                float[][] src = model.groups[g];
                float[][] dst = new float[src.length][];
                for (int i = 0; i < src.length; i++) {
                    float[] s = src[i];
                    float[] d = new float[15];
                    for (int v = 0; v < 3; v++) {
                        int off = v * 5;
                        d[off    ] = s[off    ] * invScale;
                        d[off + 1] = s[off + 1] * invScale;
                        d[off + 2] = s[off + 2] * invScale;
                        d[off + 3] = minU + s[off + 3] * uRange;
                        d[off + 4] = minV + s[off + 4] * vRange;
                    }
                    dst[i] = d;
                }
                baked[g] = dst;
            }
            return baked;
        }
    }
}
