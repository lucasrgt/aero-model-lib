package aero.modellib.test;

import aero.modellib.Aero_IkChain;
import aero.modellib.Aero_MeshModel;
import aero.modellib.Aero_MeshRenderer;
import aero.modellib.Aero_ObjLoader;
import aero.modellib.Aero_ProceduralPose;
import aero.modellib.Aero_RenderDistance;
import aero.modellib.Aero_RenderLod;
import aero.modellib.Aero_RenderOptions;
import aero.modellib.Aero_TextureBinder;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.client.render.block.entity.BlockEntityRenderer;
import net.minecraft.entity.player.PlayerEntity;

public class TurretIKBlockEntityRenderer extends BlockEntityRenderer {

    public static final Aero_MeshModel MODEL = Aero_ObjLoader.load("/models/Turret.obj");

    private static final String[] CHAIN = {"turret_base", "turret_arm", "turret_tip"};
    private static final double TRACK_RADIUS_BLOCKS = 16.0;
    private final PlayerTrackingIkChain trackingChain = new PlayerTrackingIkChain();
    private final Aero_IkChain[] trackingChains = new Aero_IkChain[] { trackingChain };

    @Override
    public void render(BlockEntity blockEntity, double x, double y, double z, float partialTick) {
        final TurretIKBlockEntity be = (TurretIKBlockEntity) blockEntity;
        Aero_RenderLod lod = Aero_RenderDistance.lodRelative(
            x, y, z, 2d, AeroTestMod.demoAnimatedLodDistance());
        if (!lod.shouldRender()) return;
        Aero_TextureBinder.bind("/models/aerotest_motor.png");
        float brightness = AeroLight.brightnessAbove(be.world, be.x, be.y, be.z);

        if (lod.shouldAnimate()) {
            trackingChain.be = be;
            try {
                Aero_MeshRenderer.renderAnimated(MODEL,
                    TurretIKBlockEntity.BUNDLE, TurretIKBlockEntity.ANIM_DEF, be.animState,
                    x, y, z, brightness, partialTick,
                    Aero_RenderOptions.DEFAULT, (Aero_ProceduralPose) null, trackingChains);
            } finally {
                trackingChain.be = null;
            }
        } else {
            Aero_MeshRenderer.renderModelAtRest(MODEL, x, y, z, 0f, brightness);
        }
    }

    private static final class PlayerTrackingIkChain implements Aero_IkChain {
        private TurretIKBlockEntity be;

        public String[] getBoneChain() {
            return CHAIN;
        }

        public boolean resolveTargetInto(float[] worldPos) {
            TurretIKBlockEntity local = be;
            if (local == null) return false;
            PlayerEntity p = local.world.getClosestPlayer(
                local.x + 0.5, local.y + 0.5, local.z + 0.5, TRACK_RADIUS_BLOCKS);
            if (p == null) return false;
            worldPos[0] = (float) ((p.x - local.x) * 16.0);
            worldPos[1] = (float) ((p.y + p.getEyeHeight() - local.y) * 16.0);
            worldPos[2] = (float) ((p.z - local.z) * 16.0);
            return true;
        }
    }
}
