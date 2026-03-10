package aero.modellib;

import java.util.Map;

/**
 * AeroMesh Model — container for triangulated OBJ models.
 *
 * Triangles are pre-classified into 4 brightness groups at parse time
 * (same directional system as Aero_JsonModelRenderer):
 *
 *   GROUP_TOP    (dominant ny, positive) → factor 1.0
 *   GROUP_BOTTOM (dominant ny, negative) → factor 0.5
 *   GROUP_NS     (dominant nz)           → factor 0.8
 *   GROUP_EW     (dominant nx)           → factor 0.6
 *
 * Classification happens during parsing (Aero_ObjLoader), not per frame.
 * This reduces setColorOpaque_F calls from O(N triangles) to 4.
 *
 * Each triangle is float[15]:
 *   [0-4]   vertex 0: x, y, z, u, v
 *   [5-9]   vertex 1: x, y, z, u, v
 *   [10-14] vertex 2: x, y, z, u, v
 *
 * Named groups (OBJ "o" / "g" directives):
 *   Triangles belonging to a named OBJ object/group are stored separately
 *   in namedGroups and excluded from the main groups array. This allows
 *   animated parts (fan, piston, gear) to be rendered independently with
 *   their own GL transforms, while the static geometry renders normally.
 *
 *   Example OBJ:
 *     o base      ← static, goes into groups[]
 *     ...faces...
 *     o fan       ← animated, goes into namedGroups["fan"]
 *     ...faces...
 *
 * The face normal is not stored — it was only used to classify the group.
 */
public class Aero_MeshModel {

    public static final int GROUP_TOP    = 0;
    public static final int GROUP_BOTTOM = 1;
    public static final int GROUP_NS     = 2;
    public static final int GROUP_EW     = 3;

    static final float[] BRIGHTNESS_FACTORS = {1.0f, 0.5f, 0.8f, 0.6f};

    public final String name;
    public final float scale;

    /**
     * Static triangles per brightness group (excludes named groups).
     * groups[GROUP_TOP][i] = float[15] for the i-th top-facing triangle.
     */
    public final float[][][] groups;

    /**
     * Named group triangles: Map<String, float[][][]>.
     * Each entry has the same 4-brightness-group structure as groups[].
     * Empty map if the OBJ has no named objects/groups.
     */
    public final Map namedGroups;

    public Aero_MeshModel(String name, float[][][] groups, float scale, Map namedGroups) {
        this.name = name;
        this.groups = groups;
        this.scale = scale;
        this.namedGroups = namedGroups;
    }

    /** Convenience constructor: scale=1, empty named groups. */
    public Aero_MeshModel(String name, float[][][] groups) {
        this(name, groups, 1.0f, new java.util.HashMap());
    }

    /** Total triangle count in static geometry (excludes named groups). */
    public int triangleCount() {
        int n = 0;
        for (int g = 0; g < 4; g++) n += groups[g].length;
        return n;
    }

    /** Total triangle count in a named group, or 0 if not found. */
    public int triangleCountForGroup(String groupName) {
        float[][][] ng = (float[][][]) namedGroups.get(groupName);
        if (ng == null) return 0;
        int n = 0;
        for (int g = 0; g < 4; g++) n += ng[g].length;
        return n;
    }
}
