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

    Aero_AnimationBundle(Map clips, Map pivots, Map childMap) {
        this.clips    = Collections.unmodifiableMap(clips);
        this.pivots   = Collections.unmodifiableMap(pivots);
        this.childMap = Collections.unmodifiableMap(childMap);
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

    /** Returns the parent animated bone for a child group, or null if none. */
    public String getParentBoneName(String childName) {
        return (String) childMap.get(childName);
    }
}
