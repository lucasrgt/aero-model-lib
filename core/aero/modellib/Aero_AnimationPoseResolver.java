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
        // scratchRot doubles as UV scratch — at this point in the
        // resolver scratchRot has already been consumed so it's safe to
        // overwrite. Avoids forcing every renderer to allocate two more
        // float[3] for UV per call.
        resolveClip(ref, clip, playback, time, partialTick,
            scratchRot, scratchPos, scratchScl, scratchRot, scratchPos, out);
    }

    static void resolveClip(int boneIdx,
                            String boneName,
                            float[] pivot,
                            Aero_AnimationClip clip,
                            Aero_AnimationPlayback playback,
                            float time,
                            float partialTick,
                            float[] scratchRot,
                            float[] scratchPos,
                            float[] scratchScl,
                            Aero_BoneRenderPose out) {
        resolveClip(boneIdx, boneName, pivot, clip, playback, time, partialTick,
            scratchRot, scratchPos, scratchScl, scratchRot, scratchPos, out);
    }

    static void resolveClip(Aero_MeshModel.BoneRef ref,
                            Aero_AnimationClip clip,
                            Aero_AnimationPlayback playback,
                            float time,
                            float partialTick,
                            float[] scratchRot,
                            float[] scratchPos,
                            float[] scratchScl,
                            float[] scratchUvOff,
                            float[] scratchUvScl,
                            Aero_BoneRenderPose out) {
        resolveClip(ref.boneIdx, ref.boneName, ref.pivot, clip, playback, time, partialTick,
            scratchRot, scratchPos, scratchScl, scratchUvOff, scratchUvScl, out);
    }

    static void resolveClip(int boneIdx,
                            String boneName,
                            float[] pivot,
                            Aero_AnimationClip clip,
                            Aero_AnimationPlayback playback,
                            float time,
                            float partialTick,
                            float[] scratchRot,
                            float[] scratchPos,
                            float[] scratchScl,
                            float[] scratchUvOff,
                            float[] scratchUvScl,
                            Aero_BoneRenderPose out) {
        out.reset();
        out.setPivot(pivot);

        if (clip == null || boneIdx < 0) return;
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
        if (sampleUvOffset(clip, playback, boneIdx, boneName, time, partialTick, scratchUvOff)) {
            out.uOffset = scratchUvOff[0];
            out.vOffset = scratchUvOff[1];
        }
        if (sampleUvScale(clip, playback, boneIdx, boneName, time, partialTick, scratchUvScl)) {
            out.uScale = scratchUvScl[0];
            out.vScale = scratchUvScl[1];
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
        // Same scratch-reuse trick as resolveClip — UV samples after
        // pose composition is finished, scratchRot/scratchPos slot freed.
        resolveStack(stack, boneName, partialTick,
            scratchPivot, scratchRot, scratchPos, scratchScl, scratchRot, scratchPos, out);
    }

    static void resolveStack(Aero_AnimationStack stack,
                             String boneName,
                             float partialTick,
                             float[] scratchPivot,
                             float[] scratchRot,
                             float[] scratchPos,
                             float[] scratchScl,
                             float[] scratchUvOff,
                             float[] scratchUvScl,
                             Aero_BoneRenderPose out) {
        out.reset();
        lookupStackPivotInto(stack, boneName, scratchPivot);
        out.setPivot(scratchPivot);

        stack.samplePose(boneName, partialTick, scratchRot, scratchPos, scratchScl,
            scratchUvOff, scratchUvScl);
        out.rotX = scratchRot[0];
        out.rotY = scratchRot[1];
        out.rotZ = scratchRot[2];
        out.offsetX = scratchPos[0] * PIXEL_TO_BLOCK;
        out.offsetY = scratchPos[1] * PIXEL_TO_BLOCK;
        out.offsetZ = scratchPos[2] * PIXEL_TO_BLOCK;
        out.scaleX = scratchScl[0];
        out.scaleY = scratchScl[1];
        out.scaleZ = scratchScl[2];
        out.uOffset = scratchUvOff[0];
        out.vOffset = scratchUvOff[1];
        out.uScale  = scratchUvScl[0];
        out.vScale  = scratchUvScl[1];
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

    private static boolean sampleUvOffset(Aero_AnimationClip clip, Aero_AnimationPlayback playback,
                                          int boneIdx, String boneName, float time,
                                          float partialTick, float[] out) {
        if (playback != null) {
            return playback.sampleUvOffsetBlended(clip, boneIdx, boneName, time, partialTick, out);
        }
        return clip.sampleUvOffsetInto(boneIdx, time, out);
    }

    private static boolean sampleUvScale(Aero_AnimationClip clip, Aero_AnimationPlayback playback,
                                         int boneIdx, String boneName, float time,
                                         float partialTick, float[] out) {
        if (playback != null) {
            return playback.sampleUvScaleBlended(clip, boneIdx, boneName, time, partialTick, out);
        }
        return clip.sampleUvScaleInto(boneIdx, time, out);
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
