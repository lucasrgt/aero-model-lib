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

    public Aero_EntityModelTransform(float offsetX, float offsetY, float offsetZ,
                                      float scale, float yawOffset) {
        requireFinite("offsetX", offsetX);
        requireFinite("offsetY", offsetY);
        requireFinite("offsetZ", offsetZ);
        requireFinite("scale", scale);
        requireFinite("yawOffset", yawOffset);
        if (scale == 0f) throw new IllegalArgumentException("scale must be non-zero");

        this.offsetX = offsetX;
        this.offsetY = offsetY;
        this.offsetZ = offsetZ;
        this.scale = scale;
        this.yawOffset = yawOffset;
    }

    public static Aero_EntityModelTransform of(float offsetX, float offsetY, float offsetZ,
                                                float scale, float yawOffset) {
        return new Aero_EntityModelTransform(offsetX, offsetY, offsetZ, scale, yawOffset);
    }

    public Aero_EntityModelTransform withOffset(float offsetX, float offsetY, float offsetZ) {
        return new Aero_EntityModelTransform(offsetX, offsetY, offsetZ, scale, yawOffset);
    }

    public Aero_EntityModelTransform withScale(float scale) {
        return new Aero_EntityModelTransform(offsetX, offsetY, offsetZ, scale, yawOffset);
    }

    public Aero_EntityModelTransform withYawOffset(float yawOffset) {
        return new Aero_EntityModelTransform(offsetX, offsetY, offsetZ, scale, yawOffset);
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
