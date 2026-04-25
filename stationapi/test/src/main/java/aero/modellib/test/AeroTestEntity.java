package aero.modellib.test;

import aero.modellib.Aero_AnimationBundle;
import aero.modellib.Aero_AnimationDefinition;
import aero.modellib.Aero_AnimationLoader;
import aero.modellib.Aero_AnimationState;
import net.minecraft.entity.Entity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.world.World;

/**
 * In-game smoke-test entity for Aero_EntityModelRenderer.
 *
 * The entity itself is intentionally simple; the point is to exercise the
 * entity render path with real world brightness, yaw and partial-tick animation.
 */
public class AeroTestEntity extends Entity {

    public static final int STATE_SPIN = 1;

    public static final Aero_AnimationBundle BUNDLE =
        Aero_AnimationLoader.load("/models/MegaCrusher.anim.json");

    public static final Aero_AnimationDefinition ANIM_DEF =
        new Aero_AnimationDefinition()
            .state(STATE_SPIN, "working");

    public final Aero_AnimationState animState = ANIM_DEF.createState(BUNDLE);

    public AeroTestEntity(World world) {
        super(world);
        setBoundingBoxSpacing(1.2f, 1.6f);
        standingEyeHeight = 1.2f;
        ignoreFrustumCull = true;
    }

    @Override
    protected void initDataTracker() {
    }

    @Override
    public void tick() {
        super.tick();
        animState.setState(STATE_SPIN);
        animState.tick();

        prevYaw = yaw;
        yaw = (age * 3f) % 360f;
    }

    @Override
    protected void readNbt(NbtCompound nbt) {
        animState.readFromNBT(nbt);
    }

    @Override
    protected void writeNbt(NbtCompound nbt) {
        animState.writeToNBT(nbt);
    }
}
