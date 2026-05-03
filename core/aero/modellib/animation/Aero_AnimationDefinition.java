package aero.modellib.animation;

import aero.modellib.Aero_AnimationState;

/**
 * Immutable animation definition for a machine type.
 * Maps state IDs (int) to clip names in the Aero_AnimationBundle.
 *
 * Single instance per machine type — store as a static field.
 *
 * Usage:
 * <pre>
 *   public static final int STATE_OFF = 0;
 *   public static final int STATE_ON  = 1;
 *
 *   public static final Aero_AnimationBundle BUNDLE =
 *       Aero_AnimationLoader.load("/models/MyMachine.anim.json");
 *
 *   public static final Aero_AnimationDefinition ANIM_DEF = new Aero_AnimationDefinition()
 *       .state(STATE_OFF, "idle")
 *       .state(STATE_ON,  "spin");
 *
 *   // Per tile entity:
 *   public final Aero_AnimationState animState = ANIM_DEF.createState(BUNDLE);
 * </pre>
 *
 * Convention: STATE_OFF should be 0 (default when NBT has no "Anim_state" key).
 */
public class Aero_AnimationDefinition {

    // Sparse array: stateClips[stateId] = clip name (null = no animation)
    private String[] stateClips;

    private static final int INITIAL_CAPACITY = 4;

    public Aero_AnimationDefinition() {
        stateClips = new String[INITIAL_CAPACITY];
    }

    private Aero_AnimationDefinition(String[] stateClips) {
        this.stateClips = stateClips;
    }

    public static Builder builder() {
        return new Builder();
    }

    /**
     * Associates a state with the clip that should be played.
     *
     * @param stateId   state ID (integer >= 0; STATE_OFF should be 0)
     * @param clipName  clip name in the .anim.json (e.g. "spin", "idle")
     */
    public Aero_AnimationDefinition state(int stateId, String clipName) {
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
     * Creates a platform-neutral playback state.
     * Useful for tests, tools and non-Minecraft integrations.
     */
    public Aero_AnimationPlayback createPlayback(Aero_AnimationBundle bundle) {
        return new Aero_AnimationPlayback(this, bundle);
    }

    /**
     * Creates a new Aero_AnimationState for this definition, linked to the bundle.
     * Call once per tile entity, in the instance field.
     */
    public Aero_AnimationState createState(Aero_AnimationBundle bundle) {
        return new Aero_AnimationState(this, bundle);
    }

    /**
     * Same as {@link #createState(Aero_AnimationBundle)} but persists under
     * a custom NBT key prefix. Use when a tile entity carries more than one
     * {@code Aero_AnimationState} that would otherwise collide on the
     * default {@code "Anim_state"} / {@code "Anim_time"} keys.
     *
     * <p>Example: {@code def.createState(bundle, "Arm_")} writes
     * {@code "Arm_state"} and {@code "Arm_time"}.
     */
    public Aero_AnimationState createState(Aero_AnimationBundle bundle, String nbtKeyPrefix) {
        return new Aero_AnimationState(this, bundle, nbtKeyPrefix);
    }

    private static String[] copyOf(String[] source) {
        String[] copy = new String[source.length];
        System.arraycopy(source, 0, copy, 0, source.length);
        return copy;
    }

    public static final class Builder {
        private String[] stateClips = new String[INITIAL_CAPACITY];

        private Builder() {
        }

        public Builder state(int stateId, String clipName) {
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

        public Aero_AnimationDefinition build() {
            return new Aero_AnimationDefinition(copyOf(stateClips));
        }
    }
}
