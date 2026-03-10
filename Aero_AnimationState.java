package aero.modellib;

import net.minecraft.src.NBTTagCompound;

/**
 * Mutable animation state per tile entity.
 *
 * Tracks which clip is playing, current and previous playback time
 * (for partial-tick interpolation), and persists via NBT.
 *
 * Lifecycle:
 * <pre>
 *   // TileEntity field:
 *   public final Aero_AnimationState animState = ANIM_DEF.createState(BUNDLE);
 *
 *   // In updateEntity() — tick() BEFORE setState():
 *   animState.tick();
 *   animState.setState(isRunning ? STATE_ON : STATE_OFF);
 *
 *   // In writeToNBT / readFromNBT:
 *   animState.writeToNBT(nbt);
 *   animState.readFromNBT(nbt);
 * </pre>
 */
public class Aero_AnimationState {

    /** Current state (public for the renderer and machine logic). */
    public int currentState;

    private final Aero_AnimationDefinition def;
    private final Aero_AnimationBundle   bundle;

    private float playbackTime;      // seconds, current time in clip
    private float prevPlaybackTime;  // seconds, time at previous tick (for interpolation)

    /** Built by Aero_AnimationDefinition.createState(). */
    Aero_AnimationState(Aero_AnimationDefinition def, Aero_AnimationBundle bundle) {
        this.def          = def;
        this.bundle       = bundle;
        this.currentState = 0;
        this.playbackTime = 0f;
        this.prevPlaybackTime = 0f;
    }

    // -----------------------------------------------------------------------
    // Tick — call at the beginning of updateEntity()
    // -----------------------------------------------------------------------

    /**
     * Advances playback by 1 tick (1/20 second).
     * Saves the previous time for partial-tick interpolation.
     * Must be called BEFORE setState() each tick.
     */
    public void tick() {
        prevPlaybackTime = playbackTime;

        Aero_AnimationClip clip = getCurrentClip();
        if (clip == null || clip.length <= 0f) {
            playbackTime = 0f;
            return;
        }

        playbackTime += 1f / 20f;

        if (clip.loop) {
            // Wrap at the end of the clip
            if (playbackTime >= clip.length) {
                playbackTime = playbackTime % clip.length;
                // Fix prevPlaybackTime so interpolation doesn't jump
                if (prevPlaybackTime >= clip.length) prevPlaybackTime = prevPlaybackTime % clip.length;
            }
        } else {
            // Clamp at the end
            if (playbackTime >= clip.length) {
                playbackTime     = clip.length;
                prevPlaybackTime = clip.length;
            }
        }
    }

    // -----------------------------------------------------------------------
    // Change state
    // -----------------------------------------------------------------------

    /**
     * Changes the current state. If the clip associated with the new state differs
     * from the current clip, playback is reset to the beginning.
     * Must be called AFTER tick().
     */
    public void setState(int stateId) {
        if (stateId == currentState) return;

        String oldClip = def.getClipName(currentState);
        String newClip = def.getClipName(stateId);

        currentState = stateId;

        // Reset time only if the clip changes (or if there was no clip before)
        boolean clipChanged = (newClip == null) ? (oldClip != null)
                                                : !newClip.equals(oldClip);
        if (clipChanged) {
            playbackTime     = 0f;
            prevPlaybackTime = 0f;
        }
    }

    // -----------------------------------------------------------------------
    // Renderer access
    // -----------------------------------------------------------------------

    /**
     * Returns the interpolated playback time for the current frame.
     * Handles loop wrap: when playbackTime < prevPlaybackTime (crossed the end),
     * interpolates correctly without jumping backwards.
     *
     * @param partialTick  tick fraction (0.0-1.0) provided by TileEntitySpecialRenderer
     */
    public float getInterpolatedTime(float partialTick) {
        Aero_AnimationClip clip = getCurrentClip();
        if (clip == null || clip.length <= 0f) return 0f;

        float cur  = playbackTime;
        float prev = prevPlaybackTime;

        if (clip.loop && cur < prev) {
            // Crossed the loop boundary — interpolate "over" the wrap
            cur += clip.length;
            float t = prev + (cur - prev) * partialTick;
            return t % clip.length;
        }

        return prev + (cur - prev) * partialTick;
    }

    /** Returns the currently active clip, or null if the state has no clip defined. */
    public Aero_AnimationClip getCurrentClip() {
        String clipName = def.getClipName(currentState);
        if (clipName == null) return null;
        return bundle.getClip(clipName);
    }

    /** Exposes the bundle for the renderer to access pivots and clips. */
    public Aero_AnimationBundle getBundle() { return bundle; }

    /** Exposes the def for the renderer to access clip names. */
    public Aero_AnimationDefinition getDef() { return def; }

    // -----------------------------------------------------------------------
    // NBT
    // -----------------------------------------------------------------------

    /**
     * Persists state and playback time.
     * Keys: "Anim_state", "Anim_time"
     */
    public void writeToNBT(NBTTagCompound nbt) {
        nbt.setInteger("Anim_state", currentState);
        nbt.setFloat("Anim_time", playbackTime);
    }

    /**
     * Restores state and time from NBT.
     * prevPlaybackTime = playbackTime to avoid artifacts on the first frame after load.
     * If keys are absent (old save), uses defaults (state=0, time=0).
     */
    public void readFromNBT(NBTTagCompound nbt) {
        currentState      = nbt.getInteger("Anim_state");   // 0 if absent
        playbackTime      = nbt.hasKey("Anim_time") ? nbt.getFloat("Anim_time") : 0f;
        prevPlaybackTime  = playbackTime;
    }
}
