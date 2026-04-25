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
public class Aero_AnimationState extends Aero_AnimationPlayback {

    /** Built by Aero_AnimationDefinition.createState(). */
    Aero_AnimationState(Aero_AnimationDefinition def, Aero_AnimationBundle bundle) {
        super(def, bundle);
    }

    // -----------------------------------------------------------------------
    // NBT
    // -----------------------------------------------------------------------

    /**
     * Persists state and playback time.
     * Keys: "Anim_state", "Anim_time"
     */
    public void writeToNBT(NBTTagCompound nbt) {
        nbt.setInteger("Anim_state", getCurrentState());
        nbt.setFloat("Anim_time", getPlaybackTime());
    }

    /**
     * Restores state and time from NBT.
     * prevPlaybackTime = playbackTime to avoid artifacts on the first frame after load.
     * If keys are absent (old save), uses defaults (state=0, time=0).
     */
    public void readFromNBT(NBTTagCompound nbt) {
        restorePlayback(
            nbt.getInteger("Anim_state"),
            nbt.hasKey("Anim_time") ? nbt.getFloat("Anim_time") : 0f
        );
    }
}
