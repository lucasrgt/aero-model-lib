package aero.modellib;

import org.junit.Test;

import static org.junit.Assert.*;

/**
 * Convergence + boundary tests for {@link Aero_CCDSolver}.
 *
 * The solver mutates pose rotations to bring an end-effector close to a
 * target. Tests use synthetic 2-3 bone chains with no animation (all
 * poses start identity) so the solver's behavior is the only variable.
 */
public class CCDSolverTest {

    private static final float DELTA = 0.05f;

    @Test
    public void singleBoneChainIsNoOp() {
        // n=1 chain has no intermediate bones to rotate — solver should return 0.
        int[] idx = {0};
        float[][] pivots = {{0f, 0f, 0f}};
        Aero_BoneRenderPose[] pool = pool(1);
        float[] target = {5f, 5f, 5f};
        int iters = Aero_CCDSolver.solve(idx, pivots, pool, target,
            Aero_CCDSolver.DEFAULT_TOLERANCE);
        assertEquals(0, iters);
    }

    @Test
    public void twoBoneChainConvergesOntoNearbyTarget() {
        // Hip at origin, foot at (0, 0, 1). Target at (1, 0, 0). Solver
        // should rotate hip ~90° around Y so foot reaches (1, 0, 0).
        int[] idx = {0, 1};
        float[][] pivots = {{0f, 0f, 0f}, {0f, 0f, 1f}};
        Aero_BoneRenderPose[] pool = pool(2);

        float[] target = {1f, 0f, 0f};
        int iters = Aero_CCDSolver.solve(idx, pivots, pool, target, 0.01f);

        // Solver should converge in a small number of iterations.
        assertTrue("expected fast convergence, got " + iters + " iters",
            iters < Aero_CCDSolver.MAX_ITER);

        // Verify end-effector landed near target via FK.
        float[] effector = new float[3];
        Aero_BoneFK.computePivotInto(idx, pivots, pool, effector);
        float dx = effector[0] - target[0];
        float dy = effector[1] - target[1];
        float dz = effector[2] - target[2];
        float dist = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
        assertTrue("end-effector should be within 0.1 of target, got dist=" + dist,
            dist < 0.1f);
    }

    @Test
    public void solverDoesNotMoveEndEffectorRotation() {
        // Solver mutates intermediate bones only — the end-effector's own
        // rotation must remain identity (the algorithm doesn't touch index n-1).
        int[] idx = {0, 1};
        float[][] pivots = {{0f, 0f, 0f}, {0f, 0f, 1f}};
        Aero_BoneRenderPose[] pool = pool(2);

        float[] target = {1f, 0f, 0f};
        Aero_CCDSolver.solve(idx, pivots, pool, target, 0.01f);

        assertEquals(0f, pool[1].rotX, DELTA);
        assertEquals(0f, pool[1].rotY, DELTA);
        assertEquals(0f, pool[1].rotZ, DELTA);
    }

    @Test
    public void solverRespectsToleranceBudget() {
        // Already-aligned chain converges in the first iteration check.
        int[] idx = {0, 1};
        float[][] pivots = {{0f, 0f, 0f}, {1f, 0f, 0f}};
        Aero_BoneRenderPose[] pool = pool(2);

        float[] target = {1f, 0f, 0f}; // exactly at end-effector's rest pos
        int iters = Aero_CCDSolver.solve(idx, pivots, pool, target, 0.01f);
        assertEquals("already aligned should return after first dist check",
            0, iters);
    }

    @Test
    public void solverDoesNotExplodeOnUnreachableTarget() {
        // Target is FAR beyond chain reach. Solver should not NaN or loop
        // forever — caps at MAX_ITER, leaves chain in a finite state.
        int[] idx = {0, 1};
        float[][] pivots = {{0f, 0f, 0f}, {0f, 0f, 1f}};
        Aero_BoneRenderPose[] pool = pool(2);

        float[] target = {1000f, 1000f, 1000f};
        int iters = Aero_CCDSolver.solve(idx, pivots, pool, target, 0.01f);
        assertEquals("unreachable target should hit max iter", Aero_CCDSolver.MAX_ITER, iters);

        for (int i = 0; i < pool.length; i++) {
            assertFalse("pose rotX must not be NaN", Float.isNaN(pool[i].rotX));
            assertFalse("pose rotY must not be NaN", Float.isNaN(pool[i].rotY));
            assertFalse("pose rotZ must not be NaN", Float.isNaN(pool[i].rotZ));
        }
    }

    @Test
    public void threeBoneChainConverges() {
        // 3-bone chain with elbow joint, target at angle.
        int[] idx = {0, 1, 2};
        float[][] pivots = {{0f, 0f, 0f}, {0f, 0f, 1f}, {0f, 0f, 2f}};
        Aero_BoneRenderPose[] pool = pool(3);

        float[] target = {1f, 0f, 1f};
        int iters = Aero_CCDSolver.solve(idx, pivots, pool, target, 0.05f);

        assertTrue("3-bone should converge before MAX_ITER, got " + iters,
            iters < Aero_CCDSolver.MAX_ITER);

        float[] effector = new float[3];
        Aero_BoneFK.computePivotInto(idx, pivots, pool, effector);
        float dx = effector[0] - target[0];
        float dy = effector[1] - target[1];
        float dz = effector[2] - target[2];
        float dist = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
        assertTrue("3-bone effector should converge close to target, dist=" + dist,
            dist < 0.2f);
    }

    private static Aero_BoneRenderPose[] pool(int n) {
        Aero_BoneRenderPose[] pool = new Aero_BoneRenderPose[n];
        for (int i = 0; i < n; i++) {
            pool[i] = new Aero_BoneRenderPose();
            pool[i].reset();
        }
        return pool;
    }
}
