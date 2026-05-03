package aero.modellib.test;
import aero.modellib.Aero_AnimationState;
import aero.modellib.Aero_CellPageRenderableBE;
import aero.modellib.Aero_RenderDistanceBlockEntity;

import aero.modellib.animation.Aero_AnimationBundle;
import aero.modellib.animation.Aero_AnimationDefinition;
import aero.modellib.animation.Aero_AnimationLoader;
import aero.modellib.model.Aero_MeshModel;

public class SpellCircleBlockEntity extends Aero_RenderDistanceBlockEntity implements Aero_CellPageRenderableBE {

    public static final int STATE_CHANNEL = 1;

    public static final Aero_AnimationBundle BUNDLE =
        Aero_AnimationLoader.load("/models/SpellCircle.anim.json");

    public static final Aero_AnimationDefinition ANIM_DEF =
        new Aero_AnimationDefinition().state(STATE_CHANNEL, "channel");

    public final Aero_AnimationState animState = ANIM_DEF.createState(BUNDLE);

    @Override protected double getAeroRenderRadius() { return 1.0d; }

    @Override
    public Aero_MeshModel aeroCellModel() {
        return SpellCircleBlockEntityRenderer.MODEL;
    }

    @Override
    public String aeroCellTexturePath() {
        return SpellCircleBlockEntityRenderer.TEXTURE;
    }

    @Override
    public float aeroCellBrightness() {
        return AeroLight.brightnessAbove(world, x, y, z);
    }

    @Override
    public double aeroCellVisualRadius() {
        return 1.0d;
    }

    @Override
    public double aeroCellAnimatedDistance() {
        return AeroTestMod.demoAnimatedLodDistance();
    }

    @Override
    public void tick() {
        super.tick();
        if (!shouldTickAnimation()) return;
        animState.setState(STATE_CHANNEL);
        animState.tick();
    }
}
