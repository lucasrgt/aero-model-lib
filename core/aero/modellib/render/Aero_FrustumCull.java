package aero.modellib.render;

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

    /**
     * Tighter sphere-vs-view cull for BlockEntities. The legacy cone below is
     * deliberately broad; this pass is used before BE dispatch/batching where
     * keeping off-screen machines alive is expensive.
     */
    public static final boolean BE_VIEW_CULL_ENABLED =
        !"false".equalsIgnoreCase(System.getProperty("aero.beViewCull"));

    private static final double BE_VIEW_MARGIN_DEGREES =
        doubleProperty("aero.beViewCull.marginDeg", 32.0d, 0.0d, 60.0d);
    private static final double BE_VIEW_RADIUS_PADDING =
        doubleProperty("aero.beViewCull.radiusPad", 3.0d, 0.0d, 32.0d);
    private static final double BE_VIEW_NEAR_PADDING =
        doubleProperty("aero.beViewCull.nearPad", 3.0d, 0.0d, 32.0d);
    private static final double BE_VIEW_FAST_TURN_DEGREES =
        doubleProperty("aero.beViewCull.fastTurnDeg", 8.0d, 0.0d, 180.0d);
    private static final int BE_VIEW_FAST_TURN_HOLD_FRAMES =
        intProperty("aero.beViewCull.fastTurnHoldFrames", 6, 0, 60);
    private static final int BE_VIEW_HISTORY_FRAMES =
        intProperty("aero.beViewCull.historyFrames", 6, 0, 16);

    private static double fwdX = 0.0d;
    private static double fwdY = 0.0d;
    private static double fwdZ = 1.0d;
    private static double rightX = 1.0d;
    private static double rightY = 0.0d;
    private static double rightZ = 0.0d;
    private static double upX = 0.0d;
    private static double upY = 1.0d;
    private static double upZ = 0.0d;
    private static double prevFwdX = 0.0d;
    private static double prevFwdY = 0.0d;
    private static double prevFwdZ = 1.0d;
    private static double prevRightX = 1.0d;
    private static double prevRightY = 0.0d;
    private static double prevRightZ = 0.0d;
    private static double prevUpX = 0.0d;
    private static double prevUpY = 1.0d;
    private static double prevUpZ = 0.0d;
    private static final double[] histFwdX = new double[16];
    private static final double[] histFwdY = new double[16];
    private static final double[] histFwdZ = new double[16];
    private static final double[] histRightX = new double[16];
    private static final double[] histRightY = new double[16];
    private static final double[] histRightZ = new double[16];
    private static final double[] histUpX = new double[16];
    private static final double[] histUpY = new double[16];
    private static final double[] histUpZ = new double[16];
    private static boolean cameraValid = false;
    private static boolean previousCameraValid = false;
    private static int historyCursor = 0;
    private static int historyCount = 0;
    private static int lastYawBits = 0;
    private static int lastPitchBits = 0;
    private static int beViewCulledThisFrame = 0;
    private static int beViewHistoryAcceptedThisFrame = 0;
    private static int beViewFastTurnHoldFrames = 0;

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
    private static double viewTanHalfHorizontal = Math.tan(Math.toRadians(60.0d));
    private static double viewTanHalfVertical = Math.tan(Math.toRadians(43.0d));

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
        if (cameraValid && BE_VIEW_FAST_TURN_HOLD_FRAMES > 0
            && angularDeltaDegrees(yawDegrees, pitchDegrees) >= BE_VIEW_FAST_TURN_DEGREES) {
            beViewFastTurnHoldFrames = BE_VIEW_FAST_TURN_HOLD_FRAMES;
        }
        previousCameraValid = cameraValid;
        if (cameraValid) {
            rememberCurrentCamera();
            prevFwdX = fwdX;
            prevFwdY = fwdY;
            prevFwdZ = fwdZ;
            prevRightX = rightX;
            prevRightY = rightY;
            prevRightZ = rightZ;
            prevUpX = upX;
            prevUpY = upY;
            prevUpZ = upZ;
        }
        double y = Math.toRadians(yawDegrees);
        double p = Math.toRadians(pitchDegrees);
        double cosP = Math.cos(p);
        fwdX = -Math.sin(y) * cosP;
        fwdY = -Math.sin(p);
        fwdZ =  Math.cos(y) * cosP;
        // Camera-space basis. For yaw=0, forward is +Z and right is +X.
        rightX = Math.cos(y);
        rightY = 0.0d;
        rightZ = Math.sin(y);
        upX = fwdY * rightZ - fwdZ * rightY;
        upY = fwdZ * rightX - fwdX * rightZ;
        upZ = fwdX * rightY - fwdY * rightX;
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
        previousCameraValid = false;
        historyCursor = 0;
        historyCount = 0;
        beViewFastTurnHoldFrames = 0;
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

    /**
     * Updates the projection half-angles used by the stricter BE cull.
     * Values should be raw view half-angles; this method applies the
     * configurable safety margin.
     */
    public static void setViewportHalfAnglesDegrees(double horizontalHalfDegrees,
                                                    double verticalHalfDegrees) {
        double h = clampHalfAngle(horizontalHalfDegrees + BE_VIEW_MARGIN_DEGREES);
        double v = clampHalfAngle(verticalHalfDegrees + BE_VIEW_MARGIN_DEGREES);
        viewTanHalfHorizontal = Math.tan(Math.toRadians(h));
        viewTanHalfVertical = Math.tan(Math.toRadians(v));
    }

    /** Reverts the cone to its compile-time default (covers ultrawide + Quake-Pro FOV). */
    public static void resetCone() {
        coneCosHalfAngleSq = DEFAULT_CONE_COS_HALF_ANGLE_SQ;
    }

    public static void beginFrameCounters() {
        beViewCulledThisFrame = 0;
        beViewHistoryAcceptedThisFrame = 0;
        if (beViewFastTurnHoldFrames > 0) beViewFastTurnHoldFrames--;
    }

    public static int beViewCulledThisFrame() {
        return beViewCulledThisFrame;
    }

    public static int beViewFastTurnHoldFrames() {
        return beViewFastTurnHoldFrames;
    }

    public static int beViewHistoryAcceptedThisFrame() {
        return beViewHistoryAcceptedThisFrame;
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

    /**
     * Sphere-vs-view test for BlockEntities. Unlike the legacy cone path, this
     * does not bypass all close-range objects; a nearby machine that is fully
     * outside the screen can still be rejected, while a large/near object whose
     * bounds overlap the view remains visible.
     */
    public static boolean isBlockEntityViewVisible(double dx, double dy, double dz,
                                                   double visualRadiusBlocks) {
        if (!ENABLED || !BE_VIEW_CULL_ENABLED) return true;
        if (!cameraValid) return true;
        if (beViewFastTurnHoldFrames > 0) return true;
        double radius = visualRadiusBlocks > 0.0d ? visualRadiusBlocks : 0.5d;
        radius += BE_VIEW_RADIUS_PADDING;

        if (isSphereInsideView(dx, dy, dz, radius,
                fwdX, fwdY, fwdZ,
                rightX, rightY, rightZ,
                upX, upY, upZ)) {
            return true;
        }
        // One-frame camera hysteresis. Fast mouse swipes can otherwise make a
        // dense tower enter/leave the BE queue by hundreds of instances per
        // frame, which is exactly the rhythmic driver stall users perceive as
        // a spike.
        if (previousCameraValid
            && isSphereInsideView(dx, dy, dz, radius,
                prevFwdX, prevFwdY, prevFwdZ,
                prevRightX, prevRightY, prevRightZ,
                prevUpX, prevUpY, prevUpZ)) {
            beViewHistoryAcceptedThisFrame++;
            return true;
        }
        if (isSphereInsideCameraHistory(dx, dy, dz, radius)) return true;
        beViewCulledThisFrame++;
        return false;
    }

    private static void rememberCurrentCamera() {
        if (BE_VIEW_HISTORY_FRAMES <= 0) return;
        int slot = historyCursor;
        histFwdX[slot] = fwdX;
        histFwdY[slot] = fwdY;
        histFwdZ[slot] = fwdZ;
        histRightX[slot] = rightX;
        histRightY[slot] = rightY;
        histRightZ[slot] = rightZ;
        histUpX[slot] = upX;
        histUpY[slot] = upY;
        histUpZ[slot] = upZ;
        historyCursor = (historyCursor + 1) % BE_VIEW_HISTORY_FRAMES;
        if (historyCount < BE_VIEW_HISTORY_FRAMES) historyCount++;
    }

    private static boolean isSphereInsideCameraHistory(double dx, double dy, double dz,
                                                       double radius) {
        int count = Math.min(historyCount, BE_VIEW_HISTORY_FRAMES);
        for (int i = 0; i < count; i++) {
            if (isSphereInsideView(dx, dy, dz, radius,
                    histFwdX[i], histFwdY[i], histFwdZ[i],
                    histRightX[i], histRightY[i], histRightZ[i],
                    histUpX[i], histUpY[i], histUpZ[i])) {
                beViewHistoryAcceptedThisFrame++;
                return true;
            }
        }
        return false;
    }

    private static boolean isSphereInsideView(double dx, double dy, double dz,
                                              double radius,
                                              double fx, double fy, double fz,
                                              double rx, double ry, double rz,
                                              double ux, double uy, double uz) {
        double viewZ = dx * fx + dy * fy + dz * fz;
        double viewX = dx * rx + dy * ry + dz * rz;
        double viewY = dx * ux + dy * uy + dz * uz;

        if (viewZ < -radius - BE_VIEW_NEAR_PADDING) return false;
        if (viewZ <= 0.0d) {
            double allowance = radius + BE_VIEW_NEAR_PADDING;
            return Math.abs(viewX) <= allowance && Math.abs(viewY) <= allowance;
        }

        double maxX = viewZ * viewTanHalfHorizontal + radius + BE_VIEW_NEAR_PADDING;
        if (Math.abs(viewX) > maxX) return false;

        double maxY = viewZ * viewTanHalfVertical + radius + BE_VIEW_NEAR_PADDING;
        return Math.abs(viewY) <= maxY;
    }

    private static double clampHalfAngle(double degrees) {
        if (degrees < 1.0d) return 1.0d;
        if (degrees > 89.0d) return 89.0d;
        return degrees;
    }

    private static double angularDeltaDegrees(float yawDegrees, float pitchDegrees) {
        float previousYaw = Float.intBitsToFloat(lastYawBits);
        float previousPitch = Float.intBitsToFloat(lastPitchBits);
        double yawDelta = wrapDegrees(yawDegrees - previousYaw);
        double pitchDelta = pitchDegrees - previousPitch;
        return Math.sqrt(yawDelta * yawDelta + pitchDelta * pitchDelta);
    }

    private static double wrapDegrees(double degrees) {
        while (degrees >= 180.0d) degrees -= 360.0d;
        while (degrees < -180.0d) degrees += 360.0d;
        return degrees;
    }

    private static double doubleProperty(String name, double fallback,
                                         double min, double max) {
        String raw = System.getProperty(name);
        if (raw == null) return fallback;
        try {
            double value = Double.parseDouble(raw.trim());
            if (Double.isNaN(value) || Double.isInfinite(value)) return fallback;
            if (value < min) return min;
            if (value > max) return max;
            return value;
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private static int intProperty(String name, int fallback, int min, int max) {
        String raw = System.getProperty(name);
        if (raw == null) return fallback;
        try {
            int value = Integer.parseInt(raw.trim());
            if (value < min) return min;
            if (value > max) return max;
            return value;
        } catch (NumberFormatException e) {
            return fallback;
        }
    }
}
