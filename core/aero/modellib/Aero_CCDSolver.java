package aero.modellib;

/**
 * Cyclic Coordinate Descent IK solver — mutates pose rotations in a chain
 * to bring the end-effector close to a world-space target.
 *
 * <p>Each iteration walks the chain backward from end-effector toward
 * root. For each intermediate bone, the algorithm computes the rotation
 * that would align the current effector direction with the target
 * direction (both relative to that bone's pivot), and applies it
 * incrementally. Convergence is typically 4-8 iterations for shallow
 * chains; we cap at {@link #MAX_ITER} as a safety net.
 *
 * <p>The solver requires a pre-resolved pose pool (built by the renderer's
 * Pass 1) and the chain definition from {@link Aero_MeshModel.BoneRef} —
 * specifically the {@code ancestorBoneIdx} + {@code ancestorPivots}
 * arrays. The end-effector is the last entry; the chain root is index 0.
 *
 * <p>Mutates {@code rotX/Y/Z} of the bones in {@code pool}. Does NOT
 * touch position, scale, or UV transforms — those stay as the keyframes
 * + procedural pose left them.
 */
public final class Aero_CCDSolver {

    /** Hard cap on iterations per chain per frame. */
    public static final int MAX_ITER = 16;

    /** Stop early when end-effector is within this distance of the target (block units). */
    public static final float DEFAULT_TOLERANCE = 0.001f;

    private Aero_CCDSolver() {}

    /**
     * Runs CCD on the chain, mutating pose rotations in {@code pool}.
     *
     * @param chainBoneIdx  chain bone indices (root → end-effector inclusive)
     * @param chainPivots   matching pivots
     * @param pool          pose pool indexed by clip.boneNames[]
     * @param targetWorld   world-space target (entity-local frame, matching FK output)
     * @param tolerance     convergence threshold; pass {@link #DEFAULT_TOLERANCE} for default
     * @return iterations actually performed (≤ MAX_ITER)
     */
    public static int solve(int[] chainBoneIdx,
                            float[][] chainPivots,
                            Aero_BoneRenderPose[] pool,
                            float[] targetWorld,
                            float tolerance) {
        int n = chainBoneIdx.length;
        if (n < 2) return 0; // single-bone chains have nothing to solve.

        float[] chainWorldPositions = new float[n * 3];
        float[] chainParentRotations = new float[n * 4];
        float[] effectorPos = new float[3];
        float[] bonePos = new float[3];
        float[] scratchVec = new float[3];
        float[] qCorrection = new float[4];
        float[] qParent = new float[4];
        float[] qParentInv = new float[4];
        float[] qLocalCorrection = new float[4];
        float[] qCurrent = new float[4];
        float[] qTemp = new float[4];
        float[] qNew = new float[4];
        float[] eulerOut = new float[3];

        for (int iter = 0; iter < MAX_ITER; iter++) {
            Aero_BoneFK.computeChainPivotsInto(chainBoneIdx, chainPivots, pool,
                chainWorldPositions, chainParentRotations, qTemp);
            int effectorBase = (n - 1) * 3;
            effectorPos[0] = chainWorldPositions[effectorBase];
            effectorPos[1] = chainWorldPositions[effectorBase + 1];
            effectorPos[2] = chainWorldPositions[effectorBase + 2];

            // Distance check.
            float dx = targetWorld[0] - effectorPos[0];
            float dy = targetWorld[1] - effectorPos[1];
            float dz = targetWorld[2] - effectorPos[2];
            if (dx * dx + dy * dy + dz * dz < tolerance * tolerance) {
                return iter;
            }

            // Walk backward from second-to-last → root, adjusting rotations.
            for (int b = n - 2; b >= 0; b--) {
                int boneBase = b * 3;
                bonePos[0] = chainWorldPositions[boneBase];
                bonePos[1] = chainWorldPositions[boneBase + 1];
                bonePos[2] = chainWorldPositions[boneBase + 2];

                // Vector from this bone to current end-effector.
                float ex = effectorPos[0] - bonePos[0];
                float ey = effectorPos[1] - bonePos[1];
                float ez = effectorPos[2] - bonePos[2];
                float eLen = (float) Math.sqrt(ex * ex + ey * ey + ez * ez);
                if (eLen < 1e-6f) continue;

                // Vector from this bone to target.
                float tx = targetWorld[0] - bonePos[0];
                float ty = targetWorld[1] - bonePos[1];
                float tz = targetWorld[2] - bonePos[2];
                float tLen = (float) Math.sqrt(tx * tx + ty * ty + tz * tz);
                if (tLen < 1e-6f) continue;

                // Normalize.
                ex /= eLen; ey /= eLen; ez /= eLen;
                tx /= tLen; ty /= tLen; tz /= tLen;

                // Axis = e × t. Angle = acos(e · t).
                float ax = ey * tz - ez * ty;
                float ay = ez * tx - ex * tz;
                float az = ex * ty - ey * tx;
                float aLen = (float) Math.sqrt(ax * ax + ay * ay + az * az);
                if (aLen < 1e-6f) continue; // already aligned

                float dot = ex * tx + ey * ty + ez * tz;
                if (dot >  1f) dot =  1f;
                if (dot < -1f) dot = -1f;
                float angle = (float) Math.acos(dot);

                // Build quaternion for the correction rotation.
                Aero_Quaternion.fromAxisAngle(ax, ay, az, angle, qCorrection);

                // Convert world correction into this bone's local parent frame,
                // then multiply with current bone's quat: q_new = q_local * q_current.
                int parentBase = b * 4;
                qParent[0] = chainParentRotations[parentBase];
                qParent[1] = chainParentRotations[parentBase + 1];
                qParent[2] = chainParentRotations[parentBase + 2];
                qParent[3] = chainParentRotations[parentBase + 3];
                qParentInv[0] = qParent[0];
                qParentInv[1] = -qParent[1];
                qParentInv[2] = -qParent[2];
                qParentInv[3] = -qParent[3];
                Aero_Quaternion.multiply(qCorrection, qParent, qTemp);
                Aero_Quaternion.multiply(qParentInv, qTemp, qLocalCorrection);

                Aero_BoneRenderPose pose = pool[chainBoneIdx[b]];
                Aero_Quaternion.fromEulerDegrees(pose.rotX, pose.rotY, pose.rotZ, qCurrent);
                Aero_Quaternion.multiply(qLocalCorrection, qCurrent, qNew);

                // Convert back to euler and write to pose.
                Aero_Quaternion.toEulerDegrees(qNew, eulerOut);
                pose.rotX = eulerOut[0];
                pose.rotY = eulerOut[1];
                pose.rotZ = eulerOut[2];

                scratchVec[0] = effectorPos[0] - bonePos[0];
                scratchVec[1] = effectorPos[1] - bonePos[1];
                scratchVec[2] = effectorPos[2] - bonePos[2];
                Aero_Quaternion.rotateVec(qCorrection, scratchVec, scratchVec);
                effectorPos[0] = bonePos[0] + scratchVec[0];
                effectorPos[1] = bonePos[1] + scratchVec[1];
                effectorPos[2] = bonePos[2] + scratchVec[2];
            }
        }
        return MAX_ITER;
    }
}
