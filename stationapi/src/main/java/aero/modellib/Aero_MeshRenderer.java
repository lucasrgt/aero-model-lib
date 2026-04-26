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

    private static final int MESH_ATTRIB_BITS =
        GL11.GL_ENABLE_BIT | GL11.GL_DEPTH_BUFFER_BIT | GL11.GL_CURRENT_BIT;

    // Reusable scratch buffers — render thread is single-threaded in Beta 1.7.3.
    private static float[] LIGHT_CACHE = new float[64];
    private static final float[] SCRATCH_ROT = new float[3];
    private static final float[] SCRATCH_POS = new float[3];
    private static final float[] SCRATCH_SCL = new float[3];
    private static final float[] SCRATCH_PIVOT = new float[3];
    private static final Aero_BoneRenderPose SCRATCH_POSE = new Aero_BoneRenderPose();

    // -----------------------------------------------------------------------
    // Full model render
    // -----------------------------------------------------------------------

    public static void renderModel(Aero_MeshModel model, double x, double y, double z,
                                    float rotation, float brightness) {
        renderModel(model, x, y, z, rotation, brightness, Aero_RenderOptions.DEFAULT);
    }

    public static void renderModel(Aero_MeshModel model, double x, double y, double z,
                                    float rotation, float brightness,
                                    Aero_RenderOptions options) {
        Aero_Profiler.start("aero.mesh.render");
        try {
            Tessellator tess = Tessellator.INSTANCE;
            GL11.glPushMatrix();
            GL11.glTranslated(x, y, z);
            applyRotation(rotation);
            beginMeshState(options);

            drawGroups(tess, model.groups, model.invScale, brightness, options);

            endMeshState();
            GL11.glPopMatrix();
        } finally {
            Aero_Profiler.end("aero.mesh.render");
        }
    }

    public static void renderModelAtRest(Aero_MeshModel model, double x, double y, double z,
                                         float rotation, float brightness) {
        renderModelAtRest(model, x, y, z, rotation, brightness, Aero_RenderOptions.DEFAULT);
    }

    public static void renderModelAtRest(Aero_MeshModel model, double x, double y, double z,
                                         float rotation, float brightness,
                                         Aero_RenderOptions options) {
        Aero_Profiler.start("aero.mesh.render");
        try {
            Tessellator tess = Tessellator.INSTANCE;
            GL11.glPushMatrix();
            GL11.glTranslated(x, y, z);
            applyRotation(rotation);
            beginMeshState(options);

            drawGroups(tess, model.groups, model.invScale, brightness, options);
            Aero_MeshModel.NamedGroup[] entries = model.getNamedGroupArray();
            for (int e = 0; e < entries.length; e++) {
                drawGroups(tess, entries[e].tris, model.invScale, brightness, options);
            }

            endMeshState();
            GL11.glPopMatrix();
        } finally {
            Aero_Profiler.end("aero.mesh.render");
        }
    }

    public static void renderModel(Aero_MeshModel model, double x, double y, double z,
                                    float rotation, World world, int ox, int topY, int oz) {
        renderModel(model, x, y, z, rotation, world, ox, topY, oz, Aero_RenderOptions.DEFAULT);
    }

    public static void renderModel(Aero_MeshModel model, double x, double y, double z,
                                    float rotation, World world, int ox, int topY, int oz,
                                    Aero_RenderOptions options) {
        Aero_Profiler.start("aero.mesh.render");
        try {
            Tessellator tess = Tessellator.INSTANCE;
            GL11.glPushMatrix();
            GL11.glTranslated(x, y, z);
            applyRotation(rotation);
            beginMeshState(options);

            drawGroupsSmooth(tess, model.groups, model.invScale, model.getStaticSmoothLightData(),
                world, ox, topY, oz, options);

            endMeshState();
            GL11.glPopMatrix();
        } finally {
            Aero_Profiler.end("aero.mesh.render");
        }
    }

    // -----------------------------------------------------------------------
    // Named group render (for animated parts)
    // -----------------------------------------------------------------------

    public static void renderGroup(Aero_MeshModel model, String groupName, float brightness) {
        renderGroup(model, groupName, brightness, Aero_RenderOptions.DEFAULT);
    }

    public static void renderGroup(Aero_MeshModel model, String groupName, float brightness,
                                   Aero_RenderOptions options) {
        float[][][] ng = model.getNamedGroup(groupName);
        if (ng == null) return;
        Tessellator tess = Tessellator.INSTANCE;
        drawGroups(tess, ng, model.invScale, brightness, options);
    }

    public static void renderGroupRotated(Aero_MeshModel model, String groupName,
                                           double x, double y, double z, float brightness,
                                           float pivotX, float pivotY, float pivotZ,
                                           float angle, float axisX, float axisY, float axisZ) {
        renderGroupRotated(model, groupName, x, y, z, brightness,
            pivotX, pivotY, pivotZ, angle, axisX, axisY, axisZ, Aero_RenderOptions.DEFAULT);
    }

    public static void renderGroupRotated(Aero_MeshModel model, String groupName,
                                           double x, double y, double z, float brightness,
                                           float pivotX, float pivotY, float pivotZ,
                                           float angle, float axisX, float axisY, float axisZ,
                                           Aero_RenderOptions options) {
        float[][][] ng = model.getNamedGroup(groupName);
        if (ng == null) return;

        Tessellator tess = Tessellator.INSTANCE;
        GL11.glPushMatrix();
        GL11.glTranslated(x, y, z);
        GL11.glTranslatef(pivotX, pivotY, pivotZ);
        GL11.glRotatef(angle, axisX, axisY, axisZ);
        GL11.glTranslatef(-pivotX, -pivotY, -pivotZ);
        beginMeshState(options);

        drawGroups(tess, ng, model.invScale, brightness, options);

        endMeshState();
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
        renderAnimated(model, bundle, def, (Aero_AnimationPlayback) state,
            x, y, z, brightness, partialTick, Aero_RenderOptions.DEFAULT);
    }

    public static void renderAnimated(Aero_MeshModel model,
                                       Aero_AnimationBundle bundle,
                                       Aero_AnimationDefinition def,
                                       Aero_AnimationState state,
                                       double x, double y, double z,
                                       float brightness, float partialTick,
                                       Aero_RenderOptions options) {
        renderAnimated(model, bundle, def, (Aero_AnimationPlayback) state,
            x, y, z, brightness, partialTick, options);
    }

    public static void renderAnimated(Aero_MeshModel model,
                                       Aero_AnimationBundle bundle,
                                       Aero_AnimationDefinition def,
                                       Aero_AnimationPlayback state,
                                       double x, double y, double z,
                                       float brightness, float partialTick) {
        renderAnimated(model, bundle, def, state, x, y, z, brightness, partialTick,
            Aero_RenderOptions.DEFAULT);
    }

    public static void renderAnimated(Aero_MeshModel model,
                                       Aero_AnimationBundle bundle,
                                       Aero_AnimationDefinition def,
                                       Aero_AnimationPlayback state,
                                       double x, double y, double z,
                                       float brightness, float partialTick,
                                       Aero_RenderOptions options) {
        renderAnimated(model, bundle, def, state, x, y, z, brightness, partialTick, options, null);
    }

    /**
     * Bundle/def/state overload with a procedural pose hook layered on top
     * of the keyframe pose — the canonical entry point for vehicles whose
     * turret/barrel/propeller follow runtime input.
     */
    public static void renderAnimated(Aero_MeshModel model,
                                       Aero_AnimationBundle bundle,
                                       Aero_AnimationDefinition def,
                                       Aero_AnimationPlayback state,
                                       double x, double y, double z,
                                       float brightness, float partialTick,
                                       Aero_RenderOptions options,
                                       Aero_ProceduralPose proceduralPose) {
        Aero_Profiler.start("aero.mesh.renderAnimated");
        try {
            Aero_MeshModel.NamedGroup[] entries = model.getNamedGroupArray();
            Aero_AnimationClip clip = null;
            float time = 0f;
            Aero_MeshModel.BoneRef[] refs = null;
            if (entries.length != 0) {
                clip = state.getCurrentClip();
                time = state.getInterpolatedTime(partialTick);
                refs = model.boneRefsFor(clip, bundle);
            }

            Tessellator tess = Tessellator.INSTANCE;
            GL11.glPushMatrix();
            GL11.glTranslated(x, y, z);
            beginMeshState(options);

            drawGroups(tess, model.groups, model.invScale, brightness, options);

            for (int e = 0; e < entries.length; e++) {
                Aero_MeshModel.NamedGroup ng = entries[e];
                Aero_MeshModel.BoneRef    rf = refs[e];

                Aero_AnimationPoseResolver.resolveClip(rf, clip, state, time, partialTick,
                    SCRATCH_ROT, SCRATCH_POS, SCRATCH_SCL, SCRATCH_POSE);
                if (proceduralPose != null) proceduralPose.apply(ng.name, SCRATCH_POSE);

                GL11.glPushMatrix();
                applyPose(SCRATCH_POSE);
                drawGroups(tess, ng.tris, model.invScale, brightness, options);
                GL11.glPopMatrix();
            }

            endMeshState();
            GL11.glPopMatrix();
        } finally {
            Aero_Profiler.end("aero.mesh.renderAnimated");
        }
    }

    public static void renderAnimated(Aero_MeshModel model,
                                       Aero_AnimationPlayback state,
                                       double x, double y, double z,
                                       float brightness, float partialTick) {
        renderAnimated(model, state, x, y, z, brightness, partialTick, Aero_RenderOptions.DEFAULT);
    }

    public static void renderAnimated(Aero_MeshModel model,
                                       Aero_AnimationPlayback state,
                                       double x, double y, double z,
                                       float brightness, float partialTick,
                                       Aero_RenderOptions options) {
        renderAnimated(model, state, x, y, z, brightness, partialTick, options, null);
    }

    public static void renderAnimated(Aero_MeshModel model,
                                       Aero_AnimationPlayback state,
                                       double x, double y, double z,
                                       float brightness, float partialTick,
                                       Aero_RenderOptions options,
                                       Aero_ProceduralPose proceduralPose) {
        if (state == null) throw new IllegalArgumentException("state must not be null");
        renderAnimated(model, state.getBundle(), state.getDef(), state,
            x, y, z, brightness, partialTick, options, proceduralPose);
    }

    /**
     * Render entry point for the multi-controller / additive layering API
     * ({@link Aero_AnimationStack}). Walks the model's named groups by
     * NAME and asks the stack for each bone's combined rotation /
     * position / scale, so a base layer + secondary layers (head look,
     * arm wave, hit reaction) all compose into a single GL transform per
     * bone.
     *
     * <p>The stack is responsible for ticking its own layers — this
     * renderer only samples them at {@code partialTick} for visual frames.
     *
     * <p>Pivots come from the FIRST layer's bundle that knows about the
     * bone (via {@code Aero_AnimationBundle.hasPivot} +
     * {@code getPivotInto}); secondary layers usually share the same
     * bundle so the pivot resolves on the first hit. Bones missing from
     * every layer's clip render at the origin, no GL transform applied.
     */
    public static void renderAnimated(Aero_MeshModel model,
                                       Aero_AnimationStack stack,
                                       double x, double y, double z,
                                       float brightness, float partialTick) {
        renderAnimated(model, stack, x, y, z, brightness, partialTick, Aero_RenderOptions.DEFAULT);
    }

    public static void renderAnimated(Aero_MeshModel model,
                                       Aero_AnimationStack stack,
                                       double x, double y, double z,
                                       float brightness, float partialTick,
                                       Aero_RenderOptions options) {
        renderAnimated(model, stack, x, y, z, brightness, partialTick, options, null);
    }

    /**
     * Stack overload with a procedural pose hook layered on top of the
     * blended pose from every layer.
     */
    public static void renderAnimated(Aero_MeshModel model,
                                       Aero_AnimationStack stack,
                                       double x, double y, double z,
                                       float brightness, float partialTick,
                                       Aero_RenderOptions options,
                                       Aero_ProceduralPose proceduralPose) {
        if (stack == null) throw new IllegalArgumentException("stack must not be null");

        Aero_MeshModel.NamedGroup[] entries = model.getNamedGroupArray();

        Tessellator tess = Tessellator.INSTANCE;
        GL11.glPushMatrix();
        GL11.glTranslated(x, y, z);
        beginMeshState(options);

        // Render the static (unnamed) geometry once at the BE origin.
        drawGroups(tess, model.groups, model.invScale, brightness, options);

        for (int e = 0; e < entries.length; e++) {
            Aero_MeshModel.NamedGroup ng = entries[e];
            String boneName = ng.name;

            Aero_AnimationPoseResolver.resolveStack(stack, boneName, partialTick,
                SCRATCH_PIVOT, SCRATCH_ROT, SCRATCH_POS, SCRATCH_SCL, SCRATCH_POSE);
            if (proceduralPose != null) proceduralPose.apply(boneName, SCRATCH_POSE);

            GL11.glPushMatrix();
            applyPose(SCRATCH_POSE);
            drawGroups(tess, ng.tris, model.invScale, brightness, options);
            GL11.glPopMatrix();
        }

        endMeshState();
        GL11.glPopMatrix();
    }

    // -----------------------------------------------------------------------
    // Internal helpers
    // -----------------------------------------------------------------------

    private static void applyPose(Aero_BoneRenderPose pose) {
        GL11.glTranslatef(pose.pivotX + pose.offsetX,
            pose.pivotY + pose.offsetY,
            pose.pivotZ + pose.offsetZ);
        GL11.glRotatef(pose.rotZ, 0f, 0f, 1f);
        GL11.glRotatef(pose.rotY, 0f, 1f, 0f);
        GL11.glRotatef(pose.rotX, 1f, 0f, 0f);
        if (pose.scaleX != 1f || pose.scaleY != 1f || pose.scaleZ != 1f) {
            GL11.glScalef(pose.scaleX, pose.scaleY, pose.scaleZ);
        }
        GL11.glTranslatef(-pose.pivotX, -pose.pivotY, -pose.pivotZ);
    }

    static void drawGroupsForInventory(Tessellator tess, float[][][] groups, float invSc) {
        drawGroups(tess, groups, invSc, 1.0f, Aero_RenderOptions.DEFAULT);
    }

    private static void drawGroups(Tessellator tess, float[][][] groups, float invSc,
                                   float brightness, Aero_RenderOptions options) {
        tess.start(GL11.GL_TRIANGLES);
        for (int g = 0; g < 4; g++) {
            float[][] tris = groups[g];
            if (tris.length == 0) continue;
            float bright = brightness * Aero_MeshModel.BRIGHTNESS_FACTORS[g];
            tess.color(bright * options.tintR, bright * options.tintG, bright * options.tintB, options.alpha);
            for (int i = 0; i < tris.length; i++) {
                float[] t = tris[i];
                tess.vertex(t[0]*invSc,  t[1]*invSc,  t[2]*invSc,  t[3],  t[4]);
                tess.vertex(t[5]*invSc,  t[6]*invSc,  t[7]*invSc,  t[8],  t[9]);
                tess.vertex(t[10]*invSc, t[11]*invSc, t[12]*invSc, t[13], t[14]);
            }
        }
        tess.draw();
    }

    private static void drawGroupsSmooth(Tessellator tess, float[][][] groups, float invSc,
                                          Aero_MeshModel.SmoothLightData light,
                                          World world, int ox, int topY, int oz,
                                          Aero_RenderOptions options) {
        if (!light.hasTriangles) return;

        int xLo = fastFloor(ox + light.minX);
        int xHi = fastFloor(ox + light.maxX) + 1;
        int zLo = fastFloor(oz + light.minZ);
        int zHi = fastFloor(oz + light.maxZ) + 1;
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
            float[] centroidX = light.centroidX[g];
            float[] centroidZ = light.centroidZ[g];
            for (int i = 0; i < tris.length; i++) {
                float[] t = tris[i];
                float wx = ox + centroidX[i];
                float wz = oz + centroidZ[i];
                int x0i = fastFloor(wx);
                int z0i = fastFloor(wz);
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
                tess.color(bright * options.tintR, bright * options.tintG, bright * options.tintB, options.alpha);
                tess.vertex(t[0]*invSc,  t[1]*invSc,  t[2]*invSc,  t[3],  t[4]);
                tess.vertex(t[5]*invSc,  t[6]*invSc,  t[7]*invSc,  t[8],  t[9]);
                tess.vertex(t[10]*invSc, t[11]*invSc, t[12]*invSc, t[13], t[14]);
            }
        }
        tess.draw();
    }

    private static float lerp(float a, float b, float t) { return a + (b - a) * t; }

    private static int fastFloor(float v) {
        int i = (int) v;
        return v < i ? i - 1 : i;
    }

    private static void beginMeshState() {
        beginMeshState(Aero_RenderOptions.DEFAULT);
    }

    private static void beginMeshState(Aero_RenderOptions options) {
        GL11.glPushAttrib(MESH_ATTRIB_BITS);
        GL11.glDisable(GL11.GL_CULL_FACE);
        GL11.glDisable(GL11.GL_LIGHTING);
        applyBlendMode(options.blend);
        GL11.glDisable(GL11.GL_ALPHA_TEST);
        if (options.depthTest) {
            GL11.glEnable(GL11.GL_DEPTH_TEST);
        } else {
            GL11.glDisable(GL11.GL_DEPTH_TEST);
        }
        GL11.glDepthMask(true);
        GL11.glColor4f(1f, 1f, 1f, 1f);
    }

    private static void applyBlendMode(Aero_MeshBlendMode mode) {
        switch (mode) {
            case ALPHA:
                GL11.glEnable(GL11.GL_BLEND);
                GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
                break;
            case ADDITIVE:
                GL11.glEnable(GL11.GL_BLEND);
                GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE);
                break;
            default:
                GL11.glDisable(GL11.GL_BLEND);
                break;
        }
    }

    private static void endMeshState() {
        GL11.glPopAttrib();
    }

    private static void applyRotation(float rotation) {
        if (rotation != 0) {
            GL11.glTranslatef(0.5f, 0.5f, 0.5f);
            GL11.glRotatef(rotation, 0.0f, 1.0f, 0.0f);
            GL11.glTranslatef(-0.5f, -0.5f, -0.5f);
        }
    }
}
