package aero.modellib.test;

import aero.modellib.Aero_MeshModel;
import aero.modellib.Aero_MeshRenderer;
import aero.modellib.Aero_ObjLoader;
import aero.modellib.Aero_RenderDistance;
import aero.modellib.Aero_RenderLod;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.client.render.block.entity.BlockEntityRenderer;

public class CrystalChaosBlockEntityRenderer extends BlockEntityRenderer {

    // Loaded from a separate path so each crystal block has its own
    // bone-ref cache slot — different clips would otherwise thrash a
    // shared model's single-slot cache.
    public static final Aero_MeshModel MODEL = Aero_ObjLoader.load("/models/CrystalChaos.obj");

    @Override
    public void render(BlockEntity blockEntity, double x, double y, double z, float partialTick) {
        CrystalChaosBlockEntity be = (CrystalChaosBlockEntity) blockEntity;
        Aero_RenderLod lod = Aero_RenderDistance.lodRelative(
            x, y, z, 2d, AeroTestMod.demoAnimatedLodDistance());
        if (!lod.shouldRender()) return;

        bindTexture("/models/aerotest_crystal_chaos.png");
        float brightness = AeroLight.brightnessAbove(be.world, be.x, be.y, be.z);
        if (lod.shouldAnimate()) {
            Aero_MeshRenderer.renderAnimated(MODEL,
                CrystalChaosBlockEntity.BUNDLE,
                CrystalChaosBlockEntity.ANIM_DEF,
                be.animState,
                x, y, z, brightness, partialTick);
        } else {
            Aero_MeshRenderer.renderModelAtRest(MODEL, x, y, z, 0f, brightness);
        }
    }
}
