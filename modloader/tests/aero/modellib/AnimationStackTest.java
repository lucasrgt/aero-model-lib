package aero.modellib;

import org.junit.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.*;

/**
 * Verifies the multi-controller / additive layering pipeline:
 *
 * <ul>
 *   <li>Replace mode at weight 1 mirrors the layer's clip exactly.</li>
 *   <li>Additive mode sums per-bone deltas across layers.</li>
 *   <li>Weight scales each layer's contribution.</li>
 *   <li>Scale composes multiplicatively in additive mode (1.5 × 1.5 = 2.25,
 *       not 1.5 + 1.5).</li>
 *   <li>Bones missing from a layer's clip are passed through unchanged.</li>
 * </ul>
 */
public class AnimationStackTest {

    private static final float DELTA = 1e-4f;

    @Test
    public void singleReplaceLayerMirrorsClip() {
        Aero_AnimationStack stack = Aero_AnimationStack.builder()
            .replace(playbackOf(constantRotClip("walk", 10f, 20f, 30f)))
            .build();
        stack.tick();

        float[] out = new float[3];
        assertTrue(stack.sampleRot("x", 0f, out));
        assertEquals(10f, out[0], DELTA);
        assertEquals(20f, out[1], DELTA);
        assertEquals(30f, out[2], DELTA);
    }

    @Test
    public void additiveLayerSumsRotation() {
        // Base rotation 10, additive layer adds 5 → expect 15.
        Aero_AnimationStack stack = Aero_AnimationStack.builder()
            .replace(playbackOf(constantRotClip("base", 10f, 0f, 0f)))
            .additive(playbackOf(constantRotClip("addon", 5f, 0f, 0f)))
            .build();
        stack.tick();

        float[] out = new float[3];
        assertTrue(stack.sampleRot("x", 0f, out));
        assertEquals(15f, out[0], DELTA);
    }

    @Test
    public void additiveWeightScalesContribution() {
        // Base 10 + additive 20 at weight 0.5 → 10 + 20 * 0.5 = 20.
        Aero_AnimationStack stack = Aero_AnimationStack.builder()
            .replace(playbackOf(constantRotClip("base", 10f, 0f, 0f)))
            .additive(playbackOf(constantRotClip("addon", 20f, 0f, 0f)), 0.5f)
            .build();
        stack.tick();

        float[] out = new float[3];
        stack.sampleRot("x", 0f, out);
        assertEquals(20f, out[0], DELTA);
    }

    @Test
    public void replaceWeightLerpsTowardNewValue() {
        // Base 10, second replace layer at value 30 weight 0.25 → 10 + (30-10)*0.25 = 15.
        Aero_AnimationStack stack = Aero_AnimationStack.builder()
            .replace(playbackOf(constantRotClip("base", 10f, 0f, 0f)))
            .add(Aero_AnimationLayer.builder(playbackOf(constantRotClip("override", 30f, 0f, 0f)))
                .weight(0.25f)
                .build())
            .build();
        stack.tick();

        float[] out = new float[3];
        stack.sampleRot("x", 0f, out);
        assertEquals(15f, out[0], DELTA);
    }

    @Test
    public void additiveScaleComposesMultiplicatively() {
        // Base scale 1.5, additive layer at scale 2.0 weight 1 → 1.5 * 2 = 3.0.
        // Tests that we do not accidentally lerp scale or sum it linearly.
        Aero_AnimationStack stack = Aero_AnimationStack.builder()
            .replace(playbackOf(constantSclClip("base",  1.5f)))
            .additive(playbackOf(constantSclClip("addon", 2.0f)))
            .build();
        stack.tick();

        float[] out = new float[3];
        stack.sampleScl("x", 0f, out);
        assertEquals(3.0f, out[0], DELTA);
    }

    @Test
    public void layerWithoutBoneIsPassthrough() {
        // First layer animates "head"; second layer animates "tail" only.
        // Sampling "head" should return the first layer's value untouched.
        Aero_AnimationStack stack = Aero_AnimationStack.builder()
            .replace(playbackOf(constantRotClipForBone("base", "head", 42f)))
            .additive(playbackOf(constantRotClipForBone("other", "tail", 99f)))
            .build();
        stack.tick();

        float[] out = new float[3];
        stack.sampleRot("head", 0f, out);
        assertEquals(42f, out[0], DELTA);
    }

    @Test
    public void emptyStackReturnsFalse() {
        Aero_AnimationStack stack = Aero_AnimationStack.empty();
        float[] out = new float[3];
        assertFalse(stack.sampleRot("anything", 0f, out));
    }

    @Test
    public void singleLayerStackMatchesPlaybackSampling() {
        Aero_AnimationClip clip = TestClips.clip("move", Aero_AnimationLoop.LOOP, 1f,
            new String[]{"x"},
            new float[][]{{0f, 1f}}, new float[][][]{{{0f, 0f, 0f}, {100f, 50f, 25f}}},
            new float[][]{{0f}}, new float[][][]{{{0f, 0f, 0f}}});
        Aero_AnimationPlayback playback = playbackOf(clip);
        playback.tick();

        Aero_AnimationStack stack = Aero_AnimationStack.builder()
            .replace(playback)
            .build();

        float partialTick = 0.5f;
        float[] direct = new float[3];
        float[] layered = new float[3];
        assertTrue(playback.sampleRotBlended(clip, clip.indexOfBone("x"), "x",
            playback.getInterpolatedTime(partialTick), partialTick, direct));
        assertTrue(stack.sampleRot("x", partialTick, layered));
        assertArrayEquals(direct, layered, DELTA);
    }

    @Test
    public void samplePoseMatchesSeparateChannelSampling() {
        Aero_AnimationStack stack = Aero_AnimationStack.builder()
            .replace(playbackOf(fullPoseClip("base", 10f, 2f, 1.5f)))
            .additive(playbackOf(fullPoseClip("addon", 5f, 4f, 2.0f)), 0.5f)
            .build();
        stack.tick();

        float partialTick = 0.25f;
        float[] rot = new float[3];
        float[] pos = new float[3];
        float[] scl = new float[3];
        float[] poseRot = new float[3];
        float[] posePos = new float[3];
        float[] poseScl = new float[3];

        assertTrue(stack.sampleRot("x", partialTick, rot));
        assertTrue(stack.samplePos("x", partialTick, pos));
        assertTrue(stack.sampleScl("x", partialTick, scl));
        assertTrue(stack.samplePose("x", partialTick, poseRot, posePos, poseScl));

        assertArrayEquals(rot, poseRot, DELTA);
        assertArrayEquals(pos, posePos, DELTA);
        assertArrayEquals(scl, poseScl, DELTA);
    }

    @Test
    public void layerBuilderRejectsInvalidWeight() {
        try {
            Aero_AnimationLayer.builder(playbackOf(constantRotClip("bad", 0f, 0f, 0f)))
                .weight(1.1f);
            fail("expected invalid layer weight");
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage().contains("weight"));
        }
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private static Aero_AnimationClip constantRotClip(String name, float rx, float ry, float rz) {
        return constantRotClipForBone(name, "x", rx, ry, rz);
    }

    private static Aero_AnimationClip constantRotClipForBone(String name, String bone, float rx) {
        return constantRotClipForBone(name, bone, rx, 0f, 0f);
    }

    private static Aero_AnimationClip constantRotClipForBone(String name, String bone,
                                                              float rx, float ry, float rz) {
        return Aero_AnimationClip.builder(name)
            .loop(Aero_AnimationLoop.LOOP)
            .length(1f)
            .bone(bone)
                .rotation(
                    new float[]{0f},
                    new float[][]{{rx, ry, rz}},
                    new Aero_Easing[]{Aero_Easing.LINEAR})
                .endBone()
            .build();
    }

    private static Aero_AnimationClip constantSclClip(String name, float s) {
        return Aero_AnimationClip.builder(name)
            .loop(Aero_AnimationLoop.LOOP)
            .length(1f)
            .bone("x")
                .scale(
                    new float[]{0f},
                    new float[][]{{s, s, s}},
                    new Aero_Easing[]{Aero_Easing.LINEAR})
                .endBone()
            .build();
    }

    private static Aero_AnimationClip fullPoseClip(String name, float rotX, float posX, float scale) {
        return Aero_AnimationClip.builder(name)
            .loop(Aero_AnimationLoop.LOOP)
            .length(1f)
            .bone("x")
                .rotation(
                    new float[]{0f},
                    new float[][]{{rotX, 0f, 0f}},
                    new Aero_Easing[]{Aero_Easing.LINEAR})
                .position(
                    new float[]{0f},
                    new float[][]{{posX, 0f, 0f}},
                    new Aero_Easing[]{Aero_Easing.LINEAR})
                .scale(
                    new float[]{0f},
                    new float[][]{{scale, scale, scale}},
                    new Aero_Easing[]{Aero_Easing.LINEAR})
                .endBone()
            .build();
    }

    private static Aero_AnimationPlayback playbackOf(Aero_AnimationClip clip) {
        Map clips = new HashMap();
        clips.put(clip.name, clip);
        Aero_AnimationBundle bundle = new Aero_AnimationBundle(clips, new HashMap(), new HashMap());
        return new Aero_AnimationDefinition()
            .state(0, clip.name)
            .createPlayback(bundle);
    }
}
