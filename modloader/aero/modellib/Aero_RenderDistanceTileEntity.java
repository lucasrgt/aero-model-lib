package aero.modellib;

import net.minecraft.src.TileEntity;

/**
 * Optional TileEntity base class that makes vanilla's hardcoded 64 block
 * special-renderer limit scale with the player's render distance under a cap.
 */
public class Aero_RenderDistanceTileEntity extends TileEntity {

    protected double getAeroRenderRadius() {
        return 0.0d;
    }

    protected double getAeroMaxRenderDistance() {
        return Aero_RenderDistanceCulling.DEFAULT_SPECIAL_RENDER_RADIUS;
    }

    public double getDistanceFrom(double x, double y, double z) {
        return Aero_RenderDistance.tileEntityDistanceFrom(this, x, y, z,
            getAeroRenderRadius(), getAeroMaxRenderDistance());
    }
}
