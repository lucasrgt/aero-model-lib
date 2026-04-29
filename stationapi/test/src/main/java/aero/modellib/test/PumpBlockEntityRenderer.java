package aero.modellib.test;

import aero.modellib.Aero_AnimatedBatcher;
import aero.modellib.Aero_MeshModel;
import aero.modellib.Aero_MeshRenderer;
import aero.modellib.Aero_ObjLoader;
import aero.modellib.Aero_RenderDistance;
import aero.modellib.Aero_RenderLod;
import aero.modellib.Aero_RenderOptions;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.client.render.block.entity.BlockEntityRenderer;

public class PumpBlockEntityRenderer extends BlockEntityRenderer {

    public static final Aero_MeshModel MODEL = Aero_ObjLoader.load("/models/Pump.obj");
    private static final String TEXTURE = "/models/aerotest_pump.png";

    @Override
    public void render(BlockEntity blockEntity, double x, double y, double z, float partialTick) {
        PumpBlockEntity be = (PumpBlockEntity) blockEntity;
        Aero_RenderLod lod = Aero_RenderDistance.lodRelative(
            x, y, z, 2d, AeroTestMod.demoAnimatedLodDistance());
        if (!lod.shouldRender()) return;

        float brightness = AeroLight.brightnessAbove(be.world, be.x, be.y, be.z);
        if (lod.shouldAnimate()) {
            Aero_AnimatedBatcher.queueAnimated(MODEL, TEXTURE,
                PumpBlockEntity.BUNDLE,
                PumpBlockEntity.ANIM_DEF,
                be.animState,
                x, y, z, brightness, partialTick,
                Aero_RenderOptions.DEFAULT);
        } else {
            bindTexture(TEXTURE);
            Aero_MeshRenderer.renderModelAtRest(MODEL, x, y, z, 0f, brightness);
        }
    }
}
