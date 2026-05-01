package aero.modellib.test;

import aero.modellib.Aero_AnimatedBatcher;
import aero.modellib.Aero_BECellRenderer;
import aero.modellib.Aero_MeshModel;
import aero.modellib.Aero_ObjLoader;
import aero.modellib.Aero_RenderDistance;
import aero.modellib.Aero_RenderLod;
import aero.modellib.Aero_RenderOptions;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.client.render.block.entity.BlockEntityRenderer;

/**
 * Renderer for {@link AnimatedMegaModelBlockEntity} — calls
 * {@link Aero_MeshRenderer#renderAnimated} so keyframes from the
 * .anim.json bundle drive bone transforms each frame.
 */
public class AnimatedMegaModelBlockEntityRenderer extends BlockEntityRenderer {

    public static final Aero_MeshModel MODEL = Aero_ObjLoader.load("/models/MegaCrusher.obj");
    private static final String TEXTURE = "/models/retronism_megacrusher.png";

    @Override
    public void render(BlockEntity blockEntity, double x, double y, double z, float partialTick) {
        AnimatedMegaModelBlockEntity be = (AnimatedMegaModelBlockEntity) blockEntity;
        Aero_RenderLod lod = Aero_RenderDistance.lodRelative(
            x, y, z, 4d, AeroTestMod.demoAnimatedLodDistance());
        if (!lod.shouldRender()) return;

        // method_1782 = float-brightness API (Yarn intermediary, the
        // float-returning equivalent of vanilla getLightBrightness).
        // Drives day/night + torch lighting on the mesh.
        float brightness = AeroLight.brightnessAbove(be.world, be.x, be.y, be.z);
        if (lod.shouldAnimate()) {
            Aero_AnimatedBatcher.queueAnimated(MODEL, TEXTURE, be.animState,
                x, y, z, brightness, partialTick,
                Aero_RenderOptions.DEFAULT);
        } else {
            Aero_BECellRenderer.queueAtRest(MODEL, TEXTURE, be,
                x, y, z, 0f, brightness, Aero_RenderOptions.DEFAULT);
        }
    }
}
