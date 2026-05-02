package aero.modellib.test;

import aero.modellib.Aero_IkChain;
import aero.modellib.Aero_MeshModel;
import aero.modellib.Aero_MeshRenderer;
import aero.modellib.Aero_MorphTarget;
import aero.modellib.Aero_ObjLoader;
import aero.modellib.Aero_ProceduralPose;
import aero.modellib.Aero_RenderDistance;
import aero.modellib.Aero_RenderLod;
import aero.modellib.Aero_RenderOptions;
import aero.modellib.Aero_TextureBinder;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.client.render.block.entity.BlockEntityRenderer;

public class MorphCrystalBlockEntityRenderer extends BlockEntityRenderer {

    public static final Aero_MeshModel MODEL = Aero_ObjLoader.load("/models/MorphCrystal.obj");

    static {
        Aero_MorphTarget.attachAllFromBundle(MODEL, MorphCrystalBlockEntity.BUNDLE);
    }

    @Override
    public void render(BlockEntity blockEntity, double x, double y, double z, float partialTick) {
        MorphCrystalBlockEntity be = (MorphCrystalBlockEntity) blockEntity;
        Aero_RenderLod lod = Aero_RenderDistance.lodRelative(
            x, y, z, 2d, AeroTestMod.demoAnimatedLodDistance());
        if (!lod.shouldRender()) return;
        Aero_TextureBinder.bind("/models/aerotest_motor.png");
        float brightness = AeroLight.brightnessAbove(be.world, be.x, be.y, be.z);

        if (lod.shouldAnimate()) {
            Aero_MeshRenderer.renderAnimated(MODEL,
                MorphCrystalBlockEntity.BUNDLE, MorphCrystalBlockEntity.ANIM_DEF, be.animState,
                x, y, z, brightness, partialTick,
                Aero_RenderOptions.DEFAULT, (Aero_ProceduralPose) null,
                (Aero_IkChain[]) null, be.morphState);
        } else {
            Aero_MeshRenderer.renderModelAtRest(MODEL, x, y, z, 0f, brightness);
        }
    }
}
