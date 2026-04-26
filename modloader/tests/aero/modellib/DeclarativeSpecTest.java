package aero.modellib;

import org.junit.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.*;

public class DeclarativeSpecTest {

    private static final float DELTA = 0.0001f;

    @Test
    public void animationSpecOwnsDefinitionAndCreatesPlayback() {
        Aero_AnimationSpec spec = Aero_AnimationSpec.builder(bundleWithClip("idle"))
            .state(0, "idle")
            .build();

        Aero_AnimationPlayback playback = spec.createPlayback();

        assertSame(spec.getBundle(), playback.getBundle());
        assertSame(spec.getDefinition(), playback.getDef());
        assertEquals("idle", spec.getClipName(0));
        assertNotNull(playback.getCurrentClip());
        assertEquals("idle", playback.getCurrentClip().name);
    }

    @Test
    public void modelSpecDeclaresMeshAnimationTransformOptionsAndLod() {
        Aero_AnimationSpec animation = Aero_AnimationSpec.builder(bundleWithClip("idle"))
            .state(0, "idle")
            .build();
        Aero_RenderOptions tint = Aero_RenderOptions.tint(1f, 0.5f, 0.25f);

        Aero_ModelSpec spec = Aero_ModelSpec.mesh(mesh())
            .texture("/models/robot.png")
            .animations(animation)
            .offset(-0.5f, 0f, -0.5f)
            .scale(0.75f)
            .cullingRadius(2f)
            .maxRenderDistance(128f)
            .animatedDistance(24d)
            .renderOptions(tint)
            .build();

        assertTrue(spec.isMesh());
        assertTrue(spec.isAnimated());
        assertEquals("/models/robot.png", spec.getTexturePath());
        assertSame(animation, spec.getAnimationSpec());
        assertSame(tint, spec.getRenderOptions());
        assertEquals(-0.5f, spec.getEntityTransform().offsetX, DELTA);
        assertEquals(0.75f, spec.getEntityTransform().scale, DELTA);
        assertEquals(24d, spec.getAnimatedDistanceBlocks(), 0.0001d);
        assertEquals(Aero_RenderLod.ANIMATED,
            spec.lodRelative(8d, 0d, 0d, Aero_RenderDistanceCulling.VIEW_DISTANCE_FAR));
        assertEquals(Aero_RenderLod.STATIC,
            spec.lodRelative(40d, 0d, 0d, Aero_RenderDistanceCulling.VIEW_DISTANCE_FAR));
    }

    @Test
    public void modelSpecCanDeclareStatesInlineAfterAnimationBundle() {
        Aero_ModelSpec spec = Aero_ModelSpec.mesh(mesh())
            .animations(bundleWithClip("idle"))
            .state(0, "idle")
            .build();

        assertEquals("idle", spec.getAnimationDefinition().getClipName(0));
        assertSame(spec.getAnimationBundle(), spec.createPlayback().getBundle());
    }

    @Test
    public void jsonSpecIsStaticAndCanKeepTexturePath() {
        Aero_JsonModel model = json();
        Aero_ModelSpec spec = Aero_ModelSpec.json(model)
            .texture("/models/static.png")
            .offset(1f, 2f, 3f)
            .build();

        assertTrue(spec.isJson());
        assertFalse(spec.isAnimated());
        assertSame(model, spec.getJsonModel());
        assertEquals("/models/static.png", spec.getTexturePath());
        assertEquals(1f, spec.getEntityTransform().offsetX, DELTA);
    }

    @Test(expected = IllegalStateException.class)
    public void jsonSpecRejectsAnimations() {
        Aero_ModelSpec.json(json())
            .animations(bundleWithClip("idle"))
            .state(0, "idle")
            .build();
    }

    @Test(expected = IllegalStateException.class)
    public void stateRequiresAnimationDeclaration() {
        Aero_ModelSpec.mesh(mesh())
            .state(0, "idle");
    }

    @Test(expected = IllegalStateException.class)
    public void createPlaybackRequiresAnimations() {
        Aero_ModelSpec.mesh(mesh()).build().createPlayback();
    }

    @Test
    public void applyStateSnapsByDefault() {
        Aero_AnimationBundle b = bundleWith("a", "b");
        Aero_AnimationSpec spec = Aero_AnimationSpec.builder(b)
            .state(0, "a").state(1, "b")
            .build();
        Aero_AnimationPlayback playback = spec.createPlayback();

        spec.applyState(playback, 1);

        assertEquals(0, spec.getDefaultTransitionTicks());
        assertEquals(1, playback.getCurrentState());
        assertFalse(playback.inTransition());
    }

    @Test
    public void applyStateBlendsWhenDefaultTransitionTicksSet() {
        Aero_AnimationBundle b = bundleWith("a", "b");
        Aero_AnimationSpec spec = Aero_AnimationSpec.builder(b)
            .state(0, "a").state(1, "b")
            .defaultTransitionTicks(4)
            .build();
        Aero_AnimationPlayback playback = spec.createPlayback();

        spec.applyState(playback, 1);

        assertEquals(4, spec.getDefaultTransitionTicks());
        assertEquals(1, playback.getCurrentState());
        assertTrue(playback.inTransition());
    }

    @Test
    public void modelSpecForwardsDefaultTransitionTicks() {
        Aero_ModelSpec spec = Aero_ModelSpec.mesh(mesh())
            .animations(bundleWith("a", "b"))
            .state(0, "a").state(1, "b")
            .defaultTransitionTicks(6)
            .build();

        Aero_AnimationPlayback playback = spec.createPlayback();
        spec.applyState(playback, 1);

        assertEquals(6, spec.getDefaultTransitionTicks());
        assertTrue(playback.inTransition());
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsNegativeDefaultTransitionTicks() {
        Aero_AnimationSpec.builder(bundleWithClip("idle"))
            .state(0, "idle")
            .defaultTransitionTicks(-1)
            .build();
    }

    @Test
    public void modelSpecDefaultTransitionTicksZeroWhenNotAnimated() {
        Aero_ModelSpec spec = Aero_ModelSpec.json(json()).build();
        assertEquals(0, spec.getDefaultTransitionTicks());
    }

    private static Aero_AnimationBundle bundleWith(String... clipNames) {
        Map clips = new HashMap();
        for (int i = 0; i < clipNames.length; i++) {
            clips.put(clipNames[i], TestClips.loopClip(clipNames[i], 1f));
        }
        return new Aero_AnimationBundle(clips, new HashMap(), new HashMap());
    }

    private static Aero_AnimationBundle bundleWithClip(String clipName) {
        Aero_AnimationClip clip = TestClips.clip(
            clipName, Aero_AnimationLoop.LOOP, 1f,
            new String[]{"root"},
            new float[][]{{0f}},
            new float[][][]{{{0f, 0f, 0f}}},
            new float[][]{{0f}},
            new float[][][]{{{0f, 0f, 0f}}}
        );
        Map clips = new HashMap();
        clips.put(clipName, clip);
        return new Aero_AnimationBundle(clips, new HashMap(), new HashMap());
    }

    private static Aero_MeshModel mesh() {
        return new Aero_MeshModel("mesh", emptyGroups(), 16f, new HashMap());
    }

    private static Aero_JsonModel json() {
        return new Aero_JsonModel("json", new float[0][30]);
    }

    private static float[][][] emptyGroups() {
        return new float[][][] {
            new float[0][],
            new float[0][],
            new float[0][],
            new float[0][]
        };
    }
}
