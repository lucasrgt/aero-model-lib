package aero.modellib;

import org.junit.Test;

import static org.junit.Assert.*;

/**
 * Asserts that validation errors thrown during clip / loader construction
 * carry the surrounding context (clip name, bone name, channel kind, time,
 * keyframe index, observed value). Without this context users had to grep
 * their .anim.json against a generic "channel arrays must not be null" — the
 * enriched messages should let them jump straight to the offending keyframe.
 */
public class AnimationErrorContextTest {

    private static final String VER = "\"format_version\":\"1.0\",";

    // ----- Aero_AnimationClip.Builder -----

    @Test
    public void clipLengthErrorIncludesClipNameAndValue() {
        try {
            Aero_AnimationClip.builder("walk").length(Float.NaN).build();
            fail("expected length validation");
        } catch (IllegalArgumentException ex) {
            assertContains(ex.getMessage(), "clip 'walk'");
            assertContains(ex.getMessage(), "length");
            assertContains(ex.getMessage(), "NaN");
        }
    }

    @Test
    public void emptyBoneNameErrorIncludesClipName() {
        try {
            Aero_AnimationClip.builder("walk").bone("");
            fail("expected empty bone name validation");
        } catch (IllegalArgumentException ex) {
            assertContains(ex.getMessage(), "clip 'walk'");
            assertContains(ex.getMessage(), "bone name");
        }
    }

    @Test
    public void eventTimeErrorIncludesClipName() {
        try {
            Aero_AnimationClip.builder("walk").event(Float.POSITIVE_INFINITY, "sound", "click", null);
            fail("expected event time validation");
        } catch (IllegalArgumentException ex) {
            assertContains(ex.getMessage(), "clip 'walk'");
            assertContains(ex.getMessage(), "event time");
            assertContains(ex.getMessage(), "Infinity");
        }
    }

    @Test
    public void eventChannelErrorIncludesClipName() {
        try {
            Aero_AnimationClip.builder("walk").event(0f, "", "click", null);
            fail("expected event channel validation");
        } catch (IllegalArgumentException ex) {
            assertContains(ex.getMessage(), "clip 'walk'");
            assertContains(ex.getMessage(), "channel");
        }
    }

    @Test
    public void eventNameErrorIncludesClipChannelAndTime() {
        try {
            Aero_AnimationClip.builder("walk").event(0.5f, "sound", "", null);
            fail("expected event name validation");
        } catch (IllegalArgumentException ex) {
            assertContains(ex.getMessage(), "clip 'walk'");
            assertContains(ex.getMessage(), "channel 'sound'");
            assertContains(ex.getMessage(), "0.5");
        }
    }

    // ----- ChannelTrack via BoneBuilder -----

    @Test
    public void channelArraysNullErrorIncludesClipBoneChannel() {
        try {
            Aero_AnimationClip.builder("walk").bone("leg").rotation(null, null, null);
            fail("expected null channel rejection");
        } catch (IllegalArgumentException ex) {
            assertContains(ex.getMessage(), "clip 'walk'");
            assertContains(ex.getMessage(), "bone 'leg'");
            assertContains(ex.getMessage(), "rotation");
        }
    }

    @Test
    public void channelLengthMismatchErrorIncludesObservedLengths() {
        try {
            Aero_AnimationClip.builder("walk").bone("leg").position(
                new float[]{0f, 1f},
                new float[][]{{0f, 0f, 0f}},
                new Aero_Easing[]{Aero_Easing.LINEAR, Aero_Easing.LINEAR});
            fail("expected length mismatch rejection");
        } catch (IllegalArgumentException ex) {
            assertContains(ex.getMessage(), "clip 'walk'");
            assertContains(ex.getMessage(), "bone 'leg'");
            assertContains(ex.getMessage(), "position");
            assertContains(ex.getMessage(), "times=2");
            assertContains(ex.getMessage(), "values=1");
        }
    }

    @Test
    public void keyframeTimeNonFiniteErrorIncludesIndex() {
        try {
            Aero_AnimationClip.builder("walk").bone("leg").rotation(
                new float[]{0f, Float.NaN},
                new float[][]{{0f, 0f, 0f}, {0f, 0f, 0f}},
                new Aero_Easing[]{Aero_Easing.LINEAR, Aero_Easing.LINEAR});
            fail("expected non-finite time rejection");
        } catch (IllegalArgumentException ex) {
            assertContains(ex.getMessage(), "clip 'walk'");
            assertContains(ex.getMessage(), "bone 'leg'");
            assertContains(ex.getMessage(), "rotation");
            assertContains(ex.getMessage(), "keyframe[1]");
            assertContains(ex.getMessage(), "NaN");
        }
    }

    @Test
    public void unsortedKeyframesErrorIncludesObservedValues() {
        try {
            Aero_AnimationClip.builder("walk").bone("leg").rotation(
                new float[]{1f, 0f},
                new float[][]{{0f, 0f, 0f}, {1f, 1f, 1f}},
                new Aero_Easing[]{Aero_Easing.LINEAR, Aero_Easing.LINEAR});
            fail("expected unsorted rejection");
        } catch (IllegalArgumentException ex) {
            assertContains(ex.getMessage(), "clip 'walk'");
            assertContains(ex.getMessage(), "bone 'leg'");
            assertContains(ex.getMessage(), "sorted");
            // observed values surface so the user knows which two keyframes
            assertContains(ex.getMessage(), "t[0]=1.0");
            assertContains(ex.getMessage(), "t[1]=0.0");
        }
    }

    @Test
    public void shortKeyframeValueErrorIncludesIndexAndLength() {
        try {
            Aero_AnimationClip.builder("walk").bone("leg").rotation(
                new float[]{0f},
                new float[][]{{0f, 1f}},   // only 2 components
                new Aero_Easing[]{Aero_Easing.LINEAR});
            fail("expected short-value rejection");
        } catch (IllegalArgumentException ex) {
            assertContains(ex.getMessage(), "clip 'walk'");
            assertContains(ex.getMessage(), "bone 'leg'");
            assertContains(ex.getMessage(), "keyframe[0]");
            assertContains(ex.getMessage(), "length=2");
        }
    }

    @Test
    public void nullEasingErrorIncludesIndex() {
        try {
            Aero_AnimationClip.builder("walk").bone("leg").rotation(
                new float[]{0f},
                new float[][]{{0f, 0f, 0f}},
                new Aero_Easing[]{null});
            fail("expected null-easing rejection");
        } catch (IllegalArgumentException ex) {
            assertContains(ex.getMessage(), "clip 'walk'");
            assertContains(ex.getMessage(), "bone 'leg'");
            assertContains(ex.getMessage(), "keyframe[0]");
            assertContains(ex.getMessage(), "easing");
        }
    }

    // ----- Aero_AnimationLoader -----

    @Test
    public void loaderLoopErrorIncludesClipName() {
        try {
            Aero_AnimationLoader.loadFromString(
                "{" + VER + "\"animations\":{\"idle\":{\"loop\":true,\"bones\":{}}}}");
            fail("expected loop rejection");
        } catch (RuntimeException ex) {
            assertContains(ex.getMessage(), "clip 'idle'");
            assertContains(ex.getMessage(), "loop");
        }
    }

    @Test
    public void loaderUnknownEasingErrorIncludesClipBoneChannelAndTime() {
        try {
            Aero_AnimationLoader.loadFromString(
                "{" + VER + "\"animations\":{\"idle\":{\"loop\":\"loop\","
                    + "\"bones\":{\"leg\":{\"rotation\":{\"0.5\":{\"value\":[0,0,0],\"interp\":\"nope\"}}}}}}}");
            fail("expected unknown-easing rejection");
        } catch (RuntimeException ex) {
            assertContains(ex.getMessage(), "clip 'idle'");
            assertContains(ex.getMessage(), "bone 'leg'");
            assertContains(ex.getMessage(), "rotation");
            assertContains(ex.getMessage(), "0.5");
            assertContains(ex.getMessage(), "unknown easing");
        }
    }

    @Test
    public void loaderPoseKeyframeWrongTypeIncludesClipBoneChannelAndTime() {
        try {
            // legacy bare-array pose keyframe — schema rejects it.
            Aero_AnimationLoader.loadFromString(
                "{" + VER + "\"animations\":{\"idle\":{\"loop\":\"loop\","
                    + "\"bones\":{\"leg\":{\"rotation\":{\"0\":[0,0,0]}}}}}}");
            fail("expected pose keyframe object rejection");
        } catch (RuntimeException ex) {
            assertContains(ex.getMessage(), "clip 'idle'");
            assertContains(ex.getMessage(), "bone 'leg'");
            assertContains(ex.getMessage(), "rotation");
            assertContains(ex.getMessage(), "pose keyframe");
        }
    }

    @Test
    public void loaderEventKeyframeWrongTypeIncludesClipChannelAndTime() {
        try {
            // legacy string event payload — schema rejects it.
            Aero_AnimationLoader.loadFromString(
                "{" + VER + "\"animations\":{\"idle\":{\"loop\":\"loop\","
                    + "\"keyframes\":{\"sound\":{\"0.25\":\"random.click\"}},\"bones\":{}}}}");
            fail("expected event keyframe object rejection");
        } catch (RuntimeException ex) {
            assertContains(ex.getMessage(), "clip 'idle'");
            assertContains(ex.getMessage(), "channel 'sound'");
            assertContains(ex.getMessage(), "0.25");
            assertContains(ex.getMessage(), "event keyframe");
        }
    }

    private static void assertContains(String message, String fragment) {
        assertNotNull("error message was null", message);
        assertTrue("expected message to contain '" + fragment + "', got: " + message,
            message.contains(fragment));
    }
}
