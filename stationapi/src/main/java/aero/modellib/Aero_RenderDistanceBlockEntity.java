package aero.modellib;

import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.player.PlayerEntity;

/**
 * Optional BlockEntity base class that makes vanilla's hardcoded 64 block
 * special-renderer limit scale with the player's render distance under a cap,
 * and provides an opt-in animation-tick LOD via {@link #shouldTickAnimation()}.
 */
public class Aero_RenderDistanceBlockEntity extends BlockEntity {

    /** Monotonic counter for phase-stable tick LOD; incremented on every call. */
    private int aeroTickAge = 0;

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

    /**
     * Distance-tiered animation tick decision. Call from {@code tick()}:
     * <pre>{@code
     * public void tick() {
     *     super.tick();
     *     if (!shouldTickAnimation()) return;
     *     animState.setState(currentState);
     *     animState.tick();
     * }
     * }</pre>
     *
     * <p>Uses {@link Aero_AnimationTickLOD}'s default tiers (every-tick at
     * &lt;64, half-rate at &lt;128, quarter-rate at &lt;256, skip beyond).
     * For custom thresholds, call
     * {@link Aero_AnimationTickLOD#tickStride(double, double, double, double)}
     * directly.
     *
     * @return true if the entity should advance its animation this tick
     */
    public boolean shouldTickAnimation() {
        if (this.world == null) return false;
        double cx = this.x + 0.5;
        double cy = this.y + 0.5;
        double cz = this.z + 0.5;
        PlayerEntity p = this.world.getClosestPlayer(cx, cy, cz, Aero_AnimationTickLOD.DEFAULT_FAR_RADIUS);
        int stride;
        if (p == null) {
            stride = 0;
        } else {
            double dx = p.x - cx;
            double dy = p.y - cy;
            double dz = p.z - cz;
            stride = Aero_AnimationTickLOD.tickStride(dx*dx + dy*dy + dz*dz);
        }
        boolean tick = Aero_AnimationTickLOD.shouldTick(stride, aeroTickAge);
        aeroTickAge++;
        return tick;
    }
}
