package aero.modellib;

/**
 * Pure render-distance culling math shared by ModLoader and StationAPI.
 *
 * Beta 1.7.3's BlockEntity/TileEntity dispatcher compares distanceFrom()
 * against a hardcoded 64 block radius (4096). Returning a normalized distance
 * lets a tile/block entity scale with the player's view distance without
 * patching the dispatcher, while keeping a practical cap for Far distance.
 */
public final class Aero_RenderDistanceCulling {

    public static final int VIEW_DISTANCE_FAR = 0;
    public static final int VIEW_DISTANCE_NORMAL = 1;
    public static final int VIEW_DISTANCE_SHORT = 2;
    public static final int VIEW_DISTANCE_TINY = 3;

    public static final double VANILLA_DISPATCH_RADIUS = 64.0d;
    public static final double VANILLA_DISPATCH_RADIUS_SQ =
        VANILLA_DISPATCH_RADIUS * VANILLA_DISPATCH_RADIUS;
    public static final double DEFAULT_SPECIAL_RENDER_RADIUS = 96.0d;

    private Aero_RenderDistanceCulling() {
    }

    /**
     * Maps Beta's view distance option to an approximate block radius.
     * Values are Far, Normal, Short and Tiny.
     */
    public static double blockRadiusForViewDistance(int viewDistance) {
        switch (viewDistance) {
            case VIEW_DISTANCE_FAR:
                return 256.0d;
            case VIEW_DISTANCE_NORMAL:
                return 128.0d;
            case VIEW_DISTANCE_TINY:
                return 32.0d;
            case VIEW_DISTANCE_SHORT:
            default:
                return 64.0d;
        }
    }

    public static double blockRadiusWithMargin(int viewDistance, double visualRadiusBlocks) {
        return blockRadiusWithMargin(viewDistance, visualRadiusBlocks, DEFAULT_SPECIAL_RENDER_RADIUS);
    }

    public static double blockRadiusWithMargin(int viewDistance, double visualRadiusBlocks,
                                               double maxRenderDistanceBlocks) {
        requireNonNegativeFinite("visualRadiusBlocks", visualRadiusBlocks);
        requirePositiveFinite("maxRenderDistanceBlocks", maxRenderDistanceBlocks);
        double baseRadius = Math.min(blockRadiusForViewDistance(viewDistance), maxRenderDistanceBlocks);
        return baseRadius + visualRadiusBlocks;
    }

    public static double maximumBlockRadiusWithMargin(double visualRadiusBlocks) {
        return maximumBlockRadiusWithMargin(visualRadiusBlocks, DEFAULT_SPECIAL_RENDER_RADIUS);
    }

    public static double maximumBlockRadiusWithMargin(double visualRadiusBlocks,
                                                      double maxRenderDistanceBlocks) {
        return blockRadiusWithMargin(VIEW_DISTANCE_FAR, visualRadiusBlocks, maxRenderDistanceBlocks);
    }

    public static boolean shouldRenderRelative(double x, double y, double z,
                                               int viewDistance,
                                               double visualRadiusBlocks) {
        return shouldRenderRelative(x, y, z, viewDistance, visualRadiusBlocks,
            DEFAULT_SPECIAL_RENDER_RADIUS);
    }

    public static boolean shouldRenderRelative(double x, double y, double z,
                                               int viewDistance,
                                               double visualRadiusBlocks,
                                               double maxRenderDistanceBlocks) {
        requireFinite("x", x);
        requireFinite("y", y);
        requireFinite("z", z);
        double radius = blockRadiusWithMargin(viewDistance, visualRadiusBlocks,
            maxRenderDistanceBlocks);
        return effectiveDistanceSq(x, y, z) <= radius * radius;
    }

    public static Aero_RenderLod lodRelative(double x, double y, double z,
                                             int viewDistance,
                                             double visualRadiusBlocks,
                                             double animatedDistanceBlocks) {
        return lodRelative(x, y, z, viewDistance, visualRadiusBlocks,
            animatedDistanceBlocks, DEFAULT_SPECIAL_RENDER_RADIUS);
    }

    public static Aero_RenderLod lodRelative(double x, double y, double z,
                                             int viewDistance,
                                             double visualRadiusBlocks,
                                             double animatedDistanceBlocks,
                                             double maxRenderDistanceBlocks) {
        requireFinite("x", x);
        requireFinite("y", y);
        requireFinite("z", z);
        requireNonNegativeFinite("visualRadiusBlocks", visualRadiusBlocks);
        requireNonNegativeFinite("animatedDistanceBlocks", animatedDistanceBlocks);
        requirePositiveFinite("maxRenderDistanceBlocks", maxRenderDistanceBlocks);

        double viewRadius = Math.min(blockRadiusForViewDistance(viewDistance), maxRenderDistanceBlocks);
        double staticRadius = viewRadius + visualRadiusBlocks;
        double distanceSq = effectiveDistanceSq(x, y, z);
        if (distanceSq > staticRadius * staticRadius) return Aero_RenderLod.CULLED;

        double animatedRadius = Math.min(animatedDistanceBlocks, viewRadius) + visualRadiusBlocks;
        if (distanceSq <= animatedRadius * animatedRadius) return Aero_RenderLod.ANIMATED;
        return Aero_RenderLod.STATIC;
    }

    /**
     * Returns a synthetic distance squared for vanilla's hardcoded 64 block
     * BlockEntity/TileEntity dispatcher comparison.
     */
    public static double normalizedDistanceForVanillaDispatcher(double distanceSq,
                                                                 int viewDistance,
                                                                 double visualRadiusBlocks) {
        return normalizedDistanceForVanillaDispatcher(distanceSq, viewDistance,
            visualRadiusBlocks, DEFAULT_SPECIAL_RENDER_RADIUS);
    }

    public static double normalizedDistanceForVanillaDispatcher(double distanceSq,
                                                                 int viewDistance,
                                                                 double visualRadiusBlocks,
                                                                 double maxRenderDistanceBlocks) {
        requireNonNegativeFinite("distanceSq", distanceSq);
        double radius = blockRadiusWithMargin(viewDistance, visualRadiusBlocks,
            maxRenderDistanceBlocks);
        if (radius <= 0.0d) return Double.POSITIVE_INFINITY;
        return distanceSq * VANILLA_DISPATCH_RADIUS_SQ / (radius * radius);
    }

    public static double blockEntityDistanceFrom(double blockX, double blockY, double blockZ,
                                                 double cameraX, double cameraY, double cameraZ,
                                                 int viewDistance,
                                                 double visualRadiusBlocks) {
        return blockEntityDistanceFrom(blockX, blockY, blockZ, cameraX, cameraY, cameraZ,
            viewDistance, visualRadiusBlocks, DEFAULT_SPECIAL_RENDER_RADIUS);
    }

    public static double blockEntityDistanceFrom(double blockX, double blockY, double blockZ,
                                                 double cameraX, double cameraY, double cameraZ,
                                                 int viewDistance,
                                                 double visualRadiusBlocks,
                                                 double maxRenderDistanceBlocks) {
        double dx = blockX + 0.5d - cameraX;
        double dy = blockY + 0.5d - cameraY;
        double dz = blockZ + 0.5d - cameraZ;
        return normalizedDistanceForVanillaDispatcher(
            squaredDistance(dx, dy, dz),
            viewDistance,
            visualRadiusBlocks,
            maxRenderDistanceBlocks
        );
    }

    public static double entityRenderDistanceMultiplier(double desiredRadiusBlocks,
                                                        double averageSideLength) {
        requireNonNegativeFinite("desiredRadiusBlocks", desiredRadiusBlocks);
        requireNonNegativeFinite("averageSideLength", averageSideLength);
        double side = averageSideLength < 0.25d ? 0.25d : averageSideLength;
        return desiredRadiusBlocks / (side * VANILLA_DISPATCH_RADIUS);
    }

    public static double squaredDistance(double x, double y, double z) {
        return x * x + y * y + z * z;
    }

    private static double effectiveDistanceSq(double x, double y, double z) {
        return Aero_LODConfig.ENABLED
            ? biasedDistanceSq(x, y, z, Aero_LODConfig.Y_BIAS)
            : squaredDistance(x, y, z);
    }

    private static double biasedDistanceSq(double x, double y, double z, double yBias) {
        double by = y * yBias;
        return x * x + by * by + z * z;
    }

    private static void requireFinite(String name, double value) {
        if (Double.isNaN(value) || Double.isInfinite(value)) {
            throw new IllegalArgumentException(name + " must be finite");
        }
    }

    private static void requireNonNegativeFinite(String name, double value) {
        requireFinite(name, value);
        if (value < 0.0d) throw new IllegalArgumentException(name + " must be >= 0");
    }

    private static void requirePositiveFinite(String name, double value) {
        requireFinite(name, value);
        if (value <= 0.0d) throw new IllegalArgumentException(name + " must be > 0");
    }
}
