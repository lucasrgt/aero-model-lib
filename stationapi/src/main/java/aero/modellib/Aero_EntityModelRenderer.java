package aero.modellib;

import net.minecraft.entity.Entity;
import org.lwjgl.opengl.GL11;

/**
 * Entity-oriented render helper for Aero models.
 *
 * Texture binding stays in the caller's EntityRenderer. This class only owns
 * entity-origin translation, yaw conversion, optional scale and delegating to
 * the existing JSON/Mesh renderers.
 */
public final class Aero_EntityModelRenderer {

    private Aero_EntityModelRenderer() {
    }

    public static void render(Aero_ModelSpec spec, Entity entity,
                              double x, double y, double z,
                              float yaw, float partialTick) {
        requireSpec(spec);
        render(spec, x, y, z, yaw, entity.getBrightnessAtEyes(partialTick), partialTick);
    }

    public static void render(Aero_ModelSpec spec,
                              double x, double y, double z,
                              float yaw, float brightness, float partialTick) {
        requireSpec(spec);
        if (spec.isJson()) {
            render(spec.getJsonModel(), x, y, z, yaw, brightness, spec.getEntityTransform());
        } else {
            render(spec.getMeshModel(), x, y, z, yaw, brightness,
                spec.getEntityTransform(), spec.getRenderOptions());
        }
    }

    public static void render(Aero_ModelSpec spec, Aero_AnimationPlayback state,
                              Entity entity,
                              double x, double y, double z,
                              float yaw, float partialTick) {
        render(spec, state, entity, x, y, z, yaw, partialTick, null);
    }

    /**
     * Spec-based render with a procedural-pose hook for runtime / input-driven
     * rotations (vehicle turret tracking the rider's mouse, propeller spin
     * proportional to throttle, control surface deflection, etc.). The hook
     * fires per animated bone AFTER the keyframe pose is resolved, so it
     * composes additively on top of the lib's normal animation pipeline.
     */
    public static void render(Aero_ModelSpec spec, Aero_AnimationPlayback state,
                              Entity entity,
                              double x, double y, double z,
                              float yaw, float partialTick,
                              Aero_ProceduralPose proceduralPose) {
        requireSpec(spec);
        Aero_RenderLod lod = Aero_RenderDistance.lodRelative(spec, x, y, z);
        if (!lod.shouldRender()) return;
        if (!shouldRender(x, y, z, spec.getEntityTransform())) return;
        float brightness = entity.getBrightnessAtEyes(partialTick);
        if (lod.shouldAnimate()) {
            renderAnimated(spec, state, x, y, z, yaw, brightness, partialTick,
                spec.getRenderOptions(), proceduralPose);
        } else {
            renderAtRest(spec, x, y, z, yaw, brightness);
        }
    }

    public static void render(Aero_ModelSpec spec, Aero_AnimationPlayback state,
                              double x, double y, double z,
                              float yaw, float brightness, float partialTick) {
        render(spec, state, x, y, z, yaw, brightness, partialTick,
            spec != null ? spec.getRenderOptions() : Aero_RenderOptions.DEFAULT);
    }

    public static void render(Aero_ModelSpec spec, Aero_AnimationPlayback state,
                              double x, double y, double z,
                              float yaw, float brightness, float partialTick,
                              Aero_RenderOptions options) {
        requireSpec(spec);
        requireOptions(options);
        Aero_RenderLod lod = Aero_RenderDistance.lodRelative(spec, x, y, z);
        render(spec, state, lod, x, y, z, yaw, brightness, partialTick, options);
    }

    public static void render(Aero_ModelSpec spec, Aero_AnimationPlayback state,
                              Aero_RenderLod lod, Entity entity,
                              double x, double y, double z,
                              float yaw, float partialTick) {
        requireSpec(spec);
        if (lod == null) throw new IllegalArgumentException("lod must not be null");
        if (!lod.shouldRender()) return;
        if (!shouldRender(x, y, z, spec.getEntityTransform())) return;
        float brightness = entity.getBrightnessAtEyes(partialTick);
        render(spec, state, lod, x, y, z, yaw, brightness, partialTick);
    }

    public static void render(Aero_ModelSpec spec, Aero_AnimationPlayback state,
                              Aero_RenderLod lod,
                              double x, double y, double z,
                              float yaw, float brightness, float partialTick) {
        render(spec, state, lod, x, y, z, yaw, brightness, partialTick,
            spec != null ? spec.getRenderOptions() : Aero_RenderOptions.DEFAULT);
    }

    public static void render(Aero_ModelSpec spec, Aero_AnimationPlayback state,
                              Aero_RenderLod lod,
                              double x, double y, double z,
                              float yaw, float brightness, float partialTick,
                              Aero_RenderOptions options) {
        requireSpec(spec);
        requireOptions(options);
        if (lod == null) throw new IllegalArgumentException("lod must not be null");
        if (!lod.shouldRender()) return;
        if (!shouldRender(x, y, z, spec.getEntityTransform())) return;
        if (lod.shouldAnimate()) {
            renderAnimated(spec, state, x, y, z, yaw, brightness, partialTick, options);
        } else {
            renderAtRest(spec, x, y, z, yaw, brightness, options);
        }
    }

    public static void renderAtRest(Aero_ModelSpec spec,
                                    double x, double y, double z,
                                    float yaw, float brightness) {
        renderAtRest(spec, x, y, z, yaw, brightness,
            spec != null ? spec.getRenderOptions() : Aero_RenderOptions.DEFAULT);
    }

    public static void renderAtRest(Aero_ModelSpec spec,
                                    double x, double y, double z,
                                    float yaw, float brightness,
                                    Aero_RenderOptions options) {
        requireSpec(spec);
        requireOptions(options);
        if (spec.isJson()) {
            render(spec.getJsonModel(), x, y, z, yaw, brightness, spec.getEntityTransform());
        } else {
            renderAtRest(spec.getMeshModel(), x, y, z, yaw, brightness,
                spec.getEntityTransform(), options);
        }
    }

    public static void renderAnimated(Aero_ModelSpec spec,
                                      Aero_AnimationPlayback state,
                                      Entity entity,
                                      double x, double y, double z,
                                      float yaw, float partialTick) {
        renderAnimated(spec, state, entity, x, y, z, yaw, partialTick, null);
    }

    public static void renderAnimated(Aero_ModelSpec spec,
                                      Aero_AnimationPlayback state,
                                      Entity entity,
                                      double x, double y, double z,
                                      float yaw, float partialTick,
                                      Aero_ProceduralPose proceduralPose) {
        requireSpec(spec);
        renderAnimated(spec, state, x, y, z, yaw,
            entity.getBrightnessAtEyes(partialTick), partialTick, proceduralPose);
    }

    public static void renderAnimated(Aero_ModelSpec spec,
                                      Aero_AnimationPlayback state,
                                      double x, double y, double z,
                                      float yaw, float brightness, float partialTick) {
        renderAnimated(spec, state, x, y, z, yaw, brightness, partialTick,
            spec != null ? spec.getRenderOptions() : Aero_RenderOptions.DEFAULT);
    }

    public static void renderAnimated(Aero_ModelSpec spec,
                                      Aero_AnimationPlayback state,
                                      double x, double y, double z,
                                      float yaw, float brightness, float partialTick,
                                      Aero_ProceduralPose proceduralPose) {
        renderAnimated(spec, state, x, y, z, yaw, brightness, partialTick,
            spec != null ? spec.getRenderOptions() : Aero_RenderOptions.DEFAULT,
            proceduralPose);
    }

    public static void renderAnimated(Aero_ModelSpec spec,
                                      Aero_AnimationPlayback state,
                                      double x, double y, double z,
                                      float yaw, float brightness, float partialTick,
                                      Aero_RenderOptions options) {
        renderAnimated(spec, state, x, y, z, yaw, brightness, partialTick, options, null);
    }

    public static void renderAnimated(Aero_ModelSpec spec,
                                      Aero_AnimationPlayback state,
                                      double x, double y, double z,
                                      float yaw, float brightness, float partialTick,
                                      Aero_RenderOptions options,
                                      Aero_ProceduralPose proceduralPose) {
        requireSpec(spec);
        requireOptions(options);
        if (!spec.isMesh()) {
            throw new IllegalStateException("animated rendering requires a mesh spec");
        }
        renderAnimated(spec.getMeshModel(), state, x, y, z, yaw, brightness, partialTick,
            spec.getEntityTransform(), options, proceduralPose);
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
        render(model, x, y, z, yaw, entity.getBrightnessAtEyes(partialTick), transform);
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
        if (!shouldRender(x, y, z, transform)) return;
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
        render(model, x, y, z, yaw, entity.getBrightnessAtEyes(partialTick), transform);
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
        render(model, x, y, z, yaw, brightness, transform, Aero_RenderOptions.DEFAULT);
    }

    public static void render(Aero_MeshModel model,
                              double x, double y, double z,
                              float yaw, float brightness,
                              Aero_EntityModelTransform transform,
                              Aero_RenderOptions options) {
        requireTransform(transform);
        if (!shouldRender(x, y, z, transform)) return;
        beginEntityTransform(x, y, z, yaw, transform);
        try {
            Aero_MeshRenderer.renderModel(model, transform.offsetX, transform.offsetY, transform.offsetZ,
                0f, brightness, options);
        } finally {
            GL11.glPopMatrix();
        }
    }

    public static void renderAtRest(Aero_MeshModel model, Entity entity,
                                    double x, double y, double z,
                                    float yaw, float partialTick,
                                    Aero_EntityModelTransform transform) {
        renderAtRest(model, x, y, z, yaw, entity.getBrightnessAtEyes(partialTick), transform);
    }

    public static void renderAtRest(Aero_MeshModel model,
                                    double x, double y, double z,
                                    float yaw, float brightness,
                                    Aero_EntityModelTransform transform) {
        renderAtRest(model, x, y, z, yaw, brightness, transform, Aero_RenderOptions.DEFAULT);
    }

    public static void renderAtRest(Aero_MeshModel model,
                                    double x, double y, double z,
                                    float yaw, float brightness,
                                    Aero_EntityModelTransform transform,
                                    Aero_RenderOptions options) {
        requireTransform(transform);
        if (!shouldRender(x, y, z, transform)) return;
        beginEntityTransform(x, y, z, yaw, transform);
        try {
            Aero_MeshRenderer.renderModelAtRest(model,
                transform.offsetX, transform.offsetY, transform.offsetZ,
                0f, brightness, options);
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
        renderAnimated(model, state, x, y, z, yaw, entity.getBrightnessAtEyes(partialTick), partialTick, transform);
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
        renderAnimated(model, state, x, y, z, yaw, brightness, partialTick,
            transform, Aero_RenderOptions.DEFAULT);
    }

    public static void renderAnimated(Aero_MeshModel model,
                                      Aero_AnimationPlayback state,
                                      double x, double y, double z,
                                      float yaw, float brightness, float partialTick,
                                      Aero_EntityModelTransform transform,
                                      Aero_RenderOptions options) {
        renderAnimated(model, state, x, y, z, yaw, brightness, partialTick,
            transform, options, null);
    }

    public static void renderAnimated(Aero_MeshModel model,
                                      Aero_AnimationPlayback state,
                                      double x, double y, double z,
                                      float yaw, float brightness, float partialTick,
                                      Aero_EntityModelTransform transform,
                                      Aero_RenderOptions options,
                                      Aero_ProceduralPose proceduralPose) {
        if (state == null) throw new IllegalArgumentException("state must not be null");
        renderAnimated(model, state.getBundle(), state.getDef(), state,
            x, y, z, yaw, brightness, partialTick, transform, options, proceduralPose);
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
        renderAnimated(model, bundle, def, state, x, y, z, yaw, entity.getBrightnessAtEyes(partialTick), partialTick, transform);
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
        renderAnimated(model, bundle, def, state, x, y, z, yaw, brightness, partialTick,
            transform, Aero_RenderOptions.DEFAULT);
    }

    public static void renderAnimated(Aero_MeshModel model,
                                      Aero_AnimationBundle bundle,
                                      Aero_AnimationDefinition def,
                                      Aero_AnimationPlayback state,
                                      double x, double y, double z,
                                      float yaw, float brightness, float partialTick,
                                      Aero_EntityModelTransform transform,
                                      Aero_RenderOptions options) {
        renderAnimated(model, bundle, def, state, x, y, z, yaw, brightness, partialTick,
            transform, options, null);
    }

    public static void renderAnimated(Aero_MeshModel model,
                                      Aero_AnimationBundle bundle,
                                      Aero_AnimationDefinition def,
                                      Aero_AnimationPlayback state,
                                      double x, double y, double z,
                                      float yaw, float brightness, float partialTick,
                                      Aero_EntityModelTransform transform,
                                      Aero_RenderOptions options,
                                      Aero_ProceduralPose proceduralPose) {
        requireTransform(transform);
        if (!shouldRender(x, y, z, transform)) return;
        beginEntityTransform(x, y, z, yaw, transform);
        try {
            Aero_MeshRenderer.renderAnimated(model, bundle, def, state,
                transform.offsetX, transform.offsetY, transform.offsetZ,
                brightness, partialTick, options, proceduralPose);
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

    private static void requireSpec(Aero_ModelSpec spec) {
        if (spec == null) throw new IllegalArgumentException("spec must not be null");
    }

    private static void requireOptions(Aero_RenderOptions options) {
        if (options == null) throw new IllegalArgumentException("options must not be null");
    }

    private static boolean shouldRender(double x, double y, double z,
                                        Aero_EntityModelTransform transform) {
        return Aero_RenderDistance.shouldRenderRelative(x, y, z,
            transform.cullingRadius, transform.maxRenderDistance);
    }
}
