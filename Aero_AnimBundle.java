package aero.modellib;

import java.util.Map;

/**
 * Container with all animation data loaded from a .anim.json file:
 *   - clips: Map<String, Aero_AnimClip>  — named clips
 *   - pivots: Map<String, float[]>       — pivot of each bone in block units (pixels / 16)
 *
 * Immutable instance, safe to store as a static field.
 * Created by Aero_AnimationLoader.load().
 */
public class Aero_AnimBundle {

    /** Map<String, Aero_AnimClip> — clips indexed by name. */
    public final Map clips;

    /**
     * Map<String, float[]> — pivot of each bone in block units (pixels / 16).
     * Absent = pivot [0, 0, 0].
     */
    public final Map pivots;

    /**
     * Map<String, String> — childName → parentBoneName.
     * Maps child elements to the parent animated group (Blockbench hierarchy).
     * E.g.: "shred_blade_L_0_0" → "shredder_L"
     */
    public final Map childMap;

    Aero_AnimBundle(Map clips, Map pivots, Map childMap) {
        this.clips    = clips;
        this.pivots   = pivots;
        this.childMap = childMap;
    }

    /**
     * Returns the clip by name, or null if it doesn't exist.
     * Example: bundle.getClip("spin")
     */
    public Aero_AnimClip getClip(String name) {
        if (name == null) return null;
        return (Aero_AnimClip) clips.get(name);
    }

    /**
     * Returns the bone pivot in block units, or float[]{0,0,0} if not defined.
     * Values already divided by 16 (Blockbench pixels → block units).
     */
    public float[] getPivot(String boneName) {
        float[] p = (float[]) pivots.get(boneName);
        return p != null ? p : new float[]{0f, 0f, 0f};
    }
}
