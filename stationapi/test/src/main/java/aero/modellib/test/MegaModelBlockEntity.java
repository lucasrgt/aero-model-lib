package aero.modellib.test;

import aero.modellib.Aero_CellPageRenderableBE;
import aero.modellib.Aero_MeshModel;
import aero.modellib.Aero_RenderDistanceBlockEntity;

/**
 * Empty BlockEntity — exists only so the renderer dispatcher attaches.
 * Smoke-test scope: no animation state, no inventory, no NBT.
 */
public class MegaModelBlockEntity extends Aero_RenderDistanceBlockEntity implements Aero_CellPageRenderableBE {

    @Override
    protected double getAeroRenderRadius() {
        return 4.0d;
    }

    @Override
    public Aero_MeshModel aeroCellModel() {
        return MegaModelBlockEntityRenderer.MODEL;
    }

    @Override
    public String aeroCellTexturePath() {
        return MegaModelBlockEntityRenderer.TEXTURE;
    }

    @Override
    public float aeroCellBrightness() {
        return AeroLight.brightnessAbove(world, x, y, z);
    }

    @Override
    public double aeroCellVisualRadius() {
        return 4.0d;
    }

    @Override
    public double aeroCellAnimatedDistance() {
        return 0.0d;
    }
}
