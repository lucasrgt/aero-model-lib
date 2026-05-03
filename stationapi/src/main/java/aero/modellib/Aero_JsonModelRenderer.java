package aero.modellib;

import net.minecraft.client.render.Tessellator;
import org.lwjgl.opengl.GL11;

import aero.modellib.model.Aero_JsonModel;
import aero.modellib.util.Aero_Profiler;

/**
 * AeroModel JSON Renderer (StationAPI/Yarn port).
 * Same algorithm as the ModLoader version, with Yarn-mapped Tessellator API.
 *
 * Performance:
 *   - Coordinates and UVs are pre-baked on Aero_JsonModel construction.
 *   - Faces are grouped by direction, so normal/color changes are capped at 6
 *     per draw instead of once per cuboid face.
 */
public class Aero_JsonModelRenderer {

    public static void renderModel(Aero_JsonModel model, double x, double y, double z, float rotation, float brightness) {
        Aero_Profiler.start("aero.json.render");
        try {
            Tessellator tessellator = Tessellator.INSTANCE;
            GL11.glPushMatrix();
            try {
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
                float[][][] quads = model.quadsByFace;
                drawQuadGroup(tessellator, quads[Aero_JsonModel.FACE_DOWN],  0.0F, -1.0F,  0.0F, cBottom);
                drawQuadGroup(tessellator, quads[Aero_JsonModel.FACE_UP],    0.0F,  1.0F,  0.0F, cTop);
                drawQuadGroup(tessellator, quads[Aero_JsonModel.FACE_NORTH], 0.0F,  0.0F, -1.0F, cNS);
                drawQuadGroup(tessellator, quads[Aero_JsonModel.FACE_SOUTH], 0.0F,  0.0F,  1.0F, cNS);
                drawQuadGroup(tessellator, quads[Aero_JsonModel.FACE_WEST], -1.0F,  0.0F,  0.0F, cEW);
                drawQuadGroup(tessellator, quads[Aero_JsonModel.FACE_EAST],  1.0F,  0.0F,  0.0F, cEW);

                tessellator.draw();
            } finally {
                GL11.glPopMatrix();
            }
        } finally {
            Aero_Profiler.end("aero.json.render");
        }
    }

    private static void drawQuadGroup(Tessellator tessellator, float[][] quads,
                                      float nx, float ny, float nz, float color) {
        if (quads.length == 0) return;
        tessellator.normal(nx, ny, nz);
        tessellator.color(color, color, color);
        for (int i = 0; i < quads.length; i++) {
            float[] q = quads[i];
            tessellator.vertex(q[0],  q[1],  q[2],  q[3],  q[4]);
            tessellator.vertex(q[5],  q[6],  q[7],  q[8],  q[9]);
            tessellator.vertex(q[10], q[11], q[12], q[13], q[14]);
            tessellator.vertex(q[15], q[16], q[17], q[18], q[19]);
        }
    }
}
