package aero.modellib;

import org.junit.Test;

import java.io.BufferedReader;
import java.io.StringReader;

import aero.modellib.model.Aero_MeshModel;
import aero.modellib.model.Aero_ObjLoader;

import static org.junit.Assert.assertEquals;

public class ObjHiddenFaceRemovalTest {

    @Test
    public void optInPassRemovesCoincidentOppositeTriangles() throws Exception {
        String obj =
            "v 0 0 0\n" +
            "v 1 0 0\n" +
            "v 0 1 0\n" +
            "v 0 0 1\n" +
            "f 1 2 3\n" +
            "f 1 3 2\n" +
            "f 1 2 4\n";

        Aero_MeshModel normal = Aero_ObjLoader.parseObjForTest(
            new BufferedReader(new StringReader(obj)), "normal", false);
        Aero_MeshModel culled = Aero_ObjLoader.parseObjForTest(
            new BufferedReader(new StringReader(obj)), "culled", true);

        assertEquals(3, normal.triangleCount());
        assertEquals(1, culled.triangleCount());
    }

    @Test
    public void sameDirectionCoincidentTrianglesArePreserved() throws Exception {
        String obj =
            "v 0 0 0\n" +
            "v 1 0 0\n" +
            "v 0 1 0\n" +
            "f 1 2 3\n" +
            "f 1 2 3\n";

        Aero_MeshModel model = Aero_ObjLoader.parseObjForTest(
            new BufferedReader(new StringReader(obj)), "decal", true);

        assertEquals(2, model.triangleCount());
    }
}
