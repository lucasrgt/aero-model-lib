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

    // v3.x occlusion cache. Visible BEs re-check quickly, while occluded
    // BEs hold their state longer so jump-height camera jitter cannot flip
    // them visible/hidden every few frames.
    private static final int RECHECK_VISIBLE = 4;
    private static final int RECHECK_OCCLUDED = 12;

    private int aeroOcclusionFramesUntilRecheck = 0;
    private boolean aeroOcclusionCached;

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
     * Cached wrapper around {@link Aero_OcclusionCull#isOccluded}. Visible
     * entities re-check after 4 calls; occluded entities re-check after 12.
     * The asymmetric hold kills jump flicker while still keeping visible
     * entities responsive.
     *
     * @param dx camera-relative X (BE − camera, BER convention)
     * @param dy camera-relative Y
     * @param dz camera-relative Z
     */
    boolean isOccludedCached(double dx, double dy, double dz) {
        if (aeroOcclusionFramesUntilRecheck-- > 0) return aeroOcclusionCached;
        boolean now = Aero_OcclusionCull.isOccluded(this.world, dx, dy, dz,
                                                    this.x, this.y, this.z);
        aeroOcclusionCached = now;
        aeroOcclusionFramesUntilRecheck = now ? RECHECK_OCCLUDED : RECHECK_VISIBLE;
        return aeroOcclusionCached;
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
        // Prefer the per-frame cached local player to skip the per-BE
        // playerEntities scan (576 BEs × 20 Hz = 11.5k scans/sec on the
        // MEGA test). Fall back to {@code world.getClosestPlayer} when
        // the cache hasn't been primed yet (very early init or world
        // load) so behaviour stays the same.
        PlayerEntity p = Aero_RenderDistance.getCachedLocalPlayer();
        if (p == null) {
            p = this.world.getClosestPlayer(cx, cy, cz, Aero_AnimationTickLOD.DEFAULT_FAR_RADIUS);
        }
        int stride;
        // BER convention: dx = BE − camera (positive when BE is east of
        // player). The previous version used dx = player − BE (camera − BE)
        // which fed the wrong sign into Aero_OcclusionCull and pointed the
        // ray-walk away from the camera — fixed in v3.0.
        double dx = 0, dy = 0, dz = 0;
        if (p == null) {
            stride = 0;
        } else {
            dx = cx - p.x;
            dy = cy - p.y;
            dz = cz - p.z;
            stride = Aero_AnimationTickLOD.tickStride(dx*dx + dy*dy + dz*dz);
        }
        boolean tick = Aero_AnimationTickLOD.shouldTick(stride, aeroTickAge);
        aeroTickAge++;
        // v3.0: occluded BEs freeze on their last pose — no point burning
        // CPU on keyframe interpolation when the result will never reach
        // the rasterizer. Note this means the pose snaps to the moment
        // occlusion ended; for showcase animations that's invisible
        // (player sees the moving model when it un-occludes). For
        // gameplay-critical timing, downstream mods should opt out by
        // overriding shouldTickAnimation().
        if (tick && p != null && isOccludedCached(dx, dy, dz)) {
            return false;
        }
        return tick;
    }
}
