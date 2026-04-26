package aero.modellib;

import org.junit.Test;

import static org.junit.Assert.*;

/**
 * Integration of slerp into {@link Aero_AnimationClip.ChannelTrack}.
 *
 * Tests focus on the behavior change vs euler-lerp:
 *  - rotation channels take the short arc through 180° wraps
 *  - position/scale channels are NOT affected (still linear)
 *  - endpoints + STEP behavior unchanged
 *  - CATMULLROM on rotation falls back to slerp (documented trade-off)
 */
public class RotationSlerpTest {

    private static final float DELTA = 0.5f;

    @Test
    public void rotationShortArcUsesSlerpInMultiAxisCase() {
        // Multi-axis rotation 0→90 around X simultaneously with 0→90 around
        // Y. Both axes are < 180° apart so slerp engages. Pure euler-lerp
        // visits (45°, 45°, 0°) at midpoint — but slerp follows the geodesic
        // path on the rotation sphere, which for this pair lands at roughly
        // (26.6°, 41.8°, -26.6°). The deviation IS the feature: slerp avoids
        // the gimbal-style intermediate poses pure-euler would produce.
        Aero_AnimationClip clip = Aero_AnimationClip.builder("twist")
            .length(1f)
            .bone("body")
            .rotation(
                new float[]{0f, 1f},
                new float[][]{{0f, 0f, 0f}, {90f, 90f, 0f}},
                new Aero_Easing[]{Aero_Easing.LINEAR, Aero_Easing.LINEAR})
            .endBone()
            .build();

        int boneIdx = clip.indexOfBone("body");
        float[] out = new float[3];
        assertTrue(clip.sampleRotInto(boneIdx, 0.5f, out));
        for (int i = 0; i < 3; i++) {
            assertFalse("slerp output must not be NaN at axis " + i, Float.isNaN(out[i]));
        }
        // Slerp midpoint X is ~26.57°, NOT 45°. If we accidentally regress to
        // pure euler-lerp, X would be 45° — guard against that.
        assertTrue("X must be slerp-shaped (≠ 45° euler midpoint), got " + out[0],
            out[0] > 20f && out[0] < 35f);
        assertTrue("Y must rotate forward", out[1] > 30f && out[1] < 50f);
    }

    @Test
    public void longArcSegmentFallsBackToEulerLerp() {
        // 0→360 around Y is the canonical "fan spin" pattern. Slerp would
        // collapse this to "no rotation" because both endpoints are the
        // same quat-space orientation. Hybrid heuristic must detect the
        // long arc and fall through to euler — midpoint should be 180°.
        Aero_AnimationClip clip = Aero_AnimationClip.builder("fan")
            .length(1f)
            .bone("blade")
            .rotation(
                new float[]{0f, 1f},
                new float[][]{{0f, 0f, 0f}, {0f, 360f, 0f}},
                new Aero_Easing[]{Aero_Easing.LINEAR, Aero_Easing.LINEAR})
            .endBone()
            .build();

        int boneIdx = clip.indexOfBone("blade");
        float[] out = new float[3];
        assertTrue(clip.sampleRotInto(boneIdx, 0.5f, out));
        assertEquals("0→360 Y midpoint must stay euler (180°), not slerp (0°)",
            180f, out[1], DELTA);
    }

    @Test
    public void exactly180DegBoundaryFallsBackToEuler() {
        // At |delta| == 180° the two quats are antipodal — slerp's path
        // direction is FP-sensitive (±90° flips on rounding). Heuristic uses
        // strict less-than so the boundary case is euler-lerped instead,
        // giving the deterministic v0.1 result of 90° at midpoint.
        Aero_AnimationClip clip = Aero_AnimationClip.builder("edge180")
            .length(1f)
            .bone("b")
            .rotation(
                new float[]{0f, 1f},
                new float[][]{{0f, 0f, 0f}, {180f, 0f, 0f}},
                new Aero_Easing[]{Aero_Easing.LINEAR, Aero_Easing.LINEAR})
            .endBone()
            .build();

        int boneIdx = clip.indexOfBone("b");
        float[] out = new float[3];
        assertTrue(clip.sampleRotInto(boneIdx, 0.5f, out));
        assertEquals("180° boundary uses deterministic euler-lerp", 90f, out[0], DELTA);
    }

    @Test
    public void justUnder180DegStillUsesSlerp() {
        // 179° is < 180° so slerp engages. Quat path agrees with euler
        // here (both give midpoint near 89.5°), so test mainly confirms the
        // < threshold isn't off-by-one.
        Aero_AnimationClip clip = Aero_AnimationClip.builder("edge179")
            .length(1f)
            .bone("b")
            .rotation(
                new float[]{0f, 1f},
                new float[][]{{0f, 0f, 0f}, {179f, 0f, 0f}},
                new Aero_Easing[]{Aero_Easing.LINEAR, Aero_Easing.LINEAR})
            .endBone()
            .build();

        int boneIdx = clip.indexOfBone("b");
        float[] out = new float[3];
        assertTrue(clip.sampleRotInto(boneIdx, 0.5f, out));
        assertEquals("179° midpoint is ~89.5°", 89.5f, out[0], DELTA);
    }

    @Test
    public void positionStillLinearLerp() {
        // Position channel must NOT be slerped — it's straight-line motion.
        Aero_AnimationClip clip = Aero_AnimationClip.builder("slide")
            .length(1f)
            .bone("body")
            .position(
                new float[]{0f, 1f},
                new float[][]{{0f, 0f, 0f}, {10f, 0f, 0f}},
                new Aero_Easing[]{Aero_Easing.LINEAR, Aero_Easing.LINEAR})
            .endBone()
            .build();

        int boneIdx = clip.indexOfBone("body");
        float[] out = new float[3];
        assertTrue(clip.samplePosInto(boneIdx, 0.5f, out));
        assertEquals("position lerp midpoint", 5f, out[0], DELTA);
        assertEquals(0f, out[1], DELTA);
        assertEquals(0f, out[2], DELTA);
    }

    @Test
    public void scaleStillLinearLerp() {
        Aero_AnimationClip clip = Aero_AnimationClip.builder("grow")
            .length(1f)
            .bone("body")
            .scale(
                new float[]{0f, 1f},
                new float[][]{{1f, 1f, 1f}, {2f, 2f, 2f}},
                new Aero_Easing[]{Aero_Easing.LINEAR, Aero_Easing.LINEAR})
            .endBone()
            .build();

        int boneIdx = clip.indexOfBone("body");
        float[] out = new float[3];
        assertTrue(clip.sampleSclInto(boneIdx, 0.5f, out));
        assertEquals(1.5f, out[0], DELTA);
    }

    @Test
    public void rotationEndpointsExact() {
        Aero_AnimationClip clip = Aero_AnimationClip.builder("end")
            .length(1f)
            .bone("body")
            .rotation(
                new float[]{0f, 1f},
                new float[][]{{30f, 60f, 90f}, {120f, -45f, 15f}},
                new Aero_Easing[]{Aero_Easing.LINEAR, Aero_Easing.LINEAR})
            .endBone()
            .build();

        int boneIdx = clip.indexOfBone("body");
        float[] out = new float[3];

        assertTrue(clip.sampleRotInto(boneIdx, 0f, out));
        assertEquals(30f, out[0], DELTA);
        assertEquals(60f, out[1], DELTA);
        assertEquals(90f, out[2], DELTA);

        assertTrue(clip.sampleRotInto(boneIdx, 1f, out));
        assertEquals(120f, out[0], DELTA);
        assertEquals(-45f, out[1], DELTA);
        assertEquals(15f, out[2], DELTA);
    }

    @Test
    public void rotationStepEasingHoldsLowerValue() {
        // STEP should still hold the previous keyframe value, regardless of
        // slerp. Test guards against accidentally routing STEP through slerp.
        Aero_AnimationClip clip = Aero_AnimationClip.builder("step")
            .length(1f)
            .bone("body")
            .rotation(
                new float[]{0f, 1f},
                new float[][]{{0f, 0f, 0f}, {90f, 0f, 0f}},
                new Aero_Easing[]{Aero_Easing.STEP, Aero_Easing.STEP})
            .endBone()
            .build();

        int boneIdx = clip.indexOfBone("body");
        float[] out = new float[3];
        clip.sampleRotInto(boneIdx, 0.5f, out);
        assertEquals("STEP holds lower keyframe", 0f, out[0], DELTA);
    }
}
