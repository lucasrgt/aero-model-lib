package aero.modellib.test;

import aero.modellib.Aero_MeshModel;
import aero.modellib.Aero_MeshRenderer;
import aero.modellib.Aero_ObjLoader;
import aero.modellib.Aero_RenderDistance;
import aero.modellib.Aero_RenderLod;
import aero.modellib.Aero_TextureBinder;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.client.render.block.entity.BlockEntityRenderer;

public class GraphPoweredBlockEntityRenderer extends BlockEntityRenderer {

    /** Reuses Motor.obj — single bone "core" — so no new asset is needed. */
    public static final Aero_MeshModel MODEL = Aero_ObjLoader.load("/models/Motor.obj");

    @Override
    public void render(BlockEntity blockEntity, double x, double y, double z, float partialTick) {
        GraphPoweredBlockEntity be = (GraphPoweredBlockEntity) blockEntity;
        Aero_RenderLod lod = Aero_RenderDistance.lodRelative(
            x, y, z, 2d, AeroTestMod.demoAnimatedLodDistance());
        if (!lod.shouldRender()) return;
        Aero_TextureBinder.bind("/models/aerotest_motor.png");
        float brightness = AeroLight.brightnessAbove(be.world, be.x, be.y, be.z);
        if (lod.shouldAnimate()) {
            Aero_MeshRenderer.renderAnimated(MODEL, be.graph,
                GraphPoweredBlockEntity.BUNDLE,
                x, y, z, brightness, partialTick);
        } else {
            Aero_MeshRenderer.renderModelAtRest(MODEL, x, y, z, 0f, brightness);
        }
    }
}
