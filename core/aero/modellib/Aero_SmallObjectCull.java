package aero.modellib;

/**
 * Small-object culling — skips rendering a BE / entity whose projected
 * size on screen is below a small pixel threshold (default 2 px). Below
 * that, the geometry contributes at most one or two anti-aliased pixels
 * that the player cannot resolve at typical movement speeds.
 *
 * <h2>Why this is different from the other culls</h2>
 * <p>Cone, frustum, occlusion, and chunk-visibility are all heuristics
 * that can false-positive (cut something the player can clearly see).
 * Small-object culling is geometric: if the projected diameter in pixels
 * is below the threshold, it is mathematically below the player's
 * resolving power, period. No "screen-edge case" or "sand dune in the
 * raycast" failure mode.
 *
 * <h2>Math</h2>
 * <p>Beta 1.7.3 hardcodes vertical FOV at 70°, so the focal length in
 * pixels is {@code focal = displayHeight / (2 * tan(35°))}. A sphere of
 * radius {@code r} blocks at distance {@code d} blocks projects to a
 * diameter of {@code 2 * r * focal / d} pixels. Below the threshold →
 * cull.
 *
 * <p>Rearranged to avoid the sqrt for the per-BE check:
 * {@code distSq > (2 * focal / threshold)² * r²}. The expensive part
 * ({@code coeff = (2 * focal / threshold)²}) is computed once per
 * display-resize via {@link #updateFromDisplayHeight(int)}; the per-BE
 * cost is one multiply + one compare.
 *
 * <h2>Typical reach</h2>
 * <p>For a 1080p viewport (focal ≈ 770 px) and the default 2 px threshold:
 * <ul>
 *   <li>{@code r=0.5} (a single-block-sized BE) → cull above ~84 blocks</li>
 *   <li>{@code r=2.0} (a 4-block mega-model) → cull above ~336 blocks</li>
 * </ul>
 * Most scenes, this cull never fires (the distance / view-distance check
 * already rejects far BEs first). Where it does fire is the long-tail
 * case of dense small detail BEs at the visual horizon.
 *
 * <h2>Toggle</h2>
 * <ul>
 *   <li>{@code -Daero.smallobj=false} disables.</li>
 *   <li>{@code -Daero.smallobj.px=N} sets the pixel threshold (default 2).</li>
 * </ul>
 */
public final class Aero_SmallObjectCull {

    public static final boolean ENABLED =
        !"false".equalsIgnoreCase(System.getProperty("aero.smallobj"));

    private static final double THRESHOLD_PX;
    static {
        double parsed = 2.0d;
        String s = System.getProperty("aero.smallobj.px");
        if (s != null) {
            try { parsed = Double.parseDouble(s.trim()); }
            catch (NumberFormatException e) { /* keep default */ }
        }
        if (parsed <= 0.0d || parsed > 100.0d) parsed = 2.0d;
        THRESHOLD_PX = parsed;
    }

    // Pre-computed half-VFOV tangent. Beta hardcodes vfov=70°, so half=35°.
    private static final double TAN_HALF_VFOV = Math.tan(Math.toRadians(35.0d));

    /**
     * Cached {@code (2 * focal / threshold)²}. The per-BE check becomes
     * {@code distSq > coeff * r²}. Updated by
     * {@link #updateFromDisplayHeight(int)} when the display height
     * changes — typically once at game start, again on F11 / resize.
     */
    private static double coeff = 0.0d;
    private static int cachedDisplayHeight = -1;

    private Aero_SmallObjectCull() {}

    /**
     * Recomputes the cached coefficient when the display height changes.
     * No-op when {@link #ENABLED} is false, the height is non-positive,
     * or the height matches the cached value.
     *
     * <p>Caller responsibility: invoke once per frame (or once per
     * resize). Cheap when the height is stable — the early-out hits.
     */
    public static void updateFromDisplayHeight(int displayHeight) {
        if (!ENABLED || displayHeight <= 0) return;
        if (displayHeight == cachedDisplayHeight) return;
        double focal = displayHeight / (2.0d * TAN_HALF_VFOV);
        double scalar = 2.0d * focal / THRESHOLD_PX;
        coeff = scalar * scalar;
        cachedDisplayHeight = displayHeight;
    }

    /**
     * Returns true if a BE with bounding radius {@code visualRadiusBlocks}
     * at squared distance {@code distSq} projects to fewer than
     * {@link #THRESHOLD_PX} pixels of diameter — i.e. is below the
     * player's resolving power and not worth rendering.
     *
     * <p>Returns {@code false} (no cull) when disabled, when display
     * height hasn't been set yet (pre-init), or when the radius is
     * zero / negative (safety — never cull a "size unspecified" BE).
     */
    public static boolean isTooSmall(double distSq, double visualRadiusBlocks) {
        if (!ENABLED || coeff <= 0.0d || visualRadiusBlocks <= 0.0d) return false;
        return distSq > coeff * visualRadiusBlocks * visualRadiusBlocks;
    }
}
