package aero.modellib.test;

import aero.modellib.Aero_MeshModel;
import aero.modellib.Aero_MeshRenderer;
import aero.modellib.Aero_ObjLoader;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.client.render.block.entity.BlockEntityRenderer;

public class CrystalBlockEntityRenderer extends BlockEntityRenderer {

    public static final Aero_MeshModel MODEL = Aero_ObjLoader.load("/models/Crystal.obj");

    @Override
    public void render(BlockEntity blockEntity, double x, double y, double z, float partialTick) {
        CrystalBlockEntity be = (CrystalBlockEntity) blockEntity;
        bindTexture("/models/aerotest_crystal.png");
        float brightness = be.world.method_1782(be.x, be.y + 1, be.z);
        Aero_MeshRenderer.renderAnimated(MODEL,
            CrystalBlockEntity.BUNDLE,
            CrystalBlockEntity.ANIM_DEF,
            be.animState,
            x, y, z, brightness, partialTick);
    }
}
