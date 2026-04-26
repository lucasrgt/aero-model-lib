package aero.modellib;

import net.minecraft.src.*;
import org.lwjgl.opengl.GL11;

/**
 * AeroModel Renderer API by lucasrgt - aerocoding.dev
 * Handles high-performance rendering of Aero_JsonModels.
 *
 * Performance notes:
 *   - Coordinates and UVs are pre-baked on Aero_JsonModel construction.
 *   - Faces are grouped by direction, so normal/color changes are capped at 6
 *     per draw instead of once per cuboid face.
 */
public class Aero_JsonModelRenderer {

    public static void renderModel(Aero_JsonModel model, double x, double y, double z, float rotation, float brightness) {
        Aero_Profiler.start("aero.json.render");
        try {
            Tessellator tessellator = Tessellator.instance;
            GL11.glPushMatrix();
            GL11.glTranslated(x, y, z);

            if (rotation != 0) {
                GL11.glTranslatef(0.5f, 0.5f, 0.5f);
                GL11.glRotatef(rotation, 0.0f, 1.0f, 0.0f);
                GL11.glTranslatef(-0.5f, -0.5f, -0.5f);
            }

            tessellator.startDrawingQuads();
            final float cTop    = brightness * 1.0F;
            final float cBottom = brightness * 0.5F;
            final float cNS     = brightness * 0.8F;
            final float cEW     = brightness * 0.6F;
            float[][][] quads = model.quadsByFace;
            drawQuadGroup(tessellator, quads[Aero_JsonModel.FACE_DOWN],  0.0F, -1.0F,  0.0F, cBottom);
            drawQuadGroup(tessellator, quads[Aero_JsonModel.FACE_UP],    0.0F,  1.0F,  0.0F, cTop);
            drawQuadGroup(tessellator, quads[Aero_JsonModel.FACE_NORTH], 0.0F,  0.0F, -1.0F, cNS);
            drawQuadGroup(tessellator, quads[Aero_JsonModel.FACE_SOUTH], 0.0F,  0.0F,  1.0F, cNS);
            drawQuadGroup(tessellator, quads[Aero_JsonModel.FACE_WEST], -1.0F,  0.0F,  0.0F, cEW);
            drawQuadGroup(tessellator, quads[Aero_JsonModel.FACE_EAST],  1.0F,  0.0F,  0.0F, cEW);

            tessellator.draw();
            GL11.glPopMatrix();
        } finally {
            Aero_Profiler.end("aero.json.render");
        }
    }

    private static void drawQuadGroup(Tessellator tessellator, float[][] quads,
                                      float nx, float ny, float nz, float color) {
        if (quads.length == 0) return;
        tessellator.setNormal(nx, ny, nz);
        tessellator.setColorOpaque_F(color, color, color);
        for (int i = 0; i < quads.length; i++) {
            float[] q = quads[i];
            tessellator.addVertexWithUV(q[0],  q[1],  q[2],  q[3],  q[4]);
            tessellator.addVertexWithUV(q[5],  q[6],  q[7],  q[8],  q[9]);
            tessellator.addVertexWithUV(q[10], q[11], q[12], q[13], q[14]);
            tessellator.addVertexWithUV(q[15], q[16], q[17], q[18], q[19]);
        }
    }
}
