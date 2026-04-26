package aero.modellib;

import org.junit.Test;

import static org.junit.Assert.*;

/**
 * Sanity tests for the {@link Aero_ProceduralPose} hook contract — the
 * resolver applies keyframe values, then the hook adds runtime deltas on
 * top.
 */
public class ProceduralPoseTest {

    private static final float DELTA = 1e-4f;

    @Test
    public void hookSeesKeyframeValuesBeforeAddingDeltas() {
        // A clip that puts the bone at rotY = 90 at t=0 (keyframed pose).
        Aero_AnimationClip clip = TestClips.constantRotClipForBone(
            "turret", "turret", 0f, 90f, 0f);
        Aero_AnimationBundle bundle = TestClips.bundle(clip);
        Aero_AnimationPlayback playback = new Aero_AnimationDefinition()
            .state(0, "turret").createPlayback(bundle);

        // Mock a BoneRef as if Aero_MeshModel resolved it (package-private ctor).
        Aero_MeshModel.BoneRef ref = new Aero_MeshModel.BoneRef(
            clip.indexOfBone("turret"), "turret", new float[]{0f, 0f, 0f});

        Aero_BoneRenderPose pose = new Aero_BoneRenderPose();
        Aero_AnimationPoseResolver.resolveClip(ref, clip, playback, 0f, 0f,
            new float[3], new float[3], new float[3], pose);

        assertEquals("keyframe Y rotation", 90f, pose.rotY, DELTA);

        // Now apply the procedural hook and verify it adds on top.
        Aero_ProceduralPose hook = new Aero_ProceduralPose() {
            public void apply(String boneName, Aero_BoneRenderPose out) {
                if ("turret".equals(boneName)) out.rotY += 45f;
            }
        };
        hook.apply(ref.boneName, pose);

        assertEquals("keyframe + procedural Y", 135f, pose.rotY, DELTA);
        assertEquals("X untouched", 0f, pose.rotX, DELTA);
        assertEquals("Z untouched", 0f, pose.rotZ, DELTA);
    }

    @Test
    public void hookCanRotatePropellerWithoutAnyKeyframes() {
        // No clip → reset pose, all rotation 0. Hook drives the entire spin.
        Aero_BoneRenderPose pose = new Aero_BoneRenderPose();
        pose.reset();

        Aero_ProceduralPose hook = new Aero_ProceduralPose() {
            public void apply(String boneName, Aero_BoneRenderPose out) {
                if ("propeller".equals(boneName)) out.rotX = 270f;
            }
        };
        hook.apply("propeller", pose);

        assertEquals("procedural-only X", 270f, pose.rotX, DELTA);
        assertEquals("scale stays at identity", 1f, pose.scaleX, DELTA);
    }

    @Test
    public void hookWithoutMatchingBoneIsNoOp() {
        Aero_BoneRenderPose pose = new Aero_BoneRenderPose();
        pose.reset();
        pose.rotY = 30f;   // some keyframe value

        Aero_ProceduralPose hook = new Aero_ProceduralPose() {
            public void apply(String boneName, Aero_BoneRenderPose out) {
                if ("notTheBone".equals(boneName)) out.rotY += 999f;
            }
        };
        hook.apply("realBone", pose);

        assertEquals("unchanged when bone doesn't match", 30f, pose.rotY, DELTA);
    }

    @Test
    public void boneRenderPoseFieldsAreMutableAndPublic() {
        // API contract: consumers can write to rotation/offset/scale fields directly.
        Aero_BoneRenderPose pose = new Aero_BoneRenderPose();
        pose.reset();
        pose.rotX = 1f;
        pose.rotY = 2f;
        pose.rotZ = 3f;
        pose.offsetX = 4f;
        pose.offsetY = 5f;
        pose.offsetZ = 6f;
        pose.scaleX = 7f;
        pose.scaleY = 8f;
        pose.scaleZ = 9f;

        assertEquals(1f, pose.rotX, DELTA);
        assertEquals(5f, pose.offsetY, DELTA);
        assertEquals(9f, pose.scaleZ, DELTA);
    }
}
