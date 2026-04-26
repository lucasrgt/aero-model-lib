package aero.modellib;

/**
 * DAG-style animation composition for animator-grade workflows. A graph
 * is a single root {@link Aero_GraphNode} that recursively combines
 * other nodes — clips at the leaves, blend1D / additive / state-machine
 * nodes in the middle.
 *
 * <p>Sample by walking per-bone: caller iterates the model's named
 * groups, calls {@link #samplePose} for each bone name, and the graph
 * dispatches the request to its root node tree.
 *
 * <p>Coexists with {@link Aero_AnimationStack} — Stack remains the
 * lightweight flat-layered API for simple cases; Graph is the
 * tree-shaped option when blend trees are warranted.
 */
public final class Aero_AnimationGraph {

    private final Aero_GraphNode root;
    private final Aero_GraphParams params;

    public Aero_AnimationGraph(Aero_GraphNode root, Aero_GraphParams params) {
        if (root == null) throw new IllegalArgumentException("root must not be null");
        if (params == null) throw new IllegalArgumentException("params must not be null");
        this.root = root;
        this.params = params;
    }

    public Aero_GraphParams getParams() {
        return params;
    }

    /**
     * Samples the graph for the given bone. Output buffers are written
     * with the composite pose. Identity output (zero rot/pos, scale 1)
     * if the bone is absent from every reachable leaf.
     */
    public boolean samplePose(String boneName, float partialTick,
                              float[] outRot, float[] outPos, float[] outScl) {
        if (outRot == null || outPos == null || outScl == null) {
            throw new IllegalArgumentException("pose outputs must not be null");
        }
        outRot[0] = 0f; outRot[1] = 0f; outRot[2] = 0f;
        outPos[0] = 0f; outPos[1] = 0f; outPos[2] = 0f;
        outScl[0] = 1f; outScl[1] = 1f; outScl[2] = 1f;
        return root.evalInto(boneName, partialTick, params, outRot, outPos, outScl);
    }
}
