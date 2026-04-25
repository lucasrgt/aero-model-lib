package aero.modellib;

import net.minecraft.nbt.NbtCompound;

/**
 * Mutable animation state per BlockEntity (StationAPI/Yarn port).
 *
 * Tracks which clip is playing, current and previous playback time
 * (for partial-tick interpolation), and persists via NBT.
 *
 * Lifecycle:
 * <pre>
 *   // BlockEntity field:
 *   public final Aero_AnimationState animState = ANIM_DEF.createState(BUNDLE);
 *
 *   // In tick() — call animState.tick() BEFORE setState():
 *   animState.tick();
 *   animState.setState(isRunning ? STATE_ON : STATE_OFF);
 *
 *   // In writeNbt / readNbt:
 *   animState.writeToNBT(nbt);
 *   animState.readFromNBT(nbt);
 * </pre>
 */
public class Aero_AnimationState {

    /** Current state (public for the renderer and machine logic). */
    public int currentState;

    private final Aero_AnimationDefinition def;
    private final Aero_AnimationBundle   bundle;

    private float playbackTime;
    private float prevPlaybackTime;

    // Lazy single-slot cache for getCurrentClip() — invalidated on state change.
    private Aero_AnimationClip cachedClip;
    private int cachedClipState = -1;

    /** Built by Aero_AnimationDefinition.createState(). */
    Aero_AnimationState(Aero_AnimationDefinition def, Aero_AnimationBundle bundle) {
        this.def          = def;
        this.bundle       = bundle;
        this.currentState = 0;
        this.playbackTime = 0f;
        this.prevPlaybackTime = 0f;
    }

    public void tick() {
        prevPlaybackTime = playbackTime;

        Aero_AnimationClip clip = getCurrentClip();
        if (clip == null || clip.length <= 0f) {
            playbackTime = 0f;
            return;
        }

        playbackTime += 1f / 20f;

        if (clip.loop) {
            if (playbackTime >= clip.length) {
                playbackTime = playbackTime % clip.length;
                if (prevPlaybackTime >= clip.length) prevPlaybackTime = prevPlaybackTime % clip.length;
            }
        } else {
            if (playbackTime >= clip.length) {
                playbackTime     = clip.length;
                prevPlaybackTime = clip.length;
            }
        }
    }

    public void setState(int stateId) {
        if (stateId == currentState) return;

        String oldClip = def.getClipName(currentState);
        String newClip = def.getClipName(stateId);

        currentState = stateId;
        cachedClipState = -1;

        boolean clipChanged = (newClip == null) ? (oldClip != null)
                                                : !newClip.equals(oldClip);
        if (clipChanged) {
            playbackTime     = 0f;
            prevPlaybackTime = 0f;
        }
    }

    public float getInterpolatedTime(float partialTick) {
        Aero_AnimationClip clip = getCurrentClip();
        if (clip == null || clip.length <= 0f) return 0f;

        float cur  = playbackTime;
        float prev = prevPlaybackTime;

        if (clip.loop && cur < prev) {
            cur += clip.length;
            float t = prev + (cur - prev) * partialTick;
            return t % clip.length;
        }

        return prev + (cur - prev) * partialTick;
    }

    public Aero_AnimationClip getCurrentClip() {
        if (cachedClipState == currentState) return cachedClip;
        String clipName = def.getClipName(currentState);
        cachedClip = clipName != null ? bundle.getClip(clipName) : null;
        cachedClipState = currentState;
        return cachedClip;
    }

    public Aero_AnimationBundle getBundle() { return bundle; }
    public Aero_AnimationDefinition getDef() { return def; }

    /**
     * Persists state and playback time.
     * Keys: "Anim_state", "Anim_time"
     */
    public void writeToNBT(NbtCompound nbt) {
        nbt.putInt("Anim_state", currentState);
        nbt.putFloat("Anim_time", playbackTime);
    }

    /**
     * Restores state and time from NBT.
     * prevPlaybackTime = playbackTime to avoid artifacts on the first frame after load.
     */
    public void readFromNBT(NbtCompound nbt) {
        currentState      = nbt.getInt("Anim_state");
        cachedClipState   = -1;
        playbackTime      = nbt.contains("Anim_time") ? nbt.getFloat("Anim_time") : 0f;
        prevPlaybackTime  = playbackTime;
    }
}
