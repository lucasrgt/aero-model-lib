package aero.modellib;

import net.minecraft.client.render.Tessellator;
import org.lwjgl.opengl.GL11;

/**
 * AeroModel JSON Renderer (StationAPI/Yarn port).
 * Same algorithm as the ModLoader version, with Yarn-mapped Tessellator API.
 *
 * Performance:
 *   - 1/scale and 1/textureSize precomputed once per draw.
 *   - Per-face normal/color is unavoidable in quads mode (each face has a
 *     different brightness factor).
 */
public class Aero_JsonModelRenderer {

    public static void renderModel(Aero_JsonModel model, double x, double y, double z, float rotation, float brightness) {
        Tessellator tessellator = Tessellator.INSTANCE;
        GL11.glPushMatrix();
        GL11.glTranslated(x, y, z);

        if (rotation != 0) {
            GL11.glTranslatef(0.5f, 0.5f, 0.5f);
            GL11.glRotatef(rotation, 0.0f, 1.0f, 0.0f);
            GL11.glTranslatef(-0.5f, -0.5f, -0.5f);
        }

        tessellator.startQuads();
        final float cTop    = brightness * 1.0F;
        final float cBottom = brightness * 0.5F;
        final float cNS     = brightness * 0.8F;
        final float cEW     = brightness * 0.6F;
        final float invScale = 1f / model.scale;
        final float invTs    = 1f / model.textureSize;

        for (int i = 0; i < model.elements.length; i++) {
            float[] p = model.elements[i];
            float minX = p[0] * invScale; float minY = p[1] * invScale; float minZ = p[2] * invScale;
            float maxX = p[3] * invScale; float maxY = p[4] * invScale; float maxZ = p[5] * invScale;

            // DOWN
            if (p[6] != -1) {
                tessellator.normal(0.0F, -1.0F, 0.0F);
                tessellator.color(cBottom, cBottom, cBottom);
                float u1 = p[6]*invTs, v1 = p[7]*invTs, u2 = p[8]*invTs, v2 = p[9]*invTs;
                tessellator.vertex(minX, minY, maxZ, u1, v2);
                tessellator.vertex(minX, minY, minZ, u1, v1);
                tessellator.vertex(maxX, minY, minZ, u2, v1);
                tessellator.vertex(maxX, minY, maxZ, u2, v2);
            }
            // UP
            if (p[10] != -1) {
                tessellator.normal(0.0F, 1.0F, 0.0F);
                tessellator.color(cTop, cTop, cTop);
                float u1 = p[10]*invTs, v1 = p[11]*invTs, u2 = p[12]*invTs, v2 = p[13]*invTs;
                tessellator.vertex(maxX, maxY, maxZ, u2, v2);
                tessellator.vertex(maxX, maxY, minZ, u2, v1);
                tessellator.vertex(minX, maxY, minZ, u1, v1);
                tessellator.vertex(minX, maxY, maxZ, u1, v2);
            }
            // NORTH
            if (p[14] != -1) {
                tessellator.normal(0.0F, 0.0F, -1.0F);
                tessellator.color(cNS, cNS, cNS);
                float u1 = p[14]*invTs, v1 = p[15]*invTs, u2 = p[16]*invTs, v2 = p[17]*invTs;
                tessellator.vertex(minX, maxY, minZ, u2, v1);
                tessellator.vertex(maxX, maxY, minZ, u1, v1);
                tessellator.vertex(maxX, minY, minZ, u1, v2);
                tessellator.vertex(minX, minY, minZ, u2, v2);
            }
            // SOUTH
            if (p[18] != -1) {
                tessellator.normal(0.0F, 0.0F, 1.0F);
                tessellator.color(cNS, cNS, cNS);
                float u1 = p[18]*invTs, v1 = p[19]*invTs, u2 = p[20]*invTs, v2 = p[21]*invTs;
                tessellator.vertex(minX, maxY, maxZ, u1, v1);
                tessellator.vertex(minX, minY, maxZ, u1, v2);
                tessellator.vertex(maxX, minY, maxZ, u2, v2);
                tessellator.vertex(maxX, maxY, maxZ, u2, v1);
            }
            // WEST
            if (p[22] != -1) {
                tessellator.normal(-1.0F, 0.0F, 0.0F);
                tessellator.color(cEW, cEW, cEW);
                float u1 = p[22]*invTs, v1 = p[23]*invTs, u2 = p[24]*invTs, v2 = p[25]*invTs;
                tessellator.vertex(minX, maxY, maxZ, u2, v1);
                tessellator.vertex(minX, maxY, minZ, u1, v1);
                tessellator.vertex(minX, minY, minZ, u1, v2);
                tessellator.vertex(minX, minY, maxZ, u2, v2);
            }
            // EAST
            if (p[26] != -1) {
                tessellator.normal(1.0F, 0.0F, 0.0F);
                tessellator.color(cEW, cEW, cEW);
                float u1 = p[26]*invTs, v1 = p[27]*invTs, u2 = p[28]*invTs, v2 = p[29]*invTs;
                tessellator.vertex(maxX, minY, maxZ, u1, v2);
                tessellator.vertex(maxX, minY, minZ, u2, v2);
                tessellator.vertex(maxX, maxY, minZ, u2, v1);
                tessellator.vertex(maxX, maxY, maxZ, u1, v1);
            }
        }

        tessellator.draw();
        GL11.glPopMatrix();
    }
}
