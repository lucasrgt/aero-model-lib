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
public class Aero_AnimationState extends Aero_AnimationPlayback {

    /** Built by Aero_AnimationDefinition.createState(). */
    Aero_AnimationState(Aero_AnimationDefinition def, Aero_AnimationBundle bundle) {
        super(def, bundle);
    }

    /**
     * Persists state and playback time.
     * Keys: "Anim_state", "Anim_time"
     */
    public void writeToNBT(NbtCompound nbt) {
        nbt.putInt("Anim_state", currentState);
        nbt.putFloat("Anim_time", getPlaybackTime());
    }

    /**
     * Restores state and time from NBT.
     * prevPlaybackTime = playbackTime to avoid artifacts on the first frame after load.
     */
    public void readFromNBT(NbtCompound nbt) {
        restorePlayback(
            nbt.getInt("Anim_state"),
            nbt.contains("Anim_time") ? nbt.getFloat("Anim_time") : 0f
        );
    }
}
