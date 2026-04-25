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
            name, true, length,
            new String[]{"bone"},
            new float[][]{{0f}},
            new float[][][]{{{0f, 0f, 0f}}},
            new float[][]{{0f}},
            new float[][][]{{{0f, 0f, 0f}}}
        );
    }
}
