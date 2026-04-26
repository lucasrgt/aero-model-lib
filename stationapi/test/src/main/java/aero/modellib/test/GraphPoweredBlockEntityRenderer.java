package aero.modellib.test;

import aero.modellib.Aero_MeshModel;
import aero.modellib.Aero_MeshRenderer;
import aero.modellib.Aero_ObjLoader;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.client.render.block.entity.BlockEntityRenderer;

public class GraphPoweredBlockEntityRenderer extends BlockEntityRenderer {

    /** Reuses Motor.obj — single bone "core" — so no new asset is needed. */
    public static final Aero_MeshModel MODEL = Aero_ObjLoader.load("/models/Motor.obj");

    @Override
    public void render(BlockEntity blockEntity, double x, double y, double z, float partialTick) {
        GraphPoweredBlockEntity be = (GraphPoweredBlockEntity) blockEntity;
        bindTexture("/models/aerotest_motor.png");
        float brightness = AeroLight.brightnessAbove(be.world, be.x, be.y, be.z);
        Aero_MeshRenderer.renderAnimated(MODEL, be.graph,
            GraphPoweredBlockEntity.BUNDLE,
            x, y, z, brightness, partialTick);
    }
}
