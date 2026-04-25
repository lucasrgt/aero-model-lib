package aero.modellib.test;

import aero.modellib.Aero_MeshModel;
import aero.modellib.Aero_MeshRenderer;
import aero.modellib.Aero_ObjLoader;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.client.render.block.entity.BlockEntityRenderer;

public class PumpBlockEntityRenderer extends BlockEntityRenderer {

    public static final Aero_MeshModel MODEL = Aero_ObjLoader.load("/models/Pump.obj");

    @Override
    public void render(BlockEntity blockEntity, double x, double y, double z, float partialTick) {
        PumpBlockEntity be = (PumpBlockEntity) blockEntity;
        bindTexture("/models/aerotest_pump.png");
        float brightness = be.world.method_1782(be.x, be.y + 1, be.z);
        Aero_MeshRenderer.renderAnimated(MODEL,
            PumpBlockEntity.BUNDLE,
            PumpBlockEntity.ANIM_DEF,
            be.animState,
            x, y, z, brightness, partialTick);
    }
}
