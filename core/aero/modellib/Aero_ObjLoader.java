package aero.modellib;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * AeroMesh OBJ Loader by lucasrgt - aerocoding.dev
 * Loads OBJ models from the classpath at runtime — no conversion pipeline needed.
 *
 * Usage:
 *   Aero_MeshModel model = Aero_ObjLoader.load("/models/my_machine.obj");
 *
 * Export from Blockbench: File > Export > Export OBJ Model
 * Place only the .obj in src/retronism/assets/models/ (the .mtl is not used).
 *
 * Supported directives:
 *   - v  (vertices), vt (UVs), vn (ignored — normal computed from geometry)
 *   - f  (faces: triangles and quads, fan triangulation)
 *   - o / g (named objects/groups — used to separate animated parts)
 *   - Negative OBJ indices (reference from end of list)
 *   - usemtl, mtllib, s → ignored
 *
 * UV: applies V-flip (1-V) — OBJ uses V=0 at bottom, Minecraft uses V=0 at top.
 *
 * Named groups (o / g directives):
 *   Triangles under a named object/group are stored separately in the model's
 *   namedGroups map and excluded from the main groups array. Unnamed triangles
 *   (before any o/g directive) go into the main groups array as static geometry.
 *
 *   This enables animated parts:
 *     Aero_MeshRenderer.renderModel(MODEL, ...);              // static geometry
 *     Aero_MeshRenderer.renderGroupRotated(MODEL, "fan", ...); // animated fan
 *
 * Triangles are classified into 4 brightness groups at parse time
 * (see Aero_MeshModel.GROUP_*), avoiding per-frame normal computation.
 */
public class Aero_ObjLoader {

    private static final int MAX_CACHE_ENTRIES =
        Integer.getInteger("aero.modellib.cache.maxEntries", 512).intValue();

    private static final Map cache = new LinkedHashMap(16, 0.75f, true) {
        protected boolean removeEldestEntry(Map.Entry eldest) {
            return MAX_CACHE_ENTRIES > 0 && size() > MAX_CACHE_ENTRIES;
        }
    };

    /** Loads and caches an OBJ model from the classpath. */
    public static synchronized Aero_MeshModel load(String resourcePath) {
        return load(resourcePath, resourcePath);
    }

    /** Loads and caches an OBJ model from the classpath with an explicit name. */
    public static synchronized Aero_MeshModel load(String resourcePath, String name) {
        if (cache.containsKey(resourcePath)) {
            return (Aero_MeshModel) cache.get(resourcePath);
        }
        try {
            InputStream is = Aero_ObjLoader.class.getResourceAsStream(resourcePath);
            if (is == null) {
                throw new RuntimeException("AeroObjLoader: resource not found: " + resourcePath);
            }
            Aero_MeshModel model;
            try {
                BufferedReader reader = new BufferedReader(new InputStreamReader(is, "UTF-8"));
                model = parseObj(reader, name);
            } finally {
                is.close();
            }
            cache.put(resourcePath, model);
            return model;
        } catch (Exception e) {
            throw new RuntimeException("AeroObjLoader: failed to load " + resourcePath + ": " + e.getMessage(), e);
        }
    }

    /** Drops all cached OBJ models. Useful for tests and hot-reload tooling. */
    public static synchronized void clearCache() {
        cache.clear();
    }

    static synchronized int cacheSize() {
        return cache.size();
    }

    // -----------------------------------------------------------------------
    // OBJ Parser
    // -----------------------------------------------------------------------

    private static Aero_MeshModel parseObj(BufferedReader reader, String name) throws Exception {
        List verts = new ArrayList();  // float[3]: x, y, z
        List uvs   = new ArrayList();  // float[2]: u, v  (V-flipped)

        // Static geometry (unnamed) — 4 brightness groups
        List[] staticGroups = new List[4];
        for (int g = 0; g < 4; g++) staticGroups[g] = new ArrayList();

        // Named groups: Map<String, List[4]>
        Map namedGroupLists = new LinkedHashMap();
        List[] currentNamedGroup = null; // null = static

        String line;
        while ((line = reader.readLine()) != null) {
            line = line.trim();
            if (line.isEmpty() || line.charAt(0) == '#') continue;

            if (line.startsWith("v ")) {
                String[] p = split(line.substring(2));
                verts.add(new float[]{f(p[0]), f(p[1]), f(p[2])});

            } else if (line.startsWith("vt ")) {
                String[] p = split(line.substring(3));
                // V-flip: OBJ uses V=0 at bottom, Minecraft uses V=0 at top
                uvs.add(new float[]{f(p[0]), 1.0f - f(p[1])});

            } else if (line.startsWith("f ")) {
                List[] target = (currentNamedGroup != null) ? currentNamedGroup : staticGroups;
                parseFace(line.substring(2).trim(), verts, uvs, target);

            } else if (line.startsWith("o ") || line.startsWith("g ")) {
                String groupName = line.substring(2).trim();
                if (!namedGroupLists.containsKey(groupName)) {
                    List[] ng = new List[4];
                    for (int g = 0; g < 4; g++) ng[g] = new ArrayList();
                    namedGroupLists.put(groupName, ng);
                }
                currentNamedGroup = (List[]) namedGroupLists.get(groupName);
            }
            // vn, s, usemtl, mtllib → ignored
        }

        boolean hasStatic = !allEmpty(staticGroups);
        boolean hasNamed  = !namedGroupLists.isEmpty();
        if (!hasStatic && !hasNamed) {
            throw new RuntimeException("AeroObjLoader: no faces found in " + name);
        }

        // Convert static groups
        float[][][] staticArrays = new float[4][][];
        for (int g = 0; g < 4; g++) {
            staticArrays[g] = (float[][]) staticGroups[g].toArray(new float[staticGroups[g].size()][]);
        }

        // Convert named groups
        Map namedGroupArrays = new LinkedHashMap();
        Iterator it = namedGroupLists.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry entry = (Map.Entry) it.next();
            String groupName = (String) entry.getKey();
            List[] ng = (List[]) entry.getValue();
            float[][][] arr = new float[4][][];
            for (int g = 0; g < 4; g++) {
                arr[g] = (float[][]) ng[g].toArray(new float[ng[g].size()][]);
            }
            namedGroupArrays.put(groupName, arr);
        }

        return new Aero_MeshModel(name, staticArrays, 1.0f, namedGroupArrays);
    }

    private static void parseFace(String faceStr, List verts, List uvs, List[] groups) {
        String[] tokens = split(faceStr);
        float[][] poly = new float[tokens.length][];
        for (int i = 0; i < tokens.length; i++) {
            poly[i] = parseFaceVert(tokens[i], verts, uvs);
        }

        // Fan triangulation (works for convex polygons)
        for (int i = 1; i < poly.length - 1; i++) {
            float[] v0 = poly[0], v1 = poly[i], v2 = poly[i + 1];

            // Face normal via cross product
            float ax = v1[0]-v0[0], ay = v1[1]-v0[1], az = v1[2]-v0[2];
            float bx = v2[0]-v0[0], by = v2[1]-v0[1], bz = v2[2]-v0[2];
            float nx = ay*bz - az*by;
            float ny = az*bx - ax*bz;
            float nz = ax*by - ay*bx;
            float len = (float) Math.sqrt(nx*nx + ny*ny + nz*nz);
            if (len > 1e-7f) { nx /= len; ny /= len; nz /= len; }

            int group = brightnessGroup(nx, ny, nz);
            groups[group].add(new float[]{
                v0[0], v0[1], v0[2], v0[3], v0[4],
                v1[0], v1[1], v1[2], v1[3], v1[4],
                v2[0], v2[1], v2[2], v2[3], v2[4]
            });
        }
    }

    /**
     * Classifies the face normal into one of the 4 brightness groups.
     * The dominant axis determines the group; Y-axis sign determines TOP vs BOTTOM.
     */
    static int brightnessGroup(float nx, float ny, float nz) {
        float ax = Math.abs(nx), ay = Math.abs(ny), az = Math.abs(nz);
        if (ay >= ax && ay >= az) return ny > 0 ? Aero_MeshModel.GROUP_TOP : Aero_MeshModel.GROUP_BOTTOM;
        if (az >= ax)             return Aero_MeshModel.GROUP_NS;
        return                           Aero_MeshModel.GROUP_EW;
    }

    /**
     * Parses a face vertex token: "v", "v/vt", "v//vn", "v/vt/vn".
     * Returns float[5]: x, y, z, u, v
     */
    private static float[] parseFaceVert(String token, List verts, List uvs) {
        String[] parts = token.split("/", -1);

        int vi = Integer.parseInt(parts[0].trim());
        int ti = (parts.length > 1 && !parts[1].isEmpty()) ? Integer.parseInt(parts[1].trim()) : 0;

        float[] v  = (float[]) verts.get(vi < 0 ? verts.size() + vi : vi - 1);
        float[] uv = ti != 0
            ? (float[]) uvs.get(ti < 0 ? uvs.size() + ti : ti - 1)
            : new float[]{0.0f, 0.0f};

        return new float[]{v[0], v[1], v[2], uv[0], uv[1]};
    }

    private static boolean allEmpty(List[] groups) {
        for (int g = 0; g < groups.length; g++) if (!groups[g].isEmpty()) return false;
        return true;
    }

    private static String[] split(String s) { return s.trim().split("\\s+"); }
    private static float f(String s)         { return Float.parseFloat(s.trim()); }
}
