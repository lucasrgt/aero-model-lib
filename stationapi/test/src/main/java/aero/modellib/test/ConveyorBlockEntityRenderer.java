package aero.modellib.test;

import aero.modellib.Aero_MeshModel;
import aero.modellib.Aero_MeshRenderer;
import aero.modellib.Aero_ObjLoader;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.client.render.block.entity.BlockEntityRenderer;

public class ConveyorBlockEntityRenderer extends BlockEntityRenderer {

    public static final Aero_MeshModel MODEL = Aero_ObjLoader.load("/models/Conveyor.obj");

    @Override
    public void render(BlockEntity blockEntity, double x, double y, double z, float partialTick) {
        ConveyorBlockEntity be = (ConveyorBlockEntity) blockEntity;
        bindTexture("/models/aerotest_motor.png");
        float brightness = AeroLight.brightnessAbove(be.world, be.x, be.y, be.z);
        Aero_MeshRenderer.renderAnimated(MODEL,
            ConveyorBlockEntity.BUNDLE, ConveyorBlockEntity.ANIM_DEF, be.animState,
            x, y, z, brightness, partialTick);
    }
}
