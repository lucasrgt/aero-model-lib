package aero.modellib.test;

import aero.modellib.Aero_AnimationBundle;
import aero.modellib.Aero_AnimationDefinition;
import aero.modellib.Aero_AnimationLoader;
import aero.modellib.Aero_AnimationState;
import net.minecraft.block.entity.BlockEntity;

public class PumpBlockEntity extends BlockEntity {

    public static final int STATE_STROKE = 1;

    public static final Aero_AnimationBundle BUNDLE =
        Aero_AnimationLoader.load("/models/Pump.anim.json");

    public static final Aero_AnimationDefinition ANIM_DEF =
        new Aero_AnimationDefinition().state(STATE_STROKE, "stroke");

    public final Aero_AnimationState animState = ANIM_DEF.createState(BUNDLE);

    @Override
    public void tick() {
        super.tick();
        animState.setState(STATE_STROKE);
        animState.tick();
    }
}
