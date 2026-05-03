package aero.modellib;

import org.junit.Test;

import aero.modellib.render.Aero_RenderDistanceCulling;
import aero.modellib.render.Aero_RenderLod;

import static org.junit.Assert.*;

public class RenderDistanceCullingTest {

    private static final double DELTA = 0.0001d;

    @Test
    public void mapsBetaViewDistanceToBlockRadii() {
        assertEquals(256d, Aero_RenderDistanceCulling.blockRadiusForViewDistance(0), DELTA);
        assertEquals(128d, Aero_RenderDistanceCulling.blockRadiusForViewDistance(1), DELTA);
        assertEquals(64d, Aero_RenderDistanceCulling.blockRadiusForViewDistance(2), DELTA);
        assertEquals(32d, Aero_RenderDistanceCulling.blockRadiusForViewDistance(3), DELTA);
        assertEquals(64d, Aero_RenderDistanceCulling.blockRadiusForViewDistance(99), DELTA);
    }

    @Test
    public void shouldRenderUsesViewDistanceAndModelMargin() {
        assertFalse(Aero_RenderDistanceCulling.shouldRenderRelative(200d, 0d, 0d, 0, 0d));
        assertTrue(Aero_RenderDistanceCulling.shouldRenderRelative(200d, 0d, 0d, 0, 0d, 256d));
        assertTrue(Aero_RenderDistanceCulling.shouldRenderRelative(90d, 0d, 0d, 0, 0d));
        assertFalse(Aero_RenderDistanceCulling.shouldRenderRelative(200d, 0d, 0d, 2, 0d));

        assertFalse(Aero_RenderDistanceCulling.shouldRenderRelative(40d, 0d, 0d, 3, 0d));
        assertTrue(Aero_RenderDistanceCulling.shouldRenderRelative(40d, 0d, 0d, 3, 16d));
    }

    @Test
    public void blockEntityDistanceNormalizesVanillaHardcodedLimit() {
        double far = Aero_RenderDistanceCulling.blockEntityDistanceFrom(
            90d, 0d, 0d,
            0.5d, 0.5d, 0.5d,
            Aero_RenderDistanceCulling.VIEW_DISTANCE_FAR,
            0d);
        double shortDistance = Aero_RenderDistanceCulling.blockEntityDistanceFrom(
            100d, 0d, 0d,
            0.5d, 0.5d, 0.5d,
            Aero_RenderDistanceCulling.VIEW_DISTANCE_SHORT,
            0d);
        double cappedFar = Aero_RenderDistanceCulling.blockEntityDistanceFrom(
            180d, 0d, 0d,
            0.5d, 0.5d, 0.5d,
            Aero_RenderDistanceCulling.VIEW_DISTANCE_FAR,
            0d);
        double uncappedFar = Aero_RenderDistanceCulling.blockEntityDistanceFrom(
            180d, 0d, 0d,
            0.5d, 0.5d, 0.5d,
            Aero_RenderDistanceCulling.VIEW_DISTANCE_FAR,
            0d,
            256d);

        assertTrue(far < Aero_RenderDistanceCulling.VANILLA_DISPATCH_RADIUS_SQ);
        assertTrue(shortDistance > Aero_RenderDistanceCulling.VANILLA_DISPATCH_RADIUS_SQ);
        assertTrue(cappedFar > Aero_RenderDistanceCulling.VANILLA_DISPATCH_RADIUS_SQ);
        assertTrue(uncappedFar < Aero_RenderDistanceCulling.VANILLA_DISPATCH_RADIUS_SQ);
    }

    @Test
    public void entityMultiplierScalesDesiredRadiusByBoundingBoxSize() {
        assertEquals(4d, Aero_RenderDistanceCulling.entityRenderDistanceMultiplier(256d, 1d), DELTA);
        assertEquals(2d, Aero_RenderDistanceCulling.entityRenderDistanceMultiplier(256d, 2d), DELTA);
        assertEquals(4d, Aero_RenderDistanceCulling.entityRenderDistanceMultiplier(64d, 0d), DELTA);
    }

    @Test
    public void entityDispatcherRadiusUsesFarDistancePlusModelMargin() {
        assertEquals(99d, Aero_RenderDistanceCulling.maximumBlockRadiusWithMargin(3d), DELTA);
        assertEquals(259d, Aero_RenderDistanceCulling.maximumBlockRadiusWithMargin(3d, 256d), DELTA);
    }

    @Test
    public void lodRelativeChoosesAnimatedStaticAndCulledBands() {
        assertEquals(Aero_RenderLod.ANIMATED, Aero_RenderDistanceCulling.lodRelative(
            24d, 0d, 0d,
            Aero_RenderDistanceCulling.VIEW_DISTANCE_FAR,
            2d, 32d));
        assertEquals(Aero_RenderLod.STATIC, Aero_RenderDistanceCulling.lodRelative(
            64d, 0d, 0d,
            Aero_RenderDistanceCulling.VIEW_DISTANCE_FAR,
            2d, 32d));
        assertEquals(Aero_RenderLod.CULLED, Aero_RenderDistanceCulling.lodRelative(
            120d, 0d, 0d,
            Aero_RenderDistanceCulling.VIEW_DISTANCE_FAR,
            2d, 32d));
    }

    @Test
    public void lodHonorsTinyViewDistanceBeforeAnimatedThreshold() {
        assertEquals(Aero_RenderLod.CULLED, Aero_RenderDistanceCulling.lodRelative(
            40d, 0d, 0d,
            Aero_RenderDistanceCulling.VIEW_DISTANCE_TINY,
            0d, 96d));
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsNegativeVisualRadius() {
        Aero_RenderDistanceCulling.shouldRenderRelative(0d, 0d, 0d, 2, -1d);
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsInvalidMaxRenderDistance() {
        Aero_RenderDistanceCulling.shouldRenderRelative(0d, 0d, 0d, 2, 0d, 0d);
    }
}
