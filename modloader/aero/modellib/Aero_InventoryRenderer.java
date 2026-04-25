package aero.modellib;

import net.minecraft.src.*;
import org.lwjgl.opengl.GL11;

/**
 * Centralized inventory thumbnail rendering for all Aero model types.
 *
 * Handles the common GL setup (scale to fit slot, center, nudge) and
 * delegates the actual draw call to the appropriate renderer.
 *
 * Performance:
 *   - Bounding box is read from the model's lazy cache (Aero_*Model.getBounds())
 *     so per-icon work is constant — no per-render iteration over geometry.
 *   - Mesh models iterate the cached NamedGroup[] (no HashMap iterator alloc).
 */
public class Aero_InventoryRenderer {

    private static final float SLOT_SCALE = 1.3f;
    private static final float Y_NUDGE = 0.12f;

    /**
     * Renders a Blockbench JSON model as an inventory thumbnail.
     */
    public static void render(RenderBlocks rb, Aero_JsonModel model) {
        float[] b = model.getBounds();
        float maxDim = Math.max(b[3] - b[0], Math.max(b[4] - b[1], b[5] - b[2]));
        float cx = (b[0] + b[3]) * 0.5f;
        float cy = (b[1] + b[4]) * 0.5f;
        float cz = (b[2] + b[5]) * 0.5f;

        beginSlot(maxDim);
        Aero_JsonModelRenderer.renderModel(model, -cx, -cy, -cz, 0, 1.0f);
        endSlot();
    }

    /**
     * Renders an OBJ mesh model as an inventory thumbnail.
     * Draws all geometry: static groups + named groups at rest pose.
     */
    public static void render(RenderBlocks rb, Aero_MeshModel model) {
        float[] b = model.getBounds();
        float maxDim = Math.max(b[3] - b[0], Math.max(b[4] - b[1], b[5] - b[2]));
        float cx = (b[0] + b[3]) * 0.5f;
        float cy = (b[1] + b[4]) * 0.5f;
        float cz = (b[2] + b[5]) * 0.5f;

        beginSlot(maxDim);
        GL11.glTranslatef(-cx, -cy, -cz);
        beginMeshState();

        Tessellator tess = Tessellator.instance;
        Aero_MeshRenderer.drawGroupsForInventory(tess, model.groups, model.invScale);

        Aero_MeshModel.NamedGroup[] entries = model.getNamedGroupArray();
        for (int e = 0; e < entries.length; e++) {
            Aero_MeshRenderer.drawGroupsForInventory(tess, entries[e].tris, model.invScale);
        }

        endMeshState();
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

    private static void beginMeshState() {
        GL11.glPushAttrib(GL11.GL_ENABLE_BIT | GL11.GL_DEPTH_BUFFER_BIT | GL11.GL_CURRENT_BIT);
        GL11.glDisable(GL11.GL_CULL_FACE);
        GL11.glDisable(GL11.GL_LIGHTING);
        GL11.glDisable(GL11.GL_BLEND);
        GL11.glDisable(GL11.GL_ALPHA_TEST);
        GL11.glDepthMask(true);
        GL11.glColor4f(1f, 1f, 1f, 1f);
    }

    private static void endMeshState() {
        GL11.glPopAttrib();
    }
}
