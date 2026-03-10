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
}
