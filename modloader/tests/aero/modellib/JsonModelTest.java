package aero.modellib;

import org.junit.Test;

import static org.junit.Assert.*;

public class JsonModelTest {

    private static final float DELTA = 0.0001f;

    @Test
    public void preBakesVisibleFacesIntoDirectionBuckets() {
        float[] cube = emptyCube();
        cube[0] = 0f; cube[1] = 0f; cube[2] = 0f;
        cube[3] = 16f; cube[4] = 16f; cube[5] = 16f;
        setFace(cube, Aero_JsonModel.FACE_DOWN, 0f, 0f, 16f, 16f);
        setFace(cube, Aero_JsonModel.FACE_EAST, 4f, 8f, 12f, 16f);

        Aero_JsonModel model = new Aero_JsonModel("cube", new float[][]{cube}, 16f, 16f);

        assertEquals(1, model.quadsByFace[Aero_JsonModel.FACE_DOWN].length);
        assertEquals(0, model.quadsByFace[Aero_JsonModel.FACE_UP].length);
        assertEquals(1, model.quadsByFace[Aero_JsonModel.FACE_EAST].length);

        float[] down = model.quadsByFace[Aero_JsonModel.FACE_DOWN][0];
        assertEquals(0f, down[0], DELTA);
        assertEquals(0f, down[1], DELTA);
        assertEquals(1f, down[2], DELTA);
        assertEquals(0f, down[3], DELTA);
        assertEquals(1f, down[4], DELTA);

        float[] east = model.quadsByFace[Aero_JsonModel.FACE_EAST][0];
        assertEquals(1f, east[0], DELTA);
        assertEquals(0f, east[1], DELTA);
        assertEquals(1f, east[2], DELTA);
        assertEquals(0.25f, east[3], DELTA);
        assertEquals(1f, east[4], DELTA);
    }

    @Test
    public void boundsAreComputedOnceInBlockUnits() {
        float[] cube = emptyCube();
        cube[0] = -8f; cube[1] = 0f; cube[2] = 4f;
        cube[3] = 24f; cube[4] = 32f; cube[5] = 20f;

        Aero_JsonModel model = new Aero_JsonModel("bounds", new float[][]{cube}, 16f, 16f);
        float[] first = model.getBounds();
        float[] second = model.getBounds();

        assertSame(first, second);
        assertArrayEquals(new float[]{-0.5f, 0f, 0.25f, 1.5f, 2f, 1.25f}, first, DELTA);
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsZeroScale() {
        new Aero_JsonModel("bad", new float[][]{emptyCube()}, 16f, 0f);
    }

    private static float[] emptyCube() {
        float[] p = new float[30];
        for (int i = 6; i < p.length; i++) p[i] = -1f;
        return p;
    }

    private static void setFace(float[] p, int face, float u1, float v1, float u2, float v2) {
        int base = 6 + face * 4;
        p[base] = u1;
        p[base + 1] = v1;
        p[base + 2] = u2;
        p[base + 3] = v2;
    }
}
