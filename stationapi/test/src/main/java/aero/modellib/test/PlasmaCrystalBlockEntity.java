package aero.modellib.test;

import aero.modellib.Aero_AnimationBundle;
import aero.modellib.Aero_AnimationDefinition;
import aero.modellib.Aero_AnimationLoader;
import aero.modellib.Aero_AnimationState;
import aero.modellib.Aero_RenderDistanceBlockEntity;

/**
 * Reuses the Crystal animation bundle and clip set, but the renderer
 * stacks an ADDITIVE blend mode + a continuous procedural Y-spin on top
 * of the keyframed all_axes clip — so this block tests the
 * Aero_RenderOptions.additive(...) and Aero_ProceduralPose features
 * without authoring a new model/animation pair.
 */
public class PlasmaCrystalBlockEntity extends Aero_RenderDistanceBlockEntity {

    public static final int STATE_ALL_AXES = 1;

    public static final Aero_AnimationBundle BUNDLE =
        Aero_AnimationLoader.load("/models/Crystal.anim.json");

    public static final Aero_AnimationDefinition ANIM_DEF =
        new Aero_AnimationDefinition().state(STATE_ALL_AXES, "all_axes");

    public final Aero_AnimationState animState = ANIM_DEF.createState(BUNDLE);

    @Override
    protected double getAeroRenderRadius() {
        return 2.0d;
    }

    @Override
    public void tick() {
        super.tick();
        if (!shouldTickAnimation()) return;
        animState.setState(STATE_ALL_AXES);
        animState.tick();
    }
}
