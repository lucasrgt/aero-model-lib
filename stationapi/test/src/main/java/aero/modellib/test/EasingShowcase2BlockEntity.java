package aero.modellib.test;

import aero.modellib.Aero_AnimationBundle;
import aero.modellib.Aero_AnimationDefinition;
import aero.modellib.Aero_AnimationLoader;
import aero.modellib.Aero_AnimationState;
import aero.modellib.Aero_RenderDistanceBlockEntity;

public class EasingShowcase2BlockEntity extends Aero_RenderDistanceBlockEntity {
    public static final int STATE_WAVE = 1;
    public static final Aero_AnimationBundle BUNDLE =
        Aero_AnimationLoader.load("/models/EasingShowcase2.anim.json");
    public static final Aero_AnimationDefinition ANIM_DEF =
        new Aero_AnimationDefinition().state(STATE_WAVE, "wave");
    public final Aero_AnimationState animState = ANIM_DEF.createState(BUNDLE);

    @Override public void tick() {
        super.tick();
        if (!shouldTickAnimation()) return;
        animState.setState(STATE_WAVE);
        animState.tick();
    }
}
