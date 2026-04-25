package aero.modellib;

import net.minecraft.src.Entity;
import org.lwjgl.opengl.GL11;

/**
 * Entity-oriented render helper for Aero models.
 *
 * The lower-level model renderers are still useful directly, but they use
 * block-style rotations. This helper wraps them in an entity-origin transform
 * and keeps texture binding in the caller, matching vanilla Render classes.
 */
public final class Aero_EntityModelRenderer {

    private Aero_EntityModelRenderer() {
    }

    public static void render(Aero_JsonModel model, Entity entity,
                              double x, double y, double z,
                              float yaw, float partialTick) {
        render(model, entity, x, y, z, yaw, partialTick, Aero_EntityModelTransform.DEFAULT);
    }

    public static void render(Aero_JsonModel model, Entity entity,
                              double x, double y, double z,
                              float yaw, float partialTick,
                              Aero_EntityModelTransform transform) {
        render(model, x, y, z, yaw, entity.getBrightness(partialTick), transform);
    }

    public static void render(Aero_JsonModel model,
                              double x, double y, double z,
                              float yaw, float brightness) {
        render(model, x, y, z, yaw, brightness, Aero_EntityModelTransform.DEFAULT);
    }

    public static void render(Aero_JsonModel model,
                              double x, double y, double z,
                              float yaw, float brightness,
                              Aero_EntityModelTransform transform) {
        requireTransform(transform);
        beginEntityTransform(x, y, z, yaw, transform);
        try {
            Aero_JsonModelRenderer.renderModel(model, transform.offsetX, transform.offsetY, transform.offsetZ, 0f, brightness);
        } finally {
            GL11.glPopMatrix();
        }
    }

    public static void render(Aero_MeshModel model, Entity entity,
                              double x, double y, double z,
                              float yaw, float partialTick) {
        render(model, entity, x, y, z, yaw, partialTick, Aero_EntityModelTransform.DEFAULT);
    }

    public static void render(Aero_MeshModel model, Entity entity,
                              double x, double y, double z,
                              float yaw, float partialTick,
                              Aero_EntityModelTransform transform) {
        render(model, x, y, z, yaw, entity.getBrightness(partialTick), transform);
    }

    public static void render(Aero_MeshModel model,
                              double x, double y, double z,
                              float yaw, float brightness) {
        render(model, x, y, z, yaw, brightness, Aero_EntityModelTransform.DEFAULT);
    }

    public static void render(Aero_MeshModel model,
                              double x, double y, double z,
                              float yaw, float brightness,
                              Aero_EntityModelTransform transform) {
        requireTransform(transform);
        beginEntityTransform(x, y, z, yaw, transform);
        try {
            Aero_MeshRenderer.renderModel(model, transform.offsetX, transform.offsetY, transform.offsetZ, 0f, brightness);
        } finally {
            GL11.glPopMatrix();
        }
    }

    public static void renderAnimated(Aero_MeshModel model,
                                      Aero_AnimationPlayback state,
                                      Entity entity,
                                      double x, double y, double z,
                                      float yaw, float partialTick) {
        renderAnimated(model, state, entity, x, y, z, yaw, partialTick, Aero_EntityModelTransform.DEFAULT);
    }

    public static void renderAnimated(Aero_MeshModel model,
                                      Aero_AnimationPlayback state,
                                      Entity entity,
                                      double x, double y, double z,
                                      float yaw, float partialTick,
                                      Aero_EntityModelTransform transform) {
        renderAnimated(model, state, x, y, z, yaw, entity.getBrightness(partialTick), partialTick, transform);
    }

    public static void renderAnimated(Aero_MeshModel model,
                                      Aero_AnimationPlayback state,
                                      double x, double y, double z,
                                      float yaw, float brightness, float partialTick) {
        renderAnimated(model, state, x, y, z, yaw, brightness, partialTick, Aero_EntityModelTransform.DEFAULT);
    }

    public static void renderAnimated(Aero_MeshModel model,
                                      Aero_AnimationPlayback state,
                                      double x, double y, double z,
                                      float yaw, float brightness, float partialTick,
                                      Aero_EntityModelTransform transform) {
        if (state == null) throw new IllegalArgumentException("state must not be null");
        renderAnimated(model, state.getBundle(), state.getDef(), state, x, y, z, yaw, brightness, partialTick, transform);
    }

    public static void renderAnimated(Aero_MeshModel model,
                                      Aero_AnimationBundle bundle,
                                      Aero_AnimationDefinition def,
                                      Aero_AnimationPlayback state,
                                      Entity entity,
                                      double x, double y, double z,
                                      float yaw, float partialTick) {
        renderAnimated(model, bundle, def, state, entity, x, y, z, yaw, partialTick, Aero_EntityModelTransform.DEFAULT);
    }

    public static void renderAnimated(Aero_MeshModel model,
                                      Aero_AnimationBundle bundle,
                                      Aero_AnimationDefinition def,
                                      Aero_AnimationPlayback state,
                                      Entity entity,
                                      double x, double y, double z,
                                      float yaw, float partialTick,
                                      Aero_EntityModelTransform transform) {
        renderAnimated(model, bundle, def, state, x, y, z, yaw, entity.getBrightness(partialTick), partialTick, transform);
    }

    public static void renderAnimated(Aero_MeshModel model,
                                      Aero_AnimationBundle bundle,
                                      Aero_AnimationDefinition def,
                                      Aero_AnimationPlayback state,
                                      double x, double y, double z,
                                      float yaw, float brightness, float partialTick) {
        renderAnimated(model, bundle, def, state, x, y, z, yaw, brightness, partialTick, Aero_EntityModelTransform.DEFAULT);
    }

    public static void renderAnimated(Aero_MeshModel model,
                                      Aero_AnimationBundle bundle,
                                      Aero_AnimationDefinition def,
                                      Aero_AnimationPlayback state,
                                      double x, double y, double z,
                                      float yaw, float brightness, float partialTick,
                                      Aero_EntityModelTransform transform) {
        requireTransform(transform);
        beginEntityTransform(x, y, z, yaw, transform);
        try {
            Aero_MeshRenderer.renderAnimated(model, bundle, def, state,
                transform.offsetX, transform.offsetY, transform.offsetZ,
                brightness, partialTick);
        } finally {
            GL11.glPopMatrix();
        }
    }

    private static void beginEntityTransform(double x, double y, double z,
                                             float yaw, Aero_EntityModelTransform transform) {
        GL11.glPushMatrix();
        GL11.glTranslated(x, y, z);
        GL11.glRotatef(transform.modelYaw(yaw), 0f, 1f, 0f);
        if (transform.scale != 1f) {
            GL11.glScalef(transform.scale, transform.scale, transform.scale);
        }
    }

    private static void requireTransform(Aero_EntityModelTransform transform) {
        if (transform == null) throw new IllegalArgumentException("transform must not be null");
    }
}
