package aero.modellib;

/**
 * AeroModel API by lucasrgt - aerocoding.dev
 * Ultra-lightweight 3D model container for Minecraft Beta 1.7.3.
 *
 * <p>Instances are effectively immutable. The {@code elements} and
 * {@code quadsByFace} arrays are exposed for renderer hot paths — treat
 * them as read-only, the lib doesn't deep-copy on construction (would
 * tank load-time perf for no win, since the renderers iterate them every
 * frame and the lib never mutates them after the constructor).
 */
public class Aero_JsonModel {

    public final String name;
    /** Raw element rows from the loader. Read-only — renderers iterate this every frame. */
    public final float[][] elements;
    public final float textureSize;
    public final float scale;
    public final float invTextureSize;
    public final float invScale;

    public static final int FACE_DOWN  = 0;
    public static final int FACE_UP    = 1;
    public static final int FACE_NORTH = 2;
    public static final int FACE_SOUTH = 3;
    public static final int FACE_WEST  = 4;
    public static final int FACE_EAST  = 5;
    public static final int FACE_COUNT = 6;

    /**
     * Pre-baked render quads grouped by face direction.
     *
     * {@code quadsByFace[face][i]} = 4 vertices packed as
     * {@code {x,y,z,u,v, x,y,z,u,v, x,y,z,u,v, x,y,z,u,v}}.
     *
     * Face order: FACE_DOWN, FACE_UP, FACE_NORTH, FACE_SOUTH, FACE_WEST, FACE_EAST.
     * Read-only — renderers iterate this every frame.
     */
    public final float[][][] quadsByFace;

    private float[] cachedBounds;

    /**
     * @param name - Model identifier
     * @param elements - Array of parts [x1, y1, z1, x2, y2, z2, u1_down, v1_down, u2_down, v2_down, ...]
     * @param textureSize - Texture size (e.g. 128.0f)
     * @param scale - Model scale (e.g. 16.0f for 1 block = 16 units)
     */
    public Aero_JsonModel(String name, float[][] elements, float textureSize, float scale) {
        if (textureSize == 0f) throw new IllegalArgumentException("textureSize must be non-zero");
        if (scale == 0f) throw new IllegalArgumentException("scale must be non-zero");
        this.name = name;
        this.elements = elements;
        this.textureSize = textureSize;
        this.scale = scale;
        this.invTextureSize = 1f / textureSize;
        this.invScale = 1f / scale;
        this.quadsByFace = buildQuadsByFace(elements, invScale, invTextureSize);
    }

    public Aero_JsonModel(String name, float[][] elements) {
        this(name, elements, 128.0f, 16.0f);
    }

    /**
     * Returns the model's axis-aligned bounding box in block units, computed
     * once and cached. Used by Aero_InventoryRenderer to center and scale the
     * model into a slot.
     *
     * @return float[6] = {minX, minY, minZ, maxX, maxY, maxZ}
     */
    public float[] getBounds() {
        float[] cached = cachedBounds;
        if (cached != null) return cached;

        float minX = Float.POSITIVE_INFINITY, minY = Float.POSITIVE_INFINITY, minZ = Float.POSITIVE_INFINITY;
        float maxX = Float.NEGATIVE_INFINITY, maxY = Float.NEGATIVE_INFINITY, maxZ = Float.NEGATIVE_INFINITY;
        final float invScale = this.invScale;

        for (int i = 0; i < elements.length; i++) {
            float[] p = elements[i];
            float x0 = p[0] * invScale, y0 = p[1] * invScale, z0 = p[2] * invScale;
            float x1 = p[3] * invScale, y1 = p[4] * invScale, z1 = p[5] * invScale;
            if (x0 < minX) minX = x0;
            if (y0 < minY) minY = y0;
            if (z0 < minZ) minZ = z0;
            if (x1 > maxX) maxX = x1;
            if (y1 > maxY) maxY = y1;
            if (z1 > maxZ) maxZ = z1;
        }

        if (minX == Float.POSITIVE_INFINITY) {
            minX = minY = minZ = 0f; maxX = maxY = maxZ = 1f;
        }
        cached = new float[]{minX, minY, minZ, maxX, maxY, maxZ};
        cachedBounds = cached;
        return cached;
    }

    private static float[][][] buildQuadsByFace(float[][] elements, float invScale, float invTs) {
        int[] counts = new int[FACE_COUNT];
        for (int i = 0; i < elements.length; i++) {
            float[] p = elements[i];
            for (int face = 0; face < FACE_COUNT; face++) {
                if (p[6 + face * 4] != -1f) counts[face]++;
            }
        }

        float[][][] quads = new float[FACE_COUNT][][];
        for (int face = 0; face < FACE_COUNT; face++) {
            quads[face] = new float[counts[face]][];
        }

        int[] write = new int[FACE_COUNT];
        for (int i = 0; i < elements.length; i++) {
            float[] p = elements[i];
            float minX = p[0] * invScale; float minY = p[1] * invScale; float minZ = p[2] * invScale;
            float maxX = p[3] * invScale; float maxY = p[4] * invScale; float maxZ = p[5] * invScale;

            for (int face = 0; face < FACE_COUNT; face++) {
                int base = 6 + face * 4;
                if (p[base] == -1f) continue;

                float u1 = p[base] * invTs, v1 = p[base + 1] * invTs;
                float u2 = p[base + 2] * invTs, v2 = p[base + 3] * invTs;
                quads[face][write[face]++] = buildQuad(face, minX, minY, minZ, maxX, maxY, maxZ, u1, v1, u2, v2);
            }
        }

        return quads;
    }

    private static float[] buildQuad(int face, float minX, float minY, float minZ,
                                     float maxX, float maxY, float maxZ,
                                     float u1, float v1, float u2, float v2) {
        switch (face) {
            case FACE_DOWN:
                return new float[]{
                    minX, minY, maxZ, u1, v2,
                    minX, minY, minZ, u1, v1,
                    maxX, minY, minZ, u2, v1,
                    maxX, minY, maxZ, u2, v2
                };
            case FACE_UP:
                return new float[]{
                    maxX, maxY, maxZ, u2, v2,
                    maxX, maxY, minZ, u2, v1,
                    minX, maxY, minZ, u1, v1,
                    minX, maxY, maxZ, u1, v2
                };
            case FACE_NORTH:
                return new float[]{
                    minX, maxY, minZ, u2, v1,
                    maxX, maxY, minZ, u1, v1,
                    maxX, minY, minZ, u1, v2,
                    minX, minY, minZ, u2, v2
                };
            case FACE_SOUTH:
                return new float[]{
                    minX, maxY, maxZ, u1, v1,
                    minX, minY, maxZ, u1, v2,
                    maxX, minY, maxZ, u2, v2,
                    maxX, maxY, maxZ, u2, v1
                };
            case FACE_WEST:
                return new float[]{
                    minX, maxY, maxZ, u2, v1,
                    minX, maxY, minZ, u1, v1,
                    minX, minY, minZ, u1, v2,
                    minX, minY, maxZ, u2, v2
                };
            default: // EAST
                return new float[]{
                    maxX, minY, maxZ, u1, v2,
                    maxX, minY, minZ, u2, v2,
                    maxX, maxY, minZ, u2, v1,
                    maxX, maxY, maxZ, u1, v1
                };
        }
    }
}
