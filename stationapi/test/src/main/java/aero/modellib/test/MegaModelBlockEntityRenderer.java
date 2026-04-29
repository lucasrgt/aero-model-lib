package aero.modellib.test;

import aero.modellib.Aero_MeshModel;
import aero.modellib.Aero_MeshRenderer;
import aero.modellib.Aero_ObjLoader;
import aero.modellib.Aero_RenderDistance;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.client.render.block.entity.BlockEntityRenderer;

/**
 * Static-mesh renderer for {@link MegaModelBlockEntity}. Uses the at-rest
 * renderer so named groups can hit Aero_MeshRenderer's display-list fast-path.
 */
public class MegaModelBlockEntityRenderer extends BlockEntityRenderer {

    public static final Aero_MeshModel MODEL = Aero_ObjLoader.load("/models/MegaCrusher.obj");

    @Override
    public void render(BlockEntity be, double x, double y, double z, float partialTick) {
        if (!Aero_RenderDistance.shouldRenderRelative(x, y, z, 4.0d)) return;
        bindTexture("/models/retronism_megacrusher.png");
        float brightness = AeroLight.brightnessAbove(be.world, be.x, be.y, be.z);
        Aero_MeshRenderer.renderModelAtRest(MODEL, x, y, z, 0f, brightness);
    }
}
