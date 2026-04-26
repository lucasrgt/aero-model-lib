package aero.modellib;

import java.util.ArrayList;
import java.util.List;

/**
 * Immutable ordered collection of {@link Aero_AnimationLayer layers} sampled
 * together into one pose per bone.
 */
public final class Aero_AnimationStack {

    private static final Aero_AnimationLayer[] EMPTY_LAYERS = new Aero_AnimationLayer[0];

    private final Aero_AnimationLayer[] layers;

    // Reused per-frame so sampleRot/Pos/Scl don't allocate.
    private final float[] tmp = new float[3];

    public static Builder builder() {
        return new Builder();
    }

    public static Aero_AnimationStack empty() {
        return new Aero_AnimationStack(EMPTY_LAYERS);
    }

    private Aero_AnimationStack(Aero_AnimationLayer[] layers) {
        this.layers = layers;
    }

    public Aero_AnimationLayer get(int index) {
        return layers[index];
    }

    public int size() {
        return layers.length;
    }

    /** Advances every layer's playback by one game tick. */
    public void tick() {
        for (int i = 0; i < layers.length; i++) {
            layers[i].getPlayback().tick();
        }
    }

    public boolean sampleRot(String boneName, float partialTick, float[] out) {
        return sampleChannel(boneName, partialTick, out, CHANNEL_ROT);
    }

    public boolean samplePos(String boneName, float partialTick, float[] out) {
        return sampleChannel(boneName, partialTick, out, CHANNEL_POS);
    }

    public boolean sampleScl(String boneName, float partialTick, float[] out) {
        out[0] = 1f; out[1] = 1f; out[2] = 1f;
        return sampleChannel(boneName, partialTick, out, CHANNEL_SCL);
    }

    /**
     * Samples rotation, position and scale in one layer walk. Renderers use
     * this to avoid doing the same clip lookup, bone-name map lookup and
     * interpolated-time calculation once per channel.
     */
    public boolean samplePose(String boneName, float partialTick,
                              float[] outRot, float[] outPos, float[] outScl) {
        return samplePose(boneName, partialTick, outRot, outPos, outScl, null, null);
    }

    /**
     * Samples rotation, position, scale plus UV offset/scale in one layer
     * walk. Pass {@code null} for outUvOffset/outUvScale to skip UV
     * sampling — same fast-path as the 5-arg overload.
     */
    public boolean samplePose(String boneName, float partialTick,
                              float[] outRot, float[] outPos, float[] outScl,
                              float[] outUvOffset, float[] outUvScale) {
        if (outRot == null || outPos == null || outScl == null) {
            throw new IllegalArgumentException("pose outputs must not be null");
        }

        outRot[0] = 0f; outRot[1] = 0f; outRot[2] = 0f;
        outPos[0] = 0f; outPos[1] = 0f; outPos[2] = 0f;
        outScl[0] = 1f; outScl[1] = 1f; outScl[2] = 1f;
        if (outUvOffset != null) { outUvOffset[0] = 0f; outUvOffset[1] = 0f; outUvOffset[2] = 0f; }
        if (outUvScale  != null) { outUvScale[0]  = 1f; outUvScale[1]  = 1f; outUvScale[2]  = 1f; }

        boolean any = false;
        for (int i = 0; i < layers.length; i++) {
            Aero_AnimationLayer layer = layers[i];
            Aero_AnimationPlayback pb = layer.getPlayback();
            Aero_AnimationClip clip = pb.getCurrentClip();
            if (clip == null) continue;
            int bi = clip.indexOfBone(boneName);
            if (bi < 0) continue;

            float time = pb.getInterpolatedTime(partialTick);
            float weight = layer.getWeight();
            boolean additive = layer.isAdditive();

            if (pb.sampleRotBlended(clip, bi, boneName, time, partialTick, tmp)) {
                compose(outRot, tmp, weight, additive, CHANNEL_ROT);
                any = true;
            }
            if (pb.samplePosBlended(clip, bi, boneName, time, partialTick, tmp)) {
                compose(outPos, tmp, weight, additive, CHANNEL_POS);
                any = true;
            }
            if (pb.sampleSclBlended(clip, bi, boneName, time, partialTick, tmp)) {
                compose(outScl, tmp, weight, additive, CHANNEL_SCL);
                any = true;
            }
            if (outUvOffset != null
                && pb.sampleUvOffsetBlended(clip, bi, boneName, time, partialTick, tmp)) {
                // UV offset composes like position (additive sums, REPLACE lerps).
                compose(outUvOffset, tmp, weight, additive, CHANNEL_POS);
                any = true;
            }
            if (outUvScale != null
                && pb.sampleUvScaleBlended(clip, bi, boneName, time, partialTick, tmp)) {
                // UV scale composes like scale (additive multiplies, REPLACE lerps).
                compose(outUvScale, tmp, weight, additive, CHANNEL_SCL);
                any = true;
            }
        }
        return any;
    }

    private static final int CHANNEL_ROT = 0;
    private static final int CHANNEL_POS = 1;
    private static final int CHANNEL_SCL = 2;

    private boolean sampleChannel(String boneName, float partialTick, float[] out, int channel) {
        if (channel != CHANNEL_SCL) {
            out[0] = 0f; out[1] = 0f; out[2] = 0f;
        }

        boolean any = false;
        for (int i = 0; i < layers.length; i++) {
            Aero_AnimationLayer layer = layers[i];
            Aero_AnimationPlayback pb = layer.getPlayback();
            Aero_AnimationClip clip = pb.getCurrentClip();
            if (clip == null) continue;
            int bi = clip.indexOfBone(boneName);
            if (bi < 0) continue;

            float time = pb.getInterpolatedTime(partialTick);
            boolean got;
            switch (channel) {
                case CHANNEL_ROT: got = pb.sampleRotBlended(clip, bi, boneName, time, partialTick, tmp); break;
                case CHANNEL_POS: got = pb.samplePosBlended(clip, bi, boneName, time, partialTick, tmp); break;
                default:          got = pb.sampleSclBlended(clip, bi, boneName, time, partialTick, tmp); break;
            }
            if (!got) continue;

            compose(out, tmp, layer.getWeight(), layer.isAdditive(), channel);
            any = true;
        }
        return any;
    }

    private static void compose(float[] out, float[] value, float weight,
                                boolean additive, int channel) {
        if (additive) {
            if (channel == CHANNEL_SCL) {
                out[0] *= 1f + (value[0] - 1f) * weight;
                out[1] *= 1f + (value[1] - 1f) * weight;
                out[2] *= 1f + (value[2] - 1f) * weight;
            } else {
                out[0] += value[0] * weight;
                out[1] += value[1] * weight;
                out[2] += value[2] * weight;
            }
        } else {
            out[0] = out[0] + (value[0] - out[0]) * weight;
            out[1] = out[1] + (value[1] - out[1]) * weight;
            out[2] = out[2] + (value[2] - out[2]) * weight;
        }
    }

    public static final class Builder {
        private final List layers = new ArrayList();

        private Builder() {}

        public Builder add(Aero_AnimationLayer layer) {
            if (layer == null) throw new IllegalArgumentException("layer must not be null");
            layers.add(layer);
            return this;
        }

        public Builder replace(Aero_AnimationPlayback playback) {
            return add(Aero_AnimationLayer.replace(playback));
        }

        public Builder additive(Aero_AnimationPlayback playback) {
            return add(Aero_AnimationLayer.additive(playback));
        }

        public Builder additive(Aero_AnimationPlayback playback, float weight) {
            return add(Aero_AnimationLayer.builder(playback).additive(true).weight(weight).build());
        }

        public Aero_AnimationStack build() {
            if (layers.isEmpty()) return Aero_AnimationStack.empty();
            return new Aero_AnimationStack((Aero_AnimationLayer[])
                layers.toArray(new Aero_AnimationLayer[layers.size()]));
        }
    }
}
