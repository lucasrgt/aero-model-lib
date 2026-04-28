package aero.modellib;

import java.util.Collections;
import java.util.Iterator;
import java.util.Map;

/**
 * AeroMesh Model — container for triangulated OBJ models.
 *
 * Triangles are pre-classified into 4 brightness groups at parse time
 * (same directional system as Aero_JsonModelRenderer):
 *
 *   GROUP_TOP    (dominant ny, positive) → factor 1.0
 *   GROUP_BOTTOM (dominant ny, negative) → factor 0.5
 *   GROUP_NS     (dominant nz)           → factor 0.8
 *   GROUP_EW     (dominant nx)           → factor 0.6
 *
 * Classification happens during parsing (Aero_ObjLoader), not per frame.
 * This reduces setColorOpaque_F calls from O(N triangles) to 4.
 *
 * Each triangle is float[15]:
 *   [0-4]   vertex 0: x, y, z, u, v
 *   [5-9]   vertex 1: x, y, z, u, v
 *   [10-14] vertex 2: x, y, z, u, v
 *
 * Named groups (OBJ "o" / "g" directives):
 *   Triangles belonging to a named OBJ object/group are stored separately
 *   in namedGroups and excluded from the main groups array. This allows
 *   animated parts (fan, piston, gear) to be rendered independently with
 *   their own GL transforms, while the static geometry renders normally.
 *
 * Render-time caches:
 *   - getNamedGroupArray() returns a precomputed (name, tris) array so the
 *     animated render path can iterate without allocating a HashMap iterator.
 *   - boneRefsFor(clip, bundle) memoizes the resolved bone index and pivot
 *     for each named group against a given clip. The active clip rarely
 *     changes (state transitions only), so the per-frame cost collapses to
 *     a single identity check.
 */
public class Aero_MeshModel {

    public static final int GROUP_TOP    = 0;
    public static final int GROUP_BOTTOM = 1;
    public static final int GROUP_NS     = 2;
    public static final int GROUP_EW     = 3;

    public static final float[] BRIGHTNESS_FACTORS = {1.0f, 0.5f, 0.8f, 0.6f};

    public final String name;
    public final float scale;
    public final float invScale;

    /**
     * Static triangles per brightness group (excludes named groups).
     * {@code groups[GROUP_TOP][i] = float[15]} for the i-th top-facing
     * triangle. Treat as read-only — the renderers iterate this array
     * every frame and rely on it being stable.
     */
    public final float[][][] groups;

    /**
     * Named group triangles: {@code Map<String, float[][][]>}. Each entry
     * has the same 4-brightness-group structure as {@link #groups}. Empty
     * map if the OBJ has no named objects/groups.
     *
     * <p>The map is wrapped with {@link Collections#unmodifiableMap(Map)};
     * iteration is fine, mutation throws. The float arrays inside are raw
     * — treat them as read-only.
     */
    public final Map namedGroups;

    // Render-time caches (lazy, never invalidated — model topology is immutable).
    private NamedGroup[] cachedNamedGroups;
    private Aero_AnimationClip cachedClip;
    private Aero_AnimationBundle cachedBundle;
    private BoneRef[] cachedBoneRefs;
    private float[] cachedBounds;
    private SmoothLightData cachedStaticSmoothLightData;

    // Display-list cache for the at-rest render path (groups + namedGroups
    // composed at rest pose). Renderer-managed: Aero_MeshRenderer compiles
    // on first static draw and stores the 4 GL list IDs (one per brightness
    // bucket). Empty buckets get id 0 — caller skips them. Compile failure
    // flips the failed flag so we don't hammer glGenLists every frame.
    // Pure ints, no GL imports — keeps this class shared across runtimes.
    private int[] cachedAtRestListIds;
    private boolean atRestListsCompileFailed;

    /**
     * Optional morph targets keyed by name. Mutable holder — load-time
     * code can attach targets after construction via
     * {@link #attachMorphTarget(Aero_MorphTarget)}. Renderer fast-paths
     * skip blending when this is empty or all weights are zero.
     */
    private Map morphTargets;

    public Aero_MeshModel(String name, float[][][] groups, float scale, Map namedGroups) {
        if (scale == 0f) throw new IllegalArgumentException("scale must be non-zero");
        this.name = name;
        this.groups = groups;
        this.scale = scale;
        this.invScale = 1f / scale;
        this.namedGroups = Collections.unmodifiableMap(namedGroups);
    }

    /**
     * Attaches a morph variant. Targets are validated topology-side at
     * construction of {@link Aero_MorphTarget#fromTargetMesh}, so this
     * method only registers the named entry.
     */
    public void attachMorphTarget(Aero_MorphTarget target) {
        if (target == null) throw new IllegalArgumentException("morph target must not be null");
        if (morphTargets == null) morphTargets = new java.util.HashMap();
        morphTargets.put(target.name, target);
    }

    /** Returns a morph target by name, or null if absent. */
    public Aero_MorphTarget getMorphTarget(String name) {
        return morphTargets == null ? null : (Aero_MorphTarget) morphTargets.get(name);
    }

    /** True if at least one morph target is registered. Render fast-path probe. */
    public boolean hasMorphTargets() {
        return morphTargets != null && !morphTargets.isEmpty();
    }

    /**
     * Returns the cached display-list IDs for the at-rest composition, or
     * null if not yet compiled. {@code int[4]} indexed by brightness bucket;
     * a zero entry means the bucket has no geometry and the caller should
     * skip it. Renderer-only state — model code never reads it.
     */
    public int[] getAtRestListIds() {
        return cachedAtRestListIds;
    }

    /** Stores compiled list IDs (renderer-only). */
    public void setAtRestListIds(int[] ids) {
        this.cachedAtRestListIds = ids;
    }

    /**
     * True if at-rest list compilation already failed once. Renderer uses
     * this to avoid retrying glGenLists every frame.
     */
    public boolean atRestListsCompileFailed() {
        return atRestListsCompileFailed;
    }

    /** Marks at-rest list compilation as permanently failed (renderer-only). */
    public void markAtRestListsCompileFailed() {
        this.atRestListsCompileFailed = true;
    }

    /** Convenience constructor: scale=1, empty named groups. */
    public Aero_MeshModel(String name, float[][][] groups) {
        this(name, groups, 1.0f, new java.util.HashMap());
    }

    /** Total triangle count in static geometry (excludes named groups). */
    public int triangleCount() {
        int n = 0;
        for (int g = 0; g < 4; g++) n += groups[g].length;
        return n;
    }

    /** Total triangle count in a named group, or 0 if not found. */
    public int triangleCountForGroup(String groupName) {
        float[][][] ng = getNamedGroup(groupName);
        if (ng == null) return 0;
        int n = 0;
        for (int g = 0; g < 4; g++) n += ng[g].length;
        return n;
    }

    /** Returns a named group's 4 brightness buckets, or null if absent. */
    public float[][][] getNamedGroup(String groupName) {
        return (float[][][]) namedGroups.get(groupName);
    }

    /**
     * Returns the model's axis-aligned bounding box in block units, computed
     * once and cached. Used by Aero_InventoryRenderer to center and scale the
     * model into a slot — without this cache, every inventory icon paints
     * O(triangles) of work just measuring the model.
     *
     * @return float[6] = {minX, minY, minZ, maxX, maxY, maxZ}
     */
    public float[] getBounds() {
        float[] cached = cachedBounds;
        if (cached != null) return cached;

        float minX = Float.POSITIVE_INFINITY, minY = Float.POSITIVE_INFINITY, minZ = Float.POSITIVE_INFINITY;
        float maxX = Float.NEGATIVE_INFINITY, maxY = Float.NEGATIVE_INFINITY, maxZ = Float.NEGATIVE_INFINITY;
        final float invSc = invScale;

        for (int g = 0; g < 4; g++) {
            float[][] tris = groups[g];
            for (int i = 0; i < tris.length; i++) {
                float[] t = tris[i];
                for (int v = 0; v < 3; v++) {
                    int base = v * 5;
                    float vx = t[base]     * invSc;
                    float vy = t[base + 1] * invSc;
                    float vz = t[base + 2] * invSc;
                    if (vx < minX) minX = vx;
                    if (vx > maxX) maxX = vx;
                    if (vy < minY) minY = vy;
                    if (vy > maxY) maxY = vy;
                    if (vz < minZ) minZ = vz;
                    if (vz > maxZ) maxZ = vz;
                }
            }
        }

        NamedGroup[] entries = getNamedGroupArray();
        for (int e = 0; e < entries.length; e++) {
            float[][][] ng = entries[e].tris;
            for (int g = 0; g < 4; g++) {
                float[][] tris = ng[g];
                for (int i = 0; i < tris.length; i++) {
                    float[] t = tris[i];
                    for (int v = 0; v < 3; v++) {
                        int base = v * 5;
                        float vx = t[base]     * invSc;
                        float vy = t[base + 1] * invSc;
                        float vz = t[base + 2] * invSc;
                        if (vx < minX) minX = vx; else if (vx > maxX) maxX = vx;
                        if (vy < minY) minY = vy; else if (vy > maxY) maxY = vy;
                        if (vz < minZ) minZ = vz; else if (vz > maxZ) maxZ = vz;
                    }
                }
            }
        }

        if (minX == Float.POSITIVE_INFINITY) {
            // Empty model — return a unit cube so the inventory render does not divide by zero.
            minX = minY = minZ = 0f; maxX = maxY = maxZ = 1f;
        }
        cached = new float[]{minX, minY, minZ, maxX, maxY, maxZ};
        cachedBounds = cached;
        return cached;
    }

    /**
     * Returns cached smooth-light metadata for static geometry.
     *
     * The renderer uses this to avoid rescanning every triangle each frame just
     * to derive the XZ light footprint and triangle centroid sample positions.
     */
    public SmoothLightData getStaticSmoothLightData() {
        SmoothLightData cached = cachedStaticSmoothLightData;
        if (cached != null) return cached;
        cached = buildSmoothLightData(groups, invScale);
        cachedStaticSmoothLightData = cached;
        return cached;
    }

    private static SmoothLightData buildSmoothLightData(float[][][] groups, float invSc) {
        float minX = Float.POSITIVE_INFINITY, maxX = Float.NEGATIVE_INFINITY;
        float minZ = Float.POSITIVE_INFINITY, maxZ = Float.NEGATIVE_INFINITY;
        float[][] centroidX = new float[4][];
        float[][] centroidZ = new float[4][];
        final float oneThird = 1f / 3f;
        boolean hasTris = false;

        for (int g = 0; g < 4; g++) {
            float[][] tris = groups[g];
            float[] cx = new float[tris.length];
            float[] cz = new float[tris.length];
            centroidX[g] = cx;
            centroidZ[g] = cz;

            for (int i = 0; i < tris.length; i++) {
                float[] t = tris[i];
                float x0 = t[0] * invSc, x1 = t[5] * invSc, x2 = t[10] * invSc;
                float z0 = t[2] * invSc, z1 = t[7] * invSc, z2 = t[12] * invSc;

                if (x0 < minX) minX = x0; if (x1 < minX) minX = x1; if (x2 < minX) minX = x2;
                if (x0 > maxX) maxX = x0; if (x1 > maxX) maxX = x1; if (x2 > maxX) maxX = x2;
                if (z0 < minZ) minZ = z0; if (z1 < minZ) minZ = z1; if (z2 < minZ) minZ = z2;
                if (z0 > maxZ) maxZ = z0; if (z1 > maxZ) maxZ = z1; if (z2 > maxZ) maxZ = z2;

                cx[i] = (x0 + x1 + x2) * oneThird;
                cz[i] = (z0 + z1 + z2) * oneThird;
                hasTris = true;
            }
        }

        if (!hasTris) {
            minX = maxX = minZ = maxZ = 0f;
        }

        return new SmoothLightData(hasTris, minX, maxX, minZ, maxZ, centroidX, centroidZ);
    }

    /**
     * Returns the named groups as an array, computed once and cached.
     * Used by the animated render path to avoid allocating a HashMap
     * iterator + Map.Entry views every frame.
     */
    public NamedGroup[] getNamedGroupArray() {
        NamedGroup[] arr = cachedNamedGroups;
        if (arr != null) return arr;
        arr = new NamedGroup[namedGroups.size()];
        int i = 0;
        Iterator it = namedGroups.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry e = (Map.Entry) it.next();
            arr[i++] = new NamedGroup((String) e.getKey(), (float[][][]) e.getValue());
        }
        cachedNamedGroups = arr;
        return arr;
    }

    /**
     * Returns the resolved (bone index, pivot) for each named group against
     * the given clip. Single-slot cache keyed by clip+bundle identity — the
     * active clip rarely changes between frames, so this collapses the
     * per-group HashMap and prefix-scan lookups into a single ref-equality check.
     *
     * @param clip   the active clip; may be null (returns refs with boneIdx=-1
     *               and pivot from the bundle)
     * @param bundle the animation bundle (used to look up pivots and childMap)
     */
    public BoneRef[] boneRefsFor(Aero_AnimationClip clip, Aero_AnimationBundle bundle) {
        if (clip == cachedClip && bundle == cachedBundle && cachedBoneRefs != null) {
            return cachedBoneRefs;
        }

        NamedGroup[] entries = getNamedGroupArray();
        BoneRef[] refs = new BoneRef[entries.length];
        for (int i = 0; i < entries.length; i++) {
            String groupName = entries[i].name;
            float[] basePivot = bundle.pivotOrZero(groupName);
            int boneIdx = -1;
            float[] pivot = basePivot;
            String resolvedBoneName = null;

            if (clip != null) {
                boneIdx = clip.indexOfBone(groupName);
                if (boneIdx >= 0) resolvedBoneName = groupName;
                if (boneIdx < 0) {
                    // Hierarchy: try childMap (explicit Blockbench parent) first,
                    // walking up one level if the direct parent has no keyframes.
                    String parentName = bundle.getParentBoneName(groupName);
                    if (parentName != null) {
                        boneIdx = clip.indexOfBone(parentName);
                        if (boneIdx >= 0) resolvedBoneName = parentName;
                        if (boneIdx < 0) {
                            String grandParent = bundle.getParentBoneName(parentName);
                            if (grandParent != null) {
                                boneIdx = clip.indexOfBone(grandParent);
                                if (boneIdx >= 0) resolvedBoneName = grandParent;
                            }
                        }
                    }
                    // Fallback: longest bone name that is a "<bone>_" prefix of groupName.
                    if (boneIdx < 0) {
                        boneIdx = findParentBone(clip, groupName);
                        if (boneIdx >= 0) resolvedBoneName = clip.boneNames[boneIdx];
                    }
                    if (boneIdx >= 0) {
                        // Use the resolved parent's pivot, not the child group's.
                        pivot = bundle.pivotOrZero(clip.boneNames[boneIdx]);
                    }
                }
            }

            // Build animated ancestor chain (root → ... → resolvedBoneName).
            // Walks childMap upward, including only ancestors that have a
            // bone in the clip. Required for hierarchical rendering: a child
            // bone with its own animation must compose with every animated
            // ancestor's transform so parent rotations propagate correctly.
            int[] ancestorBoneIdx;
            String[] ancestorBoneNames;
            float[][] ancestorPivots;
            if (boneIdx < 0 || clip == null) {
                ancestorBoneIdx = EMPTY_INT;
                ancestorBoneNames = EMPTY_STRING;
                ancestorPivots = EMPTY_PIVOTS;
            } else {
                // Walk parents starting from resolvedBoneName, collecting
                // animated ancestors. Cap at MAX_DEPTH to guard cycles.
                String[] tmpNames = new String[MAX_HIERARCHY_DEPTH];
                int[] tmpIdx = new int[MAX_HIERARCHY_DEPTH];
                float[][] tmpPivots = new float[MAX_HIERARCHY_DEPTH][];
                int depth = 0;

                tmpNames[depth] = resolvedBoneName;
                tmpIdx[depth] = boneIdx;
                tmpPivots[depth] = pivot;
                depth++;

                String parent = bundle.getParentBoneName(resolvedBoneName);
                while (parent != null && depth < MAX_HIERARCHY_DEPTH) {
                    int parentIdx = clip.indexOfBone(parent);
                    if (parentIdx >= 0) {
                        tmpNames[depth] = parent;
                        tmpIdx[depth] = parentIdx;
                        tmpPivots[depth] = bundle.pivotOrZero(parent);
                        depth++;
                    }
                    parent = bundle.getParentBoneName(parent);
                }

                // Reverse so chain is root → ... → leaf.
                ancestorBoneIdx = new int[depth];
                ancestorBoneNames = new String[depth];
                ancestorPivots = new float[depth][];
                for (int d = 0; d < depth; d++) {
                    ancestorBoneIdx[d] = tmpIdx[depth - 1 - d];
                    ancestorBoneNames[d] = tmpNames[depth - 1 - d];
                    ancestorPivots[d] = tmpPivots[depth - 1 - d];
                }
            }

            refs[i] = new BoneRef(boneIdx, resolvedBoneName, pivot,
                ancestorBoneIdx, ancestorBoneNames, ancestorPivots);
        }

        cachedClip     = clip;
        cachedBundle   = bundle;
        cachedBoneRefs = refs;
        return refs;
    }

    private static final int[] EMPTY_INT = new int[0];
    private static final String[] EMPTY_STRING = new String[0];
    private static final float[][] EMPTY_PIVOTS = new float[0][];
    private static final int MAX_HIERARCHY_DEPTH = 32;

    /**
     * Finds a bone whose name is a prefix of groupName followed by '_'.
     * Returns the index of the longest matching bone, or -1 if none.
     * Avoids any String allocation in the hot path.
     */
    private static int findParentBone(Aero_AnimationClip clip, String groupName) {
        int bestIdx = -1;
        int bestLen = 0;
        int gnLen = groupName.length();
        for (int i = 0; i < clip.boneNames.length; i++) {
            String bone = clip.boneNames[i];
            int bLen = bone.length();
            if (bLen <= bestLen) continue;
            if (gnLen <= bLen) continue;
            if (groupName.charAt(bLen) != '_') continue;
            if (!groupName.regionMatches(0, bone, 0, bLen)) continue;
            bestIdx = i;
            bestLen = bLen;
        }
        return bestIdx;
    }

    /** Pair of (group name, triangles) — used by the animated render path. */
    public static final class NamedGroup {
        public final String name;
        public final float[][][] tris;
        NamedGroup(String name, float[][][] tris) {
            this.name = name;
            this.tris = tris;
        }
    }

    /**
     * Resolved bone hierarchy for a named group against a clip.
     *
     * <p>{@link #boneIdx} / {@link #boneName} / {@link #pivot} describe the
     * <em>deepest</em> animated ancestor — the bone whose pose is applied
     * last when rendering. The {@link #ancestorBoneIdx} /
     * {@link #ancestorBoneNames} / {@link #ancestorPivots} arrays hold the
     * full chain of animated ancestors from root to deepest, inclusive,
     * so the renderer can compose parent transforms hierarchically (parent
     * rotation moves child along, exactly like Blockbench's animator).
     *
     * <p>For groups without animation in the clip, the chain length is 0
     * and {@link #boneIdx} is -1 — the renderer skips them entirely (the
     * group's vertices stay at rest in absolute coordinates).
     */
    public static final class BoneRef {
        public final int boneIdx;             // -1 if no bone resolved
        public final String boneName;         // resolved animation bone name, or null
        public final float[] pivot;           // never null (falls back to bundle's zero-pivot)
        public final int[] ancestorBoneIdx;   // chain root → ... → boneIdx (inclusive). length 0 if boneIdx == -1
        public final String[] ancestorBoneNames; // matching names for procedural pose dispatch
        public final float[][] ancestorPivots;   // each ancestor's pivot (for applyPose); never null entries

        BoneRef(int boneIdx, String boneName, float[] pivot,
                int[] ancestorBoneIdx, String[] ancestorBoneNames, float[][] ancestorPivots) {
            this.boneIdx = boneIdx;
            this.boneName = boneName;
            this.pivot   = pivot;
            this.ancestorBoneIdx = ancestorBoneIdx;
            this.ancestorBoneNames = ancestorBoneNames;
            this.ancestorPivots = ancestorPivots;
        }

        /**
         * Convenience constructor for tests + manual procedural-pose flows
         * where the bone has no animated parent — builds a single-element
         * ancestor chain containing just this bone.
         */
        public BoneRef(int boneIdx, String boneName, float[] pivot) {
            this(boneIdx, boneName, pivot,
                boneIdx >= 0 ? new int[]{boneIdx} : EMPTY_INT,
                boneIdx >= 0 ? new String[]{boneName} : EMPTY_STRING,
                boneIdx >= 0 ? new float[][]{pivot} : EMPTY_PIVOTS);
        }
    }

    /** Precomputed static geometry data for smooth-light rendering. */
    public static final class SmoothLightData {
        public final boolean hasTriangles;
        public final float minX;
        public final float maxX;
        public final float minZ;
        public final float maxZ;
        public final float[][] centroidX;
        public final float[][] centroidZ;

        SmoothLightData(boolean hasTriangles, float minX, float maxX, float minZ, float maxZ,
                        float[][] centroidX, float[][] centroidZ) {
            this.hasTriangles = hasTriangles;
            this.minX = minX;
            this.maxX = maxX;
            this.minZ = minZ;
            this.maxZ = maxZ;
            this.centroidX = centroidX;
            this.centroidZ = centroidZ;
        }
    }
}
