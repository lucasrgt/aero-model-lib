package aero.modellib.test;
import aero.modellib.Aero_AnimationState;
import aero.modellib.Aero_RenderDistanceBlockEntity;

import aero.modellib.animation.Aero_AnimationBundle;
import aero.modellib.animation.Aero_AnimationDefinition;
import aero.modellib.animation.Aero_AnimationLoader;

public class CrystalChaosBlockEntity extends Aero_RenderDistanceBlockEntity {

    public static final int STATE_CHAOS = 1;

    public static final Aero_AnimationBundle BUNDLE =
        Aero_AnimationLoader.load("/models/CrystalChaos.anim.json");

    public static final Aero_AnimationDefinition ANIM_DEF =
        new Aero_AnimationDefinition().state(STATE_CHAOS, "chaos");

    public final Aero_AnimationState animState = ANIM_DEF.createState(BUNDLE);

    @Override
    protected double getAeroRenderRadius() {
        return 2.0d;
    }

    @Override
    public void tick() {
        super.tick();
        if (!shouldTickAnimation()) return;
        animState.setState(STATE_CHAOS);
        animState.tick();
    }
}
