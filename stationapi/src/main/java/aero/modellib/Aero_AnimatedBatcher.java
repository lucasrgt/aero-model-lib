package aero.modellib;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;

import java.util.Arrays;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.IdentityHashMap;

/**
 * Per-frame collector for animated BlockEntity renders. Coalesces multiple
 * instances of the same {@link Aero_MeshModel} into a single Tessellator
 * draw cycle per bone, instead of one cycle per instance.
 *
 * <h2>Why</h2>
 * <p>Each call to {@link Aero_MeshRenderer#renderAnimated} triggers
 * {@code tess.start} → emit verts → {@code tess.draw} per brightness
 * bucket (combined into 1 cycle) and per named bone. With N instances
 * of the same model visible (e.g. the stress test's 3×3 motor grid +
 * the 1 motor from the regular showcase row = 10 motors), we currently
 * pay {@code N × bones × 30-50 µs} of GL state setup cost.
 *
 * <p>This collector lets BERs queue their render call instead of
 * running it immediately. At end-of-entity-pass (via
 * {@code WorldRendererBatchFlushMixin}), all queued entries are
 * drained: same-model instances are emitted together — 1 cycle per
 * bone regardless of N.
 *
 * <h2>How it works</h2>
 * <p>BERs replace {@code Aero_MeshRenderer.renderAnimated(...)} with
 * {@link #queueAnimated(Aero_MeshModel, Aero_AnimationBundle, Aero_AnimationDefinition,
 * Aero_AnimationPlayback, double, double, double, float, float, Aero_RenderOptions)}.
 * The collector keys batches by model identity (IdentityHashMap). At
 * flush time, {@link Aero_MeshRenderer#renderAnimatedBatch} drains the
 * batch in one Tessellator session per bone with per-vertex CPU matrix
 * transforms.
 *
 * <h2>Constraints (v3.0 first cut)</h2>
 * <ul>
 *   <li><strong>Single-bone-depth models only.</strong> Flat-skeleton models
 *       (no ancestor chains, e.g. motors) batch cleanly; nested skeletons
 *       (robots) fall back to per-instance rendering at flush time.</li>
 *   <li><strong>No procedural pose / IK / morph batching.</strong> BERs
 *       using those features still render directly via the non-batched
 *       overloads.</li>
 *   <li><strong>No animation stack / graph batching.</strong> Stack and
 *       Graph overloads route to the non-batched path.</li>
 * </ul>
 *
 * <p>Toggle {@code -Daero.animatedbatch=false} to bypass entirely (each
 * {@code queueAnimated} renders immediately, identical to the pre-3.0
 * path). Useful for A/B testing or sidestepping a regression.
 *
 * <h2>Threading</h2>
 * <p>All access is single-threaded (render thread). The {@link Batch}
 * arrays grow on demand with no synchronization.
 */
public final class Aero_AnimatedBatcher {

    public static final boolean ENABLED =
        !"false".equalsIgnoreCase(System.getProperty("aero.animatedbatch"));

    /**
     * When true, {@link #flush()} sorts {@link #ACTIVE_BATCHES} by
     * {@link Batch#texturePath} so batches sharing a texture drain back
     * to back. Combined with the {@link #lastBoundPath} dedup in
     * {@link #bindBatchTexture(Batch)}, this elides redundant
     * {@code glBindTexture} calls between models that all reference the
     * same atlas (the common case for tech-mod terrain-style textures).
     * Toggle: {@code -Daero.batcher.sort=false}.
     */
    public static final boolean SORT_ENABLED =
        !"false".equalsIgnoreCase(System.getProperty("aero.batcher.sort"));

    /**
     * Per-frame batch storage. Keyed by model identity so different
     * blocks sharing a model coalesce, and the same model across
     * different downstream mods stays apart.
     */
    private static final IdentityHashMap<Aero_MeshModel, Batch> BATCHES = new IdentityHashMap<>();
    private static final ArrayList<Batch> ACTIVE_BATCHES = new ArrayList<Batch>();

    /**
     * Comparator that orders batches by texture path, with nulls first
     * (no-bind path runs before any path-bound batch). Nulls-first keeps
     * the comparator total-ordered without throwing on the rare BER that
     * doesn't set a texture path.
     */
    private static final Comparator<Batch> BY_TEXTURE_PATH =
        new Comparator<Batch>() {
            @Override
            public int compare(Batch a, Batch b) {
                String pa = a.texturePath;
                String pb = b.texturePath;
                if (pa == pb) return 0;            // identity (interned)
                if (pa == null) return -1;
                if (pb == null) return 1;
                return pa.compareTo(pb);
            }
        };

    /**
     * Last texture path bound by {@link #bindBatchTexture(Batch)} during
     * the current flush. Reset to {@code null} at the top of {@link #flush()}
     * so the first bind always fires. With {@link #SORT_ENABLED} on, this
     * dedup eliminates the rebind between adjacent same-texture batches.
     */
    private static String lastBoundPath = null;

    private Aero_AnimatedBatcher() {}

    /**
     * Queues an animated render for end-of-frame flush. If batching is
     * disabled or the queued shape can't be batched (multi-bone, etc),
     * falls back to immediate rendering.
     */
    /**
     * Convenience overload that pulls bundle/def from the state object.
     * Mirrors {@code Aero_MeshRenderer.renderAnimated(model, state, ...)}
     * for BERs that don't track bundle/def separately.
     */
    public static void queueAnimated(Aero_MeshModel model,
                                     String texturePath,
                                     Aero_AnimationPlayback state,
                                     double x, double y, double z,
                                     float brightness,
                                     float partialTick,
                                     Aero_RenderOptions options) {
        if (state == null) throw new IllegalArgumentException("state must not be null");
        queueAnimated(model, texturePath, state.getBundle(), state.getDef(), state,
            x, y, z, brightness, partialTick, options);
    }

    public static void queueAnimated(Aero_MeshModel model,
                                     String texturePath,
                                     Aero_AnimationBundle bundle,
                                     Aero_AnimationDefinition def,
                                     Aero_AnimationPlayback state,
                                     double x, double y, double z,
                                     float brightness,
                                     float partialTick,
                                     Aero_RenderOptions options) {
        if (!ENABLED) {
            // Fallback: caller already bound the texture before calling
            // us, so we can render directly. (Keeps the BER's bindTexture
            // call in the right phase.)
            Aero_MeshRenderer.renderAnimated(model, bundle, def, state,
                x, y, z, brightness, partialTick, options);
            return;
        }
        Batch batch = BATCHES.get(model);
        if (batch == null) {
            batch = new Batch(model, texturePath);
            BATCHES.put(model, batch);
        }
        if (batch.count == 0) ACTIVE_BATCHES.add(batch);
        batch.add(bundle, def, state, x, y, z, brightness, partialTick, options);
    }

    /**
     * Caches path → texture-id lookups so the batcher's per-batch
     * bindTexture call doesn't pay the HashMap hit each flush.
     * Populated lazily on first lookup.
     */
    private static final HashMap<String, Integer> TEXTURE_ID_CACHE = new HashMap<String, Integer>();

    /**
     * Binds the texture at {@code path} via Minecraft's TextureManager.
     * Mirror of {@code BlockEntityRenderer.bindTexture(String)} which is
     * protected and not callable from a static helper. Caches the resolved
     * texture ID per path to skip repeated map lookups on hot paths.
     *
     * <p>If the Minecraft instance is unavailable (very early init),
     * silently no-ops — the renderer fall-through will produce a blank /
     * default-textured mesh, which is the same outcome as a missed
     * bindTexture in the original BER code.
     */
    static void bindTexturePath(String path) {
        if (path == null) return;
        Object game = FabricLoader.getInstance().getGameInstance();
        if (!(game instanceof Minecraft)) return;
        Minecraft mc = (Minecraft) game;
        if (mc.textureManager == null) return;
        Integer cached = TEXTURE_ID_CACHE.get(path);
        int id;
        if (cached == null) {
            id = mc.textureManager.getTextureId(path);
            TEXTURE_ID_CACHE.put(path, Integer.valueOf(id));
        } else {
            id = cached.intValue();
        }
        mc.textureManager.bindTexture(id);
    }

    /**
     * Internal — called by Aero_MeshRenderer.renderAnimatedBatch before
     * emitting. Skips the bind when the path matches the last one bound
     * during the current flush, so adjacent same-texture batches (after
     * the {@link #SORT_ENABLED} sort) cost zero {@code glBindTexture}.
     */
    static void bindBatchTexture(Batch batch) {
        String path = batch.texturePath;
        if (path == null) return;
        if (path == lastBoundPath || path.equals(lastBoundPath)) return;
        bindTexturePath(path);
        lastBoundPath = path;
    }

    /**
     * Drains all queued batches. Called from
     * {@code WorldRendererBatchFlushMixin} at the tail of the entity
     * render pass.
     */
    public static void flush() {
        if (ACTIVE_BATCHES.isEmpty()) return;
        // Reset the bound-path tracker. Vanilla rendering between frames
        // routinely changes glBindTexture, so the bound-id we cached last
        // frame is stale. First bind of this flush must always fire.
        lastBoundPath = null;
        // Sort by texture path so batches sharing a texture drain
        // adjacently, letting bindBatchTexture's lastBoundPath dedup
        // skip redundant glBindTexture calls. Skipped when only one
        // batch is queued (sort cost > savings) or the toggle is off.
        if (SORT_ENABLED && ACTIVE_BATCHES.size() > 1) {
            ACTIVE_BATCHES.sort(BY_TEXTURE_PATH);
        }
        for (int i = 0; i < ACTIVE_BATCHES.size(); i++) {
            Batch b = ACTIVE_BATCHES.get(i);
            Aero_MeshRenderer.renderAnimatedBatch(b);
            b.clear();
        }
        ACTIVE_BATCHES.clear();
        // Map kept populated; Batch instances reused next frame.
    }

    /**
     * Per-model batch — parallel SoA arrays grown on demand. SoA
     * (rather than Array of Object refs) keeps cache lines hotter when
     * the renderer iterates over instances at flush time.
     */
    static final class Batch {
        // Initial capacity tuned for the MEGA torture case (3×3 stack
        // × 16 floors = 144 instances per model per chunk × 1-3 visible
        // chunks ≈ 200 BEs in worst-case approach). Starting at 16 made
        // the first frame after chunk-load do 5 array growths × 9 SoA
        // arrays = 45 array allocations + corresponding GC pressure,
        // which mapped to a visible "FLICK" the user reported on
        // approach. Starting at 256 absorbs the worst case in zero
        // grow operations; oversized cost is small (256 × 9 arrays =
        // ~30KB per unique-model batch) and only paid for batches that
        // actually fire.
        private static final int INITIAL_CAPACITY = 256;
        final Aero_MeshModel model;
        final String texturePath;
        Aero_AnimationBundle[] bundles = new Aero_AnimationBundle[INITIAL_CAPACITY];
        Aero_AnimationDefinition[] defs = new Aero_AnimationDefinition[INITIAL_CAPACITY];
        Aero_AnimationPlayback[] states = new Aero_AnimationPlayback[INITIAL_CAPACITY];
        double[] xs = new double[INITIAL_CAPACITY];
        double[] ys = new double[INITIAL_CAPACITY];
        double[] zs = new double[INITIAL_CAPACITY];
        float[] brightnesses = new float[INITIAL_CAPACITY];
        float[] partialTicks = new float[INITIAL_CAPACITY];
        Aero_RenderOptions[] options = new Aero_RenderOptions[INITIAL_CAPACITY];
        int count = 0;

        Batch(Aero_MeshModel model, String texturePath) {
            this.model = model;
            this.texturePath = texturePath;
        }

        void add(Aero_AnimationBundle bundle, Aero_AnimationDefinition def,
                 Aero_AnimationPlayback state, double x, double y, double z,
                 float brightness, float partialTick, Aero_RenderOptions opts) {
            ensureCapacity();
            bundles[count] = bundle;
            defs[count] = def;
            states[count] = state;
            xs[count] = x; ys[count] = y; zs[count] = z;
            brightnesses[count] = brightness;
            partialTicks[count] = partialTick;
            options[count] = opts;
            count++;
        }

        void clear() {
            // Null out object refs so GC can reclaim transient state
            // refs from the previous frame. Numeric primitives just
            // get overwritten next frame.
            for (int i = 0; i < count; i++) {
                bundles[i] = null;
                defs[i] = null;
                states[i] = null;
                options[i] = null;
            }
            count = 0;
        }

        private void ensureCapacity() {
            if (count < bundles.length) return;
            int n = bundles.length * 2;
            bundles = Arrays.copyOf(bundles, n);
            defs = Arrays.copyOf(defs, n);
            states = Arrays.copyOf(states, n);
            xs = Arrays.copyOf(xs, n);
            ys = Arrays.copyOf(ys, n);
            zs = Arrays.copyOf(zs, n);
            brightnesses = Arrays.copyOf(brightnesses, n);
            partialTicks = Arrays.copyOf(partialTicks, n);
            options = Arrays.copyOf(options, n);
        }
    }
}
