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
 *
 * <p>Default NBT keys are {@code "Anim_state"} and {@code "Anim_time"}. To
 * carry more than one Aero_AnimationState on the same BlockEntity, build
 * each with a distinct prefix via
 * {@link Aero_AnimationDefinition#createState(Aero_AnimationBundle, String)}.
 */
public class Aero_AnimationState extends Aero_AnimationPlayback {

    /** Default NBT key prefix when no override is supplied. */
    public static final String DEFAULT_NBT_KEY_PREFIX = "Anim_";

    private final String stateKey;
    private final String timeKey;

    /** Built by Aero_AnimationDefinition.createState(). */
    Aero_AnimationState(Aero_AnimationDefinition def, Aero_AnimationBundle bundle) {
        this(def, bundle, DEFAULT_NBT_KEY_PREFIX);
    }

    Aero_AnimationState(Aero_AnimationDefinition def, Aero_AnimationBundle bundle,
                        String nbtKeyPrefix) {
        super(def, bundle);
        if (nbtKeyPrefix == null || nbtKeyPrefix.length() == 0) {
            throw new IllegalArgumentException("nbtKeyPrefix must not be empty");
        }
        this.stateKey = nbtKeyPrefix + "state";
        this.timeKey = nbtKeyPrefix + "time";
    }

    /**
     * Persists state and playback time.
     * Keys: {@code <prefix>state}, {@code <prefix>time} (default prefix
     * {@code "Anim_"}).
     */
    public void writeToNBT(NbtCompound nbt) {
        nbt.putInt(stateKey, getCurrentState());
        nbt.putFloat(timeKey, getPlaybackTime());
    }

    /**
     * Restores state and time from NBT.
     * prevPlaybackTime = playbackTime to avoid artifacts on the first frame after load.
     */
    public void readFromNBT(NbtCompound nbt) {
        restorePlayback(
            nbt.getInt(stateKey),
            nbt.contains(timeKey) ? nbt.getFloat(timeKey) : 0f
        );
    }
}
