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

        assertEquals(1f, base.offsetX, DELTA);
        assertEquals(-1f, moved.offsetX, DELTA);
        assertEquals(-2f, moved.offsetY, DELTA);
        assertEquals(-3f, moved.offsetZ, DELTA);
        assertEquals(2f, scaled.scale, DELTA);
        assertEquals(45f, yawed.yawOffset, DELTA);
        assertEquals(135f, yawed.modelYaw(90f), DELTA);
    }

    @Test
    public void rejectsInvalidNumbers() {
        expectInvalidScale(0f);
        expectInvalidScale(Float.NaN);
        expectInvalidScale(Float.POSITIVE_INFINITY);

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
}
