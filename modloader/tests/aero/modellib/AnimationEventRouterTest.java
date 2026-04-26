package aero.modellib;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.*;

public class AnimationEventRouterTest {

    @Test
    public void exactChannelAndNameRouteFires() {
        Recorder hit = new Recorder();
        Recorder miss = new Recorder();
        Aero_AnimationEventRouter r = Aero_AnimationEventRouter.builder()
            .on("sound", "random.click", hit)
            .on("sound", "random.bow", miss)
            .build();

        r.onEvent("sound", "random.click", null, 0.5f);

        assertEquals(1, hit.calls.size());
        assertEquals(0, miss.calls.size());
    }

    @Test
    public void channelFallbackFiresWhenNoExactMatch() {
        Recorder exact = new Recorder();
        Recorder fallback = new Recorder();
        Aero_AnimationEventRouter r = Aero_AnimationEventRouter.builder()
            .on("sound", "random.click", exact)
            .onChannel("sound", fallback)
            .build();

        r.onEvent("sound", "unknown.name", null, 0.5f);

        assertEquals(0, exact.calls.size());
        assertEquals(1, fallback.calls.size());
    }

    @Test
    public void exactWinsOverChannelFallback() {
        Recorder exact = new Recorder();
        Recorder channelFallback = new Recorder();
        Aero_AnimationEventRouter r = Aero_AnimationEventRouter.builder()
            .on("sound", "random.click", exact)
            .onChannel("sound", channelFallback)
            .build();

        r.onEvent("sound", "random.click", null, 0.5f);

        assertEquals(1, exact.calls.size());
        assertEquals(0, channelFallback.calls.size());
    }

    @Test
    public void otherwiseFiresWhenNothingElseMatches() {
        Recorder otherwise = new Recorder();
        Aero_AnimationEventRouter r = Aero_AnimationEventRouter.builder()
            .on("sound", "random.click", new Recorder())
            .otherwise(otherwise)
            .build();

        r.onEvent("particle", "smoke", null, 0.1f);

        assertEquals(1, otherwise.calls.size());
    }

    @Test
    public void unrouteableEventIsSilentlyDropped() {
        Aero_AnimationEventRouter r = Aero_AnimationEventRouter.builder()
            .on("sound", "random.click", new Recorder())
            .build();

        // No throw, no listener, no otherwise — must just no-op.
        r.onEvent("particle", "smoke", null, 0.1f);
    }

    @Test
    public void locatorAndTimeAreForwardedToHandler() {
        Recorder rec = new Recorder();
        Aero_AnimationEventRouter r = Aero_AnimationEventRouter.builder()
            .on("particle", "smoke", rec)
            .build();

        r.onEvent("particle", "smoke", "muzzle", 0.42f);

        assertEquals(1, rec.calls.size());
        Recorder.Call call = (Recorder.Call) rec.calls.get(0);
        assertEquals("muzzle", call.locator);
        assertEquals(0.42f, call.time, 1e-4f);
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsEmptyChannel() {
        Aero_AnimationEventRouter.builder().on("", "name", new Recorder());
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsNullHandler() {
        Aero_AnimationEventRouter.builder().on("sound", "name", null);
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

        static final class Call {
            String channel;
            String data;
            String locator;
            float time;
        }
    }
}
