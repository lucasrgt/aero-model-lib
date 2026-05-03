package aero.modellib;

import org.junit.Test;

import aero.modellib.animation.Aero_Easing;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class EasingTest {

    private static final float EPS = 1e-4f;

    @Test
    public void linearIsIdentity() {
        for (float t = 0f; t <= 1f; t += 0.1f) {
            assertEquals(t, Aero_Easing.LINEAR.apply(t), EPS);
        }
    }

    @Test
    public void everyEasingAnchorsAt0And1() {
        Aero_Easing[] easings = Aero_Easing.values();
        for (int i = 0; i < easings.length; i++) {
            assertEquals(easings[i].name() + " at t=0", 0f, easings[i].apply(0f), EPS);
            assertEquals(easings[i].name() + " at t=1", 1f, easings[i].apply(1f), EPS);
        }
    }

    @Test
    public void easeOutSineMidpointAboveLinear() {
        float eased = Aero_Easing.EASE_OUT_SINE.apply(0.5f);
        assertTrue("easeOutSine(0.5)=" + eased + " should exceed linear", eased > 0.5f);
    }

    @Test
    public void easeInQuadMidpointBelowLinear() {
        assertEquals(0.25f, Aero_Easing.EASE_IN_QUAD.apply(0.5f), EPS);
    }

    @Test
    public void easeOutBackOvershootsAboveOne() {
        boolean overshot = false;
        for (float t = 0.5f; t <= 0.95f; t += 0.05f) {
            if (Aero_Easing.EASE_OUT_BACK.apply(t) > 1.0f) {
                overshot = true;
                break;
            }
        }
        assertTrue("easeOutBack should overshoot 1.0 before settling", overshot);
    }

    @Test
    public void easeOutBounceStaysInRange() {
        for (float t = 0f; t <= 1f; t += 0.05f) {
            float v = Aero_Easing.EASE_OUT_BOUNCE.apply(t);
            assertTrue("bounce out of range at t=" + t + ": " + v, v >= 0f && v <= 1f);
        }
    }

    @Test
    public void fromNameLooksUpKnownCurves() {
        assertEquals(Aero_Easing.LINEAR, Aero_Easing.fromName("linear"));
        assertEquals(Aero_Easing.CATMULLROM, Aero_Easing.fromName("catmullrom"));
        assertEquals(Aero_Easing.STEP, Aero_Easing.fromName("step"));
        assertEquals(Aero_Easing.EASE_IN_OUT_BACK, Aero_Easing.fromName("easeInOutBack"));
        assertEquals(Aero_Easing.EASE_OUT_BOUNCE, Aero_Easing.fromName("easeOutBounce"));
    }

    @Test
    public void fromNameRejectsUnknownCurve() {
        try {
            Aero_Easing.fromName("notARealCurve");
            fail("expected unknown easing to be rejected");
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage().contains("unknown easing"));
        }
    }

    @Test
    public void fromNameRejectsNull() {
        try {
            Aero_Easing.fromName(null);
            fail("expected null easing to be rejected");
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage().contains("interp"));
        }
    }
}
