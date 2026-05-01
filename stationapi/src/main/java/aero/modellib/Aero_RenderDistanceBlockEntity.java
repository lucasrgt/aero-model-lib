package aero.modellib;

import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.player.PlayerEntity;

/**
 * Optional BlockEntity base class that makes vanilla's hardcoded 64 block
 * special-renderer limit scale with the player's render distance under a cap,
 * and provides an opt-in animation-tick LOD via {@link #shouldTickAnimation()}.
 */
public class Aero_RenderDistanceBlockEntity extends BlockEntity implements Aero_CellRenderableBE {

    /** Monotonic counter for phase-stable tick LOD; incremented on every call. */
    private int aeroTickAge = 0;

    protected double getAeroRenderRadius() {
        return 0.0d;
    }

    protected double getAeroMaxRenderDistance() {
        return Aero_RenderDistanceCulling.DEFAULT_SPECIAL_RENDER_RADIUS;
    }

    @Override
    public void tick() {
        super.tick();
        Aero_BECellIndex.track(this);
    }

    @Override
    public void markRemoved() {
        Aero_BECellIndex.untrack(this);
        super.markRemoved();
    }

    @Override
    public void cancelRemoval() {
        super.cancelRemoval();
        Aero_BECellIndex.track(this);
    }

    @Override
    public double distanceFrom(double x, double y, double z) {
        Aero_BECellIndex.track(this);
        if (this instanceof Aero_CellPageRenderableBE) {
            if (!Aero_ChunkVisibility.isBlockChunkVisible(this.x, this.z)) {
                return Double.POSITIVE_INFINITY;
            }
            Aero_CellPageRenderableBE renderable = (Aero_CellPageRenderableBE) this;
            double dx = this.x - x;
            double dy = this.y - y;
            double dz = this.z - z;
            Aero_RenderLod lod = Aero_RenderDistance.lodRelative(dx, dy, dz,
                renderable.aeroCellVisualRadius(),
                renderable.aeroCellAnimatedDistance(),
                renderable.aeroCellMaxRenderDistance());
            if (!lod.shouldRender()) {
                return Double.POSITIVE_INFINITY;
            }
            if (renderable.aeroCanSkipIndividualRenderer(lod)
                && Aero_BECellRenderer.tryQueueManagedAtRest(this, renderable)) {
                return Double.POSITIVE_INFINITY;
            }
        }
        return Aero_RenderDistance.blockEntityDistanceFrom(this, x, y, z,
            getAeroRenderRadius(), getAeroMaxRenderDistance());
    }

    @Override
    public int aeroRenderStateHash() {
        return 0;
    }

    @Override
    public int aeroOrientationHash() {
        return 0;
    }

    @Override
    public boolean aeroCanCellPage() {
        return true;
    }

    @Override
    public boolean aeroWantsAnimation() {
        return true;
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
        return shouldTickAnimation(0.0d);
    }

    /**
     * Distance-tiered animation tick decision with motion simplification.
     * Pass horizontal/visual speed in blocks per tick for fast-moving BEs or
     * contraptions whose exact pose is harder to perceive while moving.
     */
    public boolean shouldTickAnimation(double velocityBlocksPerTick) {
        if (this.world == null) return false;
        Aero_BECellIndex.track(this);
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
        if (p == null) {
            stride = 0;
        } else {
            double dx = cx - p.x;
            double dy = cy - p.y;
            double dz = cz - p.z;
            stride = Aero_AnimationTickLOD.tickStrideWithMotion(
                dx*dx + dy*dy + dz*dz, velocityBlocksPerTick);
        }
        boolean tick = Aero_AnimationTickLOD.shouldTick(stride, aeroTickAge);
        aeroTickAge++;
        return tick;
    }
}
