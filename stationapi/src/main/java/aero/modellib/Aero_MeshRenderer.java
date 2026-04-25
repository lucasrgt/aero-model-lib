package aero.modellib;

import net.minecraft.client.render.Tessellator;
import net.minecraft.world.World;
import org.lwjgl.opengl.GL11;

/**
 * AeroMesh Renderer (StationAPI/Yarn port). Same algorithm as the ModLoader
 * version, with Yarn-mapped Tessellator + World API.
 *
 * Performance:
 *   - Triangles pre-classified into 4 brightness groups at parse time.
 *   - Tessellator color called 4× per draw (vs N× naive).
 *   - Coordinate division by `sc` replaced with single multiplication.
 *   - Smooth-light path samples each (x,z) world column once per draw and
 *     bilinearly interpolates from the cache (vs 4 lookups per triangle).
 *   - renderAnimated batches GL state changes outside the named-group loop
 *     and iterates a precomputed entry array (no Iterator/Entry alloc).
 *   - Bone/pivot resolution memoized per (clip identity) on the model.
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

    public static void renderModel(Aero_MeshModel model, double x, double y, double z,
                                    float rotation, float brightness) {
        Tessellator tess = Tessellator.INSTANCE;
        GL11.glPushMatrix();
        GL11.glTranslated(x, y, z);
        applyRotation(rotation);
        // Save GL_ENABLE_BIT (CULL_FACE/LIGHTING/BLEND/ALPHA_TEST), DEPTH_BUFFER_BIT
        // (DepthMask), and CURRENT_BIT (color). glPopAttrib restores everything we
        // touched so we cannot leak BLEND/ALPHA_TEST off into vanilla particle and
        // sprite passes (which would render those as black blobs).
        GL11.glPushAttrib(GL11.GL_ENABLE_BIT | GL11.GL_DEPTH_BUFFER_BIT | GL11.GL_CURRENT_BIT);
        GL11.glDisable(GL11.GL_CULL_FACE);
        GL11.glDisable(GL11.GL_LIGHTING);
        GL11.glDisable(GL11.GL_BLEND);
        GL11.glDisable(GL11.GL_ALPHA_TEST);
        GL11.glDepthMask(true);
        GL11.glColor4f(1f, 1f, 1f, 1f);

        drawGroups(tess, model.groups, model.scale, brightness);

        GL11.glPopAttrib();
        GL11.glPopMatrix();
    }

    public static void renderModel(Aero_MeshModel model, double x, double y, double z,
                                    float rotation, World world, int ox, int topY, int oz) {
        Tessellator tess = Tessellator.INSTANCE;
        GL11.glPushMatrix();
        GL11.glTranslated(x, y, z);
        applyRotation(rotation);
        GL11.glPushAttrib(GL11.GL_ENABLE_BIT | GL11.GL_DEPTH_BUFFER_BIT | GL11.GL_CURRENT_BIT);
        GL11.glDisable(GL11.GL_CULL_FACE);
        GL11.glDisable(GL11.GL_LIGHTING);
        GL11.glDisable(GL11.GL_BLEND);
        GL11.glDisable(GL11.GL_ALPHA_TEST);
        GL11.glDepthMask(true);
        GL11.glColor4f(1f, 1f, 1f, 1f);

        drawGroupsSmooth(tess, model.groups, model.scale, world, ox, topY, oz);

        GL11.glPopAttrib();
        GL11.glPopMatrix();
    }

    // -----------------------------------------------------------------------
    // Named group render (for animated parts)
    // -----------------------------------------------------------------------

    public static void renderGroup(Aero_MeshModel model, String groupName, float brightness) {
        float[][][] ng = (float[][][]) model.namedGroups.get(groupName);
        if (ng == null) return;
        Tessellator tess = Tessellator.INSTANCE;
        drawGroups(tess, ng, model.scale, brightness);
    }

    public static void renderGroupRotated(Aero_MeshModel model, String groupName,
                                           double x, double y, double z, float brightness,
                                           float pivotX, float pivotY, float pivotZ,
                                           float angle, float axisX, float axisY, float axisZ) {
        float[][][] ng = (float[][][]) model.namedGroups.get(groupName);
        if (ng == null) return;

        Tessellator tess = Tessellator.INSTANCE;
        GL11.glPushMatrix();
        GL11.glTranslated(x, y, z);
        GL11.glTranslatef(pivotX, pivotY, pivotZ);
        GL11.glRotatef(angle, axisX, axisY, axisZ);
        GL11.glTranslatef(-pivotX, -pivotY, -pivotZ);
        GL11.glPushAttrib(GL11.GL_ENABLE_BIT | GL11.GL_DEPTH_BUFFER_BIT | GL11.GL_CURRENT_BIT);
        GL11.glDisable(GL11.GL_CULL_FACE);
        GL11.glDisable(GL11.GL_LIGHTING);
        GL11.glDisable(GL11.GL_BLEND);
        GL11.glDisable(GL11.GL_ALPHA_TEST);
        GL11.glDepthMask(true);
        GL11.glColor4f(1f, 1f, 1f, 1f);

        drawGroups(tess, ng, model.scale, brightness);

        GL11.glPopAttrib();
        GL11.glPopMatrix();
    }

    // -----------------------------------------------------------------------
    // Animated render
    // -----------------------------------------------------------------------

    public static void renderAnimated(Aero_MeshModel model,
                                       Aero_AnimationBundle bundle,
                                       Aero_AnimationDefinition def,
                                       Aero_AnimationState state,
                                       double x, double y, double z,
                                       float brightness, float partialTick) {
        renderModel(model, x, y, z, 0, brightness);

        Aero_MeshModel.NamedGroup[] entries = model.getNamedGroupArray();
        if (entries.length == 0) return;

        Aero_AnimationClip clip = state.getCurrentClip();
        float time = state.getInterpolatedTime(partialTick);
        Aero_MeshModel.BoneRef[] refs = model.boneRefsFor(clip, bundle);

        Tessellator tess = Tessellator.INSTANCE;
        // Save GL state so we can change CULL_FACE/LIGHTING/BLEND/ALPHA_TEST
        // without leaking the changes into post-renderer passes (particles,
        // sprites). glPopAttrib at the end restores everything.
        GL11.glPushAttrib(GL11.GL_ENABLE_BIT | GL11.GL_DEPTH_BUFFER_BIT | GL11.GL_CURRENT_BIT);
        GL11.glDisable(GL11.GL_CULL_FACE);
        GL11.glDisable(GL11.GL_LIGHTING);
        GL11.glDisable(GL11.GL_BLEND);
        GL11.glDisable(GL11.GL_ALPHA_TEST);
        GL11.glDepthMask(true);
        GL11.glColor4f(1f, 1f, 1f, 1f);

        for (int e = 0; e < entries.length; e++) {
            Aero_MeshModel.NamedGroup ng = entries[e];
            Aero_MeshModel.BoneRef    rf = refs[e];

            float px = rf.pivot[0], py = rf.pivot[1], pz = rf.pivot[2];
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
            GL11.glTranslatef(px + dx, py + dy, pz + dz);
            GL11.glRotatef(rz, 0f, 0f, 1f);
            GL11.glRotatef(ry, 0f, 1f, 0f);
            GL11.glRotatef(rx, 1f, 0f, 0f);
            if (sx != 1f || sy != 1f || sz != 1f) {
                GL11.glScalef(sx, sy, sz);
            }
            GL11.glTranslatef(-px, -py, -pz);

            drawGroups(tess, ng.tris, model.scale, brightness);
            GL11.glPopMatrix();
        }

        GL11.glPopAttrib();
    }

    // -----------------------------------------------------------------------
    // Internal helpers
    // -----------------------------------------------------------------------

    static void drawGroupsForInventory(Tessellator tess, float[][][] groups, float sc) {
        drawGroups(tess, groups, sc, 1.0f);
    }

    private static void drawGroups(Tessellator tess, float[][][] groups, float sc, float brightness) {
        final float invSc = 1f / sc;
        tess.start(GL11.GL_TRIANGLES);
        for (int g = 0; g < 4; g++) {
            float[][] tris = groups[g];
            if (tris.length == 0) continue;
            float bright = brightness * Aero_MeshModel.BRIGHTNESS_FACTORS[g];
            tess.color(bright, bright, bright);
            for (int i = 0; i < tris.length; i++) {
                float[] t = tris[i];
                tess.vertex(t[0]*invSc,  t[1]*invSc,  t[2]*invSc,  t[3],  t[4]);
                tess.vertex(t[5]*invSc,  t[6]*invSc,  t[7]*invSc,  t[8],  t[9]);
                tess.vertex(t[10]*invSc, t[11]*invSc, t[12]*invSc, t[13], t[14]);
            }
        }
        tess.draw();
    }

    private static void drawGroupsSmooth(Tessellator tess, float[][][] groups, float sc,
                                          World world, int ox, int topY, int oz) {
        final float invSc  = 1f / sc;
        final float invSc3 = invSc / 3f;

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

        int xLo = (int) Math.floor(ox + minWX);
        int xHi = (int) Math.floor(ox + maxWX) + 1;
        int zLo = (int) Math.floor(oz + minWZ);
        int zHi = (int) Math.floor(oz + maxWZ) + 1;
        int w = xHi - xLo + 1;
        int h = zHi - zLo + 1;

        int needed = w * h;
        if (LIGHT_CACHE.length < needed) LIGHT_CACHE = new float[needed];
        float[] cache = LIGHT_CACHE;
        for (int zi = 0; zi < h; zi++) {
            int row = zi * w;
            int wz = zLo + zi;
            for (int xi = 0; xi < w; xi++) {
                // method_1782 is the float-brightness equivalent of vanilla
                // getLightBrightness(int,int,int). Yarn for Beta 1.7.3 hasn't
                // assigned a human name yet (still raw intermediary). Update
                // when biny mappings give it a real name.
                cache[row + xi] = world.method_1782(xLo + xi, topY, wz);
            }
        }

        tess.start(GL11.GL_TRIANGLES);
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
                tess.color(bright, bright, bright);
                tess.vertex(t[0]*invSc,  t[1]*invSc,  t[2]*invSc,  t[3],  t[4]);
                tess.vertex(t[5]*invSc,  t[6]*invSc,  t[7]*invSc,  t[8],  t[9]);
                tess.vertex(t[10]*invSc, t[11]*invSc, t[12]*invSc, t[13], t[14]);
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
