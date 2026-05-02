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
    private Object aeroCellTrackedWorld;
    private int aeroCellTrackedX;
    private int aeroCellTrackedY;
    private int aeroCellTrackedZ;
    private boolean aeroCellTracked;

    protected double getAeroRenderRadius() {
        return 0.0d;
    }

    protected double getAeroMaxRenderDistance() {
        return Aero_RenderDistanceCulling.DEFAULT_SPECIAL_RENDER_RADIUS;
    }

    @Override
    public void tick() {
        super.tick();
        aeroTrackCellFull();
    }

    @Override
    public void markRemoved() {
        Aero_BECellIndex.untrack(this);
        aeroClearCellTrack();
        super.markRemoved();
    }

    @Override
    public void cancelRemoval() {
        super.cancelRemoval();
        aeroTrackCellFull();
    }

    @Override
    public double distanceFrom(double x, double y, double z) {
        aeroTrackCellIfMoved();
        if (this instanceof Aero_CellPageRenderableBE) {
            Aero_CellPageRenderableBE renderable = (Aero_CellPageRenderableBE) this;
            if (!Aero_ChunkVisibility.isBlockChunkVisible(this.x, this.z,
                    renderable.aeroCellVisualRadius())) {
                return Double.POSITIVE_INFINITY;
            }
            double dx = this.x + 0.5d - x;
            double dy = this.y + 0.5d - y;
            double dz = this.z + 0.5d - z;
            // distanceFrom runs before the individual renderer is dispatched.
            // Keep animation-budget admission in the renderer so a budget
            // downgrade can still draw the BE through its normal at-rest path
            // instead of suppressing the renderer and freezing nearby models.
            Aero_RenderLod lod = Aero_RenderDistance.lodRelativeNoAnimationBudget(dx, dy, dz,
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
        aeroTrackCellIfMoved();
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
        boolean tick = Aero_AnimationTickBudget.shouldTick(stride, aeroTickAge,
            this.x, this.y, this.z);
        aeroTickAge++;
        return tick;
    }

    private void aeroTrackCellFull() {
        Aero_BECellIndex.track(this);
        if (this.world == null) {
            aeroClearCellTrack();
            return;
        }
        aeroCellTrackedWorld = this.world;
        aeroCellTrackedX = this.x;
        aeroCellTrackedY = this.y;
        aeroCellTrackedZ = this.z;
        aeroCellTracked = true;
    }

    private void aeroTrackCellIfMoved() {
        if (this.world == null) {
            if (aeroCellTracked) {
                Aero_BECellIndex.untrack(this);
                aeroClearCellTrack();
            }
            return;
        }
        if (!aeroCellTracked
            || aeroCellTrackedWorld != this.world
            || aeroCellTrackedX != this.x
            || aeroCellTrackedY != this.y
            || aeroCellTrackedZ != this.z) {
            aeroTrackCellFull();
        }
    }

    private void aeroClearCellTrack() {
        aeroCellTrackedWorld = null;
        aeroCellTrackedX = 0;
        aeroCellTrackedY = 0;
        aeroCellTrackedZ = 0;
        aeroCellTracked = false;
    }
}
