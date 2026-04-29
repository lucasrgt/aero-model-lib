package aero.modellib;

import net.minecraft.block.Block;
import net.minecraft.world.BlockView;

/**
 * Coarse occlusion culling for block entities — the answer to "the player
 * spawned underground but the surface motors are still rendering". Vanilla
 * Beta 1.7.3 has no occlusion query in software-friendly form; the cone
 * cull and even the 6-plane frustum say "yes, in front of camera, render
 * it" for an entity that's behind 8 blocks of opaque dirt.
 *
 * <h2>Algorithm</h2>
 * <p>Walk a voxel-stepping line from camera position to the BE center
 * with 3D DDA. Each visited voxel looks up the block id at that
 * world coord and consults {@link Block#BLOCKS_OPAQUE} (boolean[]) for an
 * O(1) opaque test — no Block instance dereference, no null check. Count
 * the number of opaque blocks the line crosses. If the count reaches
 * {@link #OCCLUSION_THRESHOLD}, the entity is treated as occluded and
 * skipped.
 *
 * <h2>History</h2>
 * <p>v3.x added a vertical-only DDA sample for tower scenarios. It was
 * removed when real-world testing showed it cut visible top floors of
 * towers built with thin slab roofs — the camera→BE oblique line went
 * through the open side (correctly air), but the vertical sample tripped
 * on the floors stacked between camera Y and BE Y. The "looking up at
 * tower" geometry inherently crosses N floors and the heuristic could
 * not distinguish "tall tower I can see laterally" from "BE buried under
 * solid terrain". Single-line oblique check kept; the underground spawn
 * case is still captured because the dirt above the camera shows up on
 * any direction sample.
 *
 * <h2>Cost</h2>
 * <p>Per uncached BE check: roughly one block lookup per voxel crossed,
 * returning early once {@link #OCCLUSION_THRESHOLD} opaque blocks are
 * found. Compare to the cost of rendering an occluded BE (matrix push +
 * tess draws + matrix pop ≈ 50-150 µs). Easily wins the trade.
 *
 * <h2>Tuning</h2>
 * <ul>
 *   <li>{@link #OCCLUSION_THRESHOLD} = {@value #OCCLUSION_THRESHOLD} opaque
 *       crossings. A few thin walls between camera and BE = render anyway
 *       (might be a window, glass corridor, etc.). Threshold+ crossings =
 *       very likely behind solid terrain.</li>
 *   <li>{@link #MIN_DISTANCE_BLOCKS_SQ} = {@value #MIN_DISTANCE_BLOCKS_SQ}
 *       — within this radius (squared), never cull. Avoids pop on entities
 *       inside the room with the player when a wall samples opaque due to
 *       the line passing through the floor.</li>
 * </ul>
 *
 * <h2>False positives / negatives</h2>
 * <ul>
 *   <li><strong>False cull</strong>: a window / glass corridor with thick
 *       walls on the side — line crosses enough opaque blocks but
 *       visibility is real. Mitigated by the threshold (not 1+).</li>
 *   <li><strong>False render</strong>: a tunnel that turns 90° and the
 *       BE is around the corner. Sampled line passes through air the
 *       whole way — line ≠ visibility. The fix is real ray-marched
 *       occlusion which costs orders of magnitude more.</li>
 * </ul>
 *
 * <h2>Toggle</h2>
 * <p>{@code -Daero.occlusioncull=false} disables. On by default — the
 * stress-test scenarios (underground spawn, deep mines) lose so much FPS
 * to over-rendering that the trade-off is worth a default-on heuristic.
 *
 * @see Aero_FrustumCull cone heuristic
 * @see Aero_Frustum6Plane real frustum (vanilla)
 */
public final class Aero_OcclusionCull {

    /**
     * <strong>Default ON</strong> in v3.x. The per-BE cache uses
     * asymmetric hysteresis, so short camera-Y jumps do not flip the result
     * visible/hidden every few frames. Opt out with
     * {@code -Daero.occlusioncull=false}.
     */
    public static final boolean ENABLED =
        !"false".equalsIgnoreCase(System.getProperty("aero.occlusioncull"));

    /**
     * Number of opaque crossings to declare "occluded". 4 is the value the
     * 3.0.0 cull stack shipped with — kept after the vertical-sample
     * regression (3.x.y) was removed because the oblique line alone is the
     * less-aggressive of the two checks and tower regressions came from the
     * vertical leg, not the oblique leg.
     */
    private static final int OCCLUSION_THRESHOLD = 4;

    /**
     * Squared block radius below which occlusion check is bypassed. 8² = 64.
     * Close-range BEs always render — avoids pop-in/out flicker when the
     * player jumps near a block-blocked motor and the raycast result
     * flickers between "occluded" and "visible" on alternating frames.
     */
    private static final double MIN_DISTANCE_BLOCKS_SQ = 64.0;

    private Aero_OcclusionCull() {}

    /**
     * Returns true if the line from camera to BE center crosses
     * {@link #OCCLUSION_THRESHOLD} or more opaque blocks. Caller should
     * skip rendering this BE when this method returns true.
     *
     * <p>Coordinates are camera-relative (the {@code dx, dy, dz} that
     * {@code BlockEntityRenderer.render} receives), and the BE absolute
     * world coords (used to cast the world-space ray). The world ref
     * provides the block lookup.
     *
     * @param world  the world to query for block ids (typically
     *               {@code be.world})
     * @param dx     camera-relative X (BE - camera)
     * @param dy     camera-relative Y
     * @param dz     camera-relative Z
     * @param beX    BE world X (block)
     * @param beY    BE world Y (block)
     * @param beZ    BE world Z (block)
     * @return       true if probably occluded; false if likely visible
     */
    public static boolean isOccluded(BlockView world,
                                     double dx, double dy, double dz,
                                     int beX, int beY, int beZ) {
        if (!ENABLED || world == null) return false;
        double distSq = dx * dx + dy * dy + dz * dz;
        if (distSq < MIN_DISTANCE_BLOCKS_SQ) return false;

        // Camera world coord = BE world coord - (BE - camera).
        // The "+ 0.5" centers the camera within the camera block, but for
        // sampling along a line that's symmetric we don't need it.
        double camX = (beX + 0.5) - dx;
        double camY = (beY + 0.5) - dy;
        double camZ = (beZ + 0.5) - dz;

        return rayCountsOpaque(world, camX, camY, camZ, beX, beY, beZ)
            >= OCCLUSION_THRESHOLD;
    }

    private static int rayCountsOpaque(BlockView world,
                                       double fromX, double fromY, double fromZ,
                                       int toBlockX, int toBlockY, int toBlockZ) {
        // 3D DDA traversal: visit every integer block the line crosses,
        // not just N evenly-spaced samples. The fixed-sample approach
        // missed thin walls (small caves with open ceilings let close BEs
        // through because the line happened to step through air pockets);
        // DDA is exhaustive — every block on the line is checked.
        //
        // The cost is bounded: line length L blocks → L+1 samples max
        // (vs the previous fixed 8). For the typical 5-30 block BE
        // distance this is 5-30 lookups vs 8 — slightly more work,
        // but each lookup is a Block.BLOCKS_OPAQUE[id] array index
        // (~5 ns), and the branch returns early once OCCLUSION_THRESHOLD
        // crossings are reached.
        boolean[] opaque = Block.BLOCKS_OPAQUE;
        int registrySize = opaque.length;
        int crossings = 0;

        double toX = toBlockX + 0.5d;
        double toY = toBlockY + 0.5d;
        double toZ = toBlockZ + 0.5d;
        double dirX = toX - fromX;
        double dirY = toY - fromY;
        double dirZ = toZ - fromZ;

        // Starting block and ending block (BE).
        int x0 = floorD(fromX);
        int y0 = floorD(fromY);
        int z0 = floorD(fromZ);
        int x1 = toBlockX;
        int y1 = toBlockY;
        int z1 = toBlockZ;

        // Direction sign per axis.
        int sx = x1 > x0 ? 1 : (x1 < x0 ? -1 : 0);
        int sy = y1 > y0 ? 1 : (y1 < y0 ? -1 : 0);
        int sz = z1 > z0 ? 1 : (z1 < z0 ? -1 : 0);

        // Distance to next axis crossing (in t parameter, 0..1).
        double tDeltaX = sx != 0 ? Math.abs(1.0d / dirX) : Double.POSITIVE_INFINITY;
        double tDeltaY = sy != 0 ? Math.abs(1.0d / dirY) : Double.POSITIVE_INFINITY;
        double tDeltaZ = sz != 0 ? Math.abs(1.0d / dirZ) : Double.POSITIVE_INFINITY;
        double tMaxX = sx > 0 ? ((x0 + 1) - fromX) / dirX
                     : (sx < 0 ? (x0 - fromX) / dirX : Double.POSITIVE_INFINITY);
        double tMaxY = sy > 0 ? ((y0 + 1) - fromY) / dirY
                     : (sy < 0 ? (y0 - fromY) / dirY : Double.POSITIVE_INFINITY);
        double tMaxZ = sz > 0 ? ((z0 + 1) - fromZ) / dirZ
                     : (sz < 0 ? (z0 - fromZ) / dirZ : Double.POSITIVE_INFINITY);

        int cx = x0, cy = y0, cz = z0;
        // Walk at most ~Manhattan-distance steps; cap to safety bound.
        int maxSteps = Math.abs(x1 - x0) + Math.abs(y1 - y0) + Math.abs(z1 - z0) + 2;
        for (int step = 0; step < maxSteps; step++) {
            // Advance to next voxel boundary.
            if (tMaxX < tMaxY) {
                if (tMaxX < tMaxZ) { cx += sx; if (tMaxX > 1.0) break; tMaxX += tDeltaX; }
                else                { cz += sz; if (tMaxZ > 1.0) break; tMaxZ += tDeltaZ; }
            } else {
                if (tMaxY < tMaxZ) { cy += sy; if (tMaxY > 1.0) break; tMaxY += tDeltaY; }
                else                { cz += sz; if (tMaxZ > 1.0) break; tMaxZ += tDeltaZ; }
            }

            // Stop when we reach the BE's own block (don't count it).
            if (cx == x1 && cy == y1 && cz == z1) break;
            // Y-bounds early-out for void traversal.
            if (cy < 0 || cy > 127) continue;

            int id = world.getBlockId(cx, cy, cz);
            if (id <= 0 || id >= registrySize) continue;
            if (opaque[id]) {
                crossings++;
                if (crossings >= OCCLUSION_THRESHOLD) return crossings;
            }
        }
        return crossings;
    }

    /** {@code Math.floor((double))} cast to int, but no boxing / branch. */
    private static int floorD(double v) {
        int i = (int) v;
        return v < i ? i - 1 : i;
    }
}
