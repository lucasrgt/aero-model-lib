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

    private Aero_RenderDistance() {
    }

    public static int currentViewDistance() {
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
        if (!Aero_RenderDistanceCulling.shouldRenderRelative(
            x, y, z, currentViewDistance(), visualRadiusBlocks, maxRenderDistanceBlocks)) {
            return false;
        }
        updateCameraForwardFromPlayer();
        return Aero_FrustumCull.isLikelyVisibleWithRadius(x, y, z, visualRadiusBlocks);
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
        return Aero_RenderDistanceCulling.lodRelative(
            x, y, z, currentViewDistance(), visualRadiusBlocks,
            animatedDistanceBlocks, maxRenderDistanceBlocks);
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
        if (!Aero_FrustumCull.ENABLED) return;
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
        Aero_FrustumCull.updateCameraForward(player.yaw, player.pitch);
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
        return Aero_RenderDistanceCulling.blockEntityDistanceFrom(
            blockEntity.x, blockEntity.y, blockEntity.z,
            cameraX, cameraY, cameraZ,
            currentViewDistance(), visualRadiusBlocks, maxRenderDistanceBlocks);
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
