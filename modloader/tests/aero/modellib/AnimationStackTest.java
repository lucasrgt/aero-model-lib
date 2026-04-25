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
        Aero_AnimationStack stack = new Aero_AnimationStack()
            .add(new Aero_AnimationLayer(playbackOf(constantRotClip("walk", 10f, 20f, 30f))));
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
        Aero_AnimationStack stack = new Aero_AnimationStack()
            .add(new Aero_AnimationLayer(playbackOf(constantRotClip("base", 10f, 0f, 0f))))
            .add(new Aero_AnimationLayer(playbackOf(constantRotClip("addon", 5f, 0f, 0f))).additive(true));
        stack.tick();

        float[] out = new float[3];
        assertTrue(stack.sampleRot("x", 0f, out));
        assertEquals(15f, out[0], DELTA);
    }

    @Test
    public void additiveWeightScalesContribution() {
        // Base 10 + additive 20 at weight 0.5 → 10 + 20 * 0.5 = 20.
        Aero_AnimationStack stack = new Aero_AnimationStack()
            .add(new Aero_AnimationLayer(playbackOf(constantRotClip("base", 10f, 0f, 0f))))
            .add(new Aero_AnimationLayer(playbackOf(constantRotClip("addon", 20f, 0f, 0f)))
                .additive(true).weight(0.5f));
        stack.tick();

        float[] out = new float[3];
        stack.sampleRot("x", 0f, out);
        assertEquals(20f, out[0], DELTA);
    }

    @Test
    public void replaceWeightLerpsTowardNewValue() {
        // Base 10, second replace layer at value 30 weight 0.25 → 10 + (30-10)*0.25 = 15.
        Aero_AnimationStack stack = new Aero_AnimationStack()
            .add(new Aero_AnimationLayer(playbackOf(constantRotClip("base", 10f, 0f, 0f))))
            .add(new Aero_AnimationLayer(playbackOf(constantRotClip("override", 30f, 0f, 0f)))
                .weight(0.25f));
        stack.tick();

        float[] out = new float[3];
        stack.sampleRot("x", 0f, out);
        assertEquals(15f, out[0], DELTA);
    }

    @Test
    public void additiveScaleComposesMultiplicatively() {
        // Base scale 1.5, additive layer at scale 2.0 weight 1 → 1.5 * 2 = 3.0.
        // Tests that we do not accidentally lerp scale or sum it linearly.
        Aero_AnimationStack stack = new Aero_AnimationStack()
            .add(new Aero_AnimationLayer(playbackOf(constantSclClip("base",  1.5f))))
            .add(new Aero_AnimationLayer(playbackOf(constantSclClip("addon", 2.0f))).additive(true));
        stack.tick();

        float[] out = new float[3];
        stack.sampleScl("x", 0f, out);
        assertEquals(3.0f, out[0], DELTA);
    }

    @Test
    public void layerWithoutBoneIsPassthrough() {
        // First layer animates "head"; second layer animates "tail" only.
        // Sampling "head" should return the first layer's value untouched.
        Aero_AnimationStack stack = new Aero_AnimationStack()
            .add(new Aero_AnimationLayer(playbackOf(constantRotClipForBone("base", "head", 42f))))
            .add(new Aero_AnimationLayer(playbackOf(constantRotClipForBone("other", "tail", 99f))).additive(true));
        stack.tick();

        float[] out = new float[3];
        stack.sampleRot("head", 0f, out);
        assertEquals(42f, out[0], DELTA);
    }

    @Test
    public void emptyStackReturnsFalse() {
        Aero_AnimationStack stack = new Aero_AnimationStack();
        float[] out = new float[3];
        assertFalse(stack.sampleRot("anything", 0f, out));
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private static Aero_AnimationClip constantRotClip(String name, float rx, float ry, float rz) {
        return new Aero_AnimationClip(
            name, Aero_AnimationClip.LOOP_TYPE_LOOP, 1f,
            new String[]{"x"},
            new float[][]{{0f}}, new float[][][]{{{rx, ry, rz}}}, null,
            new float[][]{{0f}}, new float[][][]{{{0f, 0f, 0f}}}, null,
            null, null, null);
    }

    private static Aero_AnimationClip constantRotClipForBone(String name, String bone, float rx) {
        return new Aero_AnimationClip(
            name, Aero_AnimationClip.LOOP_TYPE_LOOP, 1f,
            new String[]{bone},
            new float[][]{{0f}}, new float[][][]{{{rx, 0f, 0f}}}, null,
            new float[][]{{0f}}, new float[][][]{{{0f, 0f, 0f}}}, null,
            null, null, null);
    }

    private static Aero_AnimationClip constantSclClip(String name, float s) {
        return new Aero_AnimationClip(
            name, Aero_AnimationClip.LOOP_TYPE_LOOP, 1f,
            new String[]{"x"},
            new float[][]{{0f}}, new float[][][]{{{0f, 0f, 0f}}}, null,
            new float[][]{{0f}}, new float[][][]{{{0f, 0f, 0f}}}, null,
            new float[][]{{0f}}, new float[][][]{{{s, s, s}}}, null);
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
