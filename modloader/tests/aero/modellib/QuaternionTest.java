package aero.modellib;

import org.junit.Test;

import aero.modellib.skeletal.Aero_Quaternion;

import static org.junit.Assert.*;

/**
 * Round-trip + slerp invariants for {@link Aero_Quaternion}.
 *
 * The lib stores rotation as Euler degrees and only converts to/from quat
 * for slerp on rotation channels. These tests pin down:
 *  1. fromEuler→toEuler is identity (within FP tolerance) for poses that
 *     don't sit at a gimbal singularity.
 *  2. slerp at endpoints returns the endpoints exactly.
 *  3. slerp at midpoint of a 350°→10° rotation lands at 0°, not 180°
 *     (the long-arc bug that motivated this whole feature).
 *  4. slerp produces unit quaternions even with FP drift across calls.
 */
public class QuaternionTest {

    private static final float DELTA_DEG = 0.5f;     // half a degree slack on round-trips
    private static final float DELTA_UNIT = 1e-4f;   // norm-1 tolerance

    @Test
    public void roundTripIdentityAtZero() {
        float[] q = new float[4];
        float[] back = new float[3];
        Aero_Quaternion.fromEulerDegrees(0f, 0f, 0f, q);
        Aero_Quaternion.toEulerDegrees(q, back);
        assertEquals(0f, back[0], DELTA_DEG);
        assertEquals(0f, back[1], DELTA_DEG);
        assertEquals(0f, back[2], DELTA_DEG);
    }

    @Test
    public void roundTripPreservesSmallAngles() {
        float[] q = new float[4];
        float[] back = new float[3];
        Aero_Quaternion.fromEulerDegrees(30f, -45f, 60f, q);
        Aero_Quaternion.toEulerDegrees(q, back);
        assertEquals(30f, back[0], DELTA_DEG);
        assertEquals(-45f, back[1], DELTA_DEG);
        assertEquals(60f, back[2], DELTA_DEG);
    }

    @Test
    public void fromEulerProducesUnitQuat() {
        float[] q = new float[4];
        Aero_Quaternion.fromEulerDegrees(123f, -57f, 200f, q);
        float n = q[0]*q[0] + q[1]*q[1] + q[2]*q[2] + q[3]*q[3];
        assertEquals("quat must be unit-length", 1f, n, DELTA_UNIT);
    }

    @Test
    public void slerpAtZeroReturnsA() {
        float[] a = new float[4], b = new float[4], out = new float[4];
        Aero_Quaternion.fromEulerDegrees(0f, 0f, 0f, a);
        Aero_Quaternion.fromEulerDegrees(90f, 0f, 0f, b);
        Aero_Quaternion.slerp(a, b, 0f, out);
        assertArrayEquals(a, out, DELTA_UNIT);
    }

    @Test
    public void slerpAtOneReturnsB() {
        float[] a = new float[4], b = new float[4], out = new float[4];
        Aero_Quaternion.fromEulerDegrees(0f, 0f, 0f, a);
        Aero_Quaternion.fromEulerDegrees(90f, 0f, 0f, b);
        Aero_Quaternion.slerp(a, b, 1f, out);
        assertArrayEquals(b, out, DELTA_UNIT);
    }

    @Test
    public void slerpAtMidpointOf90Degrees() {
        float[] a = new float[4], b = new float[4], out = new float[4];
        float[] euler = new float[3];
        Aero_Quaternion.fromEulerDegrees(0f, 0f, 0f, a);
        Aero_Quaternion.fromEulerDegrees(90f, 0f, 0f, b);
        Aero_Quaternion.slerp(a, b, 0.5f, out);
        Aero_Quaternion.toEulerDegrees(out, euler);
        assertEquals("midpoint of 0→90 around X is ~45°", 45f, euler[0], DELTA_DEG);
    }

    @Test
    public void slerpTakesShortArcAcross180() {
        // 350°→10° around X. Linear euler interp at t=0.5 gives 180° (the
        // long way). Slerp should give ~0° (the short way, 20° total).
        float[] a = new float[4], b = new float[4], out = new float[4];
        float[] euler = new float[3];
        Aero_Quaternion.fromEulerDegrees(350f, 0f, 0f, a);
        Aero_Quaternion.fromEulerDegrees(10f, 0f, 0f, b);
        Aero_Quaternion.slerp(a, b, 0.5f, out);
        Aero_Quaternion.toEulerDegrees(out, euler);
        // Slerp should land near 0° (or equivalently 360°). Allow either.
        float rx = euler[0];
        if (rx > 180f) rx -= 360f;
        assertEquals("slerp 350→10 midpoint should be ~0° (short arc), not ~180° (long)",
            0f, rx, 1f);
    }

    @Test
    public void slerpKeepsOutputUnitLength() {
        float[] a = new float[4], b = new float[4], out = new float[4];
        Aero_Quaternion.fromEulerDegrees(33f, 77f, -22f, a);
        Aero_Quaternion.fromEulerDegrees(-110f, 50f, 130f, b);
        for (int i = 0; i <= 10; i++) {
            float t = i / 10f;
            Aero_Quaternion.slerp(a, b, t, out);
            float n = out[0]*out[0] + out[1]*out[1] + out[2]*out[2] + out[3]*out[3];
            assertEquals("slerp output must stay unit-length at t=" + t, 1f, n, DELTA_UNIT);
        }
    }

    @Test
    public void slerpHandlesNearColinearWithoutNaN() {
        // Two near-identical quats — slerp's sin(theta) is near zero.
        // Implementation must fall back to nlerp instead of dividing by 0.
        float[] a = new float[4], b = new float[4], out = new float[4];
        Aero_Quaternion.fromEulerDegrees(45f, 30f, 15f, a);
        Aero_Quaternion.fromEulerDegrees(45.001f, 30.001f, 15.001f, b);
        Aero_Quaternion.slerp(a, b, 0.5f, out);
        for (int k = 0; k < 4; k++) {
            assertFalse("output must not be NaN at component " + k, Float.isNaN(out[k]));
            assertFalse("output must not be Inf at component " + k, Float.isInfinite(out[k]));
        }
    }
}
