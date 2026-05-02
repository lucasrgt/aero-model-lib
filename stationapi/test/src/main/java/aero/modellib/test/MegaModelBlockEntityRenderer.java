package aero.modellib.test;

import aero.modellib.Aero_BECellRenderer;
import aero.modellib.Aero_MeshModel;
import aero.modellib.Aero_ObjLoader;
import aero.modellib.Aero_RenderDistance;
import aero.modellib.Aero_RenderOptions;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.client.render.block.entity.BlockEntityRenderer;

/**
 * Static-mesh renderer for {@link MegaModelBlockEntity}. Uses the at-rest
 * renderer so named groups can hit Aero_MeshRenderer's display-list fast-path.
 */
public class MegaModelBlockEntityRenderer extends BlockEntityRenderer {

    public static final Aero_MeshModel MODEL = Aero_ObjLoader.load("/models/MegaCrusher.obj");
    public static final String TEXTURE = "/models/retronism_megacrusher.png";

    @Override
    public void render(BlockEntity be, double x, double y, double z, float partialTick) {
        if (!Aero_RenderDistance.shouldRenderRelative(x, y, z, 4.0d)) return;
        float brightness = AeroLight.brightnessAbove(be.world, be.x, be.y, be.z);
        Aero_BECellRenderer.queueAtRest(MODEL, TEXTURE, be,
            x, y, z, 0f, brightness, Aero_RenderOptions.DEFAULT);
    }
}
