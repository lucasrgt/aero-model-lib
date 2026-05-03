package aero.modellib;

import org.junit.Test;

import java.util.HashMap;
import java.util.Map;

import aero.modellib.animation.Aero_AnimationBundle;
import aero.modellib.animation.Aero_AnimationLoader;
import aero.modellib.model.Aero_MeshModel;
import aero.modellib.skeletal.Aero_MorphState;
import aero.modellib.skeletal.Aero_MorphTarget;

import static org.junit.Assert.*;

/**
 * Unit tests for the morph target data path:
 *  - delta computation from base + target geometry
 *  - topology mismatch error messages
 *  - Aero_MorphState weight invariants + NBT round-trip
 *  - Aero_MeshModel attach/lookup
 *  - Aero_AnimationLoader schema 1.1 backward compat with 1.0
 */
public class MorphTargetTest {

    private static final float DELTA = 0.001f;
    private static final String VER_10 = "\"format_version\":\"1.0\",";
    private static final String VER_11 = "\"format_version\":\"1.1\",";

    // ----- delta computation -----

    @Test
    public void fromTargetMeshComputesPerVertexDeltas() {
        // Single tri group with one triangle: base at (0,0,0)..., target shifted +1 in X.
        float[][][] base = singleTri(0f, 0f, 0f);
        float[][][] target = singleTri(1f, 0f, 0f);

        Aero_MorphTarget mt = Aero_MorphTarget.fromTargetMesh("shift", base, target);

        // Each of the 3 vertices' Δx should be +1, Δy/z should be 0.
        float[] d = mt.deltas[0][0]; // group 0, tri 0
        assertEquals(9, d.length);
        for (int v = 0; v < 3; v++) {
            assertEquals(1f, d[v * 3 + 0], DELTA);
            assertEquals(0f, d[v * 3 + 1], DELTA);
            assertEquals(0f, d[v * 3 + 2], DELTA);
        }
        assertEquals("totalMagnitude sums |Δ| across all vertices",
            3f, mt.totalMagnitude, DELTA);
    }

    @Test
    public void topologyMismatchProducesReadableError() {
        // Base has 1 tri in group 0, target has 2 — should reject with a
        // message that includes group index and counts.
        float[][][] base = new float[][][]{
            new float[][]{ singleTriRow() },
            new float[0][], new float[0][], new float[0][]
        };
        float[][][] target = new float[][][]{
            new float[][]{ singleTriRow(), singleTriRow() },
            new float[0][], new float[0][], new float[0][]
        };

        try {
            Aero_MorphTarget.fromTargetMesh("bad", base, target);
            fail("expected topology mismatch rejection");
        } catch (IllegalArgumentException ex) {
            assertTrue("error must name the morph: " + ex.getMessage(),
                ex.getMessage().contains("'bad'"));
            assertTrue("error must mention triangle count mismatch: " + ex.getMessage(),
                ex.getMessage().contains("base=1") && ex.getMessage().contains("target=2"));
        }
    }

    // ----- Aero_MorphState -----

    @Test
    public void morphStateIsEmptyByDefault() {
        Aero_MorphState s = new Aero_MorphState();
        assertTrue(s.isEmpty());
        assertEquals(0f, s.get("anything"), DELTA);
    }

    @Test
    public void morphStateRejectsNonFiniteWeight() {
        Aero_MorphState s = new Aero_MorphState();
        try {
            s.set("smile", Float.NaN);
            fail("expected NaN rejection");
        } catch (IllegalArgumentException ex) {
            assertTrue("error must name the morph",
                ex.getMessage().contains("'smile'"));
            assertTrue("error must mention NaN",
                ex.getMessage().contains("NaN"));
        }
    }

    @Test
    public void morphStateZeroWeightRemovesEntry() {
        Aero_MorphState s = new Aero_MorphState();
        s.set("smile", 0.5f);
        assertFalse(s.isEmpty());
        s.set("smile", 0f);
        assertTrue("zero-weight should be removed (saves render-time iteration)",
            s.isEmpty());
    }

    @Test
    public void morphStateNbtRoundTrip() {
        Aero_MorphState src = new Aero_MorphState();
        src.set("smile", 0.7f);
        src.set("frown", -0.2f);

        // Mock bag: simple in-memory map.
        final Map bag = new HashMap();
        src.writeStringFloatMapNbt(new Aero_MorphState.StringFloatBagWriter() {
            public void put(String name, float value) { bag.put(name, Float.valueOf(value)); }
        });

        Aero_MorphState dst = new Aero_MorphState();
        dst.readStringFloatMapNbt(new Aero_MorphState.StringFloatBagReader() {
            public void forEach(Aero_MorphState.StringFloatBagWriter sink) {
                java.util.Iterator it = bag.entrySet().iterator();
                while (it.hasNext()) {
                    Map.Entry e = (Map.Entry) it.next();
                    sink.put((String) e.getKey(), ((Float) e.getValue()).floatValue());
                }
            }
        });

        assertEquals(0.7f, dst.get("smile"), DELTA);
        assertEquals(-0.2f, dst.get("frown"), DELTA);
    }

    // ----- Aero_MeshModel attach -----

    @Test
    public void meshModelHasNoMorphTargetsByDefault() {
        Aero_MeshModel m = new Aero_MeshModel("test", emptyGroups());
        assertFalse(m.hasMorphTargets());
        assertNull(m.getMorphTarget("anything"));
    }

    @Test
    public void meshModelAttachesAndLooksUpMorphTarget() {
        Aero_MeshModel m = new Aero_MeshModel("test", emptyGroups());
        Aero_MorphTarget mt = Aero_MorphTarget.fromTargetMesh("smile",
            emptyGroups(), emptyGroups());
        m.attachMorphTarget(mt);

        assertTrue(m.hasMorphTargets());
        assertSame(mt, m.getMorphTarget("smile"));
    }

    // ----- Loader schema 1.0 / 1.1 compat -----

    @Test
    public void v10JsonsLoadUnchanged() {
        Aero_AnimationBundle bundle = Aero_AnimationLoader.loadFromString(
            "{" + VER_10 + "\"animations\":{}}");
        assertNotNull(bundle);
        assertTrue("v1.0 bundle should have empty morphTargetPaths",
            bundle.morphTargetPaths.isEmpty());
    }

    @Test
    public void v11JsonsLoadWithMorphTargets() {
        Aero_AnimationBundle bundle = Aero_AnimationLoader.loadFromString(
            "{" + VER_11 + "\"animations\":{},"
                + "\"morph_targets\":{\"smile\":\"/models/Robot_smile.obj\","
                + "\"frown\":\"/models/Robot_frown.obj\"}}");
        assertEquals(2, bundle.morphTargetPaths.size());
        assertEquals("/models/Robot_smile.obj", bundle.morphTargetPaths.get("smile"));
        assertEquals("/models/Robot_frown.obj", bundle.morphTargetPaths.get("frown"));
    }

    @Test
    public void v11JsonsWithoutMorphSectionLoadCleanly() {
        Aero_AnimationBundle bundle = Aero_AnimationLoader.loadFromString(
            "{" + VER_11 + "\"animations\":{}}");
        assertTrue(bundle.morphTargetPaths.isEmpty());
    }

    @Test
    public void unknownFormatVersionStillRejected() {
        try {
            Aero_AnimationLoader.loadFromString(
                "{\"format_version\":\"2.0\",\"animations\":{}}");
            fail("expected unsupported format_version rejection");
        } catch (RuntimeException ex) {
            assertTrue(ex.getMessage().contains("unsupported format_version"));
            // New error message should now list both 1.0 and 1.1 as supported.
            assertTrue("error must list 1.1: " + ex.getMessage(),
                ex.getMessage().contains("1.1"));
            assertTrue("error must list 1.0 (backward compat): " + ex.getMessage(),
                ex.getMessage().contains("1.0"));
        }
    }

    // ----- helpers -----

    private static float[][][] emptyGroups() {
        return new float[][][]{
            new float[0][], new float[0][], new float[0][], new float[0][]
        };
    }

    private static float[] singleTriRow() {
        return new float[]{
            0f, 0f, 0f, 0f, 0f,  // v0: x, y, z, u, v
            1f, 0f, 0f, 1f, 0f,  // v1
            0f, 1f, 0f, 0f, 1f   // v2
        };
    }

    private static float[][][] singleTri(float ox, float oy, float oz) {
        float[] tri = new float[]{
            0f + ox, 0f + oy, 0f + oz, 0f, 0f,
            1f + ox, 0f + oy, 0f + oz, 1f, 0f,
            0f + ox, 1f + oy, 0f + oz, 0f, 1f
        };
        return new float[][][]{
            new float[][]{ tri }, new float[0][], new float[0][], new float[0][]
        };
    }
}
