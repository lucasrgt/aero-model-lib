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
        assertEquals(Aero_MeshBlendMode.OFF, o.blend);
        assertTrue(o.depthTest);
    }

    @Test
    public void translucentFactoryEnablesAlphaBlend() {
        Aero_RenderOptions o = Aero_RenderOptions.translucent(0.4f);
        assertEquals(Aero_MeshBlendMode.ALPHA, o.blend);
        assertEquals(0.4f, o.alpha, DELTA);
        assertEquals(1f, o.tintR, DELTA);   // tint left at default
    }

    @Test
    public void additiveFactoryEnablesAdditiveBlend() {
        Aero_RenderOptions o = Aero_RenderOptions.additive(0.7f);
        assertEquals(Aero_MeshBlendMode.ADDITIVE, o.blend);
        assertEquals(0.7f, o.alpha, DELTA);
    }

    @Test
    public void builderComposesAllKnobs() {
        Aero_RenderOptions o = Aero_RenderOptions.builder()
            .tint(1f, 0.5f, 0.25f)
            .alpha(0.75f)
            .blend(Aero_MeshBlendMode.ADDITIVE)
            .depthTest(false)
            .build();
        assertEquals(0.5f, o.tintG, DELTA);
        assertEquals(0.75f, o.alpha, DELTA);
        assertEquals(Aero_MeshBlendMode.ADDITIVE, o.blend);
        assertFalse(o.depthTest);
    }

    @Test
    public void booleanBlendOverloadMapsToAlphaOrOff() {
        assertEquals(Aero_MeshBlendMode.ALPHA,
            Aero_RenderOptions.builder().blend(true).build().blend);
        assertEquals(Aero_MeshBlendMode.OFF,
            Aero_RenderOptions.builder().blend(false).build().blend);
    }

    @Test
    public void toBuilderRoundTripsEveryField() {
        Aero_RenderOptions src = Aero_RenderOptions.builder()
            .tint(0.2f, 0.3f, 0.4f)
            .alpha(0.5f)
            .blend(Aero_MeshBlendMode.ADDITIVE)
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

    @Test(expected = IllegalArgumentException.class)
    public void rejectsNullBlendMode() {
        Aero_RenderOptions.builder().blend((Aero_MeshBlendMode) null);
    }
}
