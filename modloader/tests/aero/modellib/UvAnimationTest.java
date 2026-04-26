package aero.modellib;

import org.junit.Test;

import static org.junit.Assert.*;

/**
 * Per-bone UV animation pipeline:
 *  - {@link Aero_AnimationClip} accepts uv_offset and uv_scale channels
 *  - {@link Aero_AnimationLoader} parses both as additive optional schema
 *  - {@link Aero_BoneRenderPose} carries uOffset/vOffset/uScale/vScale
 *  - identity transform is detected via {@link Aero_BoneRenderPose#uvIsIdentity}
 *
 * The visual integration (renderer multiplying u/v before
 * tess.addVertexWithUV) is covered by manual showcase blocks; these tests
 * pin down the data path and the schema acceptance criteria.
 */
public class UvAnimationTest {

    private static final float DELTA = 0.001f;
    private static final String VER = "\"format_version\":\"1.0\",";

    // ----- Builder API + sample -----

    @Test
    public void uvOffsetChannelSamplesLikePosition() {
        Aero_AnimationClip clip = Aero_AnimationClip.builder("scroll")
            .length(1f)
            .bone("belt")
            .uvOffset(
                new float[]{0f, 1f},
                new float[][]{{0f, 0f, 0f}, {1f, 0f, 0f}},
                new Aero_Easing[]{Aero_Easing.LINEAR, Aero_Easing.LINEAR})
            .endBone()
            .build();

        int boneIdx = clip.indexOfBone("belt");
        float[] out = new float[3];
        assertTrue(clip.sampleUvOffsetInto(boneIdx, 0.5f, out));
        assertEquals("uv_offset midpoint u", 0.5f, out[0], DELTA);
        assertEquals("uv_offset midpoint v", 0f,   out[1], DELTA);
    }

    @Test
    public void uvScaleChannelSamplesLikeScale() {
        Aero_AnimationClip clip = Aero_AnimationClip.builder("zoom")
            .length(1f)
            .bone("face")
            .uvScale(
                new float[]{0f, 1f},
                new float[][]{{1f, 1f, 0f}, {2f, 0.5f, 0f}},
                new Aero_Easing[]{Aero_Easing.LINEAR, Aero_Easing.LINEAR})
            .endBone()
            .build();

        int boneIdx = clip.indexOfBone("face");
        float[] out = new float[3];
        assertTrue(clip.sampleUvScaleInto(boneIdx, 0.5f, out));
        assertEquals("uv_scale midpoint u", 1.5f,  out[0], DELTA);
        assertEquals("uv_scale midpoint v", 0.75f, out[1], DELTA);
    }

    @Test
    public void uvSamplesReturnFalseWhenChannelAbsent() {
        // A bone that only has rotation should report no UV data.
        Aero_AnimationClip clip = Aero_AnimationClip.builder("rotonly")
            .length(1f)
            .bone("body")
            .rotation(
                new float[]{0f},
                new float[][]{{0f, 0f, 0f}},
                new Aero_Easing[]{Aero_Easing.LINEAR})
            .endBone()
            .build();

        int boneIdx = clip.indexOfBone("body");
        float[] out = new float[]{99f, 99f, 99f};
        assertFalse(clip.sampleUvOffsetInto(boneIdx, 0.5f, out));
        assertFalse(clip.sampleUvScaleInto(boneIdx, 0.5f, out));
        // out should be untouched when the channel is absent (we don't
        // overwrite). Documents the contract for callers that probe.
        assertEquals(99f, out[0], DELTA);
    }

    @Test
    public void uvOffsetSlerpHeuristicDoesNotApply() {
        // Slerp's hybrid rule is rotation-channel only. Even a >180° UV
        // offset segment must interpolate linearly (no quat-space short
        // arc on UV — UVs are not rotations).
        Aero_AnimationClip clip = Aero_AnimationClip.builder("bigshift")
            .length(1f)
            .bone("belt")
            .uvOffset(
                new float[]{0f, 1f},
                new float[][]{{0f, 0f, 0f}, {500f, 0f, 0f}},
                new Aero_Easing[]{Aero_Easing.LINEAR, Aero_Easing.LINEAR})
            .endBone()
            .build();

        int boneIdx = clip.indexOfBone("belt");
        float[] out = new float[3];
        assertTrue(clip.sampleUvOffsetInto(boneIdx, 0.5f, out));
        assertEquals("UV offset > 180° still linear-lerps to 250", 250f, out[0], DELTA);
    }

    // ----- Loader (schema) -----

    @Test
    public void loaderAcceptsUvOffsetChannelAdditively() {
        // Mixed clip: rotation + uv_offset — both should land in the bundle.
        Aero_AnimationBundle bundle = Aero_AnimationLoader.loadFromString(
            "{" + VER + "\"animations\":{\"scroll\":{\"loop\":\"loop\",\"length\":1,"
                + "\"bones\":{\"belt\":{"
                + "\"rotation\":{\"0\":{\"value\":[0,0,0],\"interp\":\"linear\"}},"
                + "\"uv_offset\":{"
                + "\"0\":{\"value\":[0,0,0],\"interp\":\"linear\"},"
                + "\"1\":{\"value\":[1,0,0],\"interp\":\"linear\"}}}}}}}");

        Aero_AnimationClip clip = bundle.getClip("scroll");
        assertNotNull(clip);
        int boneIdx = clip.indexOfBone("belt");
        assertTrue("clip must expose belt bone", boneIdx >= 0);

        float[] out = new float[3];
        assertTrue(clip.sampleUvOffsetInto(boneIdx, 0.5f, out));
        assertEquals(0.5f, out[0], DELTA);
    }

    @Test
    public void loaderAcceptsUvScaleChannelAdditively() {
        Aero_AnimationBundle bundle = Aero_AnimationLoader.loadFromString(
            "{" + VER + "\"animations\":{\"pulse\":{\"loop\":\"loop\",\"length\":1,"
                + "\"bones\":{\"face\":{\"uv_scale\":{"
                + "\"0\":{\"value\":[1,1,0],\"interp\":\"linear\"},"
                + "\"1\":{\"value\":[2,2,0],\"interp\":\"linear\"}}}}}}}");

        Aero_AnimationClip clip = bundle.getClip("pulse");
        int boneIdx = clip.indexOfBone("face");
        float[] out = new float[3];
        assertTrue(clip.sampleUvScaleInto(boneIdx, 0.5f, out));
        assertEquals(1.5f, out[0], DELTA);
        assertEquals(1.5f, out[1], DELTA);
    }

    @Test
    public void v01JsonsLoadWithoutUvChannels() {
        // Existing v0.1 schema (rot/pos/scl only) must still load — UV is
        // strictly additive and absent channels mean rest values.
        Aero_AnimationBundle bundle = Aero_AnimationLoader.loadFromString(
            "{" + VER + "\"animations\":{\"idle\":{\"loop\":\"loop\",\"length\":1,"
                + "\"bones\":{\"body\":{\"rotation\":"
                + "{\"0\":{\"value\":[0,0,0],\"interp\":\"linear\"}}}}}}}");
        assertNotNull(bundle.getClip("idle"));
    }

    // ----- BoneRenderPose -----

    @Test
    public void poseDefaultsAreUvIdentity() {
        Aero_BoneRenderPose pose = new Aero_BoneRenderPose();
        // Constructor leaves all fields at Java defaults (0/0/0/0).
        // reset() is what every renderer calls before populating, so identity
        // means uOffset/vOffset == 0 AND uScale/vScale == 1.
        pose.reset();
        assertTrue("default pose must be UV identity", pose.uvIsIdentity());
        assertEquals(0f, pose.uOffset, DELTA);
        assertEquals(0f, pose.vOffset, DELTA);
        assertEquals(1f, pose.uScale, DELTA);
        assertEquals(1f, pose.vScale, DELTA);
    }

    @Test
    public void poseUvIdentityFlipsWhenAnyFieldChanges() {
        Aero_BoneRenderPose pose = new Aero_BoneRenderPose();
        pose.reset();
        assertTrue(pose.uvIsIdentity());

        pose.uOffset = 0.1f;
        assertFalse("non-zero uOffset must NOT be identity", pose.uvIsIdentity());

        pose.reset();
        pose.vScale = 0.5f;
        assertFalse("non-1 vScale must NOT be identity", pose.uvIsIdentity());
    }

    // ----- Endpoints / clamping -----

    @Test
    public void uvOffsetEndpointsExact() {
        Aero_AnimationClip clip = Aero_AnimationClip.builder("scroll")
            .length(1f)
            .bone("b")
            .uvOffset(
                new float[]{0f, 1f},
                new float[][]{{0f, 0f, 0f}, {3f, 4f, 0f}},
                new Aero_Easing[]{Aero_Easing.LINEAR, Aero_Easing.LINEAR})
            .endBone()
            .build();

        int boneIdx = clip.indexOfBone("b");
        float[] out = new float[3];

        assertTrue(clip.sampleUvOffsetInto(boneIdx, 0f, out));
        assertEquals(0f, out[0], DELTA);
        assertEquals(0f, out[1], DELTA);

        assertTrue(clip.sampleUvOffsetInto(boneIdx, 1f, out));
        assertEquals(3f, out[0], DELTA);
        assertEquals(4f, out[1], DELTA);
    }
}
