package aero.modellib;

import net.minecraft.block.entity.BlockEntity;

/**
 * Optional BlockEntity base class that makes vanilla's hardcoded 64 block
 * special-renderer limit scale with the player's render distance under a cap.
 */
public class Aero_RenderDistanceBlockEntity extends BlockEntity {

    protected double getAeroRenderRadius() {
        return 0.0d;
    }

    protected double getAeroMaxRenderDistance() {
        return Aero_RenderDistanceCulling.DEFAULT_SPECIAL_RENDER_RADIUS;
    }

    @Override
    public double distanceFrom(double x, double y, double z) {
        return Aero_RenderDistance.blockEntityDistanceFrom(this, x, y, z,
            getAeroRenderRadius(), getAeroMaxRenderDistance());
    }
}
