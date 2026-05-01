package aero.modellib;

/**
 * Forward-kinematics walker: given a bone's resolved ancestor chain and a
 * pose pool, returns the world-space (entity-local) position of that bone's
 * pivot after all ancestor transforms have been composed.
 *
 * <p>Used by {@link Aero_CCDSolver} to project chain bones into world
 * space each iteration; also useful for procedural-pose code that needs
 * to anchor a particle emitter or sound to a bone wherever the animation
 * has rotated it.
 *
 * <p>Convention matches {@link Aero_BoneRenderPose} and the renderer's
 * {@code applyPose}: each ancestor's transform is
 * {@code T(pivot + offset) * R(Z, Y, X order) * T(-pivot)}. The chain is
 * walked leaf → root, accumulating the transform into the leaf's pivot.
 *
 * <p>Output is in the entity's local frame — the renderer's outer
 * {@code glTranslated(x, y, z)} adds the world offset on top.
 */
public final class Aero_BoneFK {

    private Aero_BoneFK() {}

    /**
     * Computes the world-space pivot of the bone whose chain is provided.
     * Chain entries are expected in root → leaf order (matching
     * {@link Aero_MeshModel.BoneRef#ancestorBoneIdx}).
     *
     * <p>Allocates one transient {@code float[4]} per call. For hot paths
     * use {@link #computePivotInto(int[], float[][], Aero_BoneRenderPose[], float[], float[])}
     * with caller-owned scratch.
     */
    public static boolean computePivotInto(int[] chainBoneIdx,
                                           float[][] chainPivots,
                                           Aero_BoneRenderPose[] pool,
                                           float[] outWorldPos) {
        return computePivotInto(chainBoneIdx, chainPivots, pool,
            outWorldPos, new float[4]);
    }

    /**
     * Alloc-free variant. {@code scratchQuat} is overwritten — pass a
     * dedicated buffer per render thread.
     */
    public static boolean computePivotInto(int[] chainBoneIdx,
                                           float[][] chainPivots,
                                           Aero_BoneRenderPose[] pool,
                                           float[] outWorldPos,
                                           float[] scratchQuat) {
        int n = chainBoneIdx.length;
        if (n == 0) return false;

        // Start at the leaf bone's pivot + offset.
        int leafIdx = chainBoneIdx[n - 1];
        Aero_BoneRenderPose leaf = pool[leafIdx];
        float[] leafPivot = chainPivots[n - 1];
        outWorldPos[0] = leafPivot[0] + leaf.offsetX;
        outWorldPos[1] = leafPivot[1] + leaf.offsetY;
        outWorldPos[2] = leafPivot[2] + leaf.offsetZ;

        // Walk leaf-1 → root, applying each ancestor's transform.
        for (int i = n - 2; i >= 0; i--) {
            Aero_BoneRenderPose anc = pool[chainBoneIdx[i]];
            float[] aPivot = chainPivots[i];

            // Translate into ancestor's pivot-centered frame.
            float vx = outWorldPos[0] - aPivot[0];
            float vy = outWorldPos[1] - aPivot[1];
            float vz = outWorldPos[2] - aPivot[2];

            // Rotate by ancestor's local rotation.
            Aero_Quaternion.fromEulerDegrees(anc.rotX, anc.rotY, anc.rotZ, scratchQuat);
            float w = scratchQuat[0], x = scratchQuat[1], y = scratchQuat[2], z = scratchQuat[3];
            float tx = 2f * (y * vz - z * vy);
            float ty = 2f * (z * vx - x * vz);
            float tz = 2f * (x * vy - y * vx);
            float rx = vx + w * tx + (y * tz - z * ty);
            float ry = vy + w * ty + (z * tx - x * tz);
            float rz = vz + w * tz + (x * ty - y * tx);

            // Translate back to world: add ancestor pivot + offset.
            outWorldPos[0] = rx + aPivot[0] + anc.offsetX;
            outWorldPos[1] = ry + aPivot[1] + anc.offsetY;
            outWorldPos[2] = rz + aPivot[2] + anc.offsetZ;
        }
        return true;
    }

    /**
     * Computes every chain pivot in one root-to-leaf pass. {@code outWorldPositions}
     * is packed as xyz triples; {@code outParentRotations} is packed as
     * wxyz quaternion tuples, matching {@link Aero_Quaternion}.
     */
    static boolean computeChainPivotsInto(int[] chainBoneIdx,
                                          float[][] chainPivots,
                                          Aero_BoneRenderPose[] pool,
                                          float[] outWorldPositions,
                                          float[] outParentRotations,
                                          float[] scratchLocalQuat) {
        int n = chainBoneIdx.length;
        if (n == 0) return false;

        float parentW = 1f, parentX = 0f, parentY = 0f, parentZ = 0f;
        float parentTx = 0f, parentTy = 0f, parentTz = 0f;

        for (int i = 0; i < n; i++) {
            int rotBase = i * 4;
            outParentRotations[rotBase] = parentW;
            outParentRotations[rotBase + 1] = parentX;
            outParentRotations[rotBase + 2] = parentY;
            outParentRotations[rotBase + 3] = parentZ;

            Aero_BoneRenderPose pose = pool[chainBoneIdx[i]];
            float[] pivot = chainPivots[i];
            float px = pivot[0], py = pivot[1], pz = pivot[2];

            float vx = px + pose.offsetX;
            float vy = py + pose.offsetY;
            float vz = pz + pose.offsetZ;
            float tx = 2f * (parentY * vz - parentZ * vy);
            float ty = 2f * (parentZ * vx - parentX * vz);
            float tz = 2f * (parentX * vy - parentY * vx);
            int posBase = i * 3;
            outWorldPositions[posBase] =
                vx + parentW * tx + (parentY * tz - parentZ * ty) + parentTx;
            outWorldPositions[posBase + 1] =
                vy + parentW * ty + (parentZ * tx - parentX * tz) + parentTy;
            outWorldPositions[posBase + 2] =
                vz + parentW * tz + (parentX * ty - parentY * tx) + parentTz;

            Aero_Quaternion.fromEulerDegrees(pose.rotX, pose.rotY, pose.rotZ, scratchLocalQuat);
            float localW = scratchLocalQuat[0];
            float localX = scratchLocalQuat[1];
            float localY = scratchLocalQuat[2];
            float localZ = scratchLocalQuat[3];

            tx = 2f * (localY * pz - localZ * py);
            ty = 2f * (localZ * px - localX * pz);
            tz = 2f * (localX * py - localY * px);
            float rpx = px + localW * tx + (localY * tz - localZ * ty);
            float rpy = py + localW * ty + (localZ * tx - localX * tz);
            float rpz = pz + localW * tz + (localX * ty - localY * tx);

            vx = px + pose.offsetX - rpx;
            vy = py + pose.offsetY - rpy;
            vz = pz + pose.offsetZ - rpz;
            tx = 2f * (parentY * vz - parentZ * vy);
            ty = 2f * (parentZ * vx - parentX * vz);
            tz = 2f * (parentX * vy - parentY * vx);
            parentTx += vx + parentW * tx + (parentY * tz - parentZ * ty);
            parentTy += vy + parentW * ty + (parentZ * tx - parentX * tz);
            parentTz += vz + parentW * tz + (parentX * ty - parentY * tx);

            float nextW = parentW * localW - parentX * localX - parentY * localY - parentZ * localZ;
            float nextX = parentW * localX + parentX * localW + parentY * localZ - parentZ * localY;
            float nextY = parentW * localY - parentX * localZ + parentY * localW + parentZ * localX;
            float nextZ = parentW * localZ + parentX * localY - parentY * localX + parentZ * localW;
            parentW = nextW;
            parentX = nextX;
            parentY = nextY;
            parentZ = nextZ;
        }
        return true;
    }
}
