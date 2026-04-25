package aero.modellib.test;

import aero.modellib.Aero_EntityModelRenderer;
import aero.modellib.Aero_EntityModelTransform;
import aero.modellib.Aero_MeshModel;
import aero.modellib.Aero_ObjLoader;
import aero.modellib.Aero_RenderDistance;
import aero.modellib.Aero_RenderLod;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.entity.Entity;
import org.lwjgl.opengl.GL11;

public class AeroTestEntityRenderer extends EntityRenderer {

    private static final Aero_MeshModel MODEL =
        Aero_ObjLoader.load("/models/MegaCrusher.obj");

    private static final Aero_EntityModelTransform TRANSFORM =
        Aero_EntityModelTransform.builder()
            .offset(-1.5f, 0f, -1.5f)
            .scale(0.45f)
            .cullingRadius(3f)
            .build();

    public AeroTestEntityRenderer() {
        shadowRadius = 0.8f;
    }

    @Override
    public void render(Entity entity, double x, double y, double z,
                       float yaw, float partialTick) {
        AeroTestEntity testEntity = (AeroTestEntity) entity;
        Aero_RenderLod lod = Aero_RenderDistance.lodRelative(
            x, y, z, 3d, AeroTestMod.DEMO_ANIMATED_LOD_DISTANCE_BLOCKS);
        if (!lod.shouldRender()) return;

        bindTexture("/models/retronism_megacrusher.png");
        GL11.glColor4f(1f, 1f, 1f, 1f);
        if (lod.shouldAnimate()) {
            Aero_EntityModelRenderer.renderAnimated(MODEL, testEntity.animState,
                entity, x, y, z, yaw, partialTick, TRANSFORM);
        } else {
            Aero_EntityModelRenderer.renderAtRest(MODEL,
                entity, x, y, z, yaw, partialTick, TRANSFORM);
        }
    }
}
