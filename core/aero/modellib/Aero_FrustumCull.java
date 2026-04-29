package aero.modellib;

/**
 * Cheap behind-the-camera / off-axis culling heuristic for animated
 * entity renderers.
 *
 * <p><strong>This is not a real view-frustum check.</strong> It is a
 * single forward-vector cone test: one cached camera forward per frame,
 * one dot product per render call. It will <em>not</em> reject entities
 * outside the projection's near/far planes, and it does <em>not</em>
 * tighten as the cone passes the screen corners. It is intentionally
 * looser than a 6-plane frustum so that screen-edge entities never
 * false-cull on wide aspects (ultrawide, HiDPI, FOV bobbing/zoom).
 *
 * <p><strong>Why a cone instead of real planes?</strong> Beta 1.7.3's
 * block-entity dispatcher does no frustum cull, so up to half the
 * far-LOD animated entities every frame are rendered behind the
 * player. The cone catches that bulk case for one dot product. Real
 * frustum-plane culling requires extracting the projection × view
 * matrix on every frame and is not worth the complexity for what it
 * adds on top of distance + behind-camera rejection.
 *
 * <p>The cone's half-angle is set per-frame from MC's window aspect
 * ratio (Beta hardcodes vertical FOV at 70°): horizontal half-FOV +
 * 20° safety margin, floored at 75°. These values are <em>frozen</em>
 * — do not micro-tune them per resolution. If a user reports
 * false-culling, the answer is to disable the whole pass with
 * {@code -Daero.frustumcull=false}, not to widen the cone further.
 *
 * <p>Below {@link #CLOSE_RANGE_SQ} the cone is bypassed entirely so
 * adjacent entities don't pop on rotation. The behind-tolerance
 * widens that close-range pass for large multiblocks whose origin can
 * cross the camera plane while their mesh is still on screen.
 */
public final class Aero_FrustumCull {

    /**
     * Master toggle — set with {@code -Daero.frustumcull=false} to
     * disable. Default true.
     */
    public static final boolean ENABLED =
        !"false".equalsIgnoreCase(System.getProperty("aero.frustumcull"));

    public static final double DEFAULT_BEHIND_TOLERANCE = 8.0d;

    private static double fwdX = 0.0d;
    private static double fwdY = 0.0d;
    private static double fwdZ = 1.0d;
    private static boolean cameraValid = false;
    private static int lastYawBits = 0;
    private static int lastPitchBits = 0;

    /**
     * Cosine² of the cone's half-angle. Updated by
     * {@link #setConeHalfAngleDegrees(double)} from the platform's
     * per-frame aspect-ratio computation (see Aero_RenderDistance).
     * The default 80° half-angle is intentionally generous so the
     * cone reduces to "behind camera + far off-axis" rejection on any
     * sane viewport. The cone is approximate by design; do not tune
     * it tighter — the savings over the current setting are not worth
     * the false-cull risk.
     */
    private static double coneCosHalfAngleSq = 0.030d; // cos²(80°)
    private static final double DEFAULT_CONE_COS_HALF_ANGLE_SQ = 0.030d;

    private Aero_FrustumCull() {}

    /**
     * Updates the cached camera forward vector. Call once per render
     * frame from the platform-specific render hook (typically the first
     * BlockEntity render of the frame). Pure trig — about 50 ns on
     * a modern CPU.
     *
     * <p>Convention follows Minecraft: yaw rotates around +Y; pitch
     * tilts down at positive values. Angles in degrees.
     */
    public static void updateCameraForward(float yawDegrees, float pitchDegrees) {
        int yawBits = Float.floatToIntBits(yawDegrees);
        int pitchBits = Float.floatToIntBits(pitchDegrees);
        if (cameraValid && yawBits == lastYawBits && pitchBits == lastPitchBits) return;
        double y = Math.toRadians(yawDegrees);
        double p = Math.toRadians(pitchDegrees);
        double cosP = Math.cos(p);
        fwdX = -Math.sin(y) * cosP;
        fwdY = -Math.sin(p);
        fwdZ =  Math.cos(y) * cosP;
        cameraValid = true;
        lastYawBits = yawBits;
        lastPitchBits = pitchBits;
    }

    /**
     * Clears the cached camera. Until the next successful update,
     * {@link #isLikelyVisible(double, double, double, double)} returns true.
     */
    public static void clearCamera() {
        cameraValid = false;
    }

    /**
     * Returns true if a point at camera-relative offset
     * {@code (dx, dy, dz)} is likely visible — that is, in front of the
     * camera or no more than {@code behindTolerance} blocks behind.
     *
     * <p>Falls back to {@code true} (no cull) when the master toggle is
     * off, so renderer code can call this unconditionally without an
     * extra branch outside.
     */
    /**
     * Below this squared-distance, never cull. Avoids pop on rotation for
     * BEs that are right next to the player and momentarily slip outside
     * the cone. 16² = 256 blocks².
     */
    private static final double CLOSE_RANGE_SQ = 256.0d;

    /**
     * Updates the cone's half-angle (in degrees). Called by the
     * platform-specific render hook each frame from the window aspect
     * ratio (Beta has no FOV setting — vertical FOV is hardcoded at
     * 70°). Stored as cos² so {@link #isLikelyVisible} can compare
     * without sqrt or acos.
     *
     * <p>Argument is the FULL half-angle the cone should cull at,
     * including any safety margin the platform code applies. The
     * current platform code adds a fixed 20° margin and floors at
     * 75°; those values are frozen — see class javadoc.
     */
    public static void setConeHalfAngleDegrees(double halfAngleDegrees) {
        if (halfAngleDegrees <= 0d || halfAngleDegrees >= 90d) {
            coneCosHalfAngleSq = DEFAULT_CONE_COS_HALF_ANGLE_SQ;
            return;
        }
        double cosVal = Math.cos(Math.toRadians(halfAngleDegrees));
        coneCosHalfAngleSq = cosVal * cosVal;
    }

    /** Reverts the cone to its compile-time default (covers ultrawide + Quake-Pro FOV). */
    public static void resetCone() {
        coneCosHalfAngleSq = DEFAULT_CONE_COS_HALF_ANGLE_SQ;
    }

    public static boolean isLikelyVisible(double dx, double dy, double dz,
                                          double behindTolerance) {
        if (!ENABLED) return true;
        if (!cameraValid) return true;
        double distSq = dx * dx + dy * dy + dz * dz;
        // Close-range pass: small radius around camera always renders.
        // behindTolerance widens this radius for big multiblocks so their
        // origin can drift slightly behind the camera while their mesh is
        // still on screen.
        double closeSq = CLOSE_RANGE_SQ + behindTolerance * behindTolerance;
        if (distSq < closeSq) return true;
        double dot = dx * fwdX + dy * fwdY + dz * fwdZ;
        if (dot <= 0) return false;                  // behind camera
        return dot * dot >= distSq * coneCosHalfAngleSq; // inside cone
    }

    /** {@link #DEFAULT_BEHIND_TOLERANCE}-block tolerance overload. */
    public static boolean isLikelyVisible(double dx, double dy, double dz) {
        return isLikelyVisible(dx, dy, dz, DEFAULT_BEHIND_TOLERANCE);
    }

    /**
     * Visibility check for a model/block/entity with an approximate radius.
     * The radius is added to the behind-camera tolerance so large machines
     * do not pop when their origin crosses the camera plane before the mesh
     * itself has fully left the screen.
     */
    public static boolean isLikelyVisible(double dx, double dy, double dz,
                                          double visualRadiusBlocks,
                                          double extraBehindTolerance) {
        if (visualRadiusBlocks < 0.0d) visualRadiusBlocks = 0.0d;
        if (extraBehindTolerance < 0.0d) extraBehindTolerance = 0.0d;
        return isLikelyVisible(dx, dy, dz, visualRadiusBlocks + extraBehindTolerance);
    }

    public static boolean isLikelyVisibleWithRadius(double dx, double dy, double dz,
                                                    double visualRadiusBlocks) {
        return isLikelyVisible(dx, dy, dz, visualRadiusBlocks,
            DEFAULT_BEHIND_TOLERANCE);
    }
}
