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

    private static final int MESH_ATTRIB_BITS =
        GL11.GL_ENABLE_BIT | GL11.GL_DEPTH_BUFFER_BIT | GL11.GL_CURRENT_BIT;

    // Reusable scratch buffers — render thread is single-threaded in Beta 1.7.3.
    private static float[] LIGHT_CACHE = new float[64];
    private static final float[] SCRATCH_ROT = new float[3];
    private static final float[] SCRATCH_POS = new float[3];
    private static final float[] SCRATCH_SCL = new float[3];
    private static final float[] SCRATCH_PIVOT = new float[3];
    private static final Aero_BoneRenderPose SCRATCH_POSE = new Aero_BoneRenderPose();

    // Pose pool indexed by clip.boneNames[i]. Pre-resolved once per frame
    // so the hierarchical render walk + IK pre-pass can read poses by
    // ancestor index without re-resolving per child. Grows monotonically;
    // never shrinks (keeps the largest clip's working set hot).
    private static Aero_BoneRenderPose[] POSE_POOL = newPosePool(16);

    private static void updateCameraForwardFromPlayer() {
        Aero_RenderDistance.updateCameraForwardFromPlayer();
    }

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
        renderModel(model, x, y, z, rotation, brightness, Aero_RenderOptions.DEFAULT);
    }

    public static void renderModel(Aero_MeshModel model, double x, double y, double z,
                                    float rotation, float brightness,
                                    Aero_RenderOptions options) {
        updateCameraForwardFromPlayer();
        if (!Aero_FrustumCull.isLikelyVisible(x, y, z)) return;
        Aero_Profiler.start("aero.mesh.render");
        try {
            Tessellator tess = Tessellator.instance;
            GL11.glPushMatrix();
            try {
                GL11.glTranslated(x, y, z);
                applyRotation(rotation);
                beginMeshState(options);
                try {
                    drawGroups(tess, model.groups, model.invScale, brightness, options);
                } finally {
                    endMeshState();
                }
            } finally {
                GL11.glPopMatrix();
            }
        } finally {
            Aero_Profiler.end("aero.mesh.render");
        }
    }

    /**
     * Renders static geometry plus every named group at rest pose.
     * This is useful for distant animation LOD because it avoids clip
     * sampling, bone resolution and per-bone GL transforms.
     */
    public static void renderModelAtRest(Aero_MeshModel model, double x, double y, double z,
                                         float rotation, float brightness) {
        renderModelAtRest(model, x, y, z, rotation, brightness, Aero_RenderOptions.DEFAULT);
    }

    public static void renderModelAtRest(Aero_MeshModel model, double x, double y, double z,
                                         float rotation, float brightness,
                                         Aero_RenderOptions options) {
        updateCameraForwardFromPlayer();
        if (!Aero_FrustumCull.isLikelyVisible(x, y, z)) return;
        Aero_Profiler.start("aero.mesh.render");
        try {
            GL11.glPushMatrix();
            try {
                GL11.glTranslated(x, y, z);
                applyRotation(rotation);
                beginMeshState(options);
                try {
                    if (!renderAtRestViaLists(model, brightness, options)) {
                        Tessellator tess = Tessellator.instance;
                        drawGroups(tess, model.groups, model.invScale, brightness, options);
                        Aero_MeshModel.NamedGroup[] entries = model.getNamedGroupArray();
                        for (int e = 0; e < entries.length; e++) {
                            drawGroups(tess, entries[e].tris, model.invScale, brightness, options);
                        }
                    }
                } finally {
                    endMeshState();
                }
            } finally {
                GL11.glPopMatrix();
            }
        } finally {
            Aero_Profiler.end("aero.mesh.render");
        }
    }

    // -----------------------------------------------------------------------
    // Display-list fast-path for the at-rest composition
    // -----------------------------------------------------------------------
    //
    // Replaces N Tessellator.draw cycles (FloatBuffer fill + 4× pointer setup
    // + getBufferAddress JNI hit + glDrawArrays JNI hit) with 4 glCallList
    // calls — one per brightness bucket. Geometry+UV is baked into the list
    // once at first render; brightness × tint × alpha is applied via glColor4f
    // before each glCallList so the same list serves every render of the
    // model regardless of world light or render-options state.
    //
    // Tradeoff: 4 glCallList vs 1 Tessellator.draw, but each glCallList is
    // a single JNI driver call replaying a pre-baked command stream. The
    // Tessellator path's per-call overhead (buffer.put N vertices, JNI for
    // getAddress, JNI for each pointer setter, JNI for glDrawArrays) is gone.
    //
    // Skips empty buckets (id 0) so models with concentrated geometry pay
    // for at most as many glCallList invocations as they have non-empty
    // buckets. Falls back to the Tessellator path if glGenLists returns 0
    // (out of list ids — extremely rare on Beta 1.7.3 / OpenGL 1.1).

    private static boolean renderAtRestViaLists(Aero_MeshModel model, float brightness,
                                                Aero_RenderOptions options) {
        int[] ids = model.getAtRestListIds();
        if (ids == null) {
            if (model.atRestListsCompileFailed()) return false;
            ids = compileAtRestLists(model);
            if (ids == null) {
                model.markAtRestListsCompileFailed();
                return false;
            }
            model.setAtRestListIds(ids);
        }
        for (int g = 0; g < 4; g++) {
            int id = ids[g];
            if (id == 0) continue;
            float bright = brightness * Aero_MeshModel.BRIGHTNESS_FACTORS[g];
            GL11.glColor4f(bright * options.tintR, bright * options.tintG,
                           bright * options.tintB, options.alpha);
            GL11.glCallList(id);
        }
        return true;
    }

    private static int[] compileAtRestLists(Aero_MeshModel model) {
        int[] ids = new int[4];
        final float invSc = model.invScale;
        Aero_MeshModel.NamedGroup[] entries = model.getNamedGroupArray();
        for (int g = 0; g < 4; g++) {
            boolean hasContent = model.groups[g].length > 0;
            for (int e = 0; e < entries.length && !hasContent; e++) {
                if (entries[e].tris[g].length > 0) hasContent = true;
            }
            if (!hasContent) { ids[g] = 0; continue; }

            int id = GL11.glGenLists(1);
            if (id == 0) {
                for (int j = 0; j < g; j++) {
                    if (ids[j] != 0) GL11.glDeleteLists(ids[j], 1);
                }
                return null;
            }
            GL11.glNewList(id, GL11.GL_COMPILE);
            GL11.glBegin(GL11.GL_TRIANGLES);
            emitTrisIntoList(model.groups[g], invSc);
            for (int e = 0; e < entries.length; e++) {
                emitTrisIntoList(entries[e].tris[g], invSc);
            }
            GL11.glEnd();
            GL11.glEndList();
            ids[g] = id;
        }
        return ids;
    }

    private static void emitTrisIntoList(float[][] tris, float invSc) {
        for (int i = 0; i < tris.length; i++) {
            float[] t = tris[i];
            GL11.glTexCoord2f(t[3],  t[4]);  GL11.glVertex3f(t[0]*invSc,  t[1]*invSc,  t[2]*invSc);
            GL11.glTexCoord2f(t[8],  t[9]);  GL11.glVertex3f(t[5]*invSc,  t[6]*invSc,  t[7]*invSc);
            GL11.glTexCoord2f(t[13], t[14]); GL11.glVertex3f(t[10]*invSc, t[11]*invSc, t[12]*invSc);
        }
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
        renderModel(model, x, y, z, rotation, world, ox, topY, oz, Aero_RenderOptions.DEFAULT);
    }

    public static void renderModel(Aero_MeshModel model, double x, double y, double z,
                                    float rotation, World world, int ox, int topY, int oz,
                                    Aero_RenderOptions options) {
        updateCameraForwardFromPlayer();
        if (!Aero_FrustumCull.isLikelyVisible(x, y, z)) return;
        Aero_Profiler.start("aero.mesh.render");
        try {
            Tessellator tess = Tessellator.instance;
            GL11.glPushMatrix();
            try {
                GL11.glTranslated(x, y, z);
                applyRotation(rotation);
                beginMeshState(options);
                try {
                    drawGroupsSmooth(tess, model.groups, model.invScale, model.getStaticSmoothLightData(),
                        world, ox, topY, oz, options);
                } finally {
                    endMeshState();
                }
            } finally {
                GL11.glPopMatrix();
            }
        } finally {
            Aero_Profiler.end("aero.mesh.render");
        }
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
        renderGroup(model, groupName, brightness, Aero_RenderOptions.DEFAULT);
    }

    public static void renderGroup(Aero_MeshModel model, String groupName, float brightness,
                                   Aero_RenderOptions options) {
        float[][][] ng = model.getNamedGroup(groupName);
        if (ng == null) return;
        Tessellator tess = Tessellator.instance;
        drawGroups(tess, ng, model.invScale, brightness, options);
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
        renderGroupRotated(model, groupName, x, y, z, brightness,
            pivotX, pivotY, pivotZ, angle, axisX, axisY, axisZ, Aero_RenderOptions.DEFAULT);
    }

    public static void renderGroupRotated(Aero_MeshModel model, String groupName,
                                           double x, double y, double z, float brightness,
                                           float pivotX, float pivotY, float pivotZ,
                                           float angle, float axisX, float axisY, float axisZ,
                                           Aero_RenderOptions options) {
        updateCameraForwardFromPlayer();
        if (!Aero_FrustumCull.isLikelyVisible(x, y, z)) return;
        float[][][] ng = model.getNamedGroup(groupName);
        if (ng == null) return;

        Tessellator tess = Tessellator.instance;
        GL11.glPushMatrix();
        try {
            GL11.glTranslated(x, y, z);
            GL11.glTranslatef(pivotX, pivotY, pivotZ);
            GL11.glRotatef(angle, axisX, axisY, axisZ);
            GL11.glTranslatef(-pivotX, -pivotY, -pivotZ);
            beginMeshState(options);
            try {
                drawGroups(tess, ng, model.invScale, brightness, options);
            } finally {
                endMeshState();
            }
        } finally {
            GL11.glPopMatrix();
        }
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

    /**
     * Renders a complete model with platform-neutral animation playback.
     * This overload is useful for entity helpers, tools and tests that do not
     * need the loader-specific NBT adapter.
     */
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
     * Bundle/def/state overload with procedural pose + IK chain hooks.
     * IK chains run between keyframe-pose resolution and the GL render
     * walk: the solver mutates intermediate-bone rotations to bring each
     * chain's end-effector close to its target, then the rendered pose
     * reflects the IK-corrected angles.
     */
    public static void renderAnimated(Aero_MeshModel model,
                                       Aero_AnimationBundle bundle,
                                       Aero_AnimationDefinition def,
                                       Aero_AnimationPlayback state,
                                       double x, double y, double z,
                                       float brightness, float partialTick,
                                       Aero_RenderOptions options,
                                       Aero_ProceduralPose proceduralPose,
                                       Aero_IkChain[] ikChains) {
        renderAnimatedInternal(model, bundle, def, state, x, y, z, brightness,
            partialTick, options, proceduralPose, ikChains, null);
    }

    /**
     * Maximal overload with morph state on top of procedural + IK. The
     * morph state blends static-geometry vertex positions before emit;
     * named-group (per-bone animated) parts are not morphed in v0.2.0.
     */
    public static void renderAnimated(Aero_MeshModel model,
                                       Aero_AnimationBundle bundle,
                                       Aero_AnimationDefinition def,
                                       Aero_AnimationPlayback state,
                                       double x, double y, double z,
                                       float brightness, float partialTick,
                                       Aero_RenderOptions options,
                                       Aero_ProceduralPose proceduralPose,
                                       Aero_IkChain[] ikChains,
                                       Aero_MorphState morphState) {
        renderAnimatedInternal(model, bundle, def, state, x, y, z, brightness,
            partialTick, options, proceduralPose, ikChains, morphState);
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
        renderAnimatedInternal(model, bundle, def, state, x, y, z, brightness,
            partialTick, options, proceduralPose, null, null);
    }

    private static void renderAnimatedInternal(Aero_MeshModel model,
                                                Aero_AnimationBundle bundle,
                                                Aero_AnimationDefinition def,
                                                Aero_AnimationPlayback state,
                                                double x, double y, double z,
                                                float brightness, float partialTick,
                                                Aero_RenderOptions options,
                                                Aero_ProceduralPose proceduralPose,
                                                Aero_IkChain[] ikChains,
                                                Aero_MorphState morphState) {
        updateCameraForwardFromPlayer();
        if (!Aero_FrustumCull.isLikelyVisible(x, y, z)) return;

        // Distance LOD: beyond the recommended animated radius (scales with
        // player render-distance setting), fall through to the display-list
        // at-rest path. Eliminates per-frame Tessellator cost for far entities
        // without requiring every renderer to plumb its own lodRelative call.
        // The threshold uses Aero_AnimationTickLOD's policy so tick + render
        // LOD line up at the same distance.
        double distSq = x * x + y * y + z * z;
        double animatedRadius = Aero_AnimationTickLOD.recommendedAnimatedDistance(
            Aero_RenderDistance.currentViewDistance());
        if (distSq > animatedRadius * animatedRadius) {
            renderModelAtRest(model, x, y, z, 0f, brightness, options);
            return;
        }

        Aero_Profiler.start("aero.mesh.renderAnimated");
        try {
            Aero_MeshModel.NamedGroup[] entries = model.getNamedGroupArray();
            Aero_AnimationClip clip = null;
            float time = 0f;
            Aero_MeshModel.BoneRef[] refs = null;
            Aero_BoneRenderPose[] pool = null;
            if (entries.length != 0) {
                clip = state.getCurrentClip();
                time = state.getInterpolatedTime(partialTick);
                refs = model.boneRefsFor(clip, bundle);

                if (clip != null) {
                    pool = ensurePoolSize(clip.boneNames.length);
                    // Pass 1: resolve every animated bone's pose into the
                    // pool so the hierarchical render walk + any IK pre-pass
                    // can read parent poses while drawing children.
                    for (int b = 0; b < clip.boneNames.length; b++) {
                        String boneName = clip.boneNames[b];
                        float[] pivot = bundle.pivotOrZero(boneName);
                        Aero_AnimationPoseResolver.resolveClip(b, boneName, pivot,
                            clip, state, time, partialTick,
                            SCRATCH_ROT, SCRATCH_POS, SCRATCH_SCL, pool[b]);
                        if (proceduralPose != null) proceduralPose.apply(boneName, pool[b]);
                    }

                    // Pass 1.5: run IK chains. Only present when the
                    // caller provided ikChains; v0.1 callers pay zero cost
                    // since the array is null.
                    if (ikChains != null && ikChains.length > 0) {
                        Aero_Profiler.start("aero.mesh.ikSolve");
                        try {
                            runIkChains(ikChains, clip, bundle, pool);
                        } finally {
                            Aero_Profiler.end("aero.mesh.ikSolve");
                        }
                    }
                }
            }

            Tessellator tess = Tessellator.instance;
            GL11.glPushMatrix();
            try {
                GL11.glTranslated(x, y, z);
                beginMeshState(options);
                try {
                    // Static geometry: morph-blended path when the caller
                    // provided a non-empty MorphState and the model carries
                    // morph targets; else the raw fast path.
                    if (morphState != null && model.hasMorphTargets() && !morphState.isEmpty()) {
                        drawGroupsMorph(tess, model, brightness, options, morphState);
                    } else {
                        drawGroups(tess, model.groups, model.invScale, brightness, options);
                    }

                    for (int e = 0; e < entries.length; e++) {
                        Aero_MeshModel.NamedGroup ng = entries[e];
                        Aero_MeshModel.BoneRef    rf = refs[e];

                        GL11.glPushMatrix();
                        try {
                            // Pass 2: walk root → leaf, applying each
                            // animated ancestor's pose. Parent rotations
                            // propagate to children automatically.
                            Aero_BoneRenderPose deepest = pool != null
                                ? applyPoseChain(rf, pool)
                                : null;
                            float uOff   = deepest != null ? deepest.uOffset : 0f;
                            float vOff   = deepest != null ? deepest.vOffset : 0f;
                            float uScale = deepest != null ? deepest.uScale  : 1f;
                            float vScale = deepest != null ? deepest.vScale  : 1f;
                            drawGroups(tess, ng.tris, model.invScale, brightness, options,
                                uOff, vOff, uScale, vScale);
                        } finally {
                            GL11.glPopMatrix();
                        }
                    }
                } finally {
                    endMeshState();
                }
            } finally {
                GL11.glPopMatrix();
            }
        } finally {
            Aero_Profiler.end("aero.mesh.renderAnimated");
        }
    }

    /**
     * Renders with a playback object that already owns its definition/bundle.
     */
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
     * Renders the model with an {@link Aero_AnimationGraph} driving every
     * bone's pose. Bones are looked up by name (no hierarchy walk — graph
     * rendering is flat in v0.2.0 because Graph itself doesn't model
     * parent-child relationships). The bundle is used for pivot lookup.
     */
    public static void renderAnimated(Aero_MeshModel model,
                                       Aero_AnimationGraph graph,
                                       Aero_AnimationBundle bundle,
                                       double x, double y, double z,
                                       float brightness, float partialTick) {
        renderAnimated(model, graph, bundle, x, y, z, brightness, partialTick,
            Aero_RenderOptions.DEFAULT);
    }

    public static void renderAnimated(Aero_MeshModel model,
                                       Aero_AnimationGraph graph,
                                       Aero_AnimationBundle bundle,
                                       double x, double y, double z,
                                       float brightness, float partialTick,
                                       Aero_RenderOptions options) {
        if (graph == null) throw new IllegalArgumentException("graph must not be null");
        if (bundle == null) throw new IllegalArgumentException("bundle must not be null");
        Aero_Profiler.start("aero.mesh.renderAnimated");
        try {
            Aero_MeshModel.NamedGroup[] entries = model.getNamedGroupArray();
            Tessellator tess = Tessellator.instance;
            GL11.glPushMatrix();
            try {
                GL11.glTranslated(x, y, z);
                beginMeshState(options);
                try {
                    drawGroups(tess, model.groups, model.invScale, brightness, options);

                    for (int e = 0; e < entries.length; e++) {
                        Aero_MeshModel.NamedGroup ng = entries[e];
                        String boneName = ng.name;

                        SCRATCH_POSE.reset();
                        bundle.getPivotInto(boneName, SCRATCH_PIVOT);
                        SCRATCH_POSE.setPivot(SCRATCH_PIVOT);
                        graph.samplePose(boneName, partialTick,
                            SCRATCH_ROT, SCRATCH_POS, SCRATCH_SCL);
                        SCRATCH_POSE.rotX = SCRATCH_ROT[0];
                        SCRATCH_POSE.rotY = SCRATCH_ROT[1];
                        SCRATCH_POSE.rotZ = SCRATCH_ROT[2];
                        SCRATCH_POSE.offsetX = SCRATCH_POS[0] / 16f;
                        SCRATCH_POSE.offsetY = SCRATCH_POS[1] / 16f;
                        SCRATCH_POSE.offsetZ = SCRATCH_POS[2] / 16f;
                        SCRATCH_POSE.scaleX = SCRATCH_SCL[0];
                        SCRATCH_POSE.scaleY = SCRATCH_SCL[1];
                        SCRATCH_POSE.scaleZ = SCRATCH_SCL[2];

                        GL11.glPushMatrix();
                        try {
                            applyPose(SCRATCH_POSE);
                            drawGroups(tess, ng.tris, model.invScale, brightness, options);
                        } finally {
                            GL11.glPopMatrix();
                        }
                    }
                } finally {
                    endMeshState();
                }
            } finally {
                GL11.glPopMatrix();
            }
        } finally {
            Aero_Profiler.end("aero.mesh.renderAnimated");
        }
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
        Aero_Profiler.start("aero.mesh.renderAnimated");
        try {
            Aero_MeshModel.NamedGroup[] entries = model.getNamedGroupArray();
            Tessellator tess = Tessellator.instance;
            GL11.glPushMatrix();
            try {
                GL11.glTranslated(x, y, z);
                beginMeshState(options);
                try {
                    drawGroups(tess, model.groups, model.invScale, brightness, options);

                    for (int e = 0; e < entries.length; e++) {
                        Aero_MeshModel.NamedGroup ng = entries[e];
                        Aero_AnimationPoseResolver.resolveStack(stack, ng.name, partialTick,
                            SCRATCH_PIVOT, SCRATCH_ROT, SCRATCH_POS, SCRATCH_SCL, SCRATCH_POSE);
                        if (proceduralPose != null) proceduralPose.apply(ng.name, SCRATCH_POSE);

                        GL11.glPushMatrix();
                        try {
                            applyPose(SCRATCH_POSE);
                            drawGroups(tess, ng.tris, model.invScale, brightness, options,
                                SCRATCH_POSE.uOffset, SCRATCH_POSE.vOffset,
                                SCRATCH_POSE.uScale,  SCRATCH_POSE.vScale);
                        } finally {
                            GL11.glPopMatrix();
                        }
                    }
                } finally {
                    endMeshState();
                }
            } finally {
                GL11.glPopMatrix();
            }
        } finally {
            Aero_Profiler.end("aero.mesh.renderAnimated");
        }
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

    /**
     * Walks the resolved ancestor chain (root → leaf) of the given BoneRef
     * and applies each ancestor's pose to the current GL matrix, so a child
     * bone's geometry inherits every animated parent's transform. This is
     * what makes parent rotations propagate to children — Blockbench's
     * default animator behavior.
     *
     * <p>Returns the deepest ancestor's pose (the leaf) so the caller can
     * read its UV transform fields for the per-vertex emit.
     */
    private static Aero_BoneRenderPose applyPoseChain(Aero_MeshModel.BoneRef rf,
                                                       Aero_BoneRenderPose[] pool) {
        int len = rf.ancestorBoneIdx.length;
        if (len == 0) return null;
        Aero_BoneRenderPose deepest = null;
        for (int c = 0; c < len; c++) {
            Aero_BoneRenderPose pose = pool[rf.ancestorBoneIdx[c]];
            applyPose(pose);
            deepest = pose;
        }
        return deepest;
    }

    private static Aero_BoneRenderPose[] newPosePool(int size) {
        Aero_BoneRenderPose[] pool = new Aero_BoneRenderPose[size];
        for (int i = 0; i < size; i++) pool[i] = new Aero_BoneRenderPose();
        return pool;
    }

    private static Aero_BoneRenderPose[] ensurePoolSize(int size) {
        if (POSE_POOL.length >= size) return POSE_POOL;
        Aero_BoneRenderPose[] grown = new Aero_BoneRenderPose[size];
        System.arraycopy(POSE_POOL, 0, grown, 0, POSE_POOL.length);
        for (int i = POSE_POOL.length; i < size; i++) grown[i] = new Aero_BoneRenderPose();
        POSE_POOL = grown;
        return grown;
    }

    /**
     * Resolves each {@link Aero_IkChain}'s named bones to indices + pivots
     * via the active clip, then dispatches to {@link Aero_CCDSolver}. Chain
     * names that are missing from the clip are silently skipped (caller
     * receives no exception so a transient missing bone in a partial state
     * doesn't crash the render).
     */
    private static void runIkChains(Aero_IkChain[] chains,
                                     Aero_AnimationClip clip,
                                     Aero_AnimationBundle bundle,
                                     Aero_BoneRenderPose[] pool) {
        float[] target = new float[3];
        for (int c = 0; c < chains.length; c++) {
            Aero_IkChain chain = chains[c];
            if (chain == null) continue;
            String[] names = chain.getBoneChain();
            if (names == null || names.length < 2) continue;

            int[] boneIdx = new int[names.length];
            float[][] pivots = new float[names.length][];
            boolean valid = true;
            for (int i = 0; i < names.length; i++) {
                int idx = clip.indexOfBone(names[i]);
                if (idx < 0) { valid = false; break; }
                boneIdx[i] = idx;
                pivots[i] = bundle.pivotOrZero(names[i]);
            }
            if (!valid) continue;

            if (!chain.resolveTargetInto(target)) continue;
            Aero_CCDSolver.solve(boneIdx, pivots, pool, target,
                Aero_CCDSolver.DEFAULT_TOLERANCE);
        }
    }

    /** Draws triangle groups at full brightness — used by Aero_InventoryRenderer. */
    static void drawGroupsForInventory(Tessellator tess, float[][][] groups, float invSc) {
        drawGroups(tess, groups, invSc, 1.0f, Aero_RenderOptions.DEFAULT);
    }

    /**
     * Static-geometry draw with morph-target blending. Per-vertex applies
     * {@code finalPos = base + Σ(weight × delta)} across every active
     * target. Skips back to the raw fast path when no targets have a
     * non-zero weight, so the caller doesn't need to gate this externally.
     */
    private static void drawGroupsMorph(Tessellator tess, Aero_MeshModel model,
                                         float brightness, Aero_RenderOptions options,
                                         Aero_MorphState morphState) {
        // Snapshot active (target, weight) pairs so the inner loop is a flat
        // index walk instead of a HashMap iteration per triangle.
        java.util.Map weights = morphState.getWeightsView();
        Aero_MorphTarget[] activeTargets = new Aero_MorphTarget[weights.size()];
        float[] activeWeights = new float[weights.size()];
        int activeCount = 0;
        java.util.Iterator it = weights.entrySet().iterator();
        while (it.hasNext()) {
            java.util.Map.Entry e = (java.util.Map.Entry) it.next();
            float w = ((Float) e.getValue()).floatValue();
            if (w == 0f) continue;
            Aero_MorphTarget target = model.getMorphTarget((String) e.getKey());
            if (target == null) continue;
            activeTargets[activeCount] = target;
            activeWeights[activeCount] = w;
            activeCount++;
        }
        if (activeCount == 0) {
            drawGroups(tess, model.groups, model.invScale, brightness, options);
            return;
        }

        float invSc = model.invScale;
        tess.startDrawing(GL11.GL_TRIANGLES);
        for (int g = 0; g < 4; g++) {
            float[][] tris = model.groups[g];
            if (tris.length == 0) continue;
            float bright = brightness * Aero_MeshModel.BRIGHTNESS_FACTORS[g];
            tess.setColorRGBA_F(bright * options.tintR, bright * options.tintG,
                bright * options.tintB, options.alpha);
            for (int i = 0; i < tris.length; i++) {
                float[] t = tris[i];
                float v0dx = 0f, v0dy = 0f, v0dz = 0f;
                float v1dx = 0f, v1dy = 0f, v1dz = 0f;
                float v2dx = 0f, v2dy = 0f, v2dz = 0f;
                for (int a = 0; a < activeCount; a++) {
                    float[] td = activeTargets[a].deltas[g][i];
                    float w = activeWeights[a];
                    v0dx += td[0] * w; v0dy += td[1] * w; v0dz += td[2] * w;
                    v1dx += td[3] * w; v1dy += td[4] * w; v1dz += td[5] * w;
                    v2dx += td[6] * w; v2dy += td[7] * w; v2dz += td[8] * w;
                }
                tess.addVertexWithUV((t[0]  + v0dx) * invSc, (t[1]  + v0dy) * invSc, (t[2]  + v0dz) * invSc, t[3],  t[4]);
                tess.addVertexWithUV((t[5]  + v1dx) * invSc, (t[6]  + v1dy) * invSc, (t[7]  + v1dz) * invSc, t[8],  t[9]);
                tess.addVertexWithUV((t[10] + v2dx) * invSc, (t[11] + v2dy) * invSc, (t[12] + v2dz) * invSc, t[13], t[14]);
            }
        }
        tess.draw();
    }

    /** Draws triangle groups with flat lighting (uniform brightness per group). */
    private static void drawGroups(Tessellator tess, float[][][] groups, float invSc,
                                   float brightness, Aero_RenderOptions options) {
        // Identity UV transform — fast path that emits raw u/v with no per-vertex math.
        drawGroups(tess, groups, invSc, brightness, options, 0f, 0f, 1f, 1f);
    }

    /**
     * UV-aware variant. Animated bones pass their pose's UV offset/scale so
     * the emit loop transforms each vertex's u/v before tessellation. When
     * the transform is identity (the default for any bone with no
     * uv_offset / uv_scale channels), the inner emit loop branches to the
     * raw path that allocates zero work per vertex.
     */
    private static void drawGroups(Tessellator tess, float[][][] groups, float invSc,
                                   float brightness, Aero_RenderOptions options,
                                   float uOff, float vOff, float uScale, float vScale) {
        boolean uvIdentity = uOff == 0f && vOff == 0f && uScale == 1f && vScale == 1f;
        tess.startDrawing(GL11.GL_TRIANGLES);
        for (int g = 0; g < 4; g++) {
            float[][] tris = groups[g];
            if (tris.length == 0) continue;
            float bright = brightness * Aero_MeshModel.BRIGHTNESS_FACTORS[g];
            tess.setColorRGBA_F(bright * options.tintR, bright * options.tintG, bright * options.tintB, options.alpha);
            if (uvIdentity) {
                for (int i = 0; i < tris.length; i++) {
                    float[] t = tris[i];
                    tess.addVertexWithUV(t[0]*invSc,  t[1]*invSc,  t[2]*invSc,  t[3],  t[4]);
                    tess.addVertexWithUV(t[5]*invSc,  t[6]*invSc,  t[7]*invSc,  t[8],  t[9]);
                    tess.addVertexWithUV(t[10]*invSc, t[11]*invSc, t[12]*invSc, t[13], t[14]);
                }
            } else {
                for (int i = 0; i < tris.length; i++) {
                    float[] t = tris[i];
                    tess.addVertexWithUV(t[0]*invSc,  t[1]*invSc,  t[2]*invSc,
                        t[3] *uScale + uOff,  t[4] *vScale + vOff);
                    tess.addVertexWithUV(t[5]*invSc,  t[6]*invSc,  t[7]*invSc,
                        t[8] *uScale + uOff,  t[9] *vScale + vOff);
                    tess.addVertexWithUV(t[10]*invSc, t[11]*invSc, t[12]*invSc,
                        t[13]*uScale + uOff, t[14]*vScale + vOff);
                }
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
    private static void drawGroupsSmooth(Tessellator tess, float[][][] groups, float invSc,
                                          Aero_MeshModel.SmoothLightData light,
                                          World world, int ox, int topY, int oz,
                                          Aero_RenderOptions options) {
        if (!light.hasTriangles) return;
        // +1 cell on the high side for the bilinear neighbor.
        int xLo = fastFloor(ox + light.minX);
        int xHi = fastFloor(ox + light.maxX) + 1;
        int zLo = fastFloor(oz + light.minZ);
        int zHi = fastFloor(oz + light.maxZ) + 1;
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
                tess.setColorRGBA_F(bright * options.tintR, bright * options.tintG, bright * options.tintB, options.alpha);
                tess.addVertexWithUV(t[0]*invSc,  t[1]*invSc,  t[2]*invSc,  t[3],  t[4]);
                tess.addVertexWithUV(t[5]*invSc,  t[6]*invSc,  t[7]*invSc,  t[8],  t[9]);
                tess.addVertexWithUV(t[10]*invSc, t[11]*invSc, t[12]*invSc, t[13], t[14]);
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
        if (options.cullFaces) {
            GL11.glEnable(GL11.GL_CULL_FACE);
            GL11.glCullFace(GL11.GL_BACK);
        } else {
            GL11.glDisable(GL11.GL_CULL_FACE);
        }
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
