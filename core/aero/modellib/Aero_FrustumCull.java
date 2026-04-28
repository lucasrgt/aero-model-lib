package aero.modellib;

/**
 * Cheap, dot-product-based behind-the-camera culler for animated entity
 * renderers.
 *
 * <p>Beta 1.7.3's block/entity dispatchers only check distance; they do
 * <em>not</em> consult the view frustum. Combined with our extended
 * render-distance LOD (which can push animated entities to 256 blocks),
 * this means up to half of the visible-from-distance entities are being
 * rendered <em>behind the player every frame</em>, paying full Tessellator
 * + GL dispatch cost for nothing.
 *
 * <p>This class fixes that with the cheapest possible heuristic:
 * one cached forward vector per frame, one dot product per render call.
 * No matrix math, no plane checks, no allocations.
 *
 * <p>Algorithm: at the top of each render frame the active renderer
 * publishes the camera's yaw/pitch via
 * {@link #updateCameraForward(float, float)}. Entity renderers then call
 * {@link #isLikelyVisible(double, double, double, double)} with the
 * BE's camera-relative offset. If the dot product against the cached
 * forward is below a small negative tolerance, the BE is judged to be
 * behind the camera and the renderer skips it.
 *
 * <p>The behind-tolerance defaults to 8 blocks — generous enough that
 * a player rotating 360° doesn't see entities pop out of existence at
 * the screen edge, and big enough to forgive larger model bounding
 * boxes (mega-multiblocks etc).
 *
 * <p>Toggle off with {@code -Daero.frustumcull=false} if you have a
 * pathological case where this culls too aggressively.
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
     * Half-angle of the cull cone in cos² form. cos²(60°) = 0.25 → 120°
     * total cone. MC's default FOV is 70°, so 120° gives ~25° margin per
     * side to absorb rotation lag without pop-in. Anything outside the
     * cone (dot²/distSq < this) gets culled, regardless of the simple
     * front/behind hemisphere test.
     */
    private static final double FOV_HALF_ANGLE_COS_SQ = 0.25d;

    /**
     * Below this squared-distance, never cull. Avoids pop on rotation for
     * BEs that are right next to the player and momentarily slip outside
     * the cone. 16² = 256 blocks².
     */
    private static final double CLOSE_RANGE_SQ = 256.0d;

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
        return dot * dot >= distSq * FOV_HALF_ANGLE_COS_SQ; // inside cone
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
