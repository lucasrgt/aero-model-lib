package aero.modellib.test;
import aero.modellib.Aero_MeshRenderer;
import aero.modellib.Aero_RenderDistance;
import aero.modellib.Aero_TextureBinder;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.client.render.block.entity.BlockEntityRenderer;

import aero.modellib.model.Aero_MeshBlendMode;
import aero.modellib.model.Aero_MeshModel;
import aero.modellib.model.Aero_ObjLoader;
import aero.modellib.render.Aero_RenderLod;
import aero.modellib.render.Aero_RenderOptions;
import aero.modellib.skeletal.Aero_BoneRenderPose;
import aero.modellib.skeletal.Aero_ProceduralPose;

/**
 * Renderer showcasing two features composed in one call:
 *
 * <ul>
 *   <li>{@code Aero_RenderOptions.additive(0.85f)} — additive GL blending
 *       so the crystal mesh adds onto whatever's behind it (glow halo).</li>
 *   <li>{@code Aero_ProceduralPose} — a continuous Y-spin layered on top of
 *       the keyframed {@code all_axes} clip; the keyframed motion still
 *       plays, but the procedural delta makes the crystal turn faster and
 *       in a different axis than the clip alone would do, proving the two
 *       compose without conflict.</li>
 * </ul>
 */
public class PlasmaCrystalBlockEntityRenderer extends BlockEntityRenderer {

    public static final Aero_MeshModel MODEL = Aero_ObjLoader.load("/models/Crystal.obj");

    private static final Aero_RenderOptions GLOW = Aero_RenderOptions.builder()
        .tint(0.6f, 0.85f, 1f)            // cool plasma blue
        .alpha(0.85f)
        .blend(aero.modellib.model.Aero_MeshBlendMode.ADDITIVE)
        .depthTest(true)
        .build();

    @Override
    public void render(BlockEntity blockEntity, double x, double y, double z, float partialTick) {
        final PlasmaCrystalBlockEntity be = (PlasmaCrystalBlockEntity) blockEntity;
        Aero_RenderLod lod = Aero_RenderDistance.lodRelative(
            x, y, z, 2d, AeroTestMod.demoAnimatedLodDistance());
        if (!lod.shouldRender()) return;

        Aero_TextureBinder.bind("/models/aerotest_crystal.png");
        float brightness = AeroLight.brightnessAbove(be.world, be.x, be.y, be.z);

        if (lod.shouldAnimate()) {
            // Procedural Y-spin driven by wall-clock time. Layered on TOP of
            // the keyframed all_axes clip — both contributions compose into
            // the final pose. Wall-clock vs game-tick gives a constant
            // visible spin even when the game is paused (good for a
            // showcase; production code would use a tile tick counter).
            final float spinDeg = (System.currentTimeMillis() / 4L) % 360L;
            Aero_ProceduralPose proceduralSpin = new Aero_ProceduralPose() {
                @Override
                public void apply(String boneName, Aero_BoneRenderPose pose) {
                    if ("crystal".equals(boneName)) pose.rotY += spinDeg;
                }
            };

            Aero_MeshRenderer.renderAnimated(MODEL,
                PlasmaCrystalBlockEntity.BUNDLE,
                PlasmaCrystalBlockEntity.ANIM_DEF,
                be.animState,
                x, y, z, brightness, partialTick,
                GLOW,
                proceduralSpin);
        } else {
            Aero_MeshRenderer.renderModelAtRest(MODEL, x, y, z, 0f, brightness, GLOW);
        }
    }
}
