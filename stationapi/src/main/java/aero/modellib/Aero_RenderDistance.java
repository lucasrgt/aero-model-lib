package aero.modellib;

import net.minecraft.block.entity.BlockEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.render.entity.EntityRenderDispatcher;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.fabricmc.loader.api.FabricLoader;

/**
 * StationAPI render-distance helpers for Aero models.
 */
public final class Aero_RenderDistance {

    private static boolean renderFrameCacheValid = false;
    private static int cachedViewDistance = Aero_RenderDistanceCulling.VIEW_DISTANCE_SHORT;

    // v3.0: camera world pos cached per frame for 6-plane frustum + occlusion
    // tests. Populated in refreshCameraForwardFromPlayer() alongside the
    // forward-vector update. Read by callers that need world-space bounds
    // (FrustumData.intersects takes world coords, occlusion raycast walks
    // from camera world coord to BE world coord).
    private static double cachedCameraX;
    private static double cachedCameraY;
    private static double cachedCameraZ;
    private static boolean cachedCameraValid;

    // The local player (server-thread world has no Minecraft.player so this
    // stays null on dedicated server runs). Refreshed by
    // refreshCameraForwardFromPlayer() — same source the camera coords come
    // from. BE.shouldTickAnimation() reads this instead of calling
    // world.getClosestPlayer per BE per tick (576 BEs × 20 Hz = 11.5k
    // playerEntities-list scans/sec on the MEGA test).
    private static PlayerEntity cachedLocalPlayer;

    // Cone half-angle cache: only recompute the atan/tan call when the
    // window dimensions change. Most frames the display size is identical,
    // so this elides 3 trig calls × 60 FPS = 180 trig calls/s. Initial
    // sentinel of -1 forces a recompute on the first frame.
    private static int cachedDisplayWidth = -1;
    private static int cachedDisplayHeight = -1;
    private static double cachedConeHalfDeg;

    private Aero_RenderDistance() {
    }

    /**
     * Called once near the beginning of StationAPI's world render. Public so
     * the platform mixin owns the frame boundary while renderers can still
     * lazily prime the cache if invoked from an unusual path.
     */
    public static void beginRenderFrame() {
        cachedViewDistance = readViewDistance();
        refreshCameraForwardFromPlayer();
        Aero_Frustum6Plane.invalidateFrame();
        // Failsafe drain: if WorldRendererBatchFlushMixin failed to apply
        // (signature drift, etc), any leftover queued instances from the
        // previous frame are flushed here. In the happy path, the queue
        // is already empty.
        Aero_AnimatedBatcher.flush();
        // Snapshot chunk visibility from vanilla's WorldRenderer.chunks[]
        // (one frame stale; see Aero_ChunkVisibility javadoc). Falls back
        // to "no cull" if the accessor mixin failed to apply.
        snapshotChunkVisibility();
        renderFrameCacheValid = true;
    }

    private static void snapshotChunkVisibility() {
        if (!Aero_ChunkVisibility.ENABLED) return;
        Object game = FabricLoader.getInstance().getGameInstance();
        if (!(game instanceof Minecraft)) return;
        Minecraft mc = (Minecraft) game;
        Object wr = mc.worldRenderer;
        if (wr == null) return;
        try {
            // The accessor interface is a mixin — every WorldRenderer
            // instance implements it after the mixin applies. If the
            // mixin failed, this cast throws, which we swallow (failsafe).
            net.minecraft.client.render.chunk.ChunkBuilder[] chunks =
                ((aero.modellib.mixin.WorldRendererChunksAccessor) wr).aero_modellib_getChunks();
            Aero_ChunkVisibility.snapshot(chunks);
        } catch (Throwable t) {
            // Accessor not applied or chunks not yet populated. Disable
            // for this frame; lookups return true (no cull).
        }
    }

    public static int currentViewDistance() {
        ensureRenderFrameCache();
        return cachedViewDistance;
    }

    private static void ensureRenderFrameCache() {
        if (!renderFrameCacheValid) beginRenderFrame();
    }

    private static int readViewDistance() {
        EntityRenderDispatcher dispatcher = EntityRenderDispatcher.INSTANCE;
        if (dispatcher != null && dispatcher.options != null) {
            return dispatcher.options.viewDistance;
        }
        return Aero_RenderDistanceCulling.VIEW_DISTANCE_SHORT;
    }

    public static double currentBlockRadius() {
        return Aero_RenderDistanceCulling.blockRadiusForViewDistance(currentViewDistance());
    }

    public static boolean shouldRenderRelative(double x, double y, double z,
                                               double visualRadiusBlocks) {
        return shouldRenderRelative(x, y, z, visualRadiusBlocks,
            Aero_RenderDistanceCulling.DEFAULT_SPECIAL_RENDER_RADIUS);
    }

    public static boolean shouldRenderRelative(double x, double y, double z,
                                               double visualRadiusBlocks,
                                               double maxRenderDistanceBlocks) {
        int viewDistance = currentViewDistance();
        if (!Aero_RenderDistanceCulling.shouldRenderRelative(
            x, y, z, viewDistance, visualRadiusBlocks, maxRenderDistanceBlocks)) {
            return false;
        }
        if (!Aero_FrustumCull.isLikelyVisibleWithRadius(x, y, z, visualRadiusBlocks)) {
            return false;
        }
        return passes6PlaneRelative(x, y, z, visualRadiusBlocks);
    }

    public static Aero_RenderLod lodRelative(double x, double y, double z,
                                             double visualRadiusBlocks,
                                             double animatedDistanceBlocks) {
        return lodRelative(x, y, z, visualRadiusBlocks, animatedDistanceBlocks,
            Aero_RenderDistanceCulling.DEFAULT_SPECIAL_RENDER_RADIUS);
    }

    public static Aero_RenderLod lodRelative(double x, double y, double z,
                                             double visualRadiusBlocks,
                                             double animatedDistanceBlocks,
                                             double maxRenderDistanceBlocks) {
        Aero_RenderLod lod = Aero_RenderDistanceCulling.lodRelative(
            x, y, z, currentViewDistance(), visualRadiusBlocks,
            animatedDistanceBlocks, maxRenderDistanceBlocks);
        if (!lod.shouldRender()) return lod;
        if (!Aero_FrustumCull.isLikelyVisibleWithRadius(x, y, z, visualRadiusBlocks)) {
            return Aero_RenderLod.CULLED;
        }
        if (!passes6PlaneRelative(x, y, z, visualRadiusBlocks)) {
            return Aero_RenderLod.CULLED;
        }
        return lod;
    }

    /**
     * 6-plane frustum test in camera-relative coords. Reconstructs world
     * coords from the per-frame cached camera position and asks
     * {@link Aero_Frustum6Plane}. Returns true (render) if the camera
     * cache isn't valid yet — falls through to the cone check above
     * which has its own valid-camera guard.
     */
    private static boolean passes6PlaneRelative(double x, double y, double z,
                                                double visualRadiusBlocks) {
        if (!cachedCameraValid) return true;
        double r = visualRadiusBlocks > 0 ? visualRadiusBlocks : 0.5;
        double cx = cachedCameraX + x;
        double cy = cachedCameraY + y;
        double cz = cachedCameraZ + z;
        return Aero_Frustum6Plane.isVisibleAABB(cx - r, cy - r, cz - r,
                                                 cx + r, cy + r, cz + r);
    }

    public static Aero_RenderLod lodRelative(Aero_ModelSpec spec,
                                             double x, double y, double z) {
        if (spec == null) throw new IllegalArgumentException("spec must not be null");
        Aero_EntityModelTransform transform = spec.getEntityTransform();
        return lodRelative(x, y, z,
            transform.cullingRadius,
            spec.getAnimatedDistanceBlocks(),
            transform.maxRenderDistance);
    }

    public static void updateCameraForwardFromPlayer() {
        ensureRenderFrameCache();
    }

    /**
     * Cached local player ref refreshed once per render frame by
     * {@link #refreshCameraForwardFromPlayer}. Lets BE tick callbacks
     * skip {@code world.getClosestPlayer(...)} (which scans
     * {@code playerEntities}) on every call. Returns null if Minecraft
     * isn't initialized yet or the player hasn't joined a world. Never
     * use the returned reference past one frame — the player can despawn
     * between frames and the renderer cache will be cleared.
     */
    public static PlayerEntity getCachedLocalPlayer() {
        return cachedLocalPlayer;
    }

    private static void refreshCameraForwardFromPlayer() {
        // v3.0: camera world pos cache used by 6-plane frustum + occlusion
        // even when cone is disabled. Populate it independently of
        // Aero_FrustumCull.ENABLED so the new culls keep working.
        cachedCameraValid = false;
        cachedLocalPlayer = null;
        Object game = FabricLoader.getInstance().getGameInstance();
        if (!(game instanceof Minecraft)) {
            Aero_FrustumCull.clearCamera();
            return;
        }
        Minecraft mc = (Minecraft) game;
        PlayerEntity player = mc.player;
        if (player == null) {
            Aero_FrustumCull.clearCamera();
            return;
        }
        cachedCameraX = player.x;
        cachedCameraY = player.y;
        cachedCameraZ = player.z;
        cachedCameraValid = true;
        cachedLocalPlayer = player;
        if (!Aero_FrustumCull.ENABLED) return;
        Aero_FrustumCull.updateCameraForward(player.yaw, player.pitch);
        // Cone half-angle from MC window aspect (Beta hardcodes
        // vertical FOV at 70°, so horizontal half = atan(tan(35°)·aspect)).
        // Constants frozen: +20° margin, 75° floor. See Aero_FrustumCull
        // class javadoc — the cone is approximate by design and these
        // values are not tuned per-resolution.
        if (mc.displayHeight > 0) {
            // Only recompute the trig when display dimensions change — typical
            // gameplay holds them constant for thousands of frames.
            if (mc.displayWidth != cachedDisplayWidth
                || mc.displayHeight != cachedDisplayHeight) {
                double aspect = (double) mc.displayWidth / (double) mc.displayHeight;
                double hHalfRad = Math.atan(Math.tan(Math.toRadians(35.0d)) * aspect);
                double hHalfDeg = Math.toDegrees(hHalfRad);
                cachedConeHalfDeg = Math.max(75.0d, hHalfDeg + 20.0d);
                cachedDisplayWidth = mc.displayWidth;
                cachedDisplayHeight = mc.displayHeight;
            }
            Aero_FrustumCull.setConeHalfAngleDegrees(cachedConeHalfDeg);
        }
    }

    public static boolean shouldRenderFrustumRelative(double x, double y, double z,
                                                      double visualRadiusBlocks) {
        updateCameraForwardFromPlayer();
        return Aero_FrustumCull.isLikelyVisibleWithRadius(x, y, z, visualRadiusBlocks);
    }

    public static double blockEntityDistanceFrom(BlockEntity blockEntity,
                                                 double cameraX, double cameraY, double cameraZ,
                                                 double visualRadiusBlocks) {
        return blockEntityDistanceFrom(blockEntity, cameraX, cameraY, cameraZ,
            visualRadiusBlocks, Aero_RenderDistanceCulling.DEFAULT_SPECIAL_RENDER_RADIUS);
    }

    public static double blockEntityDistanceFrom(BlockEntity blockEntity,
                                                 double cameraX, double cameraY, double cameraZ,
                                                 double visualRadiusBlocks,
                                                 double maxRenderDistanceBlocks) {
        int viewDistance = currentViewDistance();
        double dx = blockEntity.x + 0.5d - cameraX;
        double dy = blockEntity.y + 0.5d - cameraY;
        double dz = blockEntity.z + 0.5d - cameraZ;

        // Chunk-level frustum visibility — uses vanilla's own per-chunk
        // inFrustum flag, which is stable per-frame (only flips on chunk
        // entry/exit, not on per-frame camera jiggle). Reject early if
        // the BE's chunk wasn't in this frame's visible set.
        if (!Aero_ChunkVisibility.isBlockChunkVisible(blockEntity.x, blockEntity.z)) {
            return Double.POSITIVE_INFINITY;
        }
        // Cone (cheap behind-camera reject).
        if (!Aero_FrustumCull.isLikelyVisibleWithRadius(dx, dy, dz, visualRadiusBlocks)) {
            return Double.POSITIVE_INFINITY;
        }
        // 6-plane real frustum (catches narrow-FOV / aspect cases the cone
        // over-includes). Tests world AABB around the BE.
        if (cachedCameraValid) {
            double r = visualRadiusBlocks > 0 ? visualRadiusBlocks : 0.5;
            double cx = blockEntity.x + 0.5d;
            double cy = blockEntity.y + 0.5d;
            double cz = blockEntity.z + 0.5d;
            if (!Aero_Frustum6Plane.isVisibleAABB(cx - r, cy - r, cz - r,
                                                   cx + r, cy + r, cz + r)) {
                return Double.POSITIVE_INFINITY;
            }
        }
        // Coarse occlusion — the v3.0 killer feature for the underground
        // case. Walks a voxel-stepping line from camera world pos to the
        // BE block, counting opaque blocks crossed. Returns Infinity for
        // entities solidly behind terrain.
        // Subclasses of Aero_RenderDistanceBlockEntity get a per-BE cache
        // (every 4th call recomputes); other BlockEntity subclasses fall
        // back to the uncached path.
        boolean occluded;
        if (blockEntity instanceof Aero_RenderDistanceBlockEntity) {
            occluded = ((Aero_RenderDistanceBlockEntity) blockEntity)
                .isOccludedCached(dx, dy, dz);
        } else {
            occluded = Aero_OcclusionCull.isOccluded(blockEntity.world, dx, dy, dz,
                                                     blockEntity.x, blockEntity.y, blockEntity.z);
        }
        if (occluded) {
            return Double.POSITIVE_INFINITY;
        }
        return Aero_RenderDistanceCulling.normalizedDistanceForVanillaDispatcher(
            Aero_RenderDistanceCulling.squaredDistance(dx, dy, dz),
            viewDistance,
            visualRadiusBlocks,
            maxRenderDistanceBlocks);
    }

    public static void applyEntityRenderDistance(Entity entity, double visualRadiusBlocks) {
        applyEntityRenderDistance(entity, visualRadiusBlocks,
            Aero_RenderDistanceCulling.DEFAULT_SPECIAL_RENDER_RADIUS);
    }

    public static void applyEntityRenderDistance(Entity entity, double visualRadiusBlocks,
                                                 double maxRenderDistanceBlocks) {
        if (entity == null) return;
        double radius = Aero_RenderDistanceCulling.maximumBlockRadiusWithMargin(
            visualRadiusBlocks, maxRenderDistanceBlocks);
        double averageSideLength = entity.boundingBox != null
            ? entity.boundingBox.getAverageSideLength()
            : Math.max(0.25d, Math.max(entity.width, entity.height));
        double multiplier = Aero_RenderDistanceCulling.entityRenderDistanceMultiplier(
            radius, averageSideLength);
        if (multiplier > entity.renderDistanceMultiplier) {
            entity.renderDistanceMultiplier = multiplier;
        }
    }

    public static void applyEntityRenderDistance(Entity entity, Aero_ModelSpec spec) {
        if (spec == null) throw new IllegalArgumentException("spec must not be null");
        Aero_EntityModelTransform transform = spec.getEntityTransform();
        applyEntityRenderDistance(entity, transform.cullingRadius, transform.maxRenderDistance);
    }
}
