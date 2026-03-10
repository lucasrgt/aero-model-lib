package aero.modellib;

/**
 * Immutable animation definition for a machine type.
 * Maps state IDs (int) to clip names in the Aero_AnimBundle.
 *
 * Single instance per machine type — store as a static field.
 *
 * Usage:
 * <pre>
 *   public static final int STATE_OFF = 0;
 *   public static final int STATE_ON  = 1;
 *
 *   public static final Aero_AnimBundle BUNDLE =
 *       Aero_AnimationLoader.load("/models/MyMachine.anim.json");
 *
 *   public static final Aero_AnimationDef ANIM_DEF = new Aero_AnimationDef()
 *       .state(STATE_OFF, "idle")
 *       .state(STATE_ON,  "spin");
 *
 *   // Per tile entity:
 *   public final Aero_AnimationState animState = ANIM_DEF.createState(BUNDLE);
 * </pre>
 *
 * Convention: STATE_OFF should be 0 (default when NBT has no "Anim_state" key).
 */
public class Aero_AnimationDef {

    // Sparse array: stateClips[stateId] = clip name (null = no animation)
    private String[] stateClips;

    private static final int INITIAL_CAPACITY = 4;

    public Aero_AnimationDef() {
        stateClips = new String[INITIAL_CAPACITY];
    }

    /**
     * Associates a state with the clip that should be played.
     *
     * @param stateId   state ID (integer >= 0; STATE_OFF should be 0)
     * @param clipName  clip name in the .anim.json (e.g. "spin", "idle")
     */
    public Aero_AnimationDef state(int stateId, String clipName) {
        if (stateId < 0) throw new IllegalArgumentException("stateId must be >= 0");
        if (stateId >= stateClips.length) {
            int newLen = Math.max(stateId + 1, stateClips.length * 2);
            String[] newArr = new String[newLen];
            System.arraycopy(stateClips, 0, newArr, 0, stateClips.length);
            stateClips = newArr;
        }
        stateClips[stateId] = clipName;
        return this;
    }

    /**
     * Returns the clip name associated with the state, or null if not defined.
     */
    public String getClipName(int stateId) {
        if (stateId < 0 || stateId >= stateClips.length) return null;
        return stateClips[stateId];
    }

    /**
     * Creates a new Aero_AnimationState for this definition, linked to the bundle.
     * Call once per tile entity, in the instance field.
     */
    public Aero_AnimationState createState(Aero_AnimBundle bundle) {
        return new Aero_AnimationState(this, bundle);
    }
}
