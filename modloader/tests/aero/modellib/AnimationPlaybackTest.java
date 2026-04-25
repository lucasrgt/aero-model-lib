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
            .createPlayback(bundle(loopClip("spin", 1f)));

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
            .createPlayback(bundle(loopClip("spin", 1f), loopClip("idle", 1f)));

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
            .createPlayback(bundle(loopClip("spin", 0.1f)));

        playback.tick();
        playback.tick();

        assertEquals(0.075f, playback.getInterpolatedTime(0.5f), DELTA);
    }

    @Test
    public void missingClipProducesZeroTime() {
        Aero_AnimationPlayback playback = new Aero_AnimationDefinition()
            .state(0, "missing")
            .createPlayback(bundle(loopClip("spin", 1f)));

        playback.tick();

        assertNull(playback.getCurrentClip());
        assertEquals(0f, playback.getInterpolatedTime(1f), DELTA);
    }

    private static Aero_AnimationBundle bundle(Aero_AnimationClip... clipsIn) {
        Map clips = new HashMap();
        for (int i = 0; i < clipsIn.length; i++) {
            clips.put(clipsIn[i].name, clipsIn[i]);
        }
        return new Aero_AnimationBundle(clips, new HashMap(), new HashMap());
    }

    private static Aero_AnimationClip loopClip(String name, float length) {
        return new Aero_AnimationClip(
            name, Aero_AnimationClip.LOOP_TYPE_LOOP, length,
            new String[]{"bone"},
            new float[][]{{0f}}, new float[][][]{{{0f, 0f, 0f}}}, null,
            new float[][]{{0f}}, new float[][][]{{{0f, 0f, 0f}}}, null,
            null, null, null
        );
    }

    private static Aero_AnimationClip clipWithLoopType(String name, float length, int loopType) {
        return new Aero_AnimationClip(
            name, loopType, length,
            new String[]{"bone"},
            new float[][]{{0f}}, new float[][][]{{{0f, 0f, 0f}}}, null,
            new float[][]{{0f}}, new float[][][]{{{0f, 0f, 0f}}}, null,
            null, null, null
        );
    }

    @Test
    public void playOnceClampsAndIsFinished() {
        Aero_AnimationPlayback playback = new Aero_AnimationDefinition()
            .state(0, "once")
            .createPlayback(bundle(clipWithLoopType("once", 0.1f, Aero_AnimationClip.LOOP_TYPE_PLAY_ONCE)));

        // 0.1s clip = 2 ticks; the third tick should clamp at length and
        // flip isFinished() so a state machine can advance.
        for (int i = 0; i < 5; i++) playback.tick();

        assertEquals(0.1f, playback.getInterpolatedTime(1f), DELTA);
        assertTrue("PLAY_ONCE clip should be finished after clamping", playback.isFinished());
    }

    @Test
    public void holdClampsButNeverFinishes() {
        Aero_AnimationPlayback playback = new Aero_AnimationDefinition()
            .state(0, "hold")
            .createPlayback(bundle(clipWithLoopType("hold", 0.1f, Aero_AnimationClip.LOOP_TYPE_HOLD)));

        for (int i = 0; i < 5; i++) playback.tick();

        // HOLD clamps visually like PLAY_ONCE — but isFinished() stays false
        // so callers can keep the pose without auto-advancing.
        assertEquals(0.1f, playback.getInterpolatedTime(1f), DELTA);
        assertFalse("HOLD clips should never report finished", playback.isFinished());
    }

    @Test
    public void loopNeverFinishes() {
        Aero_AnimationPlayback playback = new Aero_AnimationDefinition()
            .state(0, "spin")
            .createPlayback(bundle(loopClip("spin", 0.1f)));

        for (int i = 0; i < 10; i++) playback.tick();

        assertFalse("LOOP clips should never report finished", playback.isFinished());
    }

    // -----------------------------------------------------------------------
    // Transition / blending tests
    // -----------------------------------------------------------------------

    @Test
    public void transitionAlphaRampsLinearlyOverTicks() {
        Aero_AnimationPlayback playback = new Aero_AnimationDefinition()
            .state(0, "a").state(1, "b")
            .createPlayback(bundle(loopClip("a", 1f), loopClip("b", 1f)));

        playback.setStateWithTransition(1, 4);
        assertTrue(playback.inTransition());
        // Right after setState, no ticks consumed yet → alpha=0 (full snapshot).
        assertEquals(0f, playback.getTransitionAlpha(0f), DELTA);

        playback.tick();
        // After 1 tick of a 4-tick transition, alpha = 1/4 at partialTick=0.
        assertEquals(0.25f, playback.getTransitionAlpha(0f), DELTA);

        playback.tick();
        playback.tick();
        playback.tick();
        // 4 ticks done — transition finished, alpha clamps at 1.
        assertEquals(1f, playback.getTransitionAlpha(0f), DELTA);
        assertFalse(playback.inTransition());
    }

    @Test
    public void zeroTickTransitionBehavesLikePlainSetState() {
        // Sanity: passing transitionTicks=0 should NOT enter a transition,
        // just snap the state. Defensive against callers that compute the
        // tick count dynamically and might get 0.
        Aero_AnimationPlayback playback = new Aero_AnimationDefinition()
            .state(0, "a").state(1, "b")
            .createPlayback(bundle(loopClip("a", 1f), loopClip("b", 1f)));

        playback.setStateWithTransition(1, 0);
        assertFalse(playback.inTransition());
        assertEquals(1, playback.currentState);
    }

    @Test
    public void transitionToSameClipIsNoOp() {
        // setStateWithTransition between two states that map to the SAME
        // clip should not enter a transition (nothing to fade).
        Aero_AnimationPlayback playback = new Aero_AnimationDefinition()
            .state(0, "a").state(1, "a")
            .createPlayback(bundle(loopClip("a", 1f)));

        playback.setStateWithTransition(1, 4);
        assertFalse("identical-clip transitions should be skipped", playback.inTransition());
        assertEquals(1, playback.currentState);
    }

    @Test
    public void blendedSampleLerpsBetweenSnapshotAndNewClip() {
        // Build two clips that animate the same bone with distinct values
        // so we can verify the lerp formula:
        //   a → bone "x" rotation [10, 20, 30] at all times
        //   b → bone "x" rotation [50, 60, 70] at all times
        // After capturing snapshot from a (=10,20,30) and ticking 1/2 of a
        // 4-tick transition (alpha=0.5), sampleRotBlended should return
        // (snap + (new - snap) * 0.5) = (30, 40, 50).
        Aero_AnimationClip clipA = constantRotClip("a", 10f, 20f, 30f);
        Aero_AnimationClip clipB = constantRotClip("b", 50f, 60f, 70f);

        Aero_AnimationPlayback playback = new Aero_AnimationDefinition()
            .state(0, "a").state(1, "b")
            .createPlayback(bundle(clipA, clipB));

        playback.setStateWithTransition(1, 4);
        playback.tick();
        playback.tick();
        // After 2 ticks of a 4-tick transition at partialTick=0 → alpha=0.5
        float[] out = new float[3];
        boolean ok = playback.sampleRotBlended(clipB, 0, "x", 0f, 0f, out);
        assertTrue(ok);
        assertEquals(30f, out[0], DELTA);
        assertEquals(40f, out[1], DELTA);
        assertEquals(50f, out[2], DELTA);
    }

    private static Aero_AnimationClip constantRotClip(String name, float rx, float ry, float rz) {
        return new Aero_AnimationClip(
            name, Aero_AnimationClip.LOOP_TYPE_LOOP, 1f,
            new String[]{"x"},
            new float[][]{{0f}}, new float[][][]{{{rx, ry, rz}}}, null,
            new float[][]{{0f}}, new float[][][]{{{0f, 0f, 0f}}}, null,
            null, null, null
        );
    }

    // ----------------------------------------------------------------------
    // getAnimatedPivot tests
    // ----------------------------------------------------------------------

    @Test
    public void animatedPivotReturnsRestPositionWhenBoneIsStill() {
        // Bundle has pivot for "x" at (0.5, 1.0, 0.5). Clip animates "x"
        // with a constant rotation but ZERO position offset, so the
        // animated pivot equals the rest pivot.
        Map pivots = new HashMap();
        pivots.put("x", new float[]{0.5f, 1.0f, 0.5f});
        Map clips = new HashMap();
        clips.put("idle", constantRotClip("idle", 0f, 0f, 0f));
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
        // Pivot at (0, 0, 0); clip animates position to (16, 8, 0) in pixels.
        // 16 px = 1 block, 8 px = 0.5 block. Expect (1.0, 0.5, 0).
        Map pivots = new HashMap();
        pivots.put("x", new float[]{0f, 0f, 0f});
        Aero_AnimationClip clip = new Aero_AnimationClip(
            "moving", Aero_AnimationClip.LOOP_TYPE_LOOP, 1f,
            new String[]{"x"},
            new float[][]{{0f}}, new float[][][]{{{0f, 0f, 0f}}}, null,
            new float[][]{{0f}}, new float[][][]{{{16f, 8f, 0f}}}, null,
            null, null, null);
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
    public void animatedPivotReturnsFalseForUnknownBone() {
        Aero_AnimationPlayback pb = new Aero_AnimationDefinition()
            .state(0, "idle").createPlayback(bundle(loopClip("idle", 1f)));

        float[] out = {99f, 99f, 99f};
        assertFalse(pb.getAnimatedPivot("not_a_bone", 0f, out));
        // Out left untouched on miss so callers can keep their own fallback.
        assertEquals(99f, out[0], DELTA);
    }
}
