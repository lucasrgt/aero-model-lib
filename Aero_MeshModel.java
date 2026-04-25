package aero.modellib;

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

    /**
     * Static triangles per brightness group (excludes named groups).
     * groups[GROUP_TOP][i] = float[15] for the i-th top-facing triangle.
     */
    public final float[][][] groups;

    /**
     * Named group triangles: Map<String, float[][][]>.
     * Each entry has the same 4-brightness-group structure as groups[].
     * Empty map if the OBJ has no named objects/groups.
     */
    public final Map namedGroups;

    // Render-time caches (lazy, never invalidated — model topology is immutable).
    private NamedGroup[] cachedNamedGroups;
    private Aero_AnimationClip cachedClip;
    private Aero_AnimationBundle cachedBundle;
    private BoneRef[] cachedBoneRefs;

    public Aero_MeshModel(String name, float[][][] groups, float scale, Map namedGroups) {
        this.name = name;
        this.groups = groups;
        this.scale = scale;
        this.namedGroups = namedGroups;
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
        float[][][] ng = (float[][][]) namedGroups.get(groupName);
        if (ng == null) return 0;
        int n = 0;
        for (int g = 0; g < 4; g++) n += ng[g].length;
        return n;
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
            float[] basePivot = bundle.getPivot(groupName);
            int boneIdx = -1;
            float[] pivot = basePivot;

            if (clip != null) {
                boneIdx = clip.indexOfBone(groupName);
                if (boneIdx < 0) {
                    // Hierarchy: try childMap (explicit Blockbench parent) first,
                    // walking up one level if the direct parent has no keyframes.
                    String parentName = (String) bundle.childMap.get(groupName);
                    if (parentName != null) {
                        boneIdx = clip.indexOfBone(parentName);
                        if (boneIdx < 0) {
                            String grandParent = (String) bundle.childMap.get(parentName);
                            if (grandParent != null) boneIdx = clip.indexOfBone(grandParent);
                        }
                    }
                    // Fallback: longest bone name that is a "<bone>_" prefix of groupName.
                    if (boneIdx < 0) boneIdx = findParentBone(clip, groupName);
                    if (boneIdx >= 0) {
                        // Use the resolved parent's pivot, not the child group's.
                        pivot = bundle.getPivot(clip.boneNames[boneIdx]);
                    }
                }
            }
            refs[i] = new BoneRef(boneIdx, pivot);
        }

        cachedClip     = clip;
        cachedBundle   = bundle;
        cachedBoneRefs = refs;
        return refs;
    }

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

    /** Resolved bone index + effective pivot for a named group against a clip. */
    public static final class BoneRef {
        public final int boneIdx;     // -1 if no bone resolved
        public final float[] pivot;   // never null (falls back to bundle's zero-pivot)
        BoneRef(int boneIdx, float[] pivot) {
            this.boneIdx = boneIdx;
            this.pivot   = pivot;
        }
    }
}
