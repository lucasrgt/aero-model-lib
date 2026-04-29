package aero.modellib;

import java.util.Collections;
import java.util.Map;

/**
 * Container with all animation data loaded from a .anim.json file:
 *   - clips: Map<String, Aero_AnimationClip>  — named clips
 *   - pivots: Map<String, float[]>       — pivot of each bone in block units (pixels / 16)
 *
 * Immutable instance, safe to store as a static field.
 * Created by Aero_AnimationLoader.load().
 *
 * <p>The exposed {@code clips}, {@code pivots} and {@code childMap} maps
 * are wrapped with {@link Collections#unmodifiableMap(Map)}, so iteration
 * is fine but any {@code put}/{@code remove} attempt throws
 * {@link UnsupportedOperationException}. The {@code float[]} pivots
 * inside the map are still raw arrays — callers must not mutate them.
 * Use {@link #getPivotInto(String, float[])} for safe per-call reads.
 */
public class Aero_AnimationBundle {

    static final float[] ZERO_PIVOT = {0f, 0f, 0f};

    /** Map<String, Aero_AnimationClip> — clips indexed by name. Unmodifiable. */
    public final Map clips;

    /**
     * Map<String, float[]> — pivot of each bone in block units (pixels / 16).
     * Absent = pivot [0, 0, 0]. Unmodifiable map; callers must not mutate the
     * float[] values either.
     */
    public final Map pivots;

    /**
     * Map<String, String> — childName → parentBoneName.
     * Maps child elements to the parent animated group (Blockbench hierarchy).
     * E.g.: "shred_blade_L_0_0" → "shredder_L". Unmodifiable.
     */
    public final Map childMap;

    /**
     * Map<String, String> — morph target name → resource path of the
     * variant OBJ. Loader exposes the parsed v1.1 schema; callers wire the
     * actual variant geometry via {@link Aero_MorphTarget#attachAllFromBundle}
     * or load each path manually with {@link Aero_MorphTarget#loadVariant}.
     * Empty for v1.0 bundles. Unmodifiable.
     */
    public final Map morphTargetPaths;

    Aero_AnimationBundle(Map clips, Map pivots, Map childMap) {
        this(clips, pivots, childMap, java.util.Collections.EMPTY_MAP);
    }

    Aero_AnimationBundle(Map clips, Map pivots, Map childMap, Map morphTargetPaths) {
        this.clips    = Collections.unmodifiableMap(clips);
        this.pivots   = Collections.unmodifiableMap(pivots);
        this.childMap = Collections.unmodifiableMap(childMap);
        this.morphTargetPaths = morphTargetPaths == null
            ? java.util.Collections.EMPTY_MAP
            : Collections.unmodifiableMap(morphTargetPaths);
    }

    /**
     * Returns the clip by name, or null if it doesn't exist.
     * Example: bundle.getClip("spin")
     */
    public Aero_AnimationClip getClip(String name) {
        if (name == null) return null;
        return (Aero_AnimationClip) clips.get(name);
    }

    public boolean hasPivot(String boneName) {
        return boneName != null && pivots.containsKey(boneName);
    }

    public boolean getPivotInto(String boneName, float[] out) {
        if (out == null) throw new IllegalArgumentException("out must not be null");
        float[] p = (float[]) pivots.get(boneName);
        if (p == null) return false;
        out[0] = p[0]; out[1] = p[1]; out[2] = p[2];
        return true;
    }

    float[] pivotOrZero(String boneName) {
        float[] p = (float[]) pivots.get(boneName);
        return p != null ? p : ZERO_PIVOT;
    }

    // Single-entry cache mapping a clip's bone-index space to the pivot
    // array per bone. Renderers loop over {@code clip.boneNames} every
    // frame doing one {@code pivotOrZero(boneName)} per bone — that's a
    // {@link java.util.HashMap#get HashMap.get} per bone per BE per frame
    // (~345k/s in mega-load). The cache reduces it to one lookup-array
    // dereference; reset only when the clip identity changes (state
    // transition).
    private Aero_AnimationClip cachedPivotsClip;
    private float[][] cachedPivotsForClip;

    /**
     * Returns an array of pivot float[3]s indexed by {@code clip}'s bone
     * index. {@code result[i]} corresponds to {@code clip.boneNames[i]}
     * — never null; bones without an explicit pivot in this bundle map
     * to the shared {@link #ZERO_PIVOT} sentinel. Memoised against the
     * last clip identity, so consecutive calls with the same clip are
     * O(1).
     *
     * <p>The returned array is shared across callers; do not mutate.</p>
     *
     * @param clip clip to resolve pivots for; must not be null.
     */
    public float[][] resolvePivotsFor(Aero_AnimationClip clip) {
        if (clip == cachedPivotsClip && cachedPivotsForClip != null) {
            return cachedPivotsForClip;
        }
        if (clip == null) throw new IllegalArgumentException("clip must not be null");
        String[] names = clip.boneNames;
        float[][] resolved = new float[names.length][];
        for (int i = 0; i < names.length; i++) {
            resolved[i] = pivotOrZero(names[i]);
        }
        cachedPivotsClip = clip;
        cachedPivotsForClip = resolved;
        return resolved;
    }

    /** Returns the parent animated bone for a child group, or null if none. */
    public String getParentBoneName(String childName) {
        return (String) childMap.get(childName);
    }
}
