package aero.modellib.test;

import aero.modellib.Aero_AnimationBundle;
import aero.modellib.Aero_AnimationDefinition;
import aero.modellib.Aero_AnimationLoader;
import aero.modellib.Aero_AnimationState;
import aero.modellib.Aero_CellPageRenderableBE;
import aero.modellib.Aero_MeshModel;
import aero.modellib.Aero_RenderDistanceBlockEntity;

public class PumpBlockEntity extends Aero_RenderDistanceBlockEntity implements Aero_CellPageRenderableBE {

    public static final int STATE_STROKE = 1;

    public static final Aero_AnimationBundle BUNDLE =
        Aero_AnimationLoader.load("/models/Pump.anim.json");

    public static final Aero_AnimationDefinition ANIM_DEF =
        new Aero_AnimationDefinition().state(STATE_STROKE, "stroke");

    public final Aero_AnimationState animState = ANIM_DEF.createState(BUNDLE);
    private boolean phaseSeeded;

    @Override
    protected double getAeroRenderRadius() {
        return 2.0d;
    }

    @Override
    public Aero_MeshModel aeroCellModel() {
        return PumpBlockEntityRenderer.MODEL;
    }

    @Override
    public String aeroCellTexturePath() {
        return PumpBlockEntityRenderer.TEXTURE;
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
    public void tick() {
        super.tick();
        if (!shouldTickAnimation()) return;
        animState.setState(STATE_STROKE);
        if (!phaseSeeded) {
            AeroTestMod.seedMegaLoopPhase(animState, STATE_STROKE, x, y, z);
            phaseSeeded = true;
        }
        animState.tick();
    }
}
