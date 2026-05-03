package aero.modellib.test;

import aero.modellib.Aero_AnimatedBatcher;
import aero.modellib.Aero_BECellRenderer;
import aero.modellib.Aero_RenderDistance;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.client.render.block.entity.BlockEntityRenderer;

import aero.modellib.model.Aero_MeshModel;
import aero.modellib.model.Aero_ObjLoader;
import aero.modellib.render.Aero_RenderLod;
import aero.modellib.render.Aero_RenderOptions;

public class MotorBlockEntityRenderer extends BlockEntityRenderer {

    public static final Aero_MeshModel MODEL = Aero_ObjLoader.load("/models/Motor.obj");
    public static final String TEXTURE = "/models/aerotest_motor.png";

    @Override
    public void render(BlockEntity blockEntity, double x, double y, double z, float partialTick) {
        MotorBlockEntity be = (MotorBlockEntity) blockEntity;
        Aero_RenderLod lod = Aero_RenderDistance.lodRelative(
            x, y, z, 2d, AeroTestMod.demoAnimatedLodDistance());
        if (!lod.shouldRender()) return;

        float brightness = AeroLight.brightnessAbove(be.world, be.x, be.y, be.z);
        if (lod.shouldAnimate()) {
            // Queue for batched render at end of entity pass — combines
            // all visible motors into a single tess cycle per bone, vs
            // one cycle per instance. The batcher will bind the texture
            // once per batch at flush time.
            Aero_AnimatedBatcher.queueAnimated(MODEL, TEXTURE,
                MotorBlockEntity.BUNDLE,
                MotorBlockEntity.ANIM_DEF,
                be.animState,
                x, y, z, brightness, partialTick,
                Aero_RenderOptions.DEFAULT);
        } else {
            // At-rest path can be cell-paged at the end of the entity pass.
            Aero_BECellRenderer.queueAtRest(MODEL, TEXTURE, be,
                x, y, z, 0f, brightness, Aero_RenderOptions.DEFAULT);
        }
    }
}
