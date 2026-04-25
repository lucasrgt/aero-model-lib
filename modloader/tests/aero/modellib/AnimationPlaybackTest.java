package aero.modellib;

import org.junit.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.*;

public class AnimationPlaybackTest {

    private static final float DELTA = 0.0001f;

    @Test
    public void advancesAndInterpolatesPlaybackTime() {
        Aero_AnimationPlayback playback = new Aero_AnimationDefinition()
            .state(0, "spin")
            .createPlayback(TestClips.bundle(TestClips.loopClip("spin", 1f)));

        playback.tick();

        assertEquals(0.025f, playback.getInterpolatedTime(0.5f), DELTA);
        assertEquals(0.05f, playback.getInterpolatedTime(1f), DELTA);
    }

    @Test
    public void switchingToSameClipPreservesTimeButDifferentClipResets() {
        Aero_AnimationPlayback playback = new Aero_AnimationDefinition()
            .state(0, "spin")
            .state(1, "spin")
            .state(2, "idle")
            .createPlayback(TestClips.bundle(
                TestClips.loopClip("spin", 1f),
                TestClips.loopClip("idle", 1f)));

        playback.tick();
        playback.setState(1);
        assertEquals(0.05f, playback.getInterpolatedTime(1f), DELTA);

        playback.setState(2);
        assertEquals(0f, playback.getInterpolatedTime(1f), DELTA);
        assertEquals("idle", playback.getCurrentClip().name);
    }

    @Test
    public void loopWrapInterpolatesForwardAcrossBoundary() {
        Aero_AnimationPlayback playback = new Aero_AnimationDefinition()
            .state(0, "spin")
            .createPlayback(TestClips.bundle(TestClips.loopClip("spin", 0.1f)));

        playback.tick();
        playback.tick();

        assertEquals(0.075f, playback.getInterpolatedTime(0.5f), DELTA);
    }

    @Test
    public void missingClipProducesZeroTime() {
        Aero_AnimationPlayback playback = new Aero_AnimationDefinition()
            .state(0, "missing")
            .createPlayback(TestClips.bundle(TestClips.loopClip("spin", 1f)));

        playback.tick();

        assertNull(playback.getCurrentClip());
        assertEquals(0f, playback.getInterpolatedTime(1f), DELTA);
    }

    @Test
    public void playOnceClampsAndIsFinished() {
        Aero_AnimationPlayback playback = new Aero_AnimationDefinition()
            .state(0, "once")
            .createPlayback(TestClips.bundle(clipWithLoop("once", 0.1f, Aero_AnimationLoop.PLAY_ONCE)));

        for (int i = 0; i < 5; i++) playback.tick();

        assertEquals(0.1f, playback.getInterpolatedTime(1f), DELTA);
        assertTrue(playback.isFinished());
    }

    @Test
    public void holdClampsButNeverFinishes() {
        Aero_AnimationPlayback playback = new Aero_AnimationDefinition()
            .state(0, "hold")
            .createPlayback(TestClips.bundle(clipWithLoop("hold", 0.1f, Aero_AnimationLoop.HOLD_ON_LAST_FRAME)));

        for (int i = 0; i < 5; i++) playback.tick();

        assertEquals(0.1f, playback.getInterpolatedTime(1f), DELTA);
        assertFalse(playback.isFinished());
    }

    @Test
    public void loopNeverFinishes() {
        Aero_AnimationPlayback playback = new Aero_AnimationDefinition()
            .state(0, "spin")
            .createPlayback(TestClips.bundle(TestClips.loopClip("spin", 0.1f)));

        for (int i = 0; i < 10; i++) playback.tick();

        assertFalse(playback.isFinished());
    }

    @Test
    public void transitionAlphaRampsLinearlyOverTicks() {
        Aero_AnimationPlayback playback = new Aero_AnimationDefinition()
            .state(0, "a").state(1, "b")
            .createPlayback(TestClips.bundle(TestClips.loopClip("a", 1f), TestClips.loopClip("b", 1f)));

        playback.setStateWithTransition(1, 4);
        assertTrue(playback.inTransition());
        assertEquals(0f, playback.getTransitionAlpha(0f), DELTA);

        playback.tick();
        assertEquals(0.25f, playback.getTransitionAlpha(0f), DELTA);

        playback.tick();
        playback.tick();
        playback.tick();
        assertEquals(1f, playback.getTransitionAlpha(0f), DELTA);
        assertFalse(playback.inTransition());
    }

    @Test
    public void zeroTickTransitionBehavesLikePlainSetState() {
        Aero_AnimationPlayback playback = new Aero_AnimationDefinition()
            .state(0, "a").state(1, "b")
            .createPlayback(TestClips.bundle(TestClips.loopClip("a", 1f), TestClips.loopClip("b", 1f)));

        playback.setStateWithTransition(1, 0);
        assertFalse(playback.inTransition());
        assertEquals(1, playback.getCurrentState());
    }

    @Test
    public void transitionToSameClipIsNoOp() {
        Aero_AnimationPlayback playback = new Aero_AnimationDefinition()
            .state(0, "a").state(1, "a")
            .createPlayback(TestClips.bundle(TestClips.loopClip("a", 1f)));

        playback.setStateWithTransition(1, 4);
        assertFalse(playback.inTransition());
        assertEquals(1, playback.getCurrentState());
    }

    @Test
    public void blendedSampleLerpsBetweenSnapshotAndNewClip() {
        Aero_AnimationClip clipA = TestClips.constantRotClip("a", 10f, 20f, 30f);
        Aero_AnimationClip clipB = TestClips.constantRotClip("b", 50f, 60f, 70f);

        Aero_AnimationPlayback playback = new Aero_AnimationDefinition()
            .state(0, "a").state(1, "b")
            .createPlayback(TestClips.bundle(clipA, clipB));

        playback.setStateWithTransition(1, 4);
        playback.tick();
        playback.tick();

        float[] out = new float[3];
        assertTrue(playback.sampleRotBlended(clipB, 0, "x", 0f, 0f, out));
        assertEquals(30f, out[0], DELTA);
        assertEquals(40f, out[1], DELTA);
        assertEquals(50f, out[2], DELTA);
    }

    @Test
    public void animatedPivotReturnsRestPositionWhenBoneIsStill() {
        Map pivots = new HashMap();
        pivots.put("x", new float[]{0.5f, 1.0f, 0.5f});
        Map clips = new HashMap();
        clips.put("idle", TestClips.constantRotClip("idle", 0f, 0f, 0f));
        Aero_AnimationBundle bundle = new Aero_AnimationBundle(clips, pivots, new HashMap());

        Aero_AnimationPlayback pb = new Aero_AnimationDefinition()
            .state(0, "idle").createPlayback(bundle);
        pb.tick();

        float[] out = new float[3];
        assertTrue(pb.getAnimatedPivot("x", 0f, out));
        assertEquals(0.5f, out[0], DELTA);
        assertEquals(1.0f, out[1], DELTA);
        assertEquals(0.5f, out[2], DELTA);
    }

    @Test
    public void animatedPivotAppliesPositionOffsetInBlockUnits() {
        Map pivots = new HashMap();
        pivots.put("x", new float[]{0f, 0f, 0f});
        Aero_AnimationClip clip = TestClips.clip("moving", Aero_AnimationLoop.LOOP, 1f,
            new String[]{"x"},
            new float[][]{{0f}}, new float[][][]{{{0f, 0f, 0f}}},
            new float[][]{{0f}}, new float[][][]{{{16f, 8f, 0f}}});
        Map clips = new HashMap();
        clips.put("moving", clip);
        Aero_AnimationBundle bundle = new Aero_AnimationBundle(clips, pivots, new HashMap());

        Aero_AnimationPlayback pb = new Aero_AnimationDefinition()
            .state(0, "moving").createPlayback(bundle);
        pb.tick();

        float[] out = new float[3];
        assertTrue(pb.getAnimatedPivot("x", 0f, out));
        assertEquals(1.0f, out[0], DELTA);
        assertEquals(0.5f, out[1], DELTA);
        assertEquals(0.0f, out[2], DELTA);
    }

    @Test
    public void animatedPivotReturnsFalseForUnknownBoneWithoutTouchingOut() {
        Aero_AnimationPlayback pb = new Aero_AnimationDefinition()
            .state(0, "idle")
            .createPlayback(TestClips.bundle(TestClips.loopClip("idle", 1f)));

        float[] out = {99f, 98f, 97f};
        assertFalse(pb.getAnimatedPivot("not_a_bone", 0f, out));
        assertEquals(99f, out[0], DELTA);
        assertEquals(98f, out[1], DELTA);
        assertEquals(97f, out[2], DELTA);
    }

    @Test
    public void animatedPivotHotPathReusesCallerBuffer() {
        Map pivots = new HashMap();
        pivots.put("x", new float[]{0f, 0f, 0f});
        Aero_AnimationClip clip = TestClips.clip("moving", Aero_AnimationLoop.LOOP, 1f,
            new String[]{"x"},
            new float[][]{{0f}}, new float[][][]{{{0f, 0f, 0f}}},
            new float[][]{{0f}}, new float[][][]{{{16f, 0f, 0f}}});
        Map clips = new HashMap();
        clips.put("moving", clip);
        Aero_AnimationPlayback pb = new Aero_AnimationDefinition()
            .state(0, "moving")
            .createPlayback(new Aero_AnimationBundle(clips, pivots, new HashMap()));

        float[] out = new float[3];
        for (int i = 0; i < 1000; i++) {
            assertTrue(pb.getAnimatedPivot("x", 0f, out));
            assertEquals(1f, out[0], DELTA);
        }
    }

    private static Aero_AnimationClip clipWithLoop(String name, float length, Aero_AnimationLoop loop) {
        return TestClips.clip(name, loop, length,
            new String[]{"bone"},
            new float[][]{{0f}}, new float[][][]{{{0f, 0f, 0f}}},
            new float[][]{{0f}}, new float[][][]{{{0f, 0f, 0f}}});
    }
}
