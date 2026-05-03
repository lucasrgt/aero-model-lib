package aero.modellib.test;

import aero.modellib.Aero_BECellRenderer;
import aero.modellib.Aero_MeshRenderer;
import aero.modellib.Aero_RenderDistance;
import aero.modellib.Aero_TextureBinder;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.client.render.block.entity.BlockEntityRenderer;

import aero.modellib.model.Aero_MeshModel;
import aero.modellib.model.Aero_ObjLoader;
import aero.modellib.render.Aero_RenderLod;
import aero.modellib.render.Aero_RenderOptions;

public class ConveyorBlockEntityRenderer extends BlockEntityRenderer {

    public static final Aero_MeshModel MODEL = Aero_ObjLoader.load("/models/Conveyor.obj");
    public static final String TEXTURE = "/models/aerotest_motor.png";

    @Override
    public void render(BlockEntity blockEntity, double x, double y, double z, float partialTick) {
        ConveyorBlockEntity be = (ConveyorBlockEntity) blockEntity;
        Aero_RenderLod lod = Aero_RenderDistance.lodRelative(
            x, y, z, 1d, AeroTestMod.demoAnimatedLodDistance());
        if (!lod.shouldRender()) return;
        float brightness = AeroLight.brightnessAbove(be.world, be.x, be.y, be.z);
        if (lod.shouldAnimate()) {
            Aero_TextureBinder.bind(TEXTURE);
            Aero_MeshRenderer.renderAnimated(MODEL,
                ConveyorBlockEntity.BUNDLE, ConveyorBlockEntity.ANIM_DEF,
                be.animState, x, y, z, brightness, partialTick,
                Aero_RenderOptions.DEFAULT);
        } else {
            Aero_BECellRenderer.queueAtRest(MODEL, TEXTURE, be,
                x, y, z, 0f, brightness, Aero_RenderOptions.DEFAULT);
        }
    }
}
