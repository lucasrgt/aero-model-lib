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

public class CrystalChaosBlockEntityRenderer extends BlockEntityRenderer {

    // Loaded from a separate path so each crystal block has its own
    // bone-ref cache slot — different clips would otherwise thrash a
    // shared model's single-slot cache.
    public static final Aero_MeshModel MODEL = Aero_ObjLoader.load("/models/CrystalChaos.obj");
    private static final String TEXTURE = "/models/aerotest_crystal_chaos.png";

    @Override
    public void render(BlockEntity blockEntity, double x, double y, double z, float partialTick) {
        CrystalChaosBlockEntity be = (CrystalChaosBlockEntity) blockEntity;
        Aero_RenderLod lod = Aero_RenderDistance.lodRelative(
            x, y, z, 2d, AeroTestMod.demoAnimatedLodDistance());
        if (!lod.shouldRender()) return;

        float brightness = AeroLight.brightnessAbove(be.world, be.x, be.y, be.z);
        if (lod.shouldAnimate()) {
            Aero_AnimatedBatcher.queueAnimated(MODEL, TEXTURE,
                CrystalChaosBlockEntity.BUNDLE,
                CrystalChaosBlockEntity.ANIM_DEF,
                be.animState,
                x, y, z, brightness, partialTick,
                Aero_RenderOptions.DEFAULT);
        } else {
            Aero_TextureBinder.bind(TEXTURE);
            Aero_MeshRenderer.renderModelAtRest(MODEL, x, y, z, 0f, brightness);
        }
    }
}
