package aero.modellib.test;

import aero.modellib.Aero_RenderDistanceBlockEntity;

/**
 * Empty BlockEntity — exists only so the renderer dispatcher attaches.
 * Smoke-test scope: no animation state, no inventory, no NBT.
 */
public class MegaModelBlockEntity extends Aero_RenderDistanceBlockEntity {

    @Override
    protected double getAeroRenderRadius() {
        return 4.0d;
    }
}
