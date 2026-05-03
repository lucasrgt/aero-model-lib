package aero.modellib;

import java.util.Arrays;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;

import aero.modellib.animation.Aero_AnimationBundle;
import aero.modellib.animation.Aero_AnimationClip;
import aero.modellib.animation.Aero_AnimationDefinition;
import aero.modellib.animation.Aero_AnimationPlayback;
import aero.modellib.model.Aero_MeshBlendMode;
import aero.modellib.model.Aero_MeshModel;
import aero.modellib.render.Aero_RenderOptions;

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
 * The collector keys batches by model identity plus texture/render options.
 * At flush time, {@link Aero_MeshRenderer#renderAnimatedBatch} drains the
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
     * When true, {@link #flush()} sorts {@link #ACTIVE_BATCHES} by a
     * composite render-state key so batches sharing texture/options drain
     * back to back. Combined with the {@link #lastBoundPath} dedup in
     * {@link #bindBatchTexture(Batch)}, this elides redundant texture binds
     * and reduces GL state churn between models.
     *
     * <p>Toggle: {@code -Daero.batcher.sort=false}.
     */
    public static final boolean SORT_ENABLED =
        !"false".equalsIgnoreCase(System.getProperty("aero.batcher.sort"));

    /**
     * Allows UV-scrolling/UV-scaling clips to stay in the CPU batched path.
     * The batch renderer applies UV offsets/scales directly to Tessellator
     * coordinates, preserving the precise path's semantics without touching
     * the OpenGL texture matrix.
     *
     * <p>Toggle: {@code -Daero.animatedbatch.uv=false}.
     */
    public static final boolean UV_BATCH_ENABLED =
        !"false".equalsIgnoreCase(System.getProperty("aero.animatedbatch.uv"));

    /**
     * Optional smoothing valve for very large animated batches. The normal
     * batch path submits one large Tessellator stream per bone; that maximizes
     * throughput, but on old GL drivers a sudden 100+ instance stream can move
     * the stall to Display.update. When this threshold is >= 0, large batches
     * drain through the normal per-instance renderer, which can use bone-page
     * display lists and spreads the work into smaller GL calls.
     *
     * <p>Default off. Stress tests can enable with e.g.
     * {@code -Daero.animatedbatch.bonePageDrainMin=64}.
     */
    private static final int BONE_PAGE_DRAIN_MIN =
        intProperty("aero.animatedbatch.bonePageDrainMin", -1, -1, 100000);

    /**
     * Per-frame batch storage. Keyed by model identity plus texture/render
     * options, so different visual states never share the same tess cycle.
     */
    private static final HashMap<BatchKey, Batch> BATCHES = new HashMap<BatchKey, Batch>();
    private static final ArrayList<Batch> ACTIVE_BATCHES = new ArrayList<Batch>();
    private static final BatchLookupKey LOOKUP_KEY = new BatchLookupKey();

    /**
     * Comparator that orders by texture, options, then model identity.
     * Texture first maximizes bind dedup; option bits then group state changes.
     */
    private static final Comparator<Batch> BY_RENDER_STATE =
        new Comparator<Batch>() {
            @Override
            public int compare(Batch a, Batch b) {
                return a.key.compareTo(b.key);
            }
        };

    /**
     * Last texture path bound by {@link #bindBatchTexture(Batch)} during
     * the current flush. Reset to {@code null} at the top of {@link #flush()}
     * so the first bind always fires. With {@link #SORT_ENABLED} on, this
     * dedup eliminates the rebind between adjacent same-texture batches.
     */
    private static String lastBoundPath = null;
    private static int queuedThisFrame;
    private static int flushedInstancesThisFrame;
    private static int flushedBatchesThisFrame;
    private static int bonePageDrainedInstancesThisFrame;
    private static int immediateRendersThisFrame;

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
        Aero_AnimationClip clip = state != null ? state.getCurrentClip() : null;
        if (clip != null && clip.hasUvAnimation() && !UV_BATCH_ENABLED) {
            immediateRendersThisFrame++;
            bindTexturePath(texturePath);
            Aero_MeshRenderer.renderAnimatedPrecise(model, bundle, def, state,
                x, y, z, brightness, partialTick, options);
            return;
        }
        if (!ENABLED) {
            // Fallback: renderers using the batcher often no longer bind
            // their texture before queuing. Bind here so the debug/opt-out
            // path is visually identical to the queued flush path.
            immediateRendersThisFrame++;
            bindTexturePath(texturePath);
            Aero_MeshRenderer.renderAnimated(model, bundle, def, state,
                x, y, z, brightness, partialTick, options);
            return;
        }
        Aero_RenderOptions opts = options != null ? options : Aero_RenderOptions.DEFAULT;
        Batch batch = BATCHES.get(LOOKUP_KEY.set(model, texturePath, opts));
        if (batch == null) {
            BatchKey key = new BatchKey(model, texturePath, opts);
            batch = new Batch(key);
            BATCHES.put(key, batch);
        }
        if (batch.count == 0) ACTIVE_BATCHES.add(batch);
        batch.add(bundle, def, state, x, y, z, brightness, partialTick, opts);
        queuedThisFrame++;
    }

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
        Aero_TextureBinder.bind(path);
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
        // Sort by composite state so compatible batches drain adjacently.
        // Skipped when only one batch is queued (sort cost > savings) or the
        // toggle is off.
        if (SORT_ENABLED && ACTIVE_BATCHES.size() > 1) {
            ACTIVE_BATCHES.sort(BY_RENDER_STATE);
        }
        for (int i = 0; i < ACTIVE_BATCHES.size(); i++) {
            Batch b = ACTIVE_BATCHES.get(i);
            flushedInstancesThisFrame += b.count;
            flushedBatchesThisFrame++;
            if (shouldDrainViaBonePages(b)) {
                bonePageDrainedInstancesThisFrame += b.count;
                Aero_MeshRenderer.renderAnimatedBatchUnbatched(b);
            } else {
                Aero_MeshRenderer.renderAnimatedBatch(b);
            }
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
        final BatchKey key;
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

        Batch(BatchKey key) {
            this.key = key;
            this.model = key.model;
            this.texturePath = key.texturePath;
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

    static void beginFrameCounters() {
        queuedThisFrame = 0;
        flushedInstancesThisFrame = 0;
        flushedBatchesThisFrame = 0;
        bonePageDrainedInstancesThisFrame = 0;
        immediateRendersThisFrame = 0;
    }

    public static int queuedThisFrame() {
        return queuedThisFrame;
    }

    public static int flushedInstancesThisFrame() {
        return flushedInstancesThisFrame;
    }

    public static int flushedBatchesThisFrame() {
        return flushedBatchesThisFrame;
    }

    public static int bonePageDrainedInstancesThisFrame() {
        return bonePageDrainedInstancesThisFrame;
    }

    public static int immediateRendersThisFrame() {
        return immediateRendersThisFrame;
    }

    private static boolean shouldDrainViaBonePages(Batch batch) {
        return BONE_PAGE_DRAIN_MIN >= 0 && batch.count >= BONE_PAGE_DRAIN_MIN;
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

    private static final class BatchKey implements Comparable<BatchKey> {
        final Aero_MeshModel model;
        final String texturePath;
        final int textureHash;
        final int tintRBits;
        final int tintGBits;
        final int tintBBits;
        final int alphaBits;
        final int alphaClipBits;
        final Aero_MeshBlendMode blend;
        final boolean depthTest;
        final boolean cullFaces;
        final int hash;

        BatchKey(Aero_MeshModel model, String texturePath, Aero_RenderOptions options) {
            this.model = model;
            this.texturePath = texturePath;
            this.textureHash = texturePath != null ? texturePath.hashCode() : 0;
            this.tintRBits = Float.floatToIntBits(options.tintR);
            this.tintGBits = Float.floatToIntBits(options.tintG);
            this.tintBBits = Float.floatToIntBits(options.tintB);
            this.alphaBits = Float.floatToIntBits(options.alpha);
            this.alphaClipBits = Float.floatToIntBits(options.alphaClip);
            this.blend = options.blend;
            this.depthTest = options.depthTest;
            this.cullFaces = options.cullFaces;

            int result = System.identityHashCode(model);
            result = 31 * result + textureHash;
            result = 31 * result + tintRBits;
            result = 31 * result + tintGBits;
            result = 31 * result + tintBBits;
            result = 31 * result + alphaBits;
            result = 31 * result + alphaClipBits;
            result = 31 * result + blend.hashCode();
            result = 31 * result + (depthTest ? 1 : 0);
            result = 31 * result + (cullFaces ? 1 : 0);
            this.hash = result;
        }

        @Override
        public int hashCode() {
            return hash;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) return true;
            if (!(obj instanceof BatchKey)) return false;
            BatchKey other = (BatchKey) obj;
            if (model != other.model) return false;
            if (tintRBits != other.tintRBits || tintGBits != other.tintGBits
                || tintBBits != other.tintBBits || alphaBits != other.alphaBits
                || alphaClipBits != other.alphaClipBits) return false;
            if (blend != other.blend || depthTest != other.depthTest
                || cullFaces != other.cullFaces) return false;
            if (texturePath == other.texturePath) return true;
            return texturePath != null && texturePath.equals(other.texturePath);
        }

        @Override
        public int compareTo(BatchKey other) {
            int c = compareTexture(texturePath, other.texturePath);
            if (c != 0) return c;
            c = blend.ordinal() - other.blend.ordinal();
            if (c != 0) return c;
            c = boolCompare(depthTest, other.depthTest);
            if (c != 0) return c;
            c = boolCompare(cullFaces, other.cullFaces);
            if (c != 0) return c;
            c = intCompare(alphaClipBits, other.alphaClipBits);
            if (c != 0) return c;
            c = intCompare(alphaBits, other.alphaBits);
            if (c != 0) return c;
            c = intCompare(tintRBits, other.tintRBits);
            if (c != 0) return c;
            c = intCompare(tintGBits, other.tintGBits);
            if (c != 0) return c;
            c = intCompare(tintBBits, other.tintBBits);
            if (c != 0) return c;
            return intCompare(System.identityHashCode(model), System.identityHashCode(other.model));
        }

        private static int compareTexture(String a, String b) {
            if (a == b) return 0;
            if (a == null) return -1;
            if (b == null) return 1;
            return a.compareTo(b);
        }

        private static int boolCompare(boolean a, boolean b) {
            return a == b ? 0 : (a ? 1 : -1);
        }

        private static int intCompare(int a, int b) {
            return a < b ? -1 : (a == b ? 0 : 1);
        }
    }

    private static final class BatchLookupKey {
        Aero_MeshModel model;
        String texturePath;
        int textureHash;
        int tintRBits;
        int tintGBits;
        int tintBBits;
        int alphaBits;
        int alphaClipBits;
        Aero_MeshBlendMode blend;
        boolean depthTest;
        boolean cullFaces;
        int hash;

        BatchLookupKey set(Aero_MeshModel model, String texturePath,
                           Aero_RenderOptions options) {
            this.model = model;
            this.texturePath = texturePath;
            this.textureHash = texturePath != null ? texturePath.hashCode() : 0;
            this.tintRBits = Float.floatToIntBits(options.tintR);
            this.tintGBits = Float.floatToIntBits(options.tintG);
            this.tintBBits = Float.floatToIntBits(options.tintB);
            this.alphaBits = Float.floatToIntBits(options.alpha);
            this.alphaClipBits = Float.floatToIntBits(options.alphaClip);
            this.blend = options.blend;
            this.depthTest = options.depthTest;
            this.cullFaces = options.cullFaces;

            int result = System.identityHashCode(model);
            result = 31 * result + textureHash;
            result = 31 * result + tintRBits;
            result = 31 * result + tintGBits;
            result = 31 * result + tintBBits;
            result = 31 * result + alphaBits;
            result = 31 * result + alphaClipBits;
            result = 31 * result + blend.hashCode();
            result = 31 * result + (depthTest ? 1 : 0);
            result = 31 * result + (cullFaces ? 1 : 0);
            this.hash = result;
            return this;
        }

        @Override
        public int hashCode() {
            return hash;
        }

        @Override
        public boolean equals(Object obj) {
            if (!(obj instanceof BatchKey)) return false;
            BatchKey other = (BatchKey) obj;
            if (model != other.model) return false;
            if (tintRBits != other.tintRBits || tintGBits != other.tintGBits
                || tintBBits != other.tintBBits || alphaBits != other.alphaBits
                || alphaClipBits != other.alphaClipBits) return false;
            if (blend != other.blend || depthTest != other.depthTest
                || cullFaces != other.cullFaces) return false;
            if (texturePath == other.texturePath) return true;
            return texturePath != null && texturePath.equals(other.texturePath);
        }
    }
}
