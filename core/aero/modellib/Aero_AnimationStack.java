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

            float w = layer.getWeight();
            if (layer.isAdditive()) {
                if (channel == CHANNEL_SCL) {
                    out[0] *= 1f + (tmp[0] - 1f) * w;
                    out[1] *= 1f + (tmp[1] - 1f) * w;
                    out[2] *= 1f + (tmp[2] - 1f) * w;
                } else {
                    out[0] += tmp[0] * w;
                    out[1] += tmp[1] * w;
                    out[2] += tmp[2] * w;
                }
            } else {
                out[0] = out[0] + (tmp[0] - out[0]) * w;
                out[1] = out[1] + (tmp[1] - out[1]) * w;
                out[2] = out[2] + (tmp[2] - out[2]) * w;
            }
            any = true;
        }
        return any;
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
