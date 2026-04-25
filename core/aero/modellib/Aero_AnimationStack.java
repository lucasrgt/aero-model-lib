package aero.modellib;

import java.util.ArrayList;
import java.util.List;

/**
 * Ordered collection of {@link Aero_AnimationLayer layers} that are sampled
 * together to produce a single combined pose per bone. The stack is what
 * a renderer queries for multi-controller / additive-layer animation:
 *
 * <pre>
 *   Aero_AnimationStack stack = new Aero_AnimationStack()
 *       .add(new Aero_AnimationLayer(walkPlayback))                 // base
 *       .add(new Aero_AnimationLayer(headTrackPlayback).additive(true));
 *
 *   stack.tick();                    // each frame, advances every layer
 *   stack.sampleRot("head", 0f, out); // returns the layered head rotation
 * </pre>
 *
 * <p>Sampling rules per bone (rotation/position/scale share the same logic):
 * <ol>
 *   <li>Iterate layers in insertion order.</li>
 *   <li>If the layer's current clip animates this bone, take its sample
 *       (with the layer's playback state, transition blend, etc.).</li>
 *   <li>If the layer is {@code additive}, multiply the sample by
 *       {@code layer.weight} and add it to the running accumulator.</li>
 *   <li>If the layer is in <em>replace</em> mode, lerp toward the new
 *       value with ratio {@code layer.weight} (full replace at weight 1,
 *       no-op at weight 0).</li>
 * </ol>
 *
 * <p>Bones not animated by any layer keep the default (zero rotation, zero
 * position, scale 1) — callers should treat the return {@code false} as
 * "no override, leave the bone at rest".
 */
public final class Aero_AnimationStack {

    private final List layers = new ArrayList();

    // Reused per-frame so sampleRot/Pos/Scl don't allocate.
    private final float[] tmp = new float[3];

    public Aero_AnimationStack add(Aero_AnimationLayer layer) {
        if (layer == null) throw new IllegalArgumentException("layer must not be null");
        layers.add(layer);
        return this;
    }

    public Aero_AnimationLayer get(int index) {
        return (Aero_AnimationLayer) layers.get(index);
    }

    public int size() {
        return layers.size();
    }

    /** Advances every layer's playback by one game tick. */
    public void tick() {
        for (int i = 0; i < layers.size(); i++) {
            ((Aero_AnimationLayer) layers.get(i)).playback.tick();
        }
    }

    public boolean sampleRot(String boneName, float partialTick, float[] out) {
        return sampleChannel(boneName, partialTick, out, CHANNEL_ROT);
    }

    public boolean samplePos(String boneName, float partialTick, float[] out) {
        return sampleChannel(boneName, partialTick, out, CHANNEL_POS);
    }

    public boolean sampleScl(String boneName, float partialTick, float[] out) {
        // Scale's identity is (1, 1, 1), not (0, 0, 0) — the channel-shared
        // accumulator starts at 0 for rot/pos and 1 for scale. We initialise
        // out before delegating so the additive math composes correctly.
        out[0] = 1f; out[1] = 1f; out[2] = 1f;
        return sampleChannel(boneName, partialTick, out, CHANNEL_SCL);
    }

    private static final int CHANNEL_ROT = 0;
    private static final int CHANNEL_POS = 1;
    private static final int CHANNEL_SCL = 2;

    private boolean sampleChannel(String boneName, float partialTick, float[] out, int channel) {
        // Rotation/position default to (0,0,0); scale starts at (1,1,1)
        // already set by the caller. The accumulator is `out` itself.
        if (channel != CHANNEL_SCL) {
            out[0] = 0f; out[1] = 0f; out[2] = 0f;
        }

        boolean any = false;
        for (int i = 0; i < layers.size(); i++) {
            Aero_AnimationLayer layer = (Aero_AnimationLayer) layers.get(i);
            Aero_AnimationPlayback pb = layer.playback;
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

            float w = layer.weight;
            if (layer.additive) {
                if (channel == CHANNEL_SCL) {
                    // Scale composes multiplicatively. An additive scale
                    // layer at weight 1 with sample (1.5, 1.5, 1.5) should
                    // multiply the running scale by 1.5; weight 0.5 lerps
                    // halfway to that multiplier — i.e. (1 + 0.5*(1.5-1)).
                    out[0] *= 1f + (tmp[0] - 1f) * w;
                    out[1] *= 1f + (tmp[1] - 1f) * w;
                    out[2] *= 1f + (tmp[2] - 1f) * w;
                } else {
                    out[0] += tmp[0] * w;
                    out[1] += tmp[1] * w;
                    out[2] += tmp[2] * w;
                }
            } else {
                // Replace mode lerps toward the new value at weight; weight
                // = 1 means full override, weight = 0 leaves the previous
                // contribution alone.
                out[0] = out[0] + (tmp[0] - out[0]) * w;
                out[1] = out[1] + (tmp[1] - out[1]) * w;
                out[2] = out[2] + (tmp[2] - out[2]) * w;
            }
            any = true;
        }
        return any;
    }
}
