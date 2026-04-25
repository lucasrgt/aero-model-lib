package aero.modellib.test;

import aero.modellib.Aero_MeshModel;
import aero.modellib.Aero_MeshRenderer;
import aero.modellib.Aero_ObjLoader;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.client.render.block.entity.BlockEntityRenderer;

public class EasingShowcase2BlockEntityRenderer extends BlockEntityRenderer {
    public static final Aero_MeshModel MODEL = Aero_ObjLoader.load("/models/EasingShowcase2.obj");

    @Override
    public void render(BlockEntity blockEntity, double x, double y, double z, float partialTick) {
        EasingShowcase2BlockEntity be = (EasingShowcase2BlockEntity) blockEntity;
        bindTexture("/models/aerotest_easings2.png");
        float brightness = AeroLight.brightnessAbove(be.world, be.x, be.y, be.z);
        Aero_MeshRenderer.renderAnimated(MODEL,
            EasingShowcase2BlockEntity.BUNDLE,
            EasingShowcase2BlockEntity.ANIM_DEF,
            be.animState,
            x, y, z, brightness, partialTick);
    }
}
