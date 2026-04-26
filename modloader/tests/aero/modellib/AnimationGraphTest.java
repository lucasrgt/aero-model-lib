package aero.modellib;

import org.junit.Test;

import static org.junit.Assert.*;

/**
 * Unit tests for the animation graph: clip leaf, blend1D, additive, +
 * the root Aero_AnimationGraph dispatch. Each test wires a small graph
 * by hand and verifies pose composition matches expected math.
 */
public class AnimationGraphTest {

    private static final float DELTA = 0.001f;

    // ----- GraphParams -----

    @Test
    public void paramsRejectNonFiniteFloat() {
        Aero_GraphParams p = new Aero_GraphParams();
        try {
            p.setFloat("speed", Float.POSITIVE_INFINITY);
            fail("expected non-finite rejection");
        } catch (IllegalArgumentException ex) {
            assertTrue(ex.getMessage().contains("'speed'"));
        }
    }

    @Test
    public void triggerConsumeIsOneShot() {
        Aero_GraphParams p = new Aero_GraphParams();
        p.fireTrigger("attack");
        assertTrue(p.peekTrigger("attack"));
        assertTrue(p.consumeTrigger("attack"));
        assertFalse("trigger must clear after consume", p.consumeTrigger("attack"));
        assertFalse(p.peekTrigger("attack"));
    }

    // ----- ClipNode -----

    @Test
    public void clipNodeReplacesOutputBuffers() {
        // Clip with rotY=90 at a single keyframe.
        Aero_AnimationClip clip = Aero_AnimationClip.builder("rot")
            .length(1f)
            .bone("body")
            .rotation(new float[]{0f}, new float[][]{{0f, 90f, 0f}},
                new Aero_Easing[]{Aero_Easing.LINEAR})
            .endBone()
            .build();
        Aero_AnimationBundle bundle = TestClips.bundle(clip);
        Aero_AnimationPlayback pb = new Aero_AnimationDefinition()
            .state(0, "rot").createPlayback(bundle);

        Aero_GraphClipNode node = new Aero_GraphClipNode(pb);
        Aero_GraphParams params = new Aero_GraphParams();
        float[] rot = {99f, 99f, 99f}, pos = {99f, 99f, 99f}, scl = {99f, 99f, 99f};
        assertTrue(node.evalInto("body", 0f, params, rot, pos, scl));
        assertEquals(0f, rot[0], DELTA);
        assertEquals(90f, rot[1], DELTA);
        assertEquals(0f, rot[2], DELTA);
        // pos/scl should be reset to identity (no clip data for them).
        assertEquals(0f, pos[0], DELTA);
        assertEquals(1f, scl[0], DELTA);
    }

    @Test
    public void clipNodeReturnsFalseForUnknownBone() {
        Aero_AnimationClip clip = Aero_AnimationClip.builder("rot")
            .length(1f)
            .bone("body")
            .rotation(new float[]{0f}, new float[][]{{0f, 90f, 0f}},
                new Aero_Easing[]{Aero_Easing.LINEAR})
            .endBone()
            .build();
        Aero_AnimationPlayback pb = new Aero_AnimationDefinition()
            .state(0, "rot").createPlayback(TestClips.bundle(clip));

        Aero_GraphClipNode node = new Aero_GraphClipNode(pb);
        float[] r = new float[3], p = new float[3], s = new float[3];
        assertFalse(node.evalInto("missing_bone", 0f, new Aero_GraphParams(), r, p, s));
    }

    // ----- Blend1D -----

    @Test
    public void blend1DLerpsBetweenTwoChildren() {
        // Walk = rotY 0; Run = rotY 90. Param "speed" between thresholds 0 and 10.
        Aero_GraphNode walk = constNode(0f, 0f, 0f);
        Aero_GraphNode run = constNode(0f, 90f, 0f);
        Aero_GraphBlend1DNode blend = new Aero_GraphBlend1DNode("speed",
            new float[]{0f, 10f}, new Aero_GraphNode[]{walk, run});

        Aero_GraphParams p = new Aero_GraphParams();
        p.setFloat("speed", 5f); // midpoint
        float[] r = new float[3], pp = new float[3], s = new float[3];
        blend.evalInto("body", 0f, p, r, pp, s);
        assertEquals("blend1D midpoint Y", 45f, r[1], DELTA);
    }

    @Test
    public void blend1DClampsBelowFirstThreshold() {
        Aero_GraphNode walk = constNode(0f, 0f, 0f);
        Aero_GraphNode run = constNode(0f, 90f, 0f);
        Aero_GraphBlend1DNode blend = new Aero_GraphBlend1DNode("speed",
            new float[]{5f, 10f}, new Aero_GraphNode[]{walk, run});

        Aero_GraphParams p = new Aero_GraphParams();
        p.setFloat("speed", -3f); // below first threshold
        float[] r = new float[3], pp = new float[3], s = new float[3];
        blend.evalInto("body", 0f, p, r, pp, s);
        assertEquals("below-threshold clamps to first child", 0f, r[1], DELTA);
    }

    @Test
    public void blend1DRejectsUnsortedThresholds() {
        Aero_GraphNode a = constNode(0f, 0f, 0f);
        Aero_GraphNode b = constNode(0f, 1f, 0f);
        try {
            new Aero_GraphBlend1DNode("p", new float[]{5f, 3f}, new Aero_GraphNode[]{a, b});
            fail("expected unsorted-threshold rejection");
        } catch (IllegalArgumentException ex) {
            assertTrue(ex.getMessage().contains("ascending"));
        }
    }

    // ----- AdditiveNode -----

    @Test
    public void additiveNodeAddsOverlayRotation() {
        // Base: 0 rotation. Overlay: +30 around X. Weight param = 1.
        Aero_GraphNode base = constNode(0f, 0f, 0f);
        Aero_GraphNode overlay = constNode(30f, 0f, 0f);
        Aero_GraphAdditiveNode add = new Aero_GraphAdditiveNode(base,
            new Aero_GraphNode[]{overlay}, new String[]{null});

        Aero_GraphParams p = new Aero_GraphParams();
        float[] r = new float[3], pp = new float[3], s = new float[3];
        add.evalInto("body", 0f, p, r, pp, s);
        assertEquals(30f, r[0], DELTA);
    }

    @Test
    public void additiveNodeWeightsOverlayByParam() {
        Aero_GraphNode base = constNode(0f, 0f, 0f);
        Aero_GraphNode overlay = constNode(30f, 0f, 0f);
        Aero_GraphAdditiveNode add = new Aero_GraphAdditiveNode(base,
            new Aero_GraphNode[]{overlay}, new String[]{"intensity"});

        Aero_GraphParams p = new Aero_GraphParams();
        p.setFloat("intensity", 0.5f);
        float[] r = new float[3], pp = new float[3], s = new float[3];
        add.evalInto("body", 0f, p, r, pp, s);
        assertEquals("overlay scaled by 0.5", 15f, r[0], DELTA);
    }

    // ----- Root graph -----

    @Test
    public void graphRequiresNonNullRootAndParams() {
        try {
            new Aero_AnimationGraph(null, new Aero_GraphParams());
            fail();
        } catch (IllegalArgumentException ex) {
            assertTrue(ex.getMessage().contains("root"));
        }
        try {
            new Aero_AnimationGraph(constNode(0f, 0f, 0f), null);
            fail();
        } catch (IllegalArgumentException ex) {
            assertTrue(ex.getMessage().contains("params"));
        }
    }

    @Test
    public void graphSamplePoseDispatchesToRoot() {
        Aero_AnimationGraph graph = new Aero_AnimationGraph(
            constNode(10f, 20f, 30f), new Aero_GraphParams());
        float[] r = new float[3], p = new float[3], s = new float[3];
        graph.samplePose("body", 0f, r, p, s);
        assertEquals(10f, r[0], DELTA);
        assertEquals(20f, r[1], DELTA);
        assertEquals(30f, r[2], DELTA);
    }

    // ----- Helper: const-rotation node for blend math tests -----

    private static Aero_GraphNode constNode(final float rx, final float ry, final float rz) {
        return new Aero_GraphNode() {
            public boolean evalInto(String boneName, float partialTick, Aero_GraphParams params,
                                    float[] outRot, float[] outPos, float[] outScl) {
                outRot[0] = rx; outRot[1] = ry; outRot[2] = rz;
                outPos[0] = 0f; outPos[1] = 0f; outPos[2] = 0f;
                outScl[0] = 1f; outScl[1] = 1f; outScl[2] = 1f;
                return true;
            }
        };
    }
}
