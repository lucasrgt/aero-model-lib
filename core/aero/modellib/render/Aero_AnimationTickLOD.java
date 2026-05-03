package aero.modellib.render;

/**
 * Tick-rate LOD math for animated entities.
 *
 * <p>Animation tick (keyframe sample, bone interpolation, IK solve, morph
 * blend) is per-tick CPU work. With dozens or hundreds of animated
 * entities in a scene, ticking every entity at full 20 Hz dominates the
 * server-thread / world-tick budget — even though most are far enough
 * away that nobody can tell the difference between 5 Hz and 20 Hz
 * animation samples.
 *
 * <p>This helper returns a per-entity tick <em>stride</em> based on
 * squared distance from the nearest player, plus a
 * {@link #shouldTick(int, int)} test that combines stride with a
 * monotonically-incrementing age counter for phase-correct thinning.
 *
 * <p>Default tiers (override via the 4-arg overload if your entities
 * benefit from a different falloff):
 * <ul>
 *   <li>d &lt; 64 blocks  → stride 1 (every tick)</li>
 *   <li>d &lt; 128 blocks → stride 2 (half rate)</li>
 *   <li>d &lt; 256 blocks → stride 4 (quarter rate)</li>
 *   <li>d ≥ 256 blocks   → stride 0 (skip — beyond animated LOD cap)</li>
 * </ul>
 *
 * <p>Pure math; depends on neither GL nor world API. Both runtimes share
 * this class.
 *
 * <p>Typical usage on a BlockEntity / TileEntity:
 * <pre>{@code
 * public void tick() {
 *   super.tick();
 *   if (!shouldTickAnimation()) return; // helper on the lib's BE base class
 *   animState.setState(currentState);
 *   animState.tick();
 * }
 * }</pre>
 */
public final class Aero_AnimationTickLOD {

    public static final double DEFAULT_CLOSE_RADIUS  = 64.0d;
    public static final double DEFAULT_MEDIUM_RADIUS = 128.0d;
    public static final double DEFAULT_FAR_RADIUS    = 256.0d;
    public static final double DEFAULT_FAST_SPEED_BLOCKS_PER_SECOND = 8.0d;

    /**
     * Recommended {@code animatedDistance} for the player's current view
     * distance setting. Beta MC has 4 view-distance tiers and this maps
     * each to a per-tier LOD radius beyond which animated entities are
     * routed to the at-rest display-list path.
     *
     * <p><strong>The numbers are a heuristic, not a derivation.</strong>
     * They were picked empirically against the {@code runClientStress}
     * worldgen on the dev workstation so a 3×3 mega-model tower per
     * qualifying chunk stays in a playable FPS range. They do not come
     * from a closed-form "keep per-frame budget constant" formula; the
     * underlying intuition is that animation invisibility-at-distance
     * scales roughly with visible chunk count, but the actual values
     * trade aggression vs visible-LOD-pop on this scene.
     *
     * <p>If your scene has a different geometry / animation cost mix,
     * call the lower-level {@link #tickStride(double, double, double, double)}
     * with your own radii. These defaults are a reasonable starting
     * point, not a universal answer.
     *
     * @param viewDistance vanilla MC's renderDistance enum
     *        (0=Far, 1=Normal, 2=Short, 3=Tiny — see
     *        {@link Aero_RenderDistanceCulling#VIEW_DISTANCE_FAR} et al)
     */
    public static double recommendedAnimatedDistance(int viewDistance) {
        switch (viewDistance) {
            // Far visible = 16 chunks = 256 blocks. 64 blocks = 1/4 of
            // visible; aggressive because the player can't perceive
            // animation that far out.
            case Aero_RenderDistanceCulling.VIEW_DISTANCE_FAR:    return 64.0d;
            // Normal visible = 8 chunks = 128 blocks. 80 blocks ≈ 5/8 of
            // visible — less aggressive, the LOD swap is more noticeable
            // at this view distance.
            case Aero_RenderDistanceCulling.VIEW_DISTANCE_NORMAL: return 80.0d;
            // Short / Tiny: cap at the visible radius (4 chunks / 2 chunks)
            // since anything beyond it is already culled by distance.
            case Aero_RenderDistanceCulling.VIEW_DISTANCE_SHORT:  return 64.0d;
            case Aero_RenderDistanceCulling.VIEW_DISTANCE_TINY:   return 32.0d;
            default:                                              return 96.0d;
        }
    }

    private Aero_AnimationTickLOD() {}

    /** Default-tier stride lookup. See class javadoc for thresholds. */
    public static int tickStride(double squaredDistance) {
        return tickStride(squaredDistance,
            DEFAULT_CLOSE_RADIUS, DEFAULT_MEDIUM_RADIUS, DEFAULT_FAR_RADIUS);
    }

    /**
     * Custom-tier stride lookup. {@code closeR}/{@code mediumR}/{@code farR}
     * are the 1×, 2×, 4× tier boundaries (block units). Anything beyond
     * {@code farR} returns 0 → skip.
     */
    public static int tickStride(double squaredDistance, double closeR, double mediumR, double farR) {
        if (squaredDistance < closeR  * closeR)  return 1;
        if (squaredDistance < mediumR * mediumR) return 2;
        if (squaredDistance < farR    * farR)    return 4;
        return 0;
    }

    /**
     * Distance stride plus motion-based simplification. Useful for moving
     * entities whose exact pose is harder to perceive while travelling fast.
     *
     * @param squaredDistance distance to viewer in block units squared
     * @param velocityBlocksPerTick motion length in block/tick
     * @return regular distance stride, doubled when speed exceeds the default
     *         fast-motion threshold; 0 still means skip
     */
    public static int tickStrideWithMotion(double squaredDistance,
                                           double velocityBlocksPerTick) {
        return adjustStrideForMotion(tickStride(squaredDistance), velocityBlocksPerTick,
            DEFAULT_FAST_SPEED_BLOCKS_PER_SECOND);
    }

    /**
     * Applies a fast-motion multiplier to an already computed stride.
     * Minecraft ticks at 20 Hz, so {@code fastSpeedBlocksPerSecond=8}
     * corresponds to {@code 0.4} blocks/tick.
     */
    public static int adjustStrideForMotion(int stride, double velocityBlocksPerTick,
                                            double fastSpeedBlocksPerSecond) {
        if (stride <= 0) return 0;
        if (fastSpeedBlocksPerSecond <= 0.0d) return stride;
        double thresholdPerTick = fastSpeedBlocksPerSecond / 20.0d;
        if (Math.abs(velocityBlocksPerTick) < thresholdPerTick) return stride;
        int adjusted = stride << 1;
        return adjusted > 8 ? 8 : adjusted;
    }

    /**
     * Combines stride with a per-entity age counter to produce a
     * tick/skip decision. Caller increments age each call regardless
     * of the return so phase stays stable as the entity drifts between
     * tiers.
     *
     * @param stride from {@link #tickStride(double)}
     * @param age    monotonically-incrementing counter on the entity
     * @return true if the entity should advance its animation this tick
     */
    public static boolean shouldTick(int stride, int age) {
        if (stride <= 0) return false;
        if (stride == 1) return true;
        // {@link #tickStride} only ever returns powers of two (1, 2, 4),
        // so the bitwise-AND form is equivalent to the modulo. AND is
        // ~3× cheaper than int division-modulo on most CPUs and saves a
        // measurable slice when 11k+ BE ticks/sec hit this method on
        // the MEGA test.
        return (age & (stride - 1)) == 0;
    }
}
