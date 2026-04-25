package aero.modellib.test;

import aero.modellib.Aero_EntityModelRenderer;
import aero.modellib.Aero_EntityModelTransform;
import aero.modellib.Aero_MeshModel;
import aero.modellib.Aero_MeshRenderer;
import aero.modellib.Aero_ObjLoader;
import aero.modellib.Aero_RenderDistance;
import aero.modellib.Aero_RenderLod;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.entity.Entity;
import org.lwjgl.opengl.GL11;

/**
 * Renderer for {@link AeroRobotEntity}. Drives the model via
 * {@link Aero_EntityModelRenderer} and tints the mesh red as the robot
 * approaches overheat using the lib's {@link Aero_MeshRenderer#setTint}.
 */
public class AeroRobotEntityRenderer extends EntityRenderer {

    private static final Aero_MeshModel MODEL =
        Aero_ObjLoader.load("/models/Robot.obj");

    // Pivot the model so its centre (in the OBJ's local x/z = 8 px) lands
    // on the entity origin instead of being offset by the OBJ-coordinate
    // bias.
    private static final Aero_EntityModelTransform TRANSFORM =
        Aero_EntityModelTransform.DEFAULT
            .withOffset(-0.5f, 0f, -0.5f);

    public AeroRobotEntityRenderer() {
        shadowRadius = 0.4f;
    }

    @Override
    public void render(Entity entity, double x, double y, double z,
                       float yaw, float partialTick) {
        AeroRobotEntity bot = (AeroRobotEntity) entity;
        Aero_RenderLod lod = Aero_RenderDistance.lodRelative(
            x, y, z, 2d, AeroTestMod.DEMO_ANIMATED_LOD_DISTANCE_BLOCKS);
        if (!lod.shouldRender()) return;

        bindTexture("/models/aerotest_robot.png");

        // Lerp the tint white → red as the robot heats up. Drop the green
        // and blue channels rather than boosting red so we never go over 1.0
        // and clip — keeps the chassis texture readable at peak overheat.
        float overheat = bot.getInterpolatedOverheat(partialTick);
        // Default heat tint: drop both G and B from 1.0 down to ~0.35 so the
        // chassis reads as red without over-saturating the texture.
        float baseGB = 1f - 0.65f * overheat;
        float g = baseGB;
        float b = baseGB;
        // During meltdown, pulse G from baseGB up to 1.0 while keeping B low
        // — that swings the tint between red (1, low, low) and yellow
        // (1, 1, low) at ~3 Hz. Boosting both channels equally would have
        // looked pink/washed-out instead of yellow.
        if (bot.isMeltdown()) {
            float t = (bot.age + partialTick) * (6f * (float) Math.PI / 20f);
            float pulse = 0.5f + 0.5f * (float) Math.sin(t);
            g = baseGB + (1.0f - baseGB) * pulse;
        }

        // Sample brightness at the entity's feet column instead of relying
        // on entity.getBrightnessAtEyes — that vanilla formula computes
        // y_eye = floor(y + boxHeight*0.66 - standingEyeHeight) which goes
        // BELOW the feet (and into the opaque ground block) whenever the
        // mob's eye height is taller than ~66% of its box height. AeroLight
        // already does the column-top fallback for water/lava chunks too.
        int ex = (int) Math.floor(entity.x);
        int ey = (int) Math.floor(entity.y);
        int ez = (int) Math.floor(entity.z);
        float brightness = AeroLight.brightnessAbove(entity.world, ex, ey, ez);

        Aero_MeshRenderer.setTint(1f, g, b);
        try {
            if (lod.shouldAnimate()) {
                Aero_EntityModelRenderer.renderAnimated(
                    MODEL, bot.animState,
                    x, y, z, yaw, brightness, partialTick, TRANSFORM);
            } else {
                Aero_EntityModelRenderer.renderAtRest(
                    MODEL, x, y, z, yaw, brightness, TRANSFORM);
            }
        } finally {
            Aero_MeshRenderer.resetTint();
        }

        // Reset the GL color register too — defensive against any caller
        // that reads it before issuing its own tess.color().
        GL11.glColor4f(1f, 1f, 1f, 1f);
    }
}
