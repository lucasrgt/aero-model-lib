package aero.modellib;

final class Aero_AnimationPoseResolver {

    private static final float PIXEL_TO_BLOCK = 1f / 16f;

    private Aero_AnimationPoseResolver() {}

    static void resolveClip(Aero_MeshModel.BoneRef ref,
                            Aero_AnimationClip clip,
                            Aero_AnimationPlayback playback,
                            float time,
                            float partialTick,
                            float[] scratchRot,
                            float[] scratchPos,
                            float[] scratchScl,
                            Aero_BoneRenderPose out) {
        out.reset();
        out.setPivot(ref.pivot);

        int boneIdx = ref.boneIdx;
        if (clip == null || boneIdx < 0) return;

        String boneName = ref.boneName;
        if (sampleRot(clip, playback, boneIdx, boneName, time, partialTick, scratchRot)) {
            out.rotX = scratchRot[0];
            out.rotY = scratchRot[1];
            out.rotZ = scratchRot[2];
        }
        if (samplePos(clip, playback, boneIdx, boneName, time, partialTick, scratchPos)) {
            out.offsetX = scratchPos[0] * PIXEL_TO_BLOCK;
            out.offsetY = scratchPos[1] * PIXEL_TO_BLOCK;
            out.offsetZ = scratchPos[2] * PIXEL_TO_BLOCK;
        }
        if (sampleScl(clip, playback, boneIdx, boneName, time, partialTick, scratchScl)) {
            out.scaleX = scratchScl[0];
            out.scaleY = scratchScl[1];
            out.scaleZ = scratchScl[2];
        }
    }

    static void resolveStack(Aero_AnimationStack stack,
                             String boneName,
                             float partialTick,
                             float[] scratchPivot,
                             float[] scratchRot,
                             float[] scratchPos,
                             float[] scratchScl,
                             Aero_BoneRenderPose out) {
        out.reset();
        lookupStackPivotInto(stack, boneName, scratchPivot);
        out.setPivot(scratchPivot);

        stack.samplePose(boneName, partialTick, scratchRot, scratchPos, scratchScl);
        out.rotX = scratchRot[0];
        out.rotY = scratchRot[1];
        out.rotZ = scratchRot[2];
        out.offsetX = scratchPos[0] * PIXEL_TO_BLOCK;
        out.offsetY = scratchPos[1] * PIXEL_TO_BLOCK;
        out.offsetZ = scratchPos[2] * PIXEL_TO_BLOCK;
        out.scaleX = scratchScl[0];
        out.scaleY = scratchScl[1];
        out.scaleZ = scratchScl[2];
    }

    private static boolean sampleRot(Aero_AnimationClip clip, Aero_AnimationPlayback playback,
                                     int boneIdx, String boneName, float time,
                                     float partialTick, float[] out) {
        if (playback != null) {
            return playback.sampleRotBlended(clip, boneIdx, boneName, time, partialTick, out);
        }
        return clip.sampleRotInto(boneIdx, time, out);
    }

    private static boolean samplePos(Aero_AnimationClip clip, Aero_AnimationPlayback playback,
                                     int boneIdx, String boneName, float time,
                                     float partialTick, float[] out) {
        if (playback != null) {
            return playback.samplePosBlended(clip, boneIdx, boneName, time, partialTick, out);
        }
        return clip.samplePosInto(boneIdx, time, out);
    }

    private static boolean sampleScl(Aero_AnimationClip clip, Aero_AnimationPlayback playback,
                                     int boneIdx, String boneName, float time,
                                     float partialTick, float[] out) {
        if (playback != null) {
            return playback.sampleSclBlended(clip, boneIdx, boneName, time, partialTick, out);
        }
        return clip.sampleSclInto(boneIdx, time, out);
    }

    private static boolean lookupStackPivotInto(Aero_AnimationStack stack, String boneName, float[] out) {
        out[0] = 0f;
        out[1] = 0f;
        out[2] = 0f;
        for (int i = 0; i < stack.size(); i++) {
            Aero_AnimationBundle bundle = stack.get(i).getPlayback().getBundle();
            if (bundle != null && bundle.getPivotInto(boneName, out)) return true;
        }
        return false;
    }
}
