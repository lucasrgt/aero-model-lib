package aero.modellib;

import org.junit.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.*;

/**
 * Exercises {@link Aero_AnimationStateRouter}: priority order of rules,
 * fallback semantics, and the optional smooth-transition wiring through
 * {@link Aero_AnimationPlayback#setStateWithTransition}.
 */
public class AnimationStateRouterTest {

    @Test
    public void firstMatchingRuleWins() {
        // walk and attack are both true — attack rule listed first must win.
        final boolean[] flags = {true, true};   // [attacking, walking]
        Aero_AnimationStateRouter router = new Aero_AnimationStateRouter()
            .when(new Aero_AnimationPredicate() {
                public boolean test(Aero_AnimationPlayback p) { return flags[0]; }
            }, 2)
            .when(new Aero_AnimationPredicate() {
                public boolean test(Aero_AnimationPlayback p) { return flags[1]; }
            }, 1);

        Aero_AnimationPlayback pb = playback();
        router.applyTo(pb);

        assertEquals(2, pb.currentState);
    }

    @Test
    public void fallbackFiresWhenNoRuleMatches() {
        Aero_AnimationStateRouter router = new Aero_AnimationStateRouter()
            .when(new Aero_AnimationPredicate() {
                public boolean test(Aero_AnimationPlayback p) { return false; }
            }, 5)
            .otherwise(99);

        Aero_AnimationPlayback pb = playback();
        router.applyTo(pb);

        assertEquals(99, pb.currentState);
    }

    @Test
    public void noFallbackLeavesStateUntouched() {
        // Router with no fallback and no matching rule must NOT flip
        // currentState — caller can rely on the playback to hold whatever
        // it had before this tick.
        Aero_AnimationStateRouter router = new Aero_AnimationStateRouter()
            .when(new Aero_AnimationPredicate() {
                public boolean test(Aero_AnimationPlayback p) { return false; }
            }, 5);

        Aero_AnimationPlayback pb = playback();
        pb.setState(7);   // pre-existing state we want preserved
        router.applyTo(pb);

        assertEquals(7, pb.currentState);
    }

    @Test
    public void transitionTicksWiresIntoSetStateWithTransition() {
        // Rules with withTransition(N) should engage the playback's
        // transition path, observable via inTransition() right after apply.
        Aero_AnimationStateRouter router = new Aero_AnimationStateRouter()
            .when(new Aero_AnimationPredicate() {
                public boolean test(Aero_AnimationPlayback p) { return true; }
            }, 1)
            .withTransition(4);

        Aero_AnimationPlayback pb = playback();   // starts at state 0 → "a"
        router.applyTo(pb);   // → state 1 → "b", with a 4-tick transition

        assertEquals(1, pb.currentState);
        assertTrue("router with withTransition should engage the blend",
                   pb.inTransition());
    }

    private static Aero_AnimationPlayback playback() {
        Aero_AnimationClip clipA = clip("a");
        Aero_AnimationClip clipB = clip("b");
        Map clips = new HashMap();
        clips.put("a", clipA);
        clips.put("b", clipB);
        Aero_AnimationBundle bundle = new Aero_AnimationBundle(clips, new HashMap(), new HashMap());
        return new Aero_AnimationDefinition()
            .state(0, "a").state(1, "b").state(7, "a").state(99, "b")
            .createPlayback(bundle);
    }

    private static Aero_AnimationClip clip(String name) {
        return new Aero_AnimationClip(
            name, Aero_AnimationClip.LOOP_TYPE_LOOP, 1f,
            new String[]{"x"},
            new float[][]{{0f}}, new float[][][]{{{0f, 0f, 0f}}}, null,
            new float[][]{{0f}}, new float[][][]{{{0f, 0f, 0f}}}, null,
            null, null, null);
    }
}
