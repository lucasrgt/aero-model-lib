package aero.modellib.test;

import aero.modellib.Aero_IkChain;
import aero.modellib.Aero_MeshModel;
import aero.modellib.Aero_MeshRenderer;
import aero.modellib.Aero_ObjLoader;
import aero.modellib.Aero_ProceduralPose;
import aero.modellib.Aero_RenderOptions;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.client.render.block.entity.BlockEntityRenderer;
import net.minecraft.entity.player.PlayerEntity;

public class TurretIKBlockEntityRenderer extends BlockEntityRenderer {

    public static final Aero_MeshModel MODEL = Aero_ObjLoader.load("/models/Turret.obj");

    private static final String[] CHAIN = {"turret_base", "turret_arm", "turret_tip"};
    private static final double TRACK_RADIUS_BLOCKS = 16.0;

    @Override
    public void render(BlockEntity blockEntity, double x, double y, double z, float partialTick) {
        final TurretIKBlockEntity be = (TurretIKBlockEntity) blockEntity;
        bindTexture("/models/aerotest_motor.png");
        float brightness = AeroLight.brightnessAbove(be.world, be.x, be.y, be.z);

        // IK target: nearest player's eye, in block-local pixel space
        // (matches the FK walker's frame). The lib's outer glTranslate
        // places the block at the world origin, so we subtract block
        // coords and convert blocks → pixels via *16 to align with the
        // bundle's pivot units.
        Aero_IkChain[] chains = new Aero_IkChain[]{ new Aero_IkChain() {
            public String[] getBoneChain() { return CHAIN; }
            public boolean resolveTargetInto(float[] worldPos) {
                PlayerEntity p = be.world.getClosestPlayer(
                    be.x + 0.5, be.y + 0.5, be.z + 0.5, TRACK_RADIUS_BLOCKS);
                if (p == null) return false;
                worldPos[0] = (float) ((p.x - be.x) * 16.0);
                worldPos[1] = (float) ((p.y + p.getEyeHeight() - be.y) * 16.0);
                worldPos[2] = (float) ((p.z - be.z) * 16.0);
                return true;
            }
        }};

        Aero_MeshRenderer.renderAnimated(MODEL,
            TurretIKBlockEntity.BUNDLE, TurretIKBlockEntity.ANIM_DEF, be.animState,
            x, y, z, brightness, partialTick,
            Aero_RenderOptions.DEFAULT, (Aero_ProceduralPose) null, chains);
    }
}
