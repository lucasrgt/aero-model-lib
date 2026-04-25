package aero.modellib.test;

import aero.modellib.Aero_AnimationBundle;
import aero.modellib.Aero_AnimationDefinition;
import aero.modellib.Aero_AnimationLoader;
import aero.modellib.Aero_AnimationState;
import net.minecraft.block.entity.BlockEntity;

/**
 * Permanently in STATE_PULSE — exercises {@link Aero_AnimationState} with
 * the scale channel of the {@code pulse} clip (core group, 1s loop).
 */
public class MotorBlockEntity extends BlockEntity {

    public static final int STATE_PULSE = 1;

    public static final Aero_AnimationBundle BUNDLE =
        Aero_AnimationLoader.load("/models/Motor.anim.json");

    public static final Aero_AnimationDefinition ANIM_DEF =
        new Aero_AnimationDefinition().state(STATE_PULSE, "pulse");

    public final Aero_AnimationState animState = ANIM_DEF.createState(BUNDLE);

    @Override
    public void tick() {
        super.tick();
        animState.setState(STATE_PULSE);
        animState.tick();
    }
}
