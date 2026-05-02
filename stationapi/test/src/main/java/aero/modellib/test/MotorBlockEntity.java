package aero.modellib.test;

import aero.modellib.Aero_AnimationBundle;
import aero.modellib.Aero_AnimationDefinition;
import aero.modellib.Aero_AnimationLoader;
import aero.modellib.Aero_AnimationState;
import aero.modellib.Aero_CellPageRenderableBE;
import aero.modellib.Aero_MeshModel;
import aero.modellib.Aero_RenderDistanceBlockEntity;

/**
 * Permanently in STATE_PULSE — exercises {@link Aero_AnimationState} with
 * the scale channel of the {@code pulse} clip (core group, 1s loop).
 */
public class MotorBlockEntity extends Aero_RenderDistanceBlockEntity implements Aero_CellPageRenderableBE {

    public static final int STATE_PULSE = 1;

    public static final Aero_AnimationBundle BUNDLE =
        Aero_AnimationLoader.load("/models/Motor.anim.json");

    public static final Aero_AnimationDefinition ANIM_DEF =
        new Aero_AnimationDefinition().state(STATE_PULSE, "pulse");

    public final Aero_AnimationState animState = ANIM_DEF.createState(BUNDLE);
    private boolean phaseSeeded;

    @Override
    protected double getAeroRenderRadius() {
        return 2.0d;
    }

    @Override
    public Aero_MeshModel aeroCellModel() {
        return MotorBlockEntityRenderer.MODEL;
    }

    @Override
    public String aeroCellTexturePath() {
        return MotorBlockEntityRenderer.TEXTURE;
    }

    @Override
    public float aeroCellBrightness() {
        return AeroLight.brightnessAbove(world, x, y, z);
    }

    @Override
    public double aeroCellVisualRadius() {
        return 2.0d;
    }

    @Override
    public double aeroCellAnimatedDistance() {
        return AeroTestMod.demoAnimatedLodDistance();
    }

    @Override
    public double aeroCellMaxRenderDistance() {
        return getAeroMaxRenderDistance();
    }

    @Override
    public void tick() {
        super.tick();
        if (!shouldTickAnimation()) return;
        animState.setState(STATE_PULSE);
        if (!phaseSeeded) {
            AeroTestMod.seedMegaLoopPhase(animState, STATE_PULSE, x, y, z);
            phaseSeeded = true;
        }
        animState.tick();
    }
}
