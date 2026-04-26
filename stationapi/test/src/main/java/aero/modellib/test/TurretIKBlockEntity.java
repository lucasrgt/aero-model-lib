package aero.modellib.test;

import aero.modellib.Aero_AnimationBundle;
import aero.modellib.Aero_AnimationDefinition;
import aero.modellib.Aero_AnimationLoader;
import aero.modellib.Aero_AnimationState;
import aero.modellib.Aero_RenderDistanceBlockEntity;

public class TurretIKBlockEntity extends Aero_RenderDistanceBlockEntity {

    public static final int STATE_REST = 1;

    public static final Aero_AnimationBundle BUNDLE =
        Aero_AnimationLoader.load("/models/Turret.anim.json");

    public static final Aero_AnimationDefinition ANIM_DEF =
        new Aero_AnimationDefinition().state(STATE_REST, "rest");

    public final Aero_AnimationState animState = ANIM_DEF.createState(BUNDLE);

    /** Tick count drives the orbiting IK target — see TurretIKBlockEntityRenderer. */
    public int orbitTick = 0;

    @Override protected double getAeroRenderRadius() { return 2.0d; }

    @Override
    public void tick() {
        super.tick();
        animState.setState(STATE_REST);
        animState.tick();
        orbitTick++;
    }
}
