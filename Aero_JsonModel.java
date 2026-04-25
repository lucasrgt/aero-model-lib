package aero.modellib;

/**
 * AeroModel API by lucasrgt - aerocoding.dev
 * Ultra-lightweight 3D model container for Minecraft Beta 1.7.3.
 */
public class Aero_JsonModel {

    public final String name;
    public final float[][] elements;
    public final float textureSize;
    public final float scale;

    private float[] cachedBounds;

    /**
     * @param name - Model identifier
     * @param elements - Array of parts [x1, y1, z1, x2, y2, z2, u1_down, v1_down, u2_down, v2_down, ...]
     * @param textureSize - Texture size (e.g. 128.0f)
     * @param scale - Model scale (e.g. 16.0f for 1 block = 16 units)
     */
    public Aero_JsonModel(String name, float[][] elements, float textureSize, float scale) {
        this.name = name;
        this.elements = elements;
        this.textureSize = textureSize;
        this.scale = scale;
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
        final float invScale = 1f / scale;

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
}
