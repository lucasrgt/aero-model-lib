package aero.modellib;

import org.junit.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.*;

/**
 * Verifies the non-pose keyframe-event channel: events fire in
 * chronological order, exactly once per crossing, and the loop-wrap path
 * splits the firing window correctly across the boundary.
 *
 * <p>Tests live in the same {@code aero.modellib} package as the lib so we
 * can use the package-private clip constructor with explicit event arrays —
 * avoids the loader's resource-path caching which would persist test
 * fixtures across cases.
 */
public class KeyframeEventTest {

    @Test
    public void singleEventFiresOnce() {
        // 1 tick = 0.05s. The keyframe at 0.05s should fire exactly once
        // when the playback head crosses it on tick 1.
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
        assertEquals("hit",   rec.get(0).data);
        assertEquals(0.05f,   rec.get(0).time, 1e-4f);
    }

    @Test
    public void eventsFireInChronologicalOrder() {
        // Three events spread across the clip — verify they fire in time
        // order regardless of construction order.
        Aero_AnimationClip clip = clipWithEvents(1f, true,
            new float[]{0.05f, 0.10f, 0.15f},
            new String[]{"sound", "particle", "sound"},
            new String[]{"a", "b", "c"});
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
        // Clip of length 0.1 (=2 ticks). Events at t=0.02 and t=0.08 must
        // each fire once per loop cycle. After 6 ticks (3 cycles), expect
        // (early, late) × 3.
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
            String expected = (i % 2 == 0) ? "early" : "late";
            assertEquals("call " + i, expected, rec.get(i).data);
        }
    }

    @Test
    public void noListenerIsNoOp() {
        // Clip has events, but no listener — tick must not throw.
        Aero_AnimationClip clip = clipWithEvents(1f, true,
            new float[]{0.05f},
            new String[]{"sound"},
            new String[]{"x"});
        Aero_AnimationPlayback playback = playbackOf(clip);
        for (int i = 0; i < 4; i++) playback.tick();
        // No assertion — just must not crash.
    }

    @Test
    public void eventAtTimeZeroFiresOnLoopWrap() {
        // Regression: the original window logic used t > fromExclusive on
        // both legs of a wrap, which swallowed any t=0 event because the
        // post-wrap leg was (0, now] instead of [0, now].
        Aero_AnimationClip clip = clipWithEvents(0.1f, true,
            new float[]{0f},
            new String[]{"custom"},
            new String[]{"CYCLE_START"});
        Aero_AnimationPlayback playback = playbackOf(clip);

        Recorder rec = new Recorder();
        playback.setEventListener(rec);

        // 0.1s clip = 2 ticks per cycle. After 6 ticks (3 cycles), the
        // t=0 event should have fired 3 times — once per loop wrap.
        for (int i = 0; i < 6; i++) playback.tick();
        assertEquals(3, rec.calls.size());
    }

    @Test
    public void locatorIsForwardedToListener() {
        // Build a clip with a locator on each event and verify it survives
        // the fireEvents → listener round-trip. Uses the 4-arg constructor
        // path that the loader exercises for {"name":..., "locator":...}.
        Aero_AnimationClip clip = new Aero_AnimationClip(
            "loc", Aero_AnimationClip.LOOP_TYPE_LOOP, 1f,
            new String[]{"x"},
            new float[][]{{0f}}, new float[][][]{{{0f, 0f, 0f}}}, null,
            new float[][]{{0f}}, new float[][][]{{{0f, 0f, 0f}}}, null,
            null, null, null,
            new float[]{0.05f, 0.10f},
            new String[]{"sound", "particle"},
            new String[]{"random.click", "smoke"},
            new String[]{"muzzle", "blade_tip"});
        Aero_AnimationPlayback playback = playbackOf(clip);
        Recorder rec = new Recorder();
        playback.setEventListener(rec);

        for (int i = 0; i < 3; i++) playback.tick();

        assertEquals(2, rec.calls.size());
        assertEquals("muzzle",    rec.get(0).locator);
        assertEquals("blade_tip", rec.get(1).locator);
    }

    @Test
    public void clipWithoutEventsHasHasEventsFalse() {
        Aero_AnimationClip clip = new Aero_AnimationClip(
            "noevt", Aero_AnimationClip.LOOP_TYPE_LOOP, 1f,
            new String[]{"x"},
            new float[][]{{0f}}, new float[][][]{{{0f, 0f, 0f}}}, null,
            new float[][]{{0f}}, new float[][][]{{{0f, 0f, 0f}}}, null,
            null, null, null);
        assertFalse(clip.hasEvents());
    }

    // ----------------------------------------------------------------------
    // Helpers
    // ----------------------------------------------------------------------

    private static Aero_AnimationClip clipWithEvents(float length, boolean loop,
                                                     float[] times, String[] channels, String[] data) {
        return clipWithEventsAndLocators(length, loop, times, channels, data, null);
    }

    private static Aero_AnimationClip clipWithEventsAndLocators(
            float length, boolean loop,
            float[] times, String[] channels, String[] data, String[] locators) {
        return new Aero_AnimationClip(
            "evtClip",
            loop ? Aero_AnimationClip.LOOP_TYPE_LOOP : Aero_AnimationClip.LOOP_TYPE_PLAY_ONCE,
            length,
            new String[]{"x"},
            new float[][]{{0f}}, new float[][][]{{{0f, 0f, 0f}}}, null,
            new float[][]{{0f}}, new float[][][]{{{0f, 0f, 0f}}}, null,
            null, null, null,
            times, channels, data, locators);
    }

    private static Aero_AnimationPlayback playbackOf(Aero_AnimationClip clip) {
        Map clips = new HashMap();
        clips.put(clip.name, clip);
        Aero_AnimationBundle bundle = new Aero_AnimationBundle(clips, new HashMap(), new HashMap());
        return new Aero_AnimationDefinition()
            .state(0, clip.name)
            .createPlayback(bundle);
    }

    private static final class Recorder implements Aero_AnimationEventListener {
        final List calls = new ArrayList();

        @Override
        public void onEvent(String channel, String data, String locator, float time) {
            Call c = new Call();
            c.channel = channel; c.data = data; c.locator = locator; c.time = time;
            calls.add(c);
        }

        Call get(int i) { return (Call) calls.get(i); }
    }

    private static final class Call {
        String channel;
        String data;
        String locator;
        float  time;
    }
}
