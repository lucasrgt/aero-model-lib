package aero.modellib;

import net.minecraft.src.NBTTagCompound;

import aero.modellib.animation.Aero_AnimationBundle;
import aero.modellib.animation.Aero_AnimationDefinition;
import aero.modellib.animation.Aero_AnimationPlayback;

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
 *
 * <p>Default NBT keys are {@code "Anim_state"} and {@code "Anim_time"}. To
 * carry more than one Aero_AnimationState on the same tile entity, build
 * each with a distinct prefix via
 * {@link Aero_AnimationDefinition#createState(Aero_AnimationBundle, String)}.
 */
public class Aero_AnimationState extends Aero_AnimationPlayback {

    /** Default NBT key prefix when no override is supplied. */
    public static final String DEFAULT_NBT_KEY_PREFIX = "Anim_";

    private final String stateKey;
    private final String timeKey;

    /** Built by Aero_AnimationDefinition.createState(). */
    public Aero_AnimationState(Aero_AnimationDefinition def, Aero_AnimationBundle bundle) {
        this(def, bundle, DEFAULT_NBT_KEY_PREFIX);
    }

    public Aero_AnimationState(Aero_AnimationDefinition def, Aero_AnimationBundle bundle,
                        String nbtKeyPrefix) {
        super(def, bundle);
        if (nbtKeyPrefix == null || nbtKeyPrefix.length() == 0) {
            throw new IllegalArgumentException("nbtKeyPrefix must not be empty");
        }
        this.stateKey = nbtKeyPrefix + "state";
        this.timeKey = nbtKeyPrefix + "time";
    }

    // -----------------------------------------------------------------------
    // NBT
    // -----------------------------------------------------------------------

    /**
     * Persists state and playback time.
     * Keys: {@code <prefix>state}, {@code <prefix>time} (default prefix
     * {@code "Anim_"}).
     */
    public void writeToNBT(NBTTagCompound nbt) {
        nbt.setInteger(stateKey, getCurrentState());
        nbt.setFloat(timeKey, getPlaybackTime());
    }

    /**
     * Restores state and time from NBT.
     * prevPlaybackTime = playbackTime to avoid artifacts on the first frame after load.
     * If keys are absent (old save), uses defaults (state=0, time=0).
     */
    public void readFromNBT(NBTTagCompound nbt) {
        restorePlayback(
            nbt.getInteger(stateKey),
            nbt.hasKey(timeKey) ? nbt.getFloat(timeKey) : 0f
        );
    }
}
