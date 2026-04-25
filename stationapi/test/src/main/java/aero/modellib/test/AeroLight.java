package aero.modellib.test;

import net.minecraft.world.World;

/**
 * Sampling helpers shared by every BlockEntity renderer in the test mod.
 *
 * The naive {@code world.method_1782(x, y+1, z)} call returns 0 when y+1
 * lands inside a non-air block — which happens for blocks placed under
 * water/lava (the cell above is fluid, light is dim) or buried in stone
 * (no light at all). Sampling at the column's top instead guarantees a
 * sky-lit reference brightness, with a fallback to the literal y+1 read
 * in case the chunk hasn't loaded its top-Y heightmap yet.
 */
public final class AeroLight {
    private AeroLight() {}

    public static float brightnessAbove(World world, int x, int y, int z) {
        int top = world.getTopY(x, z);
        int sampleY = Math.max(y + 1, top);
        // method_1782 = float-brightness equivalent of vanilla
        // getLightBrightness; returns 0 when the column is unreachable.
        float bright = world.method_1782(x, sampleY, z);
        if (bright <= 0f) bright = world.method_1782(x, y + 1, z);
        return bright;
    }
}
