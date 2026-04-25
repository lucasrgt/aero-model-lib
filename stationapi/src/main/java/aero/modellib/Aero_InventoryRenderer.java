package aero.modellib;

import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.block.BlockRenderManager;
import org.lwjgl.opengl.GL11;

/**
 * Centralized inventory thumbnail rendering for all Aero model types
 * (StationAPI/Yarn port).
 *
 * Performance:
 *   - Bounding box read from the model's lazy cache (Aero_*Model.getBounds()).
 *   - Mesh models iterate the cached NamedGroup[] (no HashMap iterator alloc).
 */
public class Aero_InventoryRenderer {

    private static final float SLOT_SCALE = 1.3f;
    private static final float Y_NUDGE = 0.12f;

    /** Renders a Blockbench JSON model as an inventory thumbnail. */
    public static void render(BlockRenderManager rb, Aero_JsonModel model) {
        float[] b = model.getBounds();
        float maxDim = Math.max(b[3] - b[0], Math.max(b[4] - b[1], b[5] - b[2]));
        float cx = (b[0] + b[3]) * 0.5f;
        float cy = (b[1] + b[4]) * 0.5f;
        float cz = (b[2] + b[5]) * 0.5f;

        beginSlot(maxDim);
        Aero_JsonModelRenderer.renderModel(model, -cx, -cy, -cz, 0, 1.0f);
        endSlot();
    }

    /** Renders an OBJ mesh model as an inventory thumbnail. */
    public static void render(BlockRenderManager rb, Aero_MeshModel model) {
        float[] b = model.getBounds();
        float maxDim = Math.max(b[3] - b[0], Math.max(b[4] - b[1], b[5] - b[2]));
        float cx = (b[0] + b[3]) * 0.5f;
        float cy = (b[1] + b[4]) * 0.5f;
        float cz = (b[2] + b[5]) * 0.5f;

        beginSlot(maxDim);
        GL11.glTranslatef(-cx, -cy, -cz);
        GL11.glDisable(GL11.GL_CULL_FACE);
        GL11.glDisable(GL11.GL_LIGHTING);

        Tessellator tess = Tessellator.INSTANCE;
        Aero_MeshRenderer.drawGroupsForInventory(tess, model.groups, model.scale);

        Aero_MeshModel.NamedGroup[] entries = model.getNamedGroupArray();
        for (int e = 0; e < entries.length; e++) {
            Aero_MeshRenderer.drawGroupsForInventory(tess, entries[e].tris, model.scale);
        }

        GL11.glEnable(GL11.GL_LIGHTING);
        GL11.glEnable(GL11.GL_CULL_FACE);
        endSlot();
    }

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
