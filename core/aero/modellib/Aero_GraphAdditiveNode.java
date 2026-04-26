package aero.modellib;

/**
 * Additive overlay node: a {@code base} node provides the foundation
 * pose (REPLACE), and each {@code overlay} node's contribution is added
 * on top, weighted by an optional float param.
 *
 * <p>Composition matches {@link Aero_AnimationStack}'s ADDITIVE mode:
 * rotation + position add, scale multiplies. Use this for layering
 * "look-at" or "carry weight" deltas onto a locomotion base — the
 * overlay clip should typically contain only the delta keyframes for
 * the bones that should change, not a full pose.
 */
public final class Aero_GraphAdditiveNode implements Aero_GraphNode {

    private final Aero_GraphNode base;
    private final Aero_GraphNode[] overlays;
    private final String[] weightParams;   // one per overlay; null entry = always 1.0
    private final float[] tmpRot = new float[3];
    private final float[] tmpPos = new float[3];
    private final float[] tmpScl = new float[3];

    public Aero_GraphAdditiveNode(Aero_GraphNode base,
                                  Aero_GraphNode[] overlays,
                                  String[] weightParams) {
        if (base == null) throw new IllegalArgumentException("base must not be null");
        if (overlays == null) throw new IllegalArgumentException("overlays must not be null");
        if (weightParams == null) weightParams = new String[overlays.length];
        if (weightParams.length != overlays.length) {
            throw new IllegalArgumentException(
                "weightParams length must match overlays length"
                + " (overlays=" + overlays.length + ", weights=" + weightParams.length + ")");
        }
        this.base = base;
        this.overlays = overlays;
        this.weightParams = weightParams;
    }

    public boolean evalInto(String boneName, float partialTick, Aero_GraphParams params,
                            float[] outRot, float[] outPos, float[] outScl) {
        boolean any = base.evalInto(boneName, partialTick, params, outRot, outPos, outScl);
        for (int i = 0; i < overlays.length; i++) {
            float w = weightParams[i] == null ? 1f : params.getFloat(weightParams[i]);
            if (w == 0f) continue;
            if (!overlays[i].evalInto(boneName, partialTick, params, tmpRot, tmpPos, tmpScl)) continue;

            outRot[0] += tmpRot[0] * w;
            outRot[1] += tmpRot[1] * w;
            outRot[2] += tmpRot[2] * w;
            outPos[0] += tmpPos[0] * w;
            outPos[1] += tmpPos[1] * w;
            outPos[2] += tmpPos[2] * w;
            // Scale multiplies (matches Aero_AnimationStack ADDITIVE):
            // out *= 1 + (overlay - 1) * weight.
            outScl[0] *= 1f + (tmpScl[0] - 1f) * w;
            outScl[1] *= 1f + (tmpScl[1] - 1f) * w;
            outScl[2] *= 1f + (tmpScl[2] - 1f) * w;
            any = true;
        }
        return any;
    }
}
