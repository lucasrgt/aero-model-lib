package aero.modellib;

/**
 * StationAPI-side opt-in for BlockEntities that can be drawn by an at-rest
 * cell page before vanilla dispatches their individual renderer.
 *
 * <p>The interface intentionally exposes only stable, at-rest render state.
 * Animated or procedural visuals should keep returning ANIMATED from LOD and
 * flow through the normal renderer/batcher path.
 */
public interface Aero_CellPageRenderableBE extends Aero_CellRenderableBE {

    Aero_MeshModel aeroCellModel();

    String aeroCellTexturePath();

    float aeroCellBrightness();

    double aeroCellVisualRadius();

    double aeroCellAnimatedDistance();

    default double aeroCellMaxRenderDistance() {
        return Aero_RenderDistanceCulling.DEFAULT_SPECIAL_RENDER_RADIUS;
    }

    default float aeroCellRotation() {
        return 0f;
    }

    default Aero_RenderOptions aeroCellRenderOptions() {
        return Aero_RenderOptions.DEFAULT;
    }

    /**
     * Final per-BE permission check before the base class suppresses vanilla's
     * individual renderer. The default only pages STATIC LOD; ANIMATED remains
     * a normal renderer call so the existing batcher can sample pose/keyframes.
     */
    default boolean aeroCanSkipIndividualRenderer(Aero_RenderLod lod) {
        return lod == Aero_RenderLod.STATIC;
    }
}
