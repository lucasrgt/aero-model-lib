package aero.modellib;

import net.minecraft.src.*;
import org.lwjgl.opengl.GL11;

/**
 * Centralized inventory thumbnail rendering for all Aero model types.
 *
 * Handles the common GL setup (scale to fit slot, center, nudge) and
 * delegates the actual draw call to the appropriate renderer.
 * The caller (RenderItem.drawItemIntoGui) already applies isometric
 * rotation — we just normalize the model into slot space.
 */
public class Aero_InventoryRenderer {

    private static final float SLOT_SCALE = 1.3f;
    private static final float Y_NUDGE = 0.12f;

    /**
     * Renders a Blockbench JSON model as an inventory thumbnail.
     */
    public static void render(RenderBlocks rb, Aero_JsonModel model) {
        // Compute bounding box in block units
        float minX = 999, minY = 999, minZ = 999;
        float maxX = -999, maxY = -999, maxZ = -999;
        for (float[] p : model.elements) {
            float x0 = p[0] / model.scale, y0 = p[1] / model.scale, z0 = p[2] / model.scale;
            float x1 = p[3] / model.scale, y1 = p[4] / model.scale, z1 = p[5] / model.scale;
            if (x0 < minX) minX = x0; if (y0 < minY) minY = y0; if (z0 < minZ) minZ = z0;
            if (x1 > maxX) maxX = x1; if (y1 > maxY) maxY = y1; if (z1 > maxZ) maxZ = z1;
        }

        float maxDim = Math.max(maxX - minX, Math.max(maxY - minY, maxZ - minZ));
        float cx = (minX + maxX) / 2.0f;
        float cy = (minY + maxY) / 2.0f;
        float cz = (minZ + maxZ) / 2.0f;

        beginSlot(maxDim);
        Aero_JsonModelRenderer.renderModel(model, -cx, -cy, -cz, 0, 1.0f);
        endSlot();
    }

    /**
     * Renders an OBJ mesh model as an inventory thumbnail.
     * Draws all geometry: static groups + named groups at rest pose.
     */
    public static void render(RenderBlocks rb, Aero_MeshModel model) {
        float sc = model.scale;
        float minX = Float.MAX_VALUE, minY = Float.MAX_VALUE, minZ = Float.MAX_VALUE;
        float maxX = -Float.MAX_VALUE, maxY = -Float.MAX_VALUE, maxZ = -Float.MAX_VALUE;

        // Bounding box over static groups
        for (int g = 0; g < 4; g++) {
            float[][] tris = model.groups[g];
            for (int i = 0; i < tris.length; i++) {
                float[] t = tris[i];
                for (int v = 0; v < 3; v++) {
                    float vx = t[v * 5] / sc, vy = t[v * 5 + 1] / sc, vz = t[v * 5 + 2] / sc;
                    if (vx < minX) minX = vx; if (vx > maxX) maxX = vx;
                    if (vy < minY) minY = vy; if (vy > maxY) maxY = vy;
                    if (vz < minZ) minZ = vz; if (vz > maxZ) maxZ = vz;
                }
            }
        }
        // Bounding box over named groups
        java.util.Iterator bit = model.namedGroups.values().iterator();
        while (bit.hasNext()) {
            float[][][] ng = (float[][][]) bit.next();
            for (int g = 0; g < 4; g++) {
                float[][] tris = ng[g];
                for (int i = 0; i < tris.length; i++) {
                    float[] t = tris[i];
                    for (int v = 0; v < 3; v++) {
                        float vx = t[v * 5] / sc, vy = t[v * 5 + 1] / sc, vz = t[v * 5 + 2] / sc;
                        if (vx < minX) minX = vx; if (vx > maxX) maxX = vx;
                        if (vy < minY) minY = vy; if (vy > maxY) maxY = vy;
                        if (vz < minZ) minZ = vz; if (vz > maxZ) maxZ = vz;
                    }
                }
            }
        }

        float maxDim = Math.max(maxX - minX, Math.max(maxY - minY, maxZ - minZ));
        float cx = (minX + maxX) / 2.0f;
        float cy = (minY + maxY) / 2.0f;
        float cz = (minZ + maxZ) / 2.0f;

        beginSlot(maxDim);
        GL11.glTranslatef(-cx, -cy, -cz);
        GL11.glDisable(GL11.GL_CULL_FACE);
        GL11.glDisable(GL11.GL_LIGHTING);

        Tessellator tess = Tessellator.instance;
        Aero_MeshRenderer.drawGroupsForInventory(tess, model.groups, sc);

        java.util.Iterator it = model.namedGroups.values().iterator();
        while (it.hasNext()) {
            float[][][] ng = (float[][][]) it.next();
            Aero_MeshRenderer.drawGroupsForInventory(tess, ng, sc);
        }

        GL11.glEnable(GL11.GL_LIGHTING);
        GL11.glEnable(GL11.GL_CULL_FACE);
        endSlot();
    }

    // -- GL setup shared by all model types --

    private static void beginSlot(float maxDim) {
        GL11.glPushMatrix();
        float scale = SLOT_SCALE / maxDim;
        GL11.glTranslatef(0.0f, Y_NUDGE, 0.0f);
        GL11.glScalef(scale, scale, scale);
    }

    private static void endSlot() {
        GL11.glPopMatrix();
    }
}
