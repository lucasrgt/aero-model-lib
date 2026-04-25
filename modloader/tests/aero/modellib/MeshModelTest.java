package aero.modellib;

import org.junit.Test;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.Assert.*;

public class MeshModelTest {

    private static final float DELTA = 0.0001f;

    @Test
    public void smoothLightMetadataIsCachedAndPrecomputedInBlockUnits() {
        float[][][] groups = emptyGroups();
        groups[Aero_MeshModel.GROUP_TOP] = new float[][]{
            tri(0f, 0f, 0f, 16f, 0f, 0f, 0f, 0f, 16f)
        };

        Aero_MeshModel model = new Aero_MeshModel("mesh", groups, 16f, new HashMap());
        Aero_MeshModel.SmoothLightData first = model.getStaticSmoothLightData();
        Aero_MeshModel.SmoothLightData second = model.getStaticSmoothLightData();

        assertSame(first, second);
        assertTrue(first.hasTriangles);
        assertEquals(0f, first.minX, DELTA);
        assertEquals(1f, first.maxX, DELTA);
        assertEquals(0f, first.minZ, DELTA);
        assertEquals(1f, first.maxZ, DELTA);
        assertEquals(1f / 3f, first.centroidX[Aero_MeshModel.GROUP_TOP][0], DELTA);
        assertEquals(1f / 3f, first.centroidZ[Aero_MeshModel.GROUP_TOP][0], DELTA);
    }

    @Test
    public void boundsIncludeStaticAndNamedGeometry() {
        float[][][] groups = emptyGroups();
        groups[Aero_MeshModel.GROUP_TOP] = new float[][]{
            tri(0f, 0f, 0f, 16f, 0f, 0f, 0f, 16f, 0f)
        };

        Map named = new LinkedHashMap();
        named.put("arm", withTopTri(tri(32f, 0f, 0f, 48f, 0f, 0f, 48f, 16f, 0f)));

        Aero_MeshModel model = new Aero_MeshModel("mesh", groups, 16f, named);

        assertArrayEquals(new float[]{0f, 0f, 0f, 3f, 1f, 0f}, model.getBounds(), DELTA);
        assertEquals(1, model.triangleCount());
        assertEquals(1, model.triangleCountForGroup("arm"));
        assertNotNull(model.getNamedGroup("arm"));
    }

    @Test
    public void boneRefsResolveExplicitParentBeforePrefixFallback() {
        Map named = new LinkedHashMap();
        named.put("blade_child", withTopTri(tri(0f, 0f, 0f, 1f, 0f, 0f, 0f, 1f, 0f)));

        Aero_MeshModel model = new Aero_MeshModel("mesh", emptyGroups(), 1f, named);
        Aero_AnimationClip clip = new Aero_AnimationClip(
            "spin", true, 1f,
            new String[]{"parent"},
            new float[][]{{0f}},
            new float[][][]{{{0f, 90f, 0f}}},
            new float[][]{{0f}},
            new float[][][]{{{0f, 0f, 0f}}}
        );

        Map clips = new HashMap();
        clips.put("spin", clip);
        Map pivots = new HashMap();
        pivots.put("parent", new float[]{1f, 2f, 3f});
        Map childMap = new HashMap();
        childMap.put("blade_child", "parent");

        Aero_MeshModel.BoneRef[] refs = model.boneRefsFor(clip, new Aero_AnimationBundle(clips, pivots, childMap));

        assertEquals(0, refs[0].boneIdx);
        assertArrayEquals(new float[]{1f, 2f, 3f}, refs[0].pivot, DELTA);
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsZeroScale() {
        new Aero_MeshModel("bad", emptyGroups(), 0f, new HashMap());
    }

    private static float[][][] emptyGroups() {
        return new float[][][]{
            new float[0][],
            new float[0][],
            new float[0][],
            new float[0][]
        };
    }

    private static float[][][] withTopTri(float[] tri) {
        float[][][] groups = emptyGroups();
        groups[Aero_MeshModel.GROUP_TOP] = new float[][]{tri};
        return groups;
    }

    private static float[] tri(float x0, float y0, float z0,
                               float x1, float y1, float z1,
                               float x2, float y2, float z2) {
        return new float[]{
            x0, y0, z0, 0f, 0f,
            x1, y1, z1, 0f, 0f,
            x2, y2, z2, 0f, 0f
        };
    }
}
