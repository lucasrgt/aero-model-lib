package aero.modellib;

import org.junit.Test;

import static org.junit.Assert.*;

public class AnimationLoaderStrictSchemaTest {

    @Test
    public void rejectsBooleanLoop() {
        assertRejected("{\"animations\":{\"idle\":{\"loop\":true,\"bones\":{}}}}", "loop");
    }

    @Test
    public void rejectsLegacyPoseArrayKeyframe() {
        assertRejected(
            "{\"animations\":{\"idle\":{\"loop\":\"loop\",\"bones\":{\"x\":{\"rotation\":{\"0\":[0,0,0]}}}}}}",
            "pose keyframe");
    }

    @Test
    public void rejectsUnknownEasing() {
        assertRejected(
            "{\"animations\":{\"idle\":{\"loop\":\"loop\",\"bones\":{\"x\":{\"rotation\":{\"0\":{\"value\":[0,0,0],\"interp\":\"nope\"}}}}}}}",
            "unknown easing");
    }

    @Test
    public void rejectsLegacyStringEvent() {
        assertRejected(
            "{\"animations\":{\"idle\":{\"loop\":\"loop\",\"keyframes\":{\"sound\":{\"0\":\"random.click\"}},\"bones\":{}}}}",
            "event keyframe");
    }

    @Test
    public void acceptsStrictPoseAndEventSchema() {
        Aero_AnimationBundle bundle = Aero_AnimationLoader.loadFromString(
            "{\"pivots\":{\"x\":[0,0,0]},\"animations\":{\"idle\":{\"loop\":\"loop\",\"length\":1,\"bones\":{\"x\":{\"rotation\":{\"0\":{\"value\":[0,0,0],\"interp\":\"linear\"}}}},\"keyframes\":{\"sound\":{\"0\":{\"name\":\"random.click\",\"locator\":\"x\"}}}}}}");

        Aero_AnimationClip clip = bundle.getClip("idle");
        assertNotNull(clip);
        assertEquals(Aero_AnimationLoop.LOOP, clip.loop);
        assertTrue(clip.hasEvents());
    }

    @Test
    public void pivotLookupDistinguishesExplicitZeroFromMiss() {
        Aero_AnimationBundle bundle = Aero_AnimationLoader.loadFromString(
            "{\"pivots\":{\"x\":[0,0,0]},\"animations\":{}}");
        float[] out = {7f, 8f, 9f};

        assertTrue(bundle.hasPivot("x"));
        assertTrue(bundle.getPivotInto("x", out));
        assertArrayEquals(new float[]{0f, 0f, 0f}, out, 0f);

        out[0] = 7f; out[1] = 8f; out[2] = 9f;
        assertFalse(bundle.hasPivot("missing"));
        assertFalse(bundle.getPivotInto("missing", out));
        assertArrayEquals(new float[]{7f, 8f, 9f}, out, 0f);
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
