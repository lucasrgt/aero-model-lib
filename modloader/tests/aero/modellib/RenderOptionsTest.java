package aero.modellib;

import org.junit.Test;

import static org.junit.Assert.*;

public class RenderOptionsTest {

    private static final float DELTA = 1e-4f;

    @Test
    public void defaultsAreOpaqueWhiteWithDepthTest() {
        Aero_RenderOptions o = Aero_RenderOptions.DEFAULT;
        assertEquals(1f, o.tintR, DELTA);
        assertEquals(1f, o.tintG, DELTA);
        assertEquals(1f, o.tintB, DELTA);
        assertEquals(1f, o.alpha, DELTA);
        assertFalse(o.blend);
        assertTrue(o.depthTest);
    }

    @Test
    public void translucentTurnsOnBlendAndAlpha() {
        Aero_RenderOptions o = Aero_RenderOptions.translucent(0.4f);
        assertTrue(o.blend);
        assertEquals(0.4f, o.alpha, DELTA);
        assertEquals(1f, o.tintR, DELTA);   // tint left at default
    }

    @Test
    public void builderComposesAllKnobs() {
        Aero_RenderOptions o = Aero_RenderOptions.builder()
            .tint(1f, 0.5f, 0.25f)
            .alpha(0.75f)
            .blend(true)
            .depthTest(false)
            .build();
        assertEquals(0.5f, o.tintG, DELTA);
        assertEquals(0.75f, o.alpha, DELTA);
        assertTrue(o.blend);
        assertFalse(o.depthTest);
    }

    @Test
    public void toBuilderRoundTripsEveryField() {
        Aero_RenderOptions src = Aero_RenderOptions.builder()
            .tint(0.2f, 0.3f, 0.4f)
            .alpha(0.5f)
            .blend(true)
            .depthTest(false)
            .build();
        Aero_RenderOptions copy = src.toBuilder().build();
        assertEquals(src.tintR, copy.tintR, DELTA);
        assertEquals(src.tintG, copy.tintG, DELTA);
        assertEquals(src.tintB, copy.tintB, DELTA);
        assertEquals(src.alpha, copy.alpha, DELTA);
        assertEquals(src.blend, copy.blend);
        assertEquals(src.depthTest, copy.depthTest);
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsAlphaAboveOne() {
        Aero_RenderOptions.builder().alpha(1.5f);
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsNegativeAlpha() {
        Aero_RenderOptions.builder().alpha(-0.1f);
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsNaNTint() {
        Aero_RenderOptions.builder().tint(Float.NaN, 0f, 0f);
    }
}
