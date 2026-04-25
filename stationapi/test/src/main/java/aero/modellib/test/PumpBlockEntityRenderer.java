package aero.modellib.test;

import aero.modellib.Aero_MeshModel;
import aero.modellib.Aero_MeshRenderer;
import aero.modellib.Aero_ObjLoader;
import aero.modellib.Aero_RenderDistance;
import aero.modellib.Aero_RenderLod;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.client.render.block.entity.BlockEntityRenderer;

public class PumpBlockEntityRenderer extends BlockEntityRenderer {

    public static final Aero_MeshModel MODEL = Aero_ObjLoader.load("/models/Pump.obj");

    @Override
    public void render(BlockEntity blockEntity, double x, double y, double z, float partialTick) {
        PumpBlockEntity be = (PumpBlockEntity) blockEntity;
        Aero_RenderLod lod = Aero_RenderDistance.lodRelative(
            x, y, z, 2d, AeroTestMod.DEMO_ANIMATED_LOD_DISTANCE_BLOCKS);
        if (!lod.shouldRender()) return;

        bindTexture("/models/aerotest_pump.png");
        float brightness = AeroLight.brightnessAbove(be.world, be.x, be.y, be.z);
        if (lod.shouldAnimate()) {
            Aero_MeshRenderer.renderAnimated(MODEL,
                PumpBlockEntity.BUNDLE,
                PumpBlockEntity.ANIM_DEF,
                be.animState,
                x, y, z, brightness, partialTick);
        } else {
            Aero_MeshRenderer.renderModelAtRest(MODEL, x, y, z, 0f, brightness);
        }
    }
}
