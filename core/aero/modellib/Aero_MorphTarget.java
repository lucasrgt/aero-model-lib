package aero.modellib;

/**
 * One morph variant of a mesh: vertex-position deltas relative to the
 * base mesh, structured to match the base's triangle layout exactly so
 * the renderer's emit loop can blend
 *   {@code finalPos = base + sum(weight_i × delta_i)}
 * per vertex without any topology lookup.
 *
 * <p>Topology must match the base mesh: same triangle count per
 * brightness group, same vertex order within each triangle. Mismatch
 * is detected at construction and surfaces as an
 * {@link IllegalArgumentException} with the offending counts.
 *
 * <p>Deltas are stored as {@code float[9]} per triangle (3 vertices × 3
 * coords). UV is intentionally absent — UV animation flows through the
 * separate UV channels in {@link Aero_AnimationClip} and would
 * double-count if mixed in here.
 */
public final class Aero_MorphTarget {

    public final String name;
    /** {@code deltas[group][triIdx]} = float[9] = (Δx, Δy, Δz) × 3 vertices. */
    public final float[][][] deltas;

    /** Total non-zero delta magnitude across all vertices, for fast-path skipping. */
    public final float totalMagnitude;

    public Aero_MorphTarget(String name, float[][][] deltas, float totalMagnitude) {
        if (name == null || name.length() == 0) {
            throw new IllegalArgumentException("morph target name must not be empty");
        }
        if (deltas == null || deltas.length != 4) {
            throw new IllegalArgumentException("morph '" + name
                + "': deltas must have 4 brightness groups, got "
                + (deltas == null ? "null" : Integer.toString(deltas.length)));
        }
        this.name = name;
        this.deltas = deltas;
        this.totalMagnitude = totalMagnitude;
    }

    /**
     * Loads a variant OBJ and computes deltas against the base mesh. The
     * variant's name is used as the morph key (callers reference it via
     * {@link Aero_MorphState#set}).
     *
     * <p>The variant must have identical topology to the base — same
     * triangle count per brightness group, same vertex order. If the OBJ
     * was exported from Blender shape keys with consistent topology this
     * is automatic.
     */
    public static Aero_MorphTarget loadVariant(String name,
                                               Aero_MeshModel baseModel,
                                               String variantResourcePath) {
        Aero_MeshModel variant = Aero_ObjLoader.load(variantResourcePath);
        return fromTargetMesh(name, baseModel.groups, variant.groups);
    }

    /**
     * Resolves every entry in the bundle's {@code morph_targets} map and
     * attaches the resulting variants to the base model. After this, the
     * mesh is ready to render with morph blending — driven by an
     * {@link Aero_MorphState} on the consumer's tile/entity.
     */
    public static void attachAllFromBundle(Aero_MeshModel baseModel,
                                           Aero_AnimationBundle bundle) {
        if (baseModel == null) throw new IllegalArgumentException("baseModel must not be null");
        if (bundle == null) throw new IllegalArgumentException("bundle must not be null");
        java.util.Iterator it = bundle.morphTargetPaths.entrySet().iterator();
        while (it.hasNext()) {
            java.util.Map.Entry e = (java.util.Map.Entry) it.next();
            String name = (String) e.getKey();
            String path = (String) e.getValue();
            baseModel.attachMorphTarget(loadVariant(name, baseModel, path));
        }
    }

    /**
     * Builds a morph target from a target mesh whose topology must match
     * the base. Computes deltas at construction and discards the target
     * geometry so only the deltas remain in memory.
     */
    public static Aero_MorphTarget fromTargetMesh(String name,
                                                  float[][][] baseGroups,
                                                  float[][][] targetGroups) {
        if (baseGroups == null || targetGroups == null) {
            throw new IllegalArgumentException("morph '" + name + "': base/target must not be null");
        }
        if (baseGroups.length != 4 || targetGroups.length != 4) {
            throw new IllegalArgumentException("morph '" + name
                + "': both meshes must have 4 brightness groups");
        }
        float[][][] deltas = new float[4][][];
        float totalMag = 0f;
        for (int g = 0; g < 4; g++) {
            float[][] base = baseGroups[g];
            float[][] target = targetGroups[g];
            if (base.length != target.length) {
                throw new IllegalArgumentException("morph '" + name
                    + "': group " + g + " triangle count mismatch (base=" + base.length
                    + ", target=" + target.length + ")");
            }
            float[][] groupDeltas = new float[base.length][];
            for (int i = 0; i < base.length; i++) {
                float[] b = base[i];
                float[] t = target[i];
                float[] d = new float[9];
                for (int v = 0; v < 3; v++) {
                    int srcBase = v * 5;
                    int dstBase = v * 3;
                    float dx = t[srcBase    ] - b[srcBase    ];
                    float dy = t[srcBase + 1] - b[srcBase + 1];
                    float dz = t[srcBase + 2] - b[srcBase + 2];
                    d[dstBase    ] = dx;
                    d[dstBase + 1] = dy;
                    d[dstBase + 2] = dz;
                    totalMag += Math.abs(dx) + Math.abs(dy) + Math.abs(dz);
                }
                groupDeltas[i] = d;
            }
            deltas[g] = groupDeltas;
        }
        return new Aero_MorphTarget(name, deltas, totalMag);
    }
}
