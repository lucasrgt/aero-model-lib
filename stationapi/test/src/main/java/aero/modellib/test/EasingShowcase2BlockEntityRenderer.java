package aero.modellib.test;

import aero.modellib.Aero_AnimatedBatcher;
import aero.modellib.Aero_MeshRenderer;
import aero.modellib.Aero_RenderDistance;
import aero.modellib.Aero_TextureBinder;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.client.render.block.entity.BlockEntityRenderer;

import aero.modellib.model.Aero_MeshModel;
import aero.modellib.model.Aero_ObjLoader;
import aero.modellib.render.Aero_RenderLod;
import aero.modellib.render.Aero_RenderOptions;

public class EasingShowcase2BlockEntityRenderer extends BlockEntityRenderer {
    public static final Aero_MeshModel MODEL = Aero_ObjLoader.load("/models/EasingShowcase2.obj");
    private static final String TEXTURE = "/models/aerotest_easings2.png";

    @Override
    public void render(BlockEntity blockEntity, double x, double y, double z, float partialTick) {
        EasingShowcase2BlockEntity be = (EasingShowcase2BlockEntity) blockEntity;
        Aero_RenderLod lod = Aero_RenderDistance.lodRelative(
            x, y, z, 2d, AeroTestMod.demoAnimatedLodDistance());
        if (!lod.shouldRender()) return;
        float brightness = AeroLight.brightnessAbove(be.world, be.x, be.y, be.z);
        if (lod.shouldAnimate()) {
            Aero_AnimatedBatcher.queueAnimated(MODEL, TEXTURE,
                EasingShowcase2BlockEntity.BUNDLE,
                EasingShowcase2BlockEntity.ANIM_DEF,
                be.animState,
                x, y, z, brightness, partialTick,
                Aero_RenderOptions.DEFAULT);
        } else {
            Aero_TextureBinder.bind(TEXTURE);
            Aero_MeshRenderer.renderModelAtRest(MODEL, x, y, z, 0f, brightness);
        }
    }
}
