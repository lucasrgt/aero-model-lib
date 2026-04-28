package aero.modellib.test;

import aero.modellib.Aero_AnimationBundle;
import aero.modellib.Aero_AnimationDefinition;
import aero.modellib.Aero_AnimationLoader;
import aero.modellib.Aero_AnimationState;
import aero.modellib.Aero_RenderDistanceBlockEntity;

public class ConveyorBlockEntity extends Aero_RenderDistanceBlockEntity {

    public static final int STATE_SCROLL = 1;

    public static final Aero_AnimationBundle BUNDLE =
        Aero_AnimationLoader.load("/models/Conveyor.anim.json");

    public static final Aero_AnimationDefinition ANIM_DEF =
        new Aero_AnimationDefinition().state(STATE_SCROLL, "scroll");

    public final Aero_AnimationState animState = ANIM_DEF.createState(BUNDLE);

    @Override protected double getAeroRenderRadius() { return 1.0d; }

    @Override
    public void tick() {
        super.tick();
        if (!shouldTickAnimation()) return;
        animState.setState(STATE_SCROLL);
        animState.tick();
    }
}
