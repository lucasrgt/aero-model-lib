package aero.modellib;

import org.junit.Test;

import aero.modellib.skeletal.Aero_BoneFK;
import aero.modellib.skeletal.Aero_BoneRenderPose;

import static org.junit.Assert.*;

/**
 * Unit tests for {@link Aero_BoneFK}'s hierarchical pivot walker.
 *
 * Synthetic 2-3 bone chains with controlled poses verify that:
 *  - identity poses leave the leaf at its rest pivot
 *  - rotating a parent moves the child's pivot through the parent's arc
 *  - offset accumulates correctly through ancestors
 */
public class BoneFKTest {

    private static final float DELTA = 0.01f;

    @Test
    public void identityPoseLeavesLeafAtRestPivot() {
        // 2-bone chain: parent at (0,0,0), child at (1,0,0). Identity poses.
        int[] idx = {0, 1};
        float[][] pivots = {{0f, 0f, 0f}, {1f, 0f, 0f}};
        Aero_BoneRenderPose[] pool = newPool(2);

        float[] out = new float[3];
        assertTrue(Aero_BoneFK.computePivotInto(idx, pivots, pool, out));
        assertEquals(1f, out[0], DELTA);
        assertEquals(0f, out[1], DELTA);
        assertEquals(0f, out[2], DELTA);
    }

    @Test
    public void parentRotation90AroundYMovesChildToZAxis() {
        // Parent at origin, child at (1, 0, 0). Parent rotates 90° around Y.
        // Child should end up at (0, 0, -1) — Y rotation takes +X to -Z under
        // GL11 convention (right-handed: Y rotation rotates X toward -Z).
        int[] idx = {0, 1};
        float[][] pivots = {{0f, 0f, 0f}, {1f, 0f, 0f}};
        Aero_BoneRenderPose[] pool = newPool(2);
        pool[0].rotY = 90f;

        float[] out = new float[3];
        assertTrue(Aero_BoneFK.computePivotInto(idx, pivots, pool, out));
        assertEquals(0f, out[0], DELTA);
        assertEquals(0f, out[1], DELTA);
        assertEquals(-1f, out[2], DELTA);
    }

    @Test
    public void leafOffsetTranslatesInLocalFrame() {
        // Single bone at origin, with offset (3, 4, 0). Identity rotation.
        int[] idx = {0};
        float[][] pivots = {{0f, 0f, 0f}};
        Aero_BoneRenderPose[] pool = newPool(1);
        pool[0].offsetX = 3f;
        pool[0].offsetY = 4f;

        float[] out = new float[3];
        assertTrue(Aero_BoneFK.computePivotInto(idx, pivots, pool, out));
        assertEquals(3f, out[0], DELTA);
        assertEquals(4f, out[1], DELTA);
        assertEquals(0f, out[2], DELTA);
    }

    @Test
    public void threeBoneChainAccumulatesParentRotations() {
        // Hip at (0,0,0), knee at (0,5,0), foot at (0,10,0). All identity.
        // Hip rotates 90° around Z. Foot should end up at (-10, 0, 0)
        // (Z rotation takes +Y to -X under GL convention).
        int[] idx = {0, 1, 2};
        float[][] pivots = {{0f, 0f, 0f}, {0f, 5f, 0f}, {0f, 10f, 0f}};
        Aero_BoneRenderPose[] pool = newPool(3);
        pool[0].rotZ = 90f;

        float[] out = new float[3];
        assertTrue(Aero_BoneFK.computePivotInto(idx, pivots, pool, out));
        assertEquals(-10f, out[0], DELTA);
        assertEquals(0f,  out[1], DELTA);
        assertEquals(0f,  out[2], DELTA);
    }

    @Test
    public void emptyChainReturnsFalse() {
        float[] out = new float[3];
        assertFalse(Aero_BoneFK.computePivotInto(new int[0], new float[0][], new Aero_BoneRenderPose[0], out));
    }

    private static Aero_BoneRenderPose[] newPool(int n) {
        Aero_BoneRenderPose[] pool = new Aero_BoneRenderPose[n];
        for (int i = 0; i < n; i++) {
            pool[i] = new Aero_BoneRenderPose();
            pool[i].reset();
        }
        return pool;
    }
}
