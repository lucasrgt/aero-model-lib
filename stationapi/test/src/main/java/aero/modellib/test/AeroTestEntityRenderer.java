package aero.modellib.test;

import aero.modellib.Aero_EntityModelRenderer;
import aero.modellib.Aero_ModelSpec;
import aero.modellib.Aero_TextureBinder;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.entity.Entity;
import org.lwjgl.opengl.GL11;

public class AeroTestEntityRenderer extends EntityRenderer {

    private static final Aero_ModelSpec MODEL =
        Aero_ModelSpec.mesh("/models/MegaCrusher.obj")
            .texture("/models/retronism_megacrusher.png")
            .animations(AeroTestEntity.ANIMATION)
            .offset(-1.5f, 0f, -1.5f)
            .scale(0.45f)
            .cullingRadius(3f)
            .animatedDistance(AeroTestMod.demoAnimatedLodDistance())
            .build();

    public AeroTestEntityRenderer() {
        shadowRadius = 0.8f;
    }

    @Override
    public void render(Entity entity, double x, double y, double z,
                       float yaw, float partialTick) {
        AeroTestEntity testEntity = (AeroTestEntity) entity;
        Aero_TextureBinder.bind(MODEL.getTexturePath());
        GL11.glColor4f(1f, 1f, 1f, 1f);
        Aero_EntityModelRenderer.render(MODEL, testEntity.animState,
            entity, x, y, z, yaw, partialTick);
    }
}
