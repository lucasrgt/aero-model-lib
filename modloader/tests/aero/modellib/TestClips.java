package aero.modellib;

import java.util.HashMap;
import java.util.Map;

import aero.modellib.animation.Aero_AnimationBundle;
import aero.modellib.animation.Aero_AnimationClip;
import aero.modellib.animation.Aero_AnimationLoop;
import aero.modellib.animation.Aero_Easing;

final class TestClips {

    private TestClips() {}

    static Aero_AnimationClip clip(String name, Aero_AnimationLoop loop, float length,
                                   String[] bones,
                                   float[][] rotTimes, float[][][] rotValues,
                                   float[][] posTimes, float[][][] posValues) {
        return clip(name, loop, length, bones,
            rotTimes, rotValues,
            posTimes, posValues,
            null, null,
            null, null, null, null);
    }

    static Aero_AnimationClip clip(String name, Aero_AnimationLoop loop, float length,
                                   String[] bones,
                                   float[][] rotTimes, float[][][] rotValues,
                                   float[][] posTimes, float[][][] posValues,
                                   float[][] sclTimes, float[][][] sclValues) {
        return clip(name, loop, length, bones,
            rotTimes, rotValues,
            posTimes, posValues,
            sclTimes, sclValues,
            null, null, null, null);
    }

    static Aero_AnimationClip clip(String name, Aero_AnimationLoop loop, float length,
                                   String[] bones,
                                   float[][] rotTimes, float[][][] rotValues,
                                   float[][] posTimes, float[][][] posValues,
                                   float[][] sclTimes, float[][][] sclValues,
                                   float[] eventTimes, String[] eventChannels,
                                   String[] eventNames, String[] eventLocators) {
        Aero_AnimationClip.Builder builder = Aero_AnimationClip.builder(name)
            .loop(loop)
            .length(length);

        for (int i = 0; i < bones.length; i++) {
            Aero_AnimationClip.BoneBuilder bone = builder.bone(bones[i]);
            if (hasChannel(rotTimes, rotValues, i)) {
                bone.rotation(rotTimes[i], rotValues[i], linearEasings(rotTimes[i].length));
            }
            if (hasChannel(posTimes, posValues, i)) {
                bone.position(posTimes[i], posValues[i], linearEasings(posTimes[i].length));
            }
            if (hasChannel(sclTimes, sclValues, i)) {
                bone.scale(sclTimes[i], sclValues[i], linearEasings(sclTimes[i].length));
            }
        }

        if (eventTimes != null) {
            for (int i = 0; i < eventTimes.length; i++) {
                String locator = eventLocators != null ? eventLocators[i] : null;
                builder.event(eventTimes[i], eventChannels[i], eventNames[i], locator);
            }
        }
        return builder.build();
    }

    static Aero_AnimationClip loopClip(String name, float length) {
        return clip(name, Aero_AnimationLoop.LOOP, length,
            new String[]{"bone"},
            new float[][]{{0f}}, new float[][][]{{{0f, 0f, 0f}}},
            new float[][]{{0f}}, new float[][][]{{{0f, 0f, 0f}}});
    }

    static Aero_AnimationClip constantRotClip(String name, float rx, float ry, float rz) {
        return constantRotClipForBone(name, "x", rx, ry, rz);
    }

    static Aero_AnimationClip constantRotClipForBone(String name, String boneName,
                                                     float rx, float ry, float rz) {
        return clip(name, Aero_AnimationLoop.LOOP, 1f,
            new String[]{boneName},
            new float[][]{{0f}}, new float[][][]{{{rx, ry, rz}}},
            new float[][]{{0f}}, new float[][][]{{{0f, 0f, 0f}}});
    }

    static Aero_AnimationClip constantSclClip(String name, float s) {
        return clip(name, Aero_AnimationLoop.LOOP, 1f,
            new String[]{"x"},
            new float[][]{{0f}}, new float[][][]{{{0f, 0f, 0f}}},
            new float[][]{{0f}}, new float[][][]{{{0f, 0f, 0f}}},
            new float[][]{{0f}}, new float[][][]{{{s, s, s}}});
    }

    static Aero_AnimationBundle bundle(Aero_AnimationClip... clipsIn) {
        Map clips = new HashMap();
        for (int i = 0; i < clipsIn.length; i++) {
            clips.put(clipsIn[i].name, clipsIn[i]);
        }
        return new Aero_AnimationBundle(clips, new HashMap(), new HashMap());
    }

    static float[] sampleRot(Aero_AnimationClip clip, int boneIdx, float time) {
        return sample(clip, boneIdx, time, 0);
    }

    static float[] samplePos(Aero_AnimationClip clip, int boneIdx, float time) {
        return sample(clip, boneIdx, time, 1);
    }

    static float[] sampleScl(Aero_AnimationClip clip, int boneIdx, float time) {
        return sample(clip, boneIdx, time, 2);
    }

    static Aero_Easing[] linearEasings(int count) {
        Aero_Easing[] easings = new Aero_Easing[count];
        for (int i = 0; i < count; i++) easings[i] = Aero_Easing.LINEAR;
        return easings;
    }

    private static float[] sample(Aero_AnimationClip clip, int boneIdx, float time, int channel) {
        float[] out = new float[3];
        boolean ok;
        if (channel == 0) ok = clip.sampleRotInto(boneIdx, time, out);
        else if (channel == 1) ok = clip.samplePosInto(boneIdx, time, out);
        else ok = clip.sampleSclInto(boneIdx, time, out);
        return ok ? out : null;
    }

    private static boolean hasChannel(float[][] times, float[][][] values, int index) {
        return times != null && values != null
            && index < times.length && index < values.length
            && times[index] != null && values[index] != null;
    }
}
