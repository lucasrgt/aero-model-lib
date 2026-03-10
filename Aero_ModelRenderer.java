package aero.modellib;

import net.minecraft.src.*;
import org.lwjgl.opengl.GL11;

/**
 * AeroModel Renderer API by lucasrgt - aerocoding.dev
 * Handles high-performance rendering of Aero_Models.
 */
public class Aero_ModelRenderer {

    public static void renderModel(Aero_Model model, double x, double y, double z, float rotation, float brightness) {
        Tessellator tessellator = Tessellator.instance;
        GL11.glPushMatrix();
        GL11.glTranslated(x, y, z);
        
        if (rotation != 0) {
            GL11.glTranslatef(0.5f, 0.5f, 0.5f);
            GL11.glRotatef(rotation, 0.0f, 1.0f, 0.0f);
            GL11.glTranslatef(-0.5f, -0.5f, -0.5f);
        }

        tessellator.startDrawingQuads();
        float cTop = brightness * 1.0F;
        float cBottom = brightness * 0.5F;
        float cNS = brightness * 0.8F;
        float cEW = brightness * 0.6F;
        float ts = model.textureSize;

        for (int i = 0; i < model.elements.length; i++) {
            float[] p = model.elements[i];
            float minX = p[0] / model.scale; float minY = p[1] / model.scale; float minZ = p[2] / model.scale;
            float maxX = p[3] / model.scale; float maxY = p[4] / model.scale; float maxZ = p[5] / model.scale;
            
            // DOWN
            if (p[6] != -1) {
                tessellator.setNormal(0.0F, -1.0F, 0.0F);
                tessellator.setColorOpaque_F(cBottom, cBottom, cBottom);
                float u1 = p[6]/ts, v1 = p[7]/ts, u2 = p[8]/ts, v2 = p[9]/ts;
                tessellator.addVertexWithUV(minX, minY, maxZ, u1, v2);
                tessellator.addVertexWithUV(minX, minY, minZ, u1, v1);
                tessellator.addVertexWithUV(maxX, minY, minZ, u2, v1);
                tessellator.addVertexWithUV(maxX, minY, maxZ, u2, v2);
            }
            // UP
            if (p[10] != -1) {
                tessellator.setNormal(0.0F, 1.0F, 0.0F);
                tessellator.setColorOpaque_F(cTop, cTop, cTop);
                float u1 = p[10]/ts, v1 = p[11]/ts, u2 = p[12]/ts, v2 = p[13]/ts;
                tessellator.addVertexWithUV(maxX, maxY, maxZ, u2, v2);
                tessellator.addVertexWithUV(maxX, maxY, minZ, u2, v1);
                tessellator.addVertexWithUV(minX, maxY, minZ, u1, v1);
                tessellator.addVertexWithUV(minX, maxY, maxZ, u1, v2);
            }
            // NORTH
            if (p[14] != -1) {
                tessellator.setNormal(0.0F, 0.0F, -1.0F);
                tessellator.setColorOpaque_F(cNS, cNS, cNS);
                float u1 = p[14]/ts, v1 = p[15]/ts, u2 = p[16]/ts, v2 = p[17]/ts;
                tessellator.addVertexWithUV(minX, maxY, minZ, u2, v1);
                tessellator.addVertexWithUV(maxX, maxY, minZ, u1, v1);
                tessellator.addVertexWithUV(maxX, minY, minZ, u1, v2);
                tessellator.addVertexWithUV(minX, minY, minZ, u2, v2);
            }
            // SOUTH
            if (p[18] != -1) {
                tessellator.setNormal(0.0F, 0.0F, 1.0F);
                tessellator.setColorOpaque_F(cNS, cNS, cNS);
                float u1 = p[18]/ts, v1 = p[19]/ts, u2 = p[20]/ts, v2 = p[21]/ts;
                tessellator.addVertexWithUV(minX, maxY, maxZ, u1, v1);
                tessellator.addVertexWithUV(minX, minY, maxZ, u1, v2);
                tessellator.addVertexWithUV(maxX, minY, maxZ, u2, v2);
                tessellator.addVertexWithUV(maxX, maxY, maxZ, u2, v1);
            }
            // WEST
            if (p[22] != -1) {
                tessellator.setNormal(-1.0F, 0.0F, 0.0F);
                tessellator.setColorOpaque_F(cEW, cEW, cEW);
                float u1 = p[22]/ts, v1 = p[23]/ts, u2 = p[24]/ts, v2 = p[25]/ts;
                tessellator.addVertexWithUV(minX, maxY, maxZ, u2, v1);
                tessellator.addVertexWithUV(minX, maxY, minZ, u1, v1);
                tessellator.addVertexWithUV(minX, minY, minZ, u1, v2);
                tessellator.addVertexWithUV(minX, minY, maxZ, u2, v2);
            }
            // EAST
            if (p[26] != -1) {
                tessellator.setNormal(1.0F, 0.0F, 0.0F);
                tessellator.setColorOpaque_F(cEW, cEW, cEW);
                float u1 = p[26]/ts, v1 = p[27]/ts, u2 = p[28]/ts, v2 = p[29]/ts;
                tessellator.addVertexWithUV(maxX, minY, maxZ, u1, v2);
                tessellator.addVertexWithUV(maxX, minY, minZ, u2, v2);
                tessellator.addVertexWithUV(maxX, maxY, minZ, u2, v1);
                tessellator.addVertexWithUV(maxX, maxY, maxZ, u1, v1);
            }
        }
        
        tessellator.draw();
        GL11.glPopMatrix();
    }

    /**
     * Inventory Thumbnail Rendering with the AeroModel API.
     * Auto-scales the model to fit within 1x1.
     */
    public static void renderInventory(RenderBlocks rb, Aero_Model model, float metadata) {
        GL11.glPushMatrix();
        
        // 1. Find the center and actual size
        float minX = 999, minY = 999, minZ = 999;
        float maxX = -999, maxY = -999, maxZ = -999;
        for (float[] p : model.elements) {
            minX = Math.min(minX, p[0]); minY = Math.min(minY, p[1]); minZ = Math.min(minZ, p[2]);
            maxX = Math.max(maxX, p[3]); maxY = Math.max(maxY, p[4]); maxZ = Math.max(maxZ, p[5]);
        }
        
        float sizeX = (maxX - minX) / model.scale;
        float sizeY = (maxY - minY) / model.scale;
        float sizeZ = (maxZ - minZ) / model.scale;
        float maxDim = Math.max(sizeX, Math.max(sizeY, sizeZ));
        
        // 2. Absolute centering at the rotation point
        float centerX = (minX + maxX) / 2.0F / model.scale;
        float centerY = (minY + maxY) / 2.0F / model.scale;
        float centerZ = (minZ + maxZ) / 2.0F / model.scale;

        // 3. Scale and Rotation adjustment (Order: Scale -> Rotate -> Translate)
        // We use 0.7 to ensure rotated corners don't clip the slot
        float scale = 0.7F / maxDim;
        GL11.glScalef(scale, scale, scale);
        
        GL11.glRotatef(30.0F, 1.0F, 0.0F, 0.0F);
        GL11.glRotatef(45.0F, 0.0F, 1.0F, 0.0F);
        
        // Ensure lighting works even with the "squeezed" model
        GL11.glEnable(32826); // GL_RESCALE_NORMAL_EXT
        
        renderModel(model, -centerX, -centerY, -centerZ, 0, 1.0f);
        
        GL11.glPopMatrix();
    }
}
