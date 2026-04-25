package aero.modellib;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * Spot-checks for {@link Aero_Easing}. Every easing curve has fixed values at
 * t=0 and t=1 (must be 0 and 1 respectively) so the keyframe lerp anchors
 * cleanly. The midpoint shapes are looser sanity checks that prove the curve
 * is actually non-linear without enforcing exact values.
 */
public class EasingTest {

    private static final float EPS = 1e-4f;

    @Test
    public void linearIsIdentity() {
        for (float t = 0f; t <= 1f; t += 0.1f) {
            assertEquals(t, Aero_Easing.ease(Aero_Easing.LINEAR, t), EPS);
        }
    }

    @Test
    public void everyEasingAnchorsAt0And1() {
        // Both endpoints must be exact for keyframe interpolation to be
        // continuous — a curve that returned 0.0001 at t=0 would jitter
        // visibly when the player crosses the keyframe boundary.
        for (int mode = 0; mode <= Aero_Easing.EASE_IN_OUT_BOUNCE; mode++) {
            assertEquals("mode=" + mode + " at t=0", 0f, Aero_Easing.ease(mode, 0f), EPS);
            assertEquals("mode=" + mode + " at t=1", 1f, Aero_Easing.ease(mode, 1f), EPS);
        }
    }

    @Test
    public void easeOutSineMidpointAboveLinear() {
        // easeOutSine should be strictly above linear at t=0.5 (curve bulges
        // toward the upper-left).
        float lin = 0.5f;
        float eased = Aero_Easing.ease(Aero_Easing.EASE_OUT_SINE, 0.5f);
        assertTrue("easeOutSine(0.5)=" + eased + " should exceed linear", eased > lin);
    }

    @Test
    public void easeInQuadMidpointBelowLinear() {
        // easeInQuad is the standard t² parabola — at t=0.5 it returns 0.25.
        assertEquals(0.25f, Aero_Easing.ease(Aero_Easing.EASE_IN_QUAD, 0.5f), EPS);
    }

    @Test
    public void easeOutBackOvershootsAboveOne() {
        // easeOutBack peaks above 1.0 around t=0.7 (the "back" overshoot
        // before settling back to 1). Confirms the C1/C3 constants are
        // wired correctly.
        boolean overshot = false;
        for (float t = 0.5f; t <= 0.95f; t += 0.05f) {
            if (Aero_Easing.ease(Aero_Easing.EASE_OUT_BACK, t) > 1.0f) {
                overshot = true;
                break;
            }
        }
        assertTrue("easeOutBack should overshoot 1.0 before settling", overshot);
    }

    @Test
    public void easeOutBounceWiggles() {
        // easeOutBounce hits 1.0 multiple times due to its piecewise parabolas.
        // A simpler invariant: the curve is monotonic-non-decreasing in
        // OUTPUT magnitude away from 0 (lots of local minima allowed) but
        // never goes below 0 or above 1.
        for (float t = 0f; t <= 1f; t += 0.05f) {
            float v = Aero_Easing.ease(Aero_Easing.EASE_OUT_BOUNCE, t);
            assertTrue("bounce out of range at t=" + t + ": " + v, v >= 0f && v <= 1f);
        }
    }

    @Test
    public void byNameLooksUpKnownCurves() {
        assertEquals(Aero_Easing.LINEAR,           Aero_Easing.byName("linear"));
        assertEquals(Aero_Easing.CATMULLROM,       Aero_Easing.byName("catmullrom"));
        assertEquals(Aero_Easing.STEP,             Aero_Easing.byName("step"));
        assertEquals(Aero_Easing.EASE_IN_OUT_BACK, Aero_Easing.byName("easeInOutBack"));
        assertEquals(Aero_Easing.EASE_OUT_BOUNCE,  Aero_Easing.byName("easeOutBounce"));
    }

    @Test
    public void byNameUnknownFallsBackToLinear() {
        // Typos in the .anim.json shouldn't crash the loader — degrade to
        // linear instead so the animation still plays (just without curve).
        assertEquals(Aero_Easing.LINEAR, Aero_Easing.byName("notARealCurve"));
        assertEquals(Aero_Easing.LINEAR, Aero_Easing.byName(""));
        assertEquals(Aero_Easing.LINEAR, Aero_Easing.byName(null));
    }
}
