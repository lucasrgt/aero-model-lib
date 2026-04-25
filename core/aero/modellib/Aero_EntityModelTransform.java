package aero.modellib;

/**
 * Immutable entity render transform.
 *
 * Entity renderers rotate around the entity origin, while block renderers
 * rotate around the block center. Keeping the conversion here makes the
 * platform render helpers small and gives tests a pure-Java target.
 */
public final class Aero_EntityModelTransform {

    public static final Aero_EntityModelTransform DEFAULT =
        new Aero_EntityModelTransform(0f, 0f, 0f, 1f, 0f);

    public final float offsetX;
    public final float offsetY;
    public final float offsetZ;
    public final float scale;
    public final float yawOffset;
    public final float cullingRadius;
    public final float maxRenderDistance;

    public Aero_EntityModelTransform(float offsetX, float offsetY, float offsetZ,
                                      float scale, float yawOffset) {
        this(offsetX, offsetY, offsetZ, scale, yawOffset, 0f,
            (float) Aero_RenderDistanceCulling.DEFAULT_SPECIAL_RENDER_RADIUS);
    }

    private Aero_EntityModelTransform(float offsetX, float offsetY, float offsetZ,
                                      float scale, float yawOffset,
                                      float cullingRadius,
                                      float maxRenderDistance) {
        requireFinite("offsetX", offsetX);
        requireFinite("offsetY", offsetY);
        requireFinite("offsetZ", offsetZ);
        requireFinite("scale", scale);
        requireFinite("yawOffset", yawOffset);
        requireFinite("cullingRadius", cullingRadius);
        requireFinite("maxRenderDistance", maxRenderDistance);
        if (scale == 0f) throw new IllegalArgumentException("scale must be non-zero");
        if (cullingRadius < 0f) throw new IllegalArgumentException("cullingRadius must be >= 0");
        if (maxRenderDistance <= 0f) throw new IllegalArgumentException("maxRenderDistance must be > 0");

        this.offsetX = offsetX;
        this.offsetY = offsetY;
        this.offsetZ = offsetZ;
        this.scale = scale;
        this.yawOffset = yawOffset;
        this.cullingRadius = cullingRadius;
        this.maxRenderDistance = maxRenderDistance;
    }

    public static Aero_EntityModelTransform of(float offsetX, float offsetY, float offsetZ,
                                                float scale, float yawOffset) {
        return new Aero_EntityModelTransform(offsetX, offsetY, offsetZ, scale, yawOffset);
    }

    public Aero_EntityModelTransform withOffset(float offsetX, float offsetY, float offsetZ) {
        return new Aero_EntityModelTransform(offsetX, offsetY, offsetZ, scale, yawOffset,
            cullingRadius, maxRenderDistance);
    }

    public Aero_EntityModelTransform withScale(float scale) {
        return new Aero_EntityModelTransform(offsetX, offsetY, offsetZ, scale, yawOffset,
            cullingRadius, maxRenderDistance);
    }

    public Aero_EntityModelTransform withYawOffset(float yawOffset) {
        return new Aero_EntityModelTransform(offsetX, offsetY, offsetZ, scale, yawOffset,
            cullingRadius, maxRenderDistance);
    }

    public Aero_EntityModelTransform withCullingRadius(float cullingRadius) {
        return new Aero_EntityModelTransform(offsetX, offsetY, offsetZ, scale, yawOffset,
            cullingRadius, maxRenderDistance);
    }

    public Aero_EntityModelTransform withMaxRenderDistance(float maxRenderDistance) {
        return new Aero_EntityModelTransform(offsetX, offsetY, offsetZ, scale, yawOffset,
            cullingRadius, maxRenderDistance);
    }

    /**
     * Converts Minecraft's entity yaw into the model-space yaw used by the
     * OpenGL helpers. The 180 degree flip matches vanilla entity renderers.
     */
    public float modelYaw(float entityYaw) {
        return 180f - entityYaw + yawOffset;
    }

    private static void requireFinite(String name, float value) {
        if (Float.isNaN(value) || Float.isInfinite(value)) {
            throw new IllegalArgumentException(name + " must be finite");
        }
    }
}
