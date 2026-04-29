package aero.modellib;

import net.minecraft.client.render.Frustum;
import net.minecraft.client.render.FrustumData;

/**
 * Real 6-plane frustum culling backed by Beta 1.7.3's vanilla
 * {@link Frustum} / {@link FrustumData}. Replaces (or augments) the cone
 * heuristic in {@link Aero_FrustumCull} for tight, projection-correct
 * culling — particularly in narrow-FOV / wide-aspect cases where the cone
 * over-includes screen-edge entities.
 *
 * <h2>How it works</h2>
 * <p>Vanilla's {@code WorldRenderer.cullChunks} populates
 * {@code Frustum.getInstance()} from the active GL projection × modelview
 * matrices each frame, then tests every chunk's bounding box against it.
 * The {@link FrustumData#intersects(double, double, double, double, double, double) intersects}
 * method does the 6-plane AABB test directly and is what we want for our
 * own block-entity culling — the same test, against the same data, that
 * vanilla already maintains.
 *
 * <p>We reach for {@code Frustum.getInstance()} on the FIRST visibility
 * query each frame (after vanilla has already computed planes during chunk
 * culling), cache the {@link FrustumData} reference, and reuse it for every
 * subsequent query that frame. Resetting the cache happens in
 * {@link Aero_RenderDistance#beginRenderFrame()}.
 *
 * <h2>When this beats the cone</h2>
 * <ul>
 *   <li><strong>Narrow FOV / zoom (Optifine zoom, spyglass-style mods):</strong>
 *       horizontal half-angle drops below the cone's 75° floor, so
 *       Aero_FrustumCull lets too many entities through. 6-plane cull
 *       follows the actual frustum.</li>
 *   <li><strong>Screen-edge entities at cone fringe:</strong> the cone
 *       pads behind-camera tolerance with a generous radius; the real
 *       frustum has a tighter near-plane.</li>
 * </ul>
 *
 * <h2>When the cone still wins</h2>
 * <p>The cone's {@code DEFAULT_BEHIND_TOLERANCE} ({@value Aero_FrustumCull#DEFAULT_BEHIND_TOLERANCE}-block
 * forgiveness behind the camera) is generous on purpose so large multiblock
 * origins don't pop when the origin crosses the camera plane while geometry
 * is still on-screen. The 6-plane test is strict — anything past the near
 * plane is gone. We compose them: if either says "visible", we render.
 *
 * <h2>Toggle</h2>
 * <p>Disable the entire 6-plane path with {@code -Daero.frustum6=false}.
 * Falls back to the cone alone — useful when chasing a regression to a
 * specific cull layer, not as a normal-operation default.
 */
public final class Aero_Frustum6Plane {

    /**
     * Default <strong>OFF</strong> in v3.0. The lazy {@code Frustum.getInstance()}
     * fetch from inside {@code distanceFrom} appears to read stale or
     * uninitialized plane data in the current Beta 1.7.3 yarn mapping —
     * over-culls visible BEs in the stress test. Opt in with
     * {@code -Daero.frustum6=true} for experimentation. Will be promoted
     * to default-on in a follow-up release once the capture point is
     * fixed (likely a Mixin into {@code WorldRenderer.cullChunks} TAIL
     * to grab a known-good {@code FrustumData} reference per frame
     * instead of fetching lazily).
     */
    public static final boolean ENABLED =
        "true".equalsIgnoreCase(System.getProperty("aero.frustum6"));

    private static FrustumData CACHED;
    private static boolean CACHED_THIS_FRAME;

    private Aero_Frustum6Plane() {}

    /**
     * Resets the per-frame cache. Called from
     * {@link Aero_RenderDistance#beginRenderFrame()} so the first query
     * each frame fetches the freshly-populated {@link FrustumData}.
     */
    static void invalidateFrame() {
        CACHED = null;
        CACHED_THIS_FRAME = false;
    }

    /**
     * 6-plane AABB visibility test in WORLD coordinates. Returns true if
     * any part of the box intersects the view frustum.
     *
     * <p>Returns true (no cull) on disabled path, on first-frame init
     * (vanilla hasn't computed planes yet), or if vanilla's frustum API
     * throws. Errs toward over-rendering rather than false-cull pop.
     */
    public static boolean isVisibleAABB(double minX, double minY, double minZ,
                                        double maxX, double maxY, double maxZ) {
        if (!ENABLED) return true;
        FrustumData fd = ensureCachedFrustum();
        if (fd == null) return true;
        try {
            return fd.intersects(minX, minY, minZ, maxX, maxY, maxZ);
        } catch (Throwable t) {
            return true;
        }
    }

    /**
     * Sphere visibility test — wraps the AABB call with a (center ± radius)
     * box. Faster than building a tight bounding sphere/plane test and
     * still tighter than the cone for typical model sizes.
     */
    public static boolean isVisibleSphere(double cx, double cy, double cz, double radius) {
        return isVisibleAABB(cx - radius, cy - radius, cz - radius,
                             cx + radius, cy + radius, cz + radius);
    }

    private static FrustumData ensureCachedFrustum() {
        if (CACHED_THIS_FRAME) return CACHED;
        try {
            CACHED = Frustum.getInstance();
        } catch (Throwable t) {
            CACHED = null;
        }
        CACHED_THIS_FRAME = true;
        return CACHED;
    }
}
