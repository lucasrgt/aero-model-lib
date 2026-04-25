package aero.modellib.test;

import aero.modellib.Aero_MeshModel;
import aero.modellib.Aero_MeshRenderer;
import aero.modellib.Aero_ObjLoader;
import aero.modellib.Aero_RenderDistance;
import aero.modellib.Aero_RenderLod;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.client.render.block.entity.BlockEntityRenderer;

/**
 * Renderer for {@link AnimatedMegaModelBlockEntity} — calls
 * {@link Aero_MeshRenderer#renderAnimated} so keyframes from the
 * .anim.json bundle drive bone transforms each frame.
 */
public class AnimatedMegaModelBlockEntityRenderer extends BlockEntityRenderer {

    public static final Aero_MeshModel MODEL = Aero_ObjLoader.load("/models/MegaCrusher.obj");

    @Override
    public void render(BlockEntity blockEntity, double x, double y, double z, float partialTick) {
        AnimatedMegaModelBlockEntity be = (AnimatedMegaModelBlockEntity) blockEntity;
        Aero_RenderLod lod = Aero_RenderDistance.lodRelative(
            x, y, z, 4d, AeroTestMod.DEMO_ANIMATED_LOD_DISTANCE_BLOCKS);
        if (!lod.shouldRender()) return;

        bindTexture("/models/retronism_megacrusher.png");
        // method_1782 = float-brightness API (Yarn intermediary, the
        // float-returning equivalent of vanilla getLightBrightness).
        // Drives day/night + torch lighting on the mesh.
        float brightness = AeroLight.brightnessAbove(be.world, be.x, be.y, be.z);
        if (lod.shouldAnimate()) {
            Aero_MeshRenderer.renderAnimated(MODEL, be.animState,
                x, y, z, brightness, partialTick);
        } else {
            Aero_MeshRenderer.renderModelAtRest(MODEL, x, y, z, 0f, brightness);
        }
    }
}
