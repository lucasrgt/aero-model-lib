package aero.modellib;

import org.junit.Test;

import static org.junit.Assert.*;

public class EntityModelTransformTest {

    private static final float DELTA = 0.0001f;

    @Test
    public void defaultTransformMatchesVanillaEntityYawFlip() {
        Aero_EntityModelTransform transform = Aero_EntityModelTransform.DEFAULT;

        assertEquals(0f, transform.offsetX, DELTA);
        assertEquals(0f, transform.offsetY, DELTA);
        assertEquals(0f, transform.offsetZ, DELTA);
        assertEquals(1f, transform.scale, DELTA);
        assertEquals(0f, transform.yawOffset, DELTA);
        assertEquals(0f, transform.cullingRadius, DELTA);
        assertEquals((float) Aero_RenderDistanceCulling.DEFAULT_SPECIAL_RENDER_RADIUS,
            transform.maxRenderDistance, DELTA);
        assertEquals(180f, transform.modelYaw(0f), DELTA);
        assertEquals(90f, transform.modelYaw(90f), DELTA);
        assertEquals(0f, transform.modelYaw(180f), DELTA);
    }

    @Test
    public void withMethodsReturnAdjustedCopies() {
        Aero_EntityModelTransform base = Aero_EntityModelTransform.of(1f, 2f, 3f, 0.5f, 10f);

        Aero_EntityModelTransform moved = base.withOffset(-1f, -2f, -3f);
        Aero_EntityModelTransform scaled = base.withScale(2f);
        Aero_EntityModelTransform yawed = base.withYawOffset(45f);
        Aero_EntityModelTransform culled = base.withCullingRadius(6f);
        Aero_EntityModelTransform far = base.withMaxRenderDistance(160f);

        assertEquals(1f, base.offsetX, DELTA);
        assertEquals(-1f, moved.offsetX, DELTA);
        assertEquals(-2f, moved.offsetY, DELTA);
        assertEquals(-3f, moved.offsetZ, DELTA);
        assertEquals(2f, scaled.scale, DELTA);
        assertEquals(45f, yawed.yawOffset, DELTA);
        assertEquals(135f, yawed.modelYaw(90f), DELTA);
        assertEquals(6f, culled.cullingRadius, DELTA);
        assertEquals(160f, far.maxRenderDistance, DELTA);
    }

    @Test
    public void rejectsInvalidNumbers() {
        expectInvalidScale(0f);
        expectInvalidScale(Float.NaN);
        expectInvalidScale(Float.POSITIVE_INFINITY);
        expectInvalidCullingRadius(-1f);
        expectInvalidCullingRadius(Float.NaN);
        expectInvalidMaxRenderDistance(0f);
        expectInvalidMaxRenderDistance(Float.NaN);

        try {
            Aero_EntityModelTransform.of(Float.NaN, 0f, 0f, 1f, 0f);
            fail("Expected invalid offset");
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage().contains("offsetX"));
        }
    }

    private static void expectInvalidScale(float scale) {
        try {
            Aero_EntityModelTransform.DEFAULT.withScale(scale);
            fail("Expected invalid scale " + scale);
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage().contains("scale"));
        }
    }

    private static void expectInvalidCullingRadius(float radius) {
        try {
            Aero_EntityModelTransform.DEFAULT.withCullingRadius(radius);
            fail("Expected invalid culling radius " + radius);
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage().contains("cullingRadius"));
        }
    }

    private static void expectInvalidMaxRenderDistance(float distance) {
        try {
            Aero_EntityModelTransform.DEFAULT.withMaxRenderDistance(distance);
            fail("Expected invalid max render distance " + distance);
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage().contains("maxRenderDistance"));
        }
    }
}
