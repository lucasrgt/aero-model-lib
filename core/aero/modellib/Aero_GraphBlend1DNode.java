package aero.modellib;

/**
 * 1D blend node: distributes a float param across N children, picking
 * the two adjacent points to lerp between. Canonical use case is a
 * locomotion blend — children at param values 0 (idle), 5 (walk),
 * 10 (run), and the param drives "movement speed".
 *
 * <p>Children are kept in (paramThreshold, child) pairs sorted by
 * threshold. The active param picks the surrounding pair and lerps
 * between their poses. Below the lowest threshold the lowest child
 * plays alone; above the highest, the highest. Smooth between.
 *
 * <p>Pose composition uses the same {@link Aero_AnimationStack#compose}
 * style as REPLACE (linear lerp); rotations are euler-lerped (not
 * slerped) to keep semantics with single-clip behavior. For long-arc
 * rotation in a blend tree, choose adjacent thresholds whose poses are
 * already short-arc compatible.
 */
public final class Aero_GraphBlend1DNode implements Aero_GraphNode {

    private final String paramName;
    private final float[] thresholds;
    private final Aero_GraphNode[] children;

    /** Per-channel scratch reused across evalInto calls (single render thread). */
    private final float[] tmpRot = new float[3];
    private final float[] tmpPos = new float[3];
    private final float[] tmpScl = new float[3];

    public Aero_GraphBlend1DNode(String paramName, float[] thresholds, Aero_GraphNode[] children) {
        if (paramName == null || paramName.length() == 0) {
            throw new IllegalArgumentException("paramName must not be empty");
        }
        if (thresholds == null || children == null) {
            throw new IllegalArgumentException("thresholds + children must not be null");
        }
        if (thresholds.length != children.length) {
            throw new IllegalArgumentException("blend1D: thresholds and children length must match"
                + " (thresholds=" + thresholds.length + ", children=" + children.length + ")");
        }
        if (thresholds.length < 2) {
            throw new IllegalArgumentException("blend1D needs at least 2 children, got "
                + thresholds.length);
        }
        for (int i = 1; i < thresholds.length; i++) {
            if (thresholds[i] <= thresholds[i - 1]) {
                throw new IllegalArgumentException("blend1D thresholds must be strictly ascending"
                    + " (t[" + (i - 1) + "]=" + thresholds[i - 1]
                    + ", t[" + i + "]=" + thresholds[i] + ")");
            }
        }
        this.paramName = paramName;
        this.thresholds = thresholds;
        this.children = children;
    }

    public boolean evalInto(String boneName, float partialTick, Aero_GraphParams params,
                            float[] outRot, float[] outPos, float[] outScl) {
        float v = params.getFloat(paramName);
        int n = thresholds.length;

        // Below first or above last threshold → just that child.
        if (v <= thresholds[0]) {
            return children[0].evalInto(boneName, partialTick, params, outRot, outPos, outScl);
        }
        if (v >= thresholds[n - 1]) {
            return children[n - 1].evalInto(boneName, partialTick, params, outRot, outPos, outScl);
        }

        // Find surrounding (lo, hi) thresholds.
        int hi = 1;
        while (hi < n && thresholds[hi] <= v) hi++;
        int lo = hi - 1;
        float alpha = (v - thresholds[lo]) / (thresholds[hi] - thresholds[lo]);

        // Sample lo into outBuffers, hi into tmp, then lerp.
        boolean gotLo = children[lo].evalInto(boneName, partialTick, params, outRot, outPos, outScl);
        boolean gotHi = children[hi].evalInto(boneName, partialTick, params, tmpRot, tmpPos, tmpScl);
        if (!gotLo && !gotHi) return false;

        outRot[0] = outRot[0] + (tmpRot[0] - outRot[0]) * alpha;
        outRot[1] = outRot[1] + (tmpRot[1] - outRot[1]) * alpha;
        outRot[2] = outRot[2] + (tmpRot[2] - outRot[2]) * alpha;
        outPos[0] = outPos[0] + (tmpPos[0] - outPos[0]) * alpha;
        outPos[1] = outPos[1] + (tmpPos[1] - outPos[1]) * alpha;
        outPos[2] = outPos[2] + (tmpPos[2] - outPos[2]) * alpha;
        outScl[0] = outScl[0] + (tmpScl[0] - outScl[0]) * alpha;
        outScl[1] = outScl[1] + (tmpScl[1] - outScl[1]) * alpha;
        outScl[2] = outScl[2] + (tmpScl[2] - outScl[2]) * alpha;
        return true;
    }
}
