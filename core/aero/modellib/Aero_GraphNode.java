package aero.modellib;

/**
 * One node in an {@link Aero_AnimationGraph}. Each node samples a pose
 * for the requested bone and writes it into the caller's output buffers,
 * additively or replacing depending on its type.
 *
 * <p>The contract is intentionally simple: every node knows how to sample
 * itself given a bone name, the current playback time, and a parameter
 * bag. Composite nodes (blend1D, additive) own children and recurse.
 *
 * <p>Output buffers are caller-owned and pre-allocated per render thread.
 * Nodes must overwrite {@code outRot/outPos/outScl} (REPLACE semantics)
 * or accumulate into them (ADDITIVE) — see each node's javadoc.
 */
public interface Aero_GraphNode {

    /**
     * Samples this node into the output buffers. {@code outRot} is
     * (rx, ry, rz) in degrees; {@code outPos} is (px, py, pz) in pixel
     * units; {@code outScl} is per-axis scale.
     *
     * @return true if the node produced a value for {@code boneName};
     *         false if the bone is absent and the buffers are unchanged.
     */
    boolean evalInto(String boneName,
                     float partialTick,
                     Aero_GraphParams params,
                     float[] outRot, float[] outPos, float[] outScl);
}
