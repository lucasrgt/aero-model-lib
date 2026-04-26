package aero.modellib;

import org.junit.Test;

import static org.junit.Assert.*;

public class AnimationLoaderStrictSchemaTest {

    private static final String VER = "\"format_version\":\"1.0\",";

    @Test
    public void rejectsBooleanLoop() {
        assertRejected("{" + VER + "\"animations\":{\"idle\":{\"loop\":true,\"bones\":{}}}}", "loop");
    }

    @Test
    public void rejectsLegacyPoseArrayKeyframe() {
        assertRejected(
            "{" + VER + "\"animations\":{\"idle\":{\"loop\":\"loop\",\"bones\":{\"x\":{\"rotation\":{\"0\":[0,0,0]}}}}}}",
            "pose keyframe");
    }

    @Test
    public void rejectsUnknownEasing() {
        assertRejected(
            "{" + VER + "\"animations\":{\"idle\":{\"loop\":\"loop\",\"bones\":{\"x\":{\"rotation\":{\"0\":{\"value\":[0,0,0],\"interp\":\"nope\"}}}}}}}",
            "unknown easing");
    }

    @Test
    public void rejectsLegacyStringEvent() {
        assertRejected(
            "{" + VER + "\"animations\":{\"idle\":{\"loop\":\"loop\",\"keyframes\":{\"sound\":{\"0\":\"random.click\"}},\"bones\":{}}}}",
            "event keyframe");
    }

    @Test
    public void rejectsMissingFormatVersion() {
        assertRejected("{\"animations\":{}}", "format_version");
    }

    @Test
    public void rejectsUnsupportedFormatVersion() {
        assertRejected("{\"format_version\":\"2.0\",\"animations\":{}}",
            "unsupported format_version");
    }

    @Test
    public void rejectsNonStringFormatVersion() {
        assertRejected("{\"format_version\":1.0,\"animations\":{}}",
            "format_version must be a string");
    }

    @Test
    public void acceptsStrictPoseAndEventSchema() {
        Aero_AnimationBundle bundle = Aero_AnimationLoader.loadFromString(
            "{" + VER + "\"pivots\":{\"x\":[0,0,0]},\"animations\":{\"idle\":{\"loop\":\"loop\",\"length\":1,\"bones\":{\"x\":{\"rotation\":{\"0\":{\"value\":[0,0,0],\"interp\":\"linear\"}}}},\"keyframes\":{\"sound\":{\"0\":{\"name\":\"random.click\",\"locator\":\"x\"}}}}}}");

        Aero_AnimationClip clip = bundle.getClip("idle");
        assertNotNull(clip);
        assertEquals(Aero_AnimationLoop.LOOP, clip.loop);
        assertTrue(clip.hasEvents());
    }

    @Test
    public void pivotLookupDistinguishesExplicitZeroFromMiss() {
        Aero_AnimationBundle bundle = Aero_AnimationLoader.loadFromString(
            "{" + VER + "\"pivots\":{\"x\":[0,0,0]},\"animations\":{}}");
        float[] out = {7f, 8f, 9f};

        assertTrue(bundle.hasPivot("x"));
        assertTrue(bundle.getPivotInto("x", out));
        assertArrayEquals(new float[]{0f, 0f, 0f}, out, 0f);

        out[0] = 7f; out[1] = 8f; out[2] = 9f;
        assertFalse(bundle.hasPivot("missing"));
        assertFalse(bundle.getPivotInto("missing", out));
        assertArrayEquals(new float[]{7f, 8f, 9f}, out, 0f);
    }

    @Test
    public void clearCacheIsCallableWithoutSideEffects() {
        // The cache is keyed by resourcePath and only populated via load(...);
        // calling clearCache() with nothing cached must be a clean no-op so
        // tests can use it as a setUp() hook unconditionally.
        Aero_AnimationLoader.clearCache();
        Aero_AnimationLoader.clearCache();   // idempotent

        // Verify the loader still works after a clear.
        Aero_AnimationBundle bundle = Aero_AnimationLoader.loadFromString(
            "{" + VER + "\"animations\":{}}");
        assertNotNull(bundle);
    }

    private static void assertRejected(String json, String expectedMessage) {
        try {
            Aero_AnimationLoader.loadFromString(json);
            fail("expected schema rejection");
        } catch (RuntimeException expected) {
            assertTrue(expected.getMessage(), expected.getMessage().contains(expectedMessage));
        }
    }
}
