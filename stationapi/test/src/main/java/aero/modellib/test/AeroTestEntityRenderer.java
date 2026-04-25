package aero.modellib.test;

import aero.modellib.Aero_EntityModelRenderer;
import aero.modellib.Aero_EntityModelTransform;
import aero.modellib.Aero_MeshModel;
import aero.modellib.Aero_ObjLoader;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.entity.Entity;
import org.lwjgl.opengl.GL11;

public class AeroTestEntityRenderer extends EntityRenderer {

    private static final Aero_MeshModel MODEL =
        Aero_ObjLoader.load("/models/MegaCrusher.obj");

    private static final Aero_EntityModelTransform TRANSFORM =
        Aero_EntityModelTransform.DEFAULT
            .withOffset(-1.5f, 0f, -1.5f)
            .withScale(0.45f)
            .withCullingRadius(3f);

    public AeroTestEntityRenderer() {
        shadowRadius = 0.8f;
    }

    @Override
    public void render(Entity entity, double x, double y, double z,
                       float yaw, float partialTick) {
        AeroTestEntity testEntity = (AeroTestEntity) entity;

        bindTexture("/models/retronism_megacrusher.png");
        GL11.glColor4f(1f, 1f, 1f, 1f);
        Aero_EntityModelRenderer.renderAnimated(MODEL, testEntity.animState,
            entity, x, y, z, yaw, partialTick, TRANSFORM);
    }
}
