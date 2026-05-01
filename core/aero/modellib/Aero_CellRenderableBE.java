package aero.modellib;

/**
 * Optional contract for BlockEntities that can be tracked by the StationAPI
 * cell index. The first index pass only records spatial/state buckets; later
 * cell-page rendering can use the same hooks to decide what is safe to draw
 * through a shared at-rest page.
 */
public interface Aero_CellRenderableBE {

    /**
     * Hash of static render state that affects the at-rest page: model,
     * texture, tint, alpha, connected-shape state, etc. Override when the
     * visual output can change without the BE moving.
     */
    default int aeroRenderStateHash() {
        return 0;
    }

    /**
     * Hash of orientation or facing. Kept separate because orientation is
     * commonly used as a cheap render bucket key for cell pages.
     */
    default int aeroOrientationHash() {
        return 0;
    }

    /**
     * @return true when this BE can eventually be represented in an at-rest
     *         cell page. Dynamic morph/procedural-only visuals should return
     *         false and stay on the individual renderer.
     */
    default boolean aeroCanCellPage() {
        return true;
    }

    /**
     * @return true when this BE is actively asking for animation. This is only
     *         a hint for future prioritisation; C0/C1 still own final budget
     *         admission.
     */
    default boolean aeroWantsAnimation() {
        return true;
    }
}
