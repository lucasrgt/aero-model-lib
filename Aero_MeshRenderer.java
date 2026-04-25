package aero.modellib;

import net.minecraft.src.*;
import org.lwjgl.opengl.GL11;

/**
 * AeroMesh Renderer by lucasrgt - aerocoding.dev
 * Renders OBJ models (Aero_MeshModel) using GL_TRIANGLES.
 *
 * Performance:
 *   - Triangles pre-classified into 4 brightness groups at parse time
 *   - setColorOpaque_F called 4× per draw (vs N× in the naive approach)
 *   - Coordinate division by `sc` replaced with single multiplication
 *   - Smooth-light path samples each (x,z) world column once per draw and
 *     bilinearly interpolates from the cache (vs 4 lookups per triangle)
 *   - renderAnimated batches GL state changes outside the named-group loop
 *     and iterates a precomputed entry array (no Iterator/Entry alloc)
 *   - Bone/pivot resolution is memoized per (clip identity) on the model,
 *     so the per-group HashMap and linear-scan lookups happen only when
 *     the active clip changes
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

    // Reusable scratch buffers — render thread is single-threaded in Beta 1.7.3.
    private static float[] LIGHT_CACHE = new float[64];
    private static final float[] SCRATCH_ROT = new float[3];
    private static final float[] SCRATCH_POS = new float[3];
    private static final float[] SCRATCH_SCL = new float[3];

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
     * Hot path: bone resolution (indexOfBone, childMap walk, prefix scan) is
     * memoized in model.boneRefsFor(clip), so per-frame work is bounded by
     * the GL transforms and the scratch-buffer keyframe samples.
     */
    public static void renderAnimated(Aero_MeshModel model,
                                       Aero_AnimationBundle bundle,
                                       Aero_AnimationDefinition def,
                                       Aero_AnimationState state,
                                       double x, double y, double z,
                                       float brightness, float partialTick) {
        // 1. Static geometry — full GL state cycle done inside renderModel
        renderModel(model, x, y, z, 0, brightness);

        // 2. Named groups — share one GL state cycle for the whole loop
        Aero_MeshModel.NamedGroup[] entries = model.getNamedGroupArray();
        if (entries.length == 0) return;

        Aero_AnimationClip clip = state.getCurrentClip();
        float time = state.getInterpolatedTime(partialTick);
        Aero_MeshModel.BoneRef[] refs = model.boneRefsFor(clip, bundle);

        Tessellator tess = Tessellator.instance;
        GL11.glDisable(GL11.GL_CULL_FACE);
        GL11.glDisable(GL11.GL_LIGHTING);

        for (int e = 0; e < entries.length; e++) {
            Aero_MeshModel.NamedGroup ng = entries[e];
            Aero_MeshModel.BoneRef    rf = refs[e];

            // Pivot — base from bundle, overridden by parent pivot when bone resolved via hierarchy
            float px = rf.pivot[0], py = rf.pivot[1], pz = rf.pivot[2];

            // Rotation, position, scale from clip (defaults = no keyframe)
            float rx = 0, ry = 0, rz = 0;
            float sx = 1, sy = 1, sz = 1;
            float dx = 0, dy = 0, dz = 0;

            int bi = rf.boneIdx;
            if (clip != null && bi >= 0) {
                if (clip.sampleRotInto(bi, time, SCRATCH_ROT)) {
                    rx = SCRATCH_ROT[0]; ry = SCRATCH_ROT[1]; rz = SCRATCH_ROT[2];
                }
                if (clip.samplePosInto(bi, time, SCRATCH_POS)) {
                    dx = SCRATCH_POS[0] / 16f; dy = SCRATCH_POS[1] / 16f; dz = SCRATCH_POS[2] / 16f;
                }
                if (clip.sampleSclInto(bi, time, SCRATCH_SCL)) {
                    sx = SCRATCH_SCL[0]; sy = SCRATCH_SCL[1]; sz = SCRATCH_SCL[2];
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
            // Scale (if animated)
            if (sx != 1f || sy != 1f || sz != 1f) {
                GL11.glScalef(sx, sy, sz);
            }
            // Move back from pivot
            GL11.glTranslatef(-px, -py, -pz);

            drawGroups(tess, ng.tris, model.scale, brightness);
            GL11.glPopMatrix();
        }

        GL11.glEnable(GL11.GL_LIGHTING);
        GL11.glEnable(GL11.GL_CULL_FACE);
    }

    // -----------------------------------------------------------------------
    // Internal helpers
    // -----------------------------------------------------------------------

    /** Draws triangle groups at full brightness — used by Aero_InventoryRenderer. */
    static void drawGroupsForInventory(Tessellator tess, float[][][] groups, float sc) {
        drawGroups(tess, groups, sc, 1.0f);
    }

    /** Draws triangle groups with flat lighting (uniform brightness per group). */
    private static void drawGroups(Tessellator tess, float[][][] groups, float sc, float brightness) {
        final float invSc = 1f / sc;
        tess.startDrawing(GL11.GL_TRIANGLES);
        for (int g = 0; g < 4; g++) {
            float[][] tris = groups[g];
            if (tris.length == 0) continue;
            float bright = brightness * Aero_MeshModel.BRIGHTNESS_FACTORS[g];
            tess.setColorOpaque_F(bright, bright, bright);
            for (int i = 0; i < tris.length; i++) {
                float[] t = tris[i];
                tess.addVertexWithUV(t[0]*invSc,  t[1]*invSc,  t[2]*invSc,  t[3],  t[4]);
                tess.addVertexWithUV(t[5]*invSc,  t[6]*invSc,  t[7]*invSc,  t[8],  t[9]);
                tess.addVertexWithUV(t[10]*invSc, t[11]*invSc, t[12]*invSc, t[13], t[14]);
            }
        }
        tess.draw();
    }

    /**
     * Draws triangle groups with smooth lighting using a precomputed light cache
     * over the structure footprint. Each unique (x,z) world column is sampled
     * once via getLightBrightness, then bilinearly interpolated at every
     * triangle centroid — replacing the previous 4 lookups per triangle.
     */
    private static void drawGroupsSmooth(Tessellator tess, float[][][] groups, float sc,
                                          World world, int ox, int topY, int oz) {
        final float invSc  = 1f / sc;
        final float invSc3 = invSc / 3f;

        // 1. Compute footprint in world block coords (XZ).
        float minWX = Float.POSITIVE_INFINITY, maxWX = Float.NEGATIVE_INFINITY;
        float minWZ = Float.POSITIVE_INFINITY, maxWZ = Float.NEGATIVE_INFINITY;
        boolean hasTris = false;
        for (int g = 0; g < 4; g++) {
            float[][] tris = groups[g];
            for (int i = 0; i < tris.length; i++) {
                float[] t = tris[i];
                float a = t[0]*invSc, b = t[5]*invSc, c = t[10]*invSc;
                if (a < minWX) minWX = a; if (b < minWX) minWX = b; if (c < minWX) minWX = c;
                if (a > maxWX) maxWX = a; if (b > maxWX) maxWX = b; if (c > maxWX) maxWX = c;
                a = t[2]*invSc; b = t[7]*invSc; c = t[12]*invSc;
                if (a < minWZ) minWZ = a; if (b < minWZ) minWZ = b; if (c < minWZ) minWZ = c;
                if (a > maxWZ) maxWZ = a; if (b > maxWZ) maxWZ = b; if (c > maxWZ) maxWZ = c;
                hasTris = true;
            }
        }
        if (!hasTris) return;

        // +1 cell on the high side for the bilinear neighbor.
        int xLo = (int) Math.floor(ox + minWX);
        int xHi = (int) Math.floor(ox + maxWX) + 1;
        int zLo = (int) Math.floor(oz + minWZ);
        int zHi = (int) Math.floor(oz + maxWZ) + 1;
        int w = xHi - xLo + 1;
        int h = zHi - zLo + 1;

        // 2. Populate the cache: one getLightBrightness per unique column.
        int needed = w * h;
        if (LIGHT_CACHE.length < needed) LIGHT_CACHE = new float[needed];
        float[] cache = LIGHT_CACHE;
        for (int zi = 0; zi < h; zi++) {
            int row = zi * w;
            int wz = zLo + zi;
            for (int xi = 0; xi < w; xi++) {
                cache[row + xi] = world.getLightBrightness(xLo + xi, topY, wz);
            }
        }

        // 3. Draw using bilinear lookup from the cache.
        tess.startDrawing(GL11.GL_TRIANGLES);
        for (int g = 0; g < 4; g++) {
            float[][] tris = groups[g];
            if (tris.length == 0) continue;
            float factor = Aero_MeshModel.BRIGHTNESS_FACTORS[g];
            for (int i = 0; i < tris.length; i++) {
                float[] t = tris[i];
                float wx = ox + (t[0] + t[5] + t[10]) * invSc3;
                float wz = oz + (t[2] + t[7] + t[12]) * invSc3;
                int x0i = (int) Math.floor(wx);
                int z0i = (int) Math.floor(wz);
                float tx = wx - x0i, tz = wz - z0i;
                int cx = x0i - xLo;
                int cz = z0i - zLo;
                int row0 = cz * w;
                int row1 = row0 + w;
                float b00 = cache[row0 + cx];
                float b10 = cache[row0 + cx + 1];
                float b01 = cache[row1 + cx];
                float b11 = cache[row1 + cx + 1];
                float bright = lerp(lerp(b00, b10, tx), lerp(b01, b11, tx), tz) * factor;
                tess.setColorOpaque_F(bright, bright, bright);
                tess.addVertexWithUV(t[0]*invSc,  t[1]*invSc,  t[2]*invSc,  t[3],  t[4]);
                tess.addVertexWithUV(t[5]*invSc,  t[6]*invSc,  t[7]*invSc,  t[8],  t[9]);
                tess.addVertexWithUV(t[10]*invSc, t[11]*invSc, t[12]*invSc, t[13], t[14]);
            }
        }
        tess.draw();
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
