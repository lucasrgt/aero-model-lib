package aero.modellib;

/**
 * Coarse render level chosen from camera-relative distance.
 *
 * The intended hot path is:
 *   ANIMATED -> full keyframe sampling
 *   STATIC   -> draw the mesh at rest pose
 *   CULLED   -> draw nothing
 */
public enum Aero_RenderLod {
    CULLED,
    STATIC,
    ANIMATED;

    public boolean shouldRender() {
        return this != CULLED;
    }

    public boolean shouldAnimate() {
        return this == ANIMATED;
    }

    public boolean isStaticOnly() {
        return this == STATIC;
    }
}
