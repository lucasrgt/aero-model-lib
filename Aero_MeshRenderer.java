package aero.modellib;

import net.minecraft.src.*;
import org.lwjgl.opengl.GL11;

/**
 * AeroMesh Renderer by lucasrgt - aerocoding.dev
 * Renders OBJ models (Aero_MeshModel) using GL_TRIANGLES.
 *
 * Performance:
 *   - Triangles pre-classified into 4 brightness groups at parse time
 *   - setColorOpaque_F called 4× per frame (vs N× in the naive approach)
 *   - Ready for Display Lists: each group can become a glCallList
 *
 * Static geometry usage (TileEntitySpecialRenderer):
 *   Aero_MeshRenderer.renderModel(MODEL, d + ox, d1 + oy, d2 + oz, rotation, brightness);
 *
 * Animated part usage:
 *   // Render static geometry (everything except the named animated group)
 *   Aero_MeshRenderer.renderModel(MODEL, d + ox, d1 + oy, d2 + oz, 0, brightness);
 *   // Render animated group with per-tick angle + partial tick smoothing
 *   float angle = tile.fanAngle + (tile.isActive ? SPEED * partialTick : 0f);
 *   Aero_MeshRenderer.renderGroupRotated(MODEL, "fan",
 *       d + ox, d1 + oy, d2 + oz, brightness,
 *       pivotX, pivotY, pivotZ,   // pivot in model space (block units)
 *       angle, 0, 1, 0);          // angle + axis (Y-axis spin)
 *
 * Inventory usage:
 *   Aero_MeshRenderer.renderInventory(rb, MODEL);
 *
 * NOTE: uses Tessellator with GL_TRIANGLES — only call outside an active
 * startDrawingQuads() block. The TileEntitySpecialRenderer context is safe.
 */
public class Aero_MeshRenderer {

    // -----------------------------------------------------------------------
    // Full model render
    // -----------------------------------------------------------------------

    /**
     * Renders static geometry (triangles not in any named group) with flat lighting.
     *
     * @param brightness  base brightness (0.0–1.0), from getLightBrightness()
     */
    public static void renderModel(Aero_MeshModel model, double x, double y, double z,
                                    float rotation, float brightness) {
        Tessellator tess = Tessellator.instance;
        GL11.glPushMatrix();
        GL11.glTranslated(x, y, z);
        applyRotation(rotation);
        GL11.glDisable(GL11.GL_CULL_FACE);
        GL11.glDisable(GL11.GL_LIGHTING);

        drawGroups(tess, model.groups, model.scale, brightness);

        GL11.glEnable(GL11.GL_LIGHTING);
        GL11.glEnable(GL11.GL_CULL_FACE);
        GL11.glPopMatrix();
    }

    /**
     * Renders static geometry with smooth lighting (bilinear world sample above structure).
     *
     * @param world   current world
     * @param ox,oz   XZ world origin of the structure
     * @param topY    world Y above the structure top (e.g. originY + structureHeight)
     */
    public static void renderModel(Aero_MeshModel model, double x, double y, double z,
                                    float rotation, World world, int ox, int topY, int oz) {
        Tessellator tess = Tessellator.instance;
        GL11.glPushMatrix();
        GL11.glTranslated(x, y, z);
        applyRotation(rotation);
        GL11.glDisable(GL11.GL_CULL_FACE);
        GL11.glDisable(GL11.GL_LIGHTING);

        drawGroupsSmooth(tess, model.groups, model.scale, world, ox, topY, oz);

        GL11.glEnable(GL11.GL_LIGHTING);
        GL11.glEnable(GL11.GL_CULL_FACE);
        GL11.glPopMatrix();
    }

    // -----------------------------------------------------------------------
    // Named group render (for animated parts)
    // -----------------------------------------------------------------------

    /**
     * Draws a named group into the current GL matrix, with flat lighting.
     * Does NOT push/pop matrix — the caller is responsible for all GL transforms.
     * Use this inside a glPushMatrix / glPopMatrix block where you have already
     * applied translation and rotation for the animated part.
     *
     * @param groupName  OBJ object/group name (e.g. "fan", "piston", "gear")
     * @param brightness base brightness (0.0–1.0)
     */
    public static void renderGroup(Aero_MeshModel model, String groupName, float brightness) {
        float[][][] ng = (float[][][]) model.namedGroups.get(groupName);
        if (ng == null) return;
        Tessellator tess = Tessellator.instance;
        drawGroups(tess, ng, model.scale, brightness);
    }

    /**
     * Renders a named group with a rotation around a pivot point in model space.
     * Handles the full GL setup: push, translate to world position, apply rotation
     * around the pivot, draw, pop.
     *
     * Typical usage (fan spinning around Y axis):
     *   float angle = tile.fanAngle + (tile.isActive ? 18f * partialTick : 0f);
     *   Aero_MeshRenderer.renderGroupRotated(MODEL, "fan",
     *       d + offsetX, d1 + offsetY, d2 + offsetZ, brightness,
     *       pivotX, pivotY, pivotZ,
     *       angle, 0f, 1f, 0f);
     *
     * @param x, y, z       world position (same as renderModel)
     * @param brightness     base brightness (0.0–1.0)
     * @param pivotX/Y/Z     rotation pivot in model space (block units)
     * @param angle          rotation angle in degrees
     * @param axisX/Y/Z      rotation axis unit vector (e.g. 0,1,0 for Y-axis spin)
     */
    public static void renderGroupRotated(Aero_MeshModel model, String groupName,
                                           double x, double y, double z, float brightness,
                                           float pivotX, float pivotY, float pivotZ,
                                           float angle, float axisX, float axisY, float axisZ) {
        float[][][] ng = (float[][][]) model.namedGroups.get(groupName);
        if (ng == null) return;

        Tessellator tess = Tessellator.instance;
        GL11.glPushMatrix();
        GL11.glTranslated(x, y, z);
        GL11.glTranslatef(pivotX, pivotY, pivotZ);
        GL11.glRotatef(angle, axisX, axisY, axisZ);
        GL11.glTranslatef(-pivotX, -pivotY, -pivotZ);
        GL11.glDisable(GL11.GL_CULL_FACE);
        GL11.glDisable(GL11.GL_LIGHTING);

        drawGroups(tess, ng, model.scale, brightness);

        GL11.glEnable(GL11.GL_LIGHTING);
        GL11.glEnable(GL11.GL_CULL_FACE);
        GL11.glPopMatrix();
    }

    // -----------------------------------------------------------------------
    // Animated render (mini-GeckoLib)
    // -----------------------------------------------------------------------

    /**
     * Renders a complete model with keyframe animation.
     *
     * Renders static geometry and, for each named group in the model,
     * fetches keyframes from the active clip, interpolates position and rotation
     * at the current time, and applies the GL transform before drawing the group.
     *
     * Usage (TileEntitySpecialRenderer):
     * <pre>
     *   Aero_MeshRenderer.renderAnimated(MODEL,
     *       Retronism_TileMyCrusher.BUNDLE,
     *       Retronism_TileMyCrusher.ANIM_DEF,
     *       tile.animState,
     *       d + offsetX, d1 + offsetY, d2 + offsetZ,
     *       brightness, partialTick);
     * </pre>
     *
     * @param model       OBJ model with named groups
     * @param bundle      animation data (loaded .anim.json)
     * @param def         state→clip mapping
     * @param state       per-tile-entity playback state
     * @param x,y,z       world-space position (same origin as renderModel)
     * @param brightness  base brightness (0.0-1.0)
     * @param partialTick tick fraction (0.0-1.0) from TileEntitySpecialRenderer
     */
    public static void renderAnimated(Aero_MeshModel model,
                                       Aero_AnimationBundle bundle,
                                       Aero_AnimationDefinition def,
                                       Aero_AnimationState state,
                                       double x, double y, double z,
                                       float brightness, float partialTick) {
        // 1. Static geometry
        renderModel(model, x, y, z, 0, brightness);

        // 2. Named groups with clip transforms
        Aero_AnimationClip clip = state.getCurrentClip();
        float time = state.getInterpolatedTime(partialTick);

        Tessellator tess = Tessellator.instance;
        java.util.Iterator it = model.namedGroups.entrySet().iterator();
        while (it.hasNext()) {
            java.util.Map.Entry entry = (java.util.Map.Entry) it.next();
            String groupName = (String) entry.getKey();
            float[][][] ng   = (float[][][]) entry.getValue();

            // Pivot do bundle (block units = pixels/16)
            float[] pivot = bundle.getPivot(groupName);
            float px = pivot[0], py = pivot[1], pz = pivot[2];

            // Rotation and position from clip (null = no keyframe → neutral values)
            float rx = 0, ry = 0, rz = 0;
            float dx = 0, dy = 0, dz = 0;

            if (clip != null) {
                int bi = clip.indexOfBone(groupName);
                // Hierarchy: if the OBJ group has no direct bone, search via childMap or prefix
                if (bi < 0) {
                    // 1. Try childMap (explicit Blockbench hierarchy)
                    String parentName = (String) bundle.childMap.get(groupName);
                    if (parentName != null) {
                        bi = clip.indexOfBone(parentName);
                        // Walk up the hierarchy if the direct parent has no keyframes
                        if (bi < 0) {
                            String grandParent = (String) bundle.childMap.get(parentName);
                            if (grandParent != null) bi = clip.indexOfBone(grandParent);
                        }
                    }
                    // 2. Fallback: prefix
                    if (bi < 0) bi = findParentBone(clip, groupName);

                    if (bi >= 0) {
                        // Use the parent bone's pivot, not the child group's
                        float[] parentPivot = bundle.getPivot(clip.boneNames[bi]);
                        px = parentPivot[0]; py = parentPivot[1]; pz = parentPivot[2];
                    }
                }
                if (bi >= 0) {
                    float[] rot = clip.sampleRot(bi, time);
                    if (rot != null) { rx = rot[0]; ry = rot[1]; rz = rot[2]; }

                    float[] pos = clip.samplePos(bi, time);
                    // Position in pixels → block units
                    if (pos != null) { dx = pos[0] / 16f; dy = pos[1] / 16f; dz = pos[2] / 16f; }
                }
            }

            GL11.glPushMatrix();
            GL11.glTranslated(x, y, z);
            // Move to pivot + animated offset
            GL11.glTranslatef(px + dx, py + dy, pz + dz);
            // Euler rotation in Z→Y→X order (Bedrock/GeckoLib compatible)
            GL11.glRotatef(rz, 0f, 0f, 1f);
            GL11.glRotatef(ry, 0f, 1f, 0f);
            GL11.glRotatef(rx, 1f, 0f, 0f);
            // Move back from pivot
            GL11.glTranslatef(-px, -py, -pz);

            GL11.glDisable(GL11.GL_CULL_FACE);
            GL11.glDisable(GL11.GL_LIGHTING);
            drawGroups(tess, ng, model.scale, brightness);
            GL11.glEnable(GL11.GL_LIGHTING);
            GL11.glEnable(GL11.GL_CULL_FACE);
            GL11.glPopMatrix();
        }
    }

    // -----------------------------------------------------------------------
    // Inventory render
    // -----------------------------------------------------------------------

    // -----------------------------------------------------------------------
    // Internal helpers
    // -----------------------------------------------------------------------

    /** Draws triangle groups at full brightness — used by Aero_InventoryRenderer. */
    static void drawGroupsForInventory(Tessellator tess, float[][][] groups, float sc) {
        drawGroups(tess, groups, sc, 1.0f);
    }

    /** Draws triangle groups with flat lighting (uniform brightness per group). */
    private static void drawGroups(Tessellator tess, float[][][] groups, float sc, float brightness) {
        tess.startDrawing(GL11.GL_TRIANGLES);
        for (int g = 0; g < 4; g++) {
            float[][] tris = groups[g];
            if (tris.length == 0) continue;
            float bright = brightness * Aero_MeshModel.BRIGHTNESS_FACTORS[g];
            tess.setColorOpaque_F(bright, bright, bright);
            for (int i = 0; i < tris.length; i++) {
                float[] t = tris[i];
                tess.addVertexWithUV(t[0]/sc, t[1]/sc, t[2]/sc, t[3], t[4]);
                tess.addVertexWithUV(t[5]/sc, t[6]/sc, t[7]/sc, t[8], t[9]);
                tess.addVertexWithUV(t[10]/sc, t[11]/sc, t[12]/sc, t[13], t[14]);
            }
        }
        tess.draw();
    }

    /** Draws triangle groups with smooth lighting (bilinear world sample per triangle). */
    private static void drawGroupsSmooth(Tessellator tess, float[][][] groups, float sc,
                                          World world, int ox, int topY, int oz) {
        tess.startDrawing(GL11.GL_TRIANGLES);
        for (int g = 0; g < 4; g++) {
            float[][] tris = groups[g];
            if (tris.length == 0) continue;
            float factor = Aero_MeshModel.BRIGHTNESS_FACTORS[g];
            for (int i = 0; i < tris.length; i++) {
                float[] t = tris[i];
                // Sample at triangle centroid XZ, fixed Y above structure.
                // Per-triangle (not per-vertex) avoids gradient artifacts.
                float wx = ox + (t[0] + t[5] + t[10]) / (3.0f * sc);
                float wz = oz + (t[2] + t[7] + t[12]) / (3.0f * sc);
                int x0 = (int) Math.floor(wx); int x1 = x0 + 1;
                int z0 = (int) Math.floor(wz); int z1 = z0 + 1;
                float tx = wx - x0, tz = wz - z0;
                float b00 = world.getLightBrightness(x0, topY, z0);
                float b10 = world.getLightBrightness(x1, topY, z0);
                float b01 = world.getLightBrightness(x0, topY, z1);
                float b11 = world.getLightBrightness(x1, topY, z1);
                float bright = lerp(lerp(b00, b10, tx), lerp(b01, b11, tx), tz) * factor;
                tess.setColorOpaque_F(bright, bright, bright);
                tess.addVertexWithUV(t[0]/sc, t[1]/sc, t[2]/sc, t[3], t[4]);
                tess.addVertexWithUV(t[5]/sc, t[6]/sc, t[7]/sc, t[8], t[9]);
                tess.addVertexWithUV(t[10]/sc, t[11]/sc, t[12]/sc, t[13], t[14]);
            }
        }
        tess.draw();
    }

    /**
     * Finds a parent bone in the clip whose name is a prefix of groupName.
     * E.g.: groupName="turbine_l_blade_0" → finds bone "turbine_l"
     * Returns the index of the longest bone that is a prefix, or -1.
     */
    private static int findParentBone(Aero_AnimationClip clip, String groupName) {
        int bestIdx = -1;
        int bestLen = 0;
        for (int i = 0; i < clip.boneNames.length; i++) {
            String bone = clip.boneNames[i];
            if (groupName.startsWith(bone + "_") && bone.length() > bestLen) {
                bestIdx = i;
                bestLen = bone.length();
            }
        }
        return bestIdx;
    }

    private static float lerp(float a, float b, float t) { return a + (b - a) * t; }

    private static void applyRotation(float rotation) {
        if (rotation != 0) {
            GL11.glTranslatef(0.5f, 0.5f, 0.5f);
            GL11.glRotatef(rotation, 0.0f, 1.0f, 0.0f);
            GL11.glTranslatef(-0.5f, -0.5f, -0.5f);
        }
    }
}
