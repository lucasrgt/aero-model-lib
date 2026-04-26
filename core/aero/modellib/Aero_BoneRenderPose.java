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

    /**
     * Per-bone UV offset added to each vertex's u/v before drawing. Default
     * is (0, 0) — no offset. Renderer applies as
     * {@code u' = u * uScale + uOffset; v' = v * vScale + vOffset}.
     */
    public float uOffset;
    public float vOffset;

    /**
     * Per-bone UV scale multiplied into each vertex's u/v before the offset
     * is added. Default is (1, 1) — identity. Combined with uOffset/vOffset
     * lets clips animate scrolling textures (offset over time), atlas frame
     * picking (step easing on offset), and pulsing zoom (scale over time).
     */
    public float uScale;
    public float vScale;

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
        uOffset = 0f;
        vOffset = 0f;
        uScale = 1f;
        vScale = 1f;
    }

    void setPivot(float[] pivot) {
        pivotX = pivot[0];
        pivotY = pivot[1];
        pivotZ = pivot[2];
    }

    /**
     * Returns true when the UV transform is identity (default), so the
     * renderer can take the fast path that emits raw u/v with no math.
     * Inactive bones pay zero cost.
     */
    public boolean uvIsIdentity() {
        return uOffset == 0f && vOffset == 0f && uScale == 1f && vScale == 1f;
    }
}
