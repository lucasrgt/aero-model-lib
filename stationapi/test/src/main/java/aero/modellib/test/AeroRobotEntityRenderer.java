package aero.modellib.test;

import aero.modellib.Aero_EntityModelRenderer;
import aero.modellib.Aero_TextureBinder;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.entity.Entity;
import org.lwjgl.opengl.GL11;

import aero.modellib.model.Aero_ModelSpec;
import aero.modellib.render.Aero_RenderOptions;

/**
 * Renderer for {@link AeroRobotEntity}. Drives the model via
 * {@link Aero_EntityModelRenderer} and tints the mesh red as the robot
 * approaches overheat using explicit render options.
 */
public class AeroRobotEntityRenderer extends EntityRenderer {

    // Pivot the model so its centre (in the OBJ's local x/z = 8 px) lands
    // on the entity origin instead of being offset by the OBJ-coordinate
    // bias.
    private static final Aero_ModelSpec MODEL =
        Aero_ModelSpec.mesh("/models/Robot.obj")
            .texture("/models/aerotest_robot.png")
            .animations(AeroRobotEntity.ANIMATION)
            .offset(-0.5f, 0f, -0.5f)
            .animatedDistance(AeroTestMod.demoAnimatedLodDistance())
            .build();

    public AeroRobotEntityRenderer() {
        shadowRadius = 0.4f;
    }

    @Override
    public void render(Entity entity, double x, double y, double z,
                       float yaw, float partialTick) {
        AeroRobotEntity bot = (AeroRobotEntity) entity;
        Aero_TextureBinder.bind(MODEL.getTexturePath());

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

        // Sample brightness at the entity's actual position — using
        // brightnessAt (not brightnessAbove). brightnessAbove walks to the
        // column top which gives full sky brightness for entities under
        // water / ice / glass and makes them visibly glow. brightnessAt
        // reads light directly at (x, y_head, z) so submerged mobs darken
        // correctly.
        int ex = (int) Math.floor(entity.x);
        int ey = (int) Math.floor(entity.y);
        int ez = (int) Math.floor(entity.z);
        float brightness = AeroLight.brightnessAt(entity.world, ex, ey, ez);

        Aero_RenderOptions renderOptions = Aero_RenderOptions.tint(1f, g, b);
        Aero_EntityModelRenderer.render(MODEL, bot.animState,
            x, y, z, yaw, brightness, partialTick, renderOptions);

        // Reset the GL color register too — defensive against any caller
        // that reads it before issuing its own tess.color().
        GL11.glColor4f(1f, 1f, 1f, 1f);
    }
}
