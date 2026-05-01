package aero.modellib;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class AnimationMotionLodTest {

    @Test
    public void fastMotionDoublesNonZeroStride() {
        assertEquals(2, Aero_AnimationTickLOD.adjustStrideForMotion(1, 0.5d, 8.0d));
        assertEquals(8, Aero_AnimationTickLOD.adjustStrideForMotion(8, 0.5d, 8.0d));
    }

    @Test
    public void slowMotionKeepsStride() {
        assertEquals(2, Aero_AnimationTickLOD.adjustStrideForMotion(2, 0.1d, 8.0d));
    }

    @Test
    public void skippedAnimationStaysSkipped() {
        assertEquals(0, Aero_AnimationTickLOD.adjustStrideForMotion(0, 1.0d, 8.0d));
    }
}
