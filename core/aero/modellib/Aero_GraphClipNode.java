package aero.modellib;

/**
 * Leaf graph node that plays a single clip via an {@link Aero_AnimationPlayback}.
 * Output is REPLACE — overwrites the caller's buffers with the sampled
 * pose. Identity (zero rotation, zero offset, scale 1) when the bone is
 * absent from the clip.
 *
 * <p>The playback is owned by the caller — the node only reads from it.
 * That lets multiple graph nodes wrap the same playback (e.g. one
 * "current state" playback shared between multiple Blend1D children when
 * they all reference the same clip but with different transitions).
 */
public final class Aero_GraphClipNode implements Aero_GraphNode {

    private final Aero_AnimationPlayback playback;

    public Aero_GraphClipNode(Aero_AnimationPlayback playback) {
        if (playback == null) throw new IllegalArgumentException("playback must not be null");
        this.playback = playback;
    }

    public Aero_AnimationPlayback getPlayback() {
        return playback;
    }

    public boolean evalInto(String boneName, float partialTick, Aero_GraphParams params,
                            float[] outRot, float[] outPos, float[] outScl) {
        Aero_AnimationClip clip = playback.getCurrentClip();
        if (clip == null) {
            outRot[0] = 0f; outRot[1] = 0f; outRot[2] = 0f;
            outPos[0] = 0f; outPos[1] = 0f; outPos[2] = 0f;
            outScl[0] = 1f; outScl[1] = 1f; outScl[2] = 1f;
            return false;
        }
        int boneIdx = clip.indexOfBone(boneName);
        if (boneIdx < 0) {
            outRot[0] = 0f; outRot[1] = 0f; outRot[2] = 0f;
            outPos[0] = 0f; outPos[1] = 0f; outPos[2] = 0f;
            outScl[0] = 1f; outScl[1] = 1f; outScl[2] = 1f;
            return false;
        }

        float time = playback.getInterpolatedTime(partialTick);
        boolean any = false;
        if (playback.sampleRotBlended(clip, boneIdx, boneName, time, partialTick, outRot)) {
            any = true;
        } else {
            outRot[0] = 0f; outRot[1] = 0f; outRot[2] = 0f;
        }
        if (playback.samplePosBlended(clip, boneIdx, boneName, time, partialTick, outPos)) {
            any = true;
        } else {
            outPos[0] = 0f; outPos[1] = 0f; outPos[2] = 0f;
        }
        if (playback.sampleSclBlended(clip, boneIdx, boneName, time, partialTick, outScl)) {
            any = true;
        } else {
            outScl[0] = 1f; outScl[1] = 1f; outScl[2] = 1f;
        }
        return any;
    }
}
