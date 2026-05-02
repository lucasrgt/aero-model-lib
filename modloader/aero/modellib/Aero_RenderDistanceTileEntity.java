package aero.modellib;

import net.minecraft.src.EntityPlayer;
import net.minecraft.src.TileEntity;

/**
 * Optional TileEntity base class that makes vanilla's hardcoded 64 block
 * special-renderer limit scale with the player's render distance under a cap,
 * and provides an opt-in animation-tick LOD via {@link #shouldTickAnimation()}.
 */
public class Aero_RenderDistanceTileEntity extends TileEntity {

    /** Monotonic counter for phase-stable tick LOD; incremented on every call. */
    private int aeroTickAge = 0;

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

    /**
     * Distance-tiered animation tick decision. Call from {@code updateEntity()}:
     * <pre>{@code
     * public void updateEntity() {
     *     super.updateEntity();
     *     if (!shouldTickAnimation()) return;
     *     animState.setState(currentState);
     *     animState.tick();
     * }
     * }</pre>
     *
     * <p>Uses {@link Aero_AnimationTickLOD}'s default tiers (every-tick at
     * &lt;64, half-rate at &lt;128, quarter-rate at &lt;256, skip beyond).
     *
     * @return true if the entity should advance its animation this tick
     */
    public boolean shouldTickAnimation() {
        return shouldTickAnimation(0.0d);
    }

    /**
     * Distance-tiered animation tick decision with motion simplification.
     * Pass horizontal/visual speed in blocks per tick for fast-moving
     * contraptions whose exact pose is harder to perceive while moving.
     */
    public boolean shouldTickAnimation(double velocityBlocksPerTick) {
        if (this.worldObj == null) return false;
        double cx = this.xCoord + 0.5;
        double cy = this.yCoord + 0.5;
        double cz = this.zCoord + 0.5;
        EntityPlayer p = Aero_RenderDistance.getCachedLocalPlayer();
        if (p == null || p.worldObj != this.worldObj) {
            p = this.worldObj.getClosestPlayer(cx, cy, cz, Aero_AnimationTickLOD.DEFAULT_FAR_RADIUS);
        }
        int stride;
        if (p == null) {
            stride = 0;
        } else {
            double dx = p.posX - cx;
            double dy = p.posY - cy;
            double dz = p.posZ - cz;
            stride = Aero_AnimationTickLOD.tickStrideWithMotion(
                dx*dx + dy*dy + dz*dz, velocityBlocksPerTick);
        }
        boolean tick = Aero_AnimationTickBudget.shouldTick(stride, aeroTickAge,
            this.xCoord, this.yCoord, this.zCoord);
        aeroTickAge++;
        return tick;
    }
}
