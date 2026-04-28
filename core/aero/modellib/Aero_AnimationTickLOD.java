package aero.modellib;

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

    /**
     * Recommended {@code animatedDistance} for the player's current view
     * distance setting. Beta MC has 4 tiers (Far / Normal / Short / Tiny);
     * this maps each to a scaled LOD radius so that the heavier the visible
     * world, the more aggressively far entities fall onto the at-rest path.
     *
     * <p>Rationale: at Far render distance the player can see 256 blocks of
     * world, which means many more entities are visible at once — animating
     * them all is overkill since the player can't perceive the animation at
     * that distance anyway. Scaling LOD inversely with visible chunk count
     * keeps the per-frame animation budget roughly constant.
     *
     * @param viewDistance vanilla MC's renderDistance enum
     *        (0=Far, 1=Normal, 2=Short, 3=Tiny — see
     *        {@link Aero_RenderDistanceCulling#VIEW_DISTANCE_FAR} et al)
     */
    public static double recommendedAnimatedDistance(int viewDistance) {
        switch (viewDistance) {
            case Aero_RenderDistanceCulling.VIEW_DISTANCE_FAR:    return 64.0d;  // most aggressive: 1/4 of visible
            case Aero_RenderDistanceCulling.VIEW_DISTANCE_NORMAL: return 80.0d;  // ~5/8 of visible
            case Aero_RenderDistanceCulling.VIEW_DISTANCE_SHORT:  return 64.0d;  // matches visible (4 chunks = 64 blocks)
            case Aero_RenderDistanceCulling.VIEW_DISTANCE_TINY:   return 32.0d;  // matches visible (2 chunks = 32 blocks)
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
        return (age % stride) == 0;
    }
}
