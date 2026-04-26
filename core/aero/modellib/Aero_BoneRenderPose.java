package aero.modellib;

/**
 * Mutable per-bone pose passed to {@link Aero_ProceduralPose} hooks so
 * vehicle/turret-style runtime rotations can compose on top of the
 * keyframed pose without a parallel render path.
 *
 * <p>Convention: the lib resets the pose at the start of each bone, sets
 * {@code pivotX/Y/Z} from the bundle, then samples keyframes into the
 * rotation/offset/scale fields. The hook fires AFTER that, with all
 * keyframe values already written, so the consumer just adds deltas:
 *
 * <pre>
 *   if ("turret".equals(boneName))   pose.rotY += tank.turretYaw;
 *   if ("barrel".equals(boneName))   pose.rotX += tank.barrelPitch;
 *   if ("propeller".equals(boneName)) pose.rotX += spinAngle(partialTick);
 * </pre>
 *
 * <p>Pivot is read-only by convention — the lib uses it to center the GL
 * transform around the bone's rest origin. Editing it after the keyframe
 * pass produces unspecified results.
 */
public final class Aero_BoneRenderPose {

    /** Bone pivot in block units. Don't write — the renderer uses these to center transforms. */
    public float pivotX;
    public float pivotY;
    public float pivotZ;

    /** Euler rotation in degrees, applied Z → Y → X. */
    public float rotX;
    public float rotY;
    public float rotZ;

    /** Position offset in block units, applied before rotation around the pivot. */
    public float offsetX;
    public float offsetY;
    public float offsetZ;

    /** Per-axis scale, applied around the pivot (defaults to 1). */
    public float scaleX;
    public float scaleY;
    public float scaleZ;

    void reset() {
        pivotX = 0f;
        pivotY = 0f;
        pivotZ = 0f;
        rotX = 0f;
        rotY = 0f;
        rotZ = 0f;
        offsetX = 0f;
        offsetY = 0f;
        offsetZ = 0f;
        scaleX = 1f;
        scaleY = 1f;
        scaleZ = 1f;
    }

    void setPivot(float[] pivot) {
        pivotX = pivot[0];
        pivotY = pivot[1];
        pivotZ = pivot[2];
    }
}
