package aero.modellib;

import net.minecraft.src.*;

/**
 * ModLoader render-distance helpers for Aero models.
 */
public final class Aero_RenderDistance {

    private Aero_RenderDistance() {
    }

    public static int currentViewDistance() {
        try {
            Minecraft mc = ModLoader.getMinecraftInstance();
            if (mc != null && mc.gameSettings != null) {
                return mc.gameSettings.renderDistance;
            }
        } catch (Throwable ignored) {
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
        return Aero_RenderDistanceCulling.shouldRenderRelative(
            x, y, z, currentViewDistance(), visualRadiusBlocks, maxRenderDistanceBlocks);
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

    public static double tileEntityDistanceFrom(TileEntity tile,
                                                double cameraX, double cameraY, double cameraZ,
                                                double visualRadiusBlocks) {
        return tileEntityDistanceFrom(tile, cameraX, cameraY, cameraZ, visualRadiusBlocks,
            Aero_RenderDistanceCulling.DEFAULT_SPECIAL_RENDER_RADIUS);
    }

    public static double tileEntityDistanceFrom(TileEntity tile,
                                                double cameraX, double cameraY, double cameraZ,
                                                double visualRadiusBlocks,
                                                double maxRenderDistanceBlocks) {
        return Aero_RenderDistanceCulling.blockEntityDistanceFrom(
            tile.xCoord, tile.yCoord, tile.zCoord,
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
        double multiplier = Aero_RenderDistanceCulling.entityRenderDistanceMultiplier(
            radius, averageSideLength(entity));
        setDoubleField(entity, "renderDistanceWeight", multiplier);
        setDoubleField(entity, "renderDistanceMultiplier", multiplier);
    }

    private static double averageSideLength(Entity entity) {
        try {
            Object box = entity.boundingBox;
            if (box != null) {
                try {
                    return ((Number) box.getClass().getMethod("getAverageEdgeLength").invoke(box)).doubleValue();
                } catch (NoSuchMethodException ignored) {
                    return ((Number) box.getClass().getMethod("getAverageSideLength").invoke(box)).doubleValue();
                }
            }
        } catch (Throwable ignored) {
        }
        return Math.max(0.25d, Math.max(entity.width, entity.height));
    }

    private static void setDoubleField(Entity entity, String name, double value) {
        try {
            java.lang.reflect.Field field = Entity.class.getField(name);
            double current = field.getDouble(entity);
            if (value > current) field.setDouble(entity, value);
        } catch (Throwable ignored) {
        }
    }
}
