package aero.modellib;

import org.junit.Before;
import org.junit.Test;

import aero.modellib.animation.Aero_AnimationClip;
import aero.modellib.animation.Aero_AnimationLoop;

import static org.junit.Assert.*;

public class AnimClipTest {

    private static final float DELTA = 0.01f;

    private Aero_AnimationClip fanClip;

    @Before
    public void setUp() {
        String[] bones = {"fan"};
        float[][] rotTimes = {{0f, 1f}};
        float[][][] rotValues = {{{0f, 0f, 0f}, {0f, 360f, 0f}}};
        float[][] posTimes = {{0f, 1f}};
        float[][][] posValues = {{{0f, 0f, 0f}, {0f, 2f, 0f}}};
        fanClip = TestClips.clip("spin", Aero_AnimationLoop.LOOP, 1.0f,
            bones, rotTimes, rotValues, posTimes, posValues);
    }

    @Test
    public void clipFieldsAreCorrect() {
        assertEquals("spin", fanClip.name);
        assertEquals(Aero_AnimationLoop.LOOP, fanClip.loop);
        assertEquals(1.0f, fanClip.length, DELTA);
    }

    @Test
    public void indexOfBoneReturnsCorrectIndex() {
        assertEquals(0, fanClip.indexOfBone("fan"));
    }

    @Test
    public void indexOfBoneReturnsNegativeOneForMissing() {
        assertEquals(-1, fanClip.indexOfBone("nonexistent"));
    }

    @Test
    public void sampleRotAtExactFirstKeyframe() {
        assertVector(TestClips.sampleRot(fanClip, 0, 0f), 0f, 0f, 0f);
    }

    @Test
    public void sampleRotAtExactLastKeyframe() {
        assertVector(TestClips.sampleRot(fanClip, 0, 1f), 0f, 360f, 0f);
    }

    @Test
    public void sampleRotAtMidpointInterpolates() {
        assertVector(TestClips.sampleRot(fanClip, 0, 0.5f), 0f, 180f, 0f);
    }

    @Test
    public void sampleRotBeforeFirstKeyframeClampsToFirst() {
        assertVector(TestClips.sampleRot(fanClip, 0, -0.5f), 0f, 0f, 0f);
    }

    @Test
    public void sampleRotAfterLastKeyframeClampsToLast() {
        assertVector(TestClips.sampleRot(fanClip, 0, 2.0f), 0f, 360f, 0f);
    }

    @Test
    public void sampleRotWithInvalidBoneIdxReturnsFalse() {
        assertNull(TestClips.sampleRot(fanClip, -1, 0f));
        assertFalse(fanClip.sampleRotInto(-1, 0f, new float[3]));
    }

    @Test
    public void samplePosAtExactFirstKeyframe() {
        assertVector(TestClips.samplePos(fanClip, 0, 0f), 0f, 0f, 0f);
    }

    @Test
    public void samplePosAtExactLastKeyframe() {
        assertVector(TestClips.samplePos(fanClip, 0, 1f), 0f, 2f, 0f);
    }

    @Test
    public void samplePosAtMidpointInterpolates() {
        assertVector(TestClips.samplePos(fanClip, 0, 0.5f), 0f, 1f, 0f);
    }

    @Test
    public void samplePosWithInvalidBoneIdxReturnsFalse() {
        assertNull(TestClips.samplePos(fanClip, -1, 0f));
        assertFalse(fanClip.samplePosInto(-1, 0f, new float[3]));
    }

    @Test
    public void sampleSclWithInvalidBoneIdxReturnsFalse() {
        assertNull(TestClips.sampleScl(fanClip, -1, 0f));
        assertFalse(fanClip.sampleSclInto(-1, 0f, new float[3]));
    }

    @Test
    public void multipleBonesHaveIndependentKeyframes() {
        String[] bones = {"arm", "leg"};
        float[][] rotTimes = {
            {0f, 1f},
            {0f, 0.5f, 1f}
        };
        float[][][] rotValues = {
            {{0f, 0f, 0f}, {90f, 0f, 0f}},
            {{0f, 0f, 0f}, {0f, 0f, 45f}, {0f, 0f, 0f}}
        };
        float[][] posTimes = {{0f}, {0f}};
        float[][][] posValues = {{{0f, 0f, 0f}}, {{0f, 0f, 0f}}};
        Aero_AnimationClip clip = TestClips.clip("walk", Aero_AnimationLoop.PLAY_ONCE, 1.0f,
            bones, rotTimes, rotValues, posTimes, posValues);

        int armIdx = clip.indexOfBone("arm");
        int legIdx = clip.indexOfBone("leg");

        assertEquals(0, armIdx);
        assertEquals(1, legIdx);
        assertVector(TestClips.sampleRot(clip, armIdx, 0.5f), 45f, 0f, 0f);
        assertVector(TestClips.sampleRot(clip, legIdx, 0.25f), 0f, 0f, 22.5f);
    }

    @Test
    public void singleKeyframeAlwaysReturnsThatValue() {
        String[] bones = {"static"};
        float[][] rotTimes = {{0f}};
        float[][][] rotValues = {{{10f, 20f, 30f}}};
        float[][] posTimes = {{0f}};
        float[][][] posValues = {{{1f, 2f, 3f}}};
        Aero_AnimationClip clip = TestClips.clip("idle", Aero_AnimationLoop.PLAY_ONCE, 1.0f,
            bones, rotTimes, rotValues, posTimes, posValues);

        assertVector(TestClips.sampleRot(clip, 0, -1f), 10f, 20f, 30f);
        assertVector(TestClips.sampleRot(clip, 0, 0f), 10f, 20f, 30f);
        assertVector(TestClips.sampleRot(clip, 0, 5f), 10f, 20f, 30f);
    }

    @Test
    public void sampleRotAtQuarterPoints() {
        assertVector(TestClips.sampleRot(fanClip, 0, 0.25f), 0f, 90f, 0f);
        assertVector(TestClips.sampleRot(fanClip, 0, 0.75f), 0f, 270f, 0f);
    }

    @Test
    public void playOnceClipFieldIsPlayOnce() {
        String[] bones = {"bone"};
        float[][] times = {{0f}};
        float[][][] values = {{{0f, 0f, 0f}}};
        Aero_AnimationClip clip = TestClips.clip("once", Aero_AnimationLoop.PLAY_ONCE, 2.0f,
            bones, times, values, times, values);

        assertEquals("once", clip.name);
        assertEquals(Aero_AnimationLoop.PLAY_ONCE, clip.loop);
        assertEquals(2.0f, clip.length, DELTA);
    }

    @Test
    public void builderRejectsUnsortedKeyframes() {
        try {
            Aero_AnimationClip.builder("bad")
                .bone("x")
                .rotation(new float[]{1f, 0f},
                    new float[][]{{0f, 0f, 0f}, {1f, 1f, 1f}},
                    TestClips.linearEasings(2));
            fail("expected unsorted keyframes to be rejected");
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage().contains("sorted"));
        }
    }

    private static void assertVector(float[] actual, float x, float y, float z) {
        assertNotNull(actual);
        assertEquals(x, actual[0], DELTA);
        assertEquals(y, actual[1], DELTA);
        assertEquals(z, actual[2], DELTA);
    }
}
