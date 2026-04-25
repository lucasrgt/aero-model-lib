package aero.modellib;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.*;

public class KeyframeEventTest {

    @Test
    public void singleEventFiresOnce() {
        Aero_AnimationClip clip = clipWithEvents(1f, true,
            new float[]{0.05f},
            new String[]{"sound"},
            new String[]{"hit"});
        Aero_AnimationPlayback playback = playbackOf(clip);

        Recorder rec = new Recorder();
        playback.setEventListener(rec);

        playback.tick();
        playback.tick();

        assertEquals(1, rec.calls.size());
        assertEquals("sound", rec.get(0).channel);
        assertEquals("hit", rec.get(0).data);
        assertEquals(0.05f, rec.get(0).time, 1e-4f);
    }

    @Test
    public void eventsFireInChronologicalOrder() {
        Aero_AnimationClip clip = clipWithEvents(1f, true,
            new float[]{0.15f, 0.05f, 0.10f},
            new String[]{"sound", "sound", "particle"},
            new String[]{"c", "a", "b"});
        Aero_AnimationPlayback playback = playbackOf(clip);

        Recorder rec = new Recorder();
        playback.setEventListener(rec);

        for (int i = 0; i < 4; i++) playback.tick();

        assertEquals(3, rec.calls.size());
        assertEquals("a", rec.get(0).data);
        assertEquals("b", rec.get(1).data);
        assertEquals("c", rec.get(2).data);
    }

    @Test
    public void wrappedLoopFiresEventsAcrossBoundary() {
        Aero_AnimationClip clip = clipWithEvents(0.1f, true,
            new float[]{0.02f, 0.08f},
            new String[]{"custom", "custom"},
            new String[]{"early", "late"});
        Aero_AnimationPlayback playback = playbackOf(clip);

        Recorder rec = new Recorder();
        playback.setEventListener(rec);

        for (int i = 0; i < 6; i++) playback.tick();

        assertEquals(6, rec.calls.size());
        for (int i = 0; i < 6; i++) {
            assertEquals("call " + i, (i % 2 == 0) ? "early" : "late", rec.get(i).data);
        }
    }

    @Test
    public void noListenerIsNoOp() {
        Aero_AnimationClip clip = clipWithEvents(1f, true,
            new float[]{0.05f},
            new String[]{"sound"},
            new String[]{"x"});
        Aero_AnimationPlayback playback = playbackOf(clip);
        for (int i = 0; i < 4; i++) playback.tick();
    }

    @Test
    public void eventAtTimeZeroFiresOnLoopWrap() {
        Aero_AnimationClip clip = clipWithEvents(0.1f, true,
            new float[]{0f},
            new String[]{"custom"},
            new String[]{"CYCLE_START"});
        Aero_AnimationPlayback playback = playbackOf(clip);

        Recorder rec = new Recorder();
        playback.setEventListener(rec);

        for (int i = 0; i < 6; i++) playback.tick();
        assertEquals(3, rec.calls.size());
    }

    @Test
    public void locatorIsForwardedToListener() {
        Aero_AnimationClip clip = TestClips.clip("loc", Aero_AnimationLoop.LOOP, 1f,
            new String[]{"x"},
            new float[][]{{0f}}, new float[][][]{{{0f, 0f, 0f}}},
            new float[][]{{0f}}, new float[][][]{{{0f, 0f, 0f}}},
            null, null,
            new float[]{0.05f, 0.10f},
            new String[]{"sound", "particle"},
            new String[]{"random.click", "smoke"},
            new String[]{"muzzle", "blade_tip"});
        Aero_AnimationPlayback playback = playbackOf(clip);
        Recorder rec = new Recorder();
        playback.setEventListener(rec);

        for (int i = 0; i < 3; i++) playback.tick();

        assertEquals(2, rec.calls.size());
        assertEquals("muzzle", rec.get(0).locator);
        assertEquals("blade_tip", rec.get(1).locator);
    }

    @Test
    public void clipWithoutEventsHasHasEventsFalse() {
        assertFalse(TestClips.loopClip("noevt", 1f).hasEvents());
    }

    private static Aero_AnimationClip clipWithEvents(float length, boolean loop,
                                                     float[] times, String[] channels, String[] data) {
        return TestClips.clip("evtClip",
            loop ? Aero_AnimationLoop.LOOP : Aero_AnimationLoop.PLAY_ONCE,
            length,
            new String[]{"x"},
            new float[][]{{0f}}, new float[][][]{{{0f, 0f, 0f}}},
            new float[][]{{0f}}, new float[][][]{{{0f, 0f, 0f}}},
            null, null,
            times, channels, data, null);
    }

    private static Aero_AnimationPlayback playbackOf(Aero_AnimationClip clip) {
        return new Aero_AnimationDefinition()
            .state(0, clip.name)
            .createPlayback(TestClips.bundle(clip));
    }

    private static final class Recorder implements Aero_AnimationEventListener {
        final List calls = new ArrayList();

        public void onEvent(String channel, String data, String locator, float time) {
            Call c = new Call();
            c.channel = channel;
            c.data = data;
            c.locator = locator;
            c.time = time;
            calls.add(c);
        }

        Call get(int i) {
            return (Call) calls.get(i);
        }
    }

    private static final class Call {
        String channel;
        String data;
        String locator;
        float time;
    }
}
