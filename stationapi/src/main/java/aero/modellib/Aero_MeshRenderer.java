package aero.modellib;

import net.minecraft.client.render.Tessellator;
import net.minecraft.world.World;
import org.lwjgl.opengl.GL11;

import java.util.IdentityHashMap;

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
        GL11.GL_ENABLE_BIT | GL11.GL_DEPTH_BUFFER_BIT | GL11.GL_CURRENT_BIT
        | GL11.GL_COLOR_BUFFER_BIT | GL11.GL_TRANSFORM_BIT;

    private static final boolean BONE_PAGES_ENABLED =
        !"false".equalsIgnoreCase(System.getProperty("aero.bonepages"));
    private static final int BONE_PAGES_MIN_TRIS =
        Math.max(0, Integer.getInteger("aero.bonepages.minTris", 24).intValue());

    // Reusable scratch buffers — render thread is single-threaded in Beta 1.7.3.
    private static float[] LIGHT_CACHE = new float[64];
    private static final float[] SCRATCH_ROT = new float[3];
    private static final float[] SCRATCH_POS = new float[3];
    private static final float[] SCRATCH_SCL = new float[3];
    private static final float[] SCRATCH_PIVOT = new float[3];
    private static final Aero_BoneRenderPose SCRATCH_POSE = new Aero_BoneRenderPose();
    private static final IdentityHashMap<Aero_MeshModel, BatchPlanCache> BATCH_PLAN_CACHE =
        new IdentityHashMap<Aero_MeshModel, BatchPlanCache>();

    // Pose pool indexed by clip.boneNames[i]. Pre-resolved once per frame
    // so the hierarchical render walk + any IK pre-pass can read poses by
    // ancestor index without re-resolving per child.
    private static Aero_BoneRenderPose[] POSE_POOL = newPosePool(16);

    private static final int ANIMATED_RENDER_CULLED = 0;
    private static final int ANIMATED_RENDER_ACTIVE = 1;
    private static final int ANIMATED_RENDER_STATIC_DONE = 2;

    // -----------------------------------------------------------------------
    // Frustum cull glue
    // -----------------------------------------------------------------------
    //
    // BlockEntity / EntityRenderDispatcher iterate every loaded entity in
    // distance range and never check the view frustum. When animated LOD is
    // bumped past vanilla's 64 block cap, half of those renders happen for
    // entities behind the player — full Tessellator + GL dispatch cost,
    // zero pixels on screen. updateCameraForward() refreshes the cached
    // forward vector from the local player; the actual cull is a single
    // dot product per render call (Aero_FrustumCull.isLikelyVisible).

    /**
     * Refreshes Aero_FrustumCull's cached forward vector from the local
     * player. Cheap (4 trig + 3 muls); no allocation. Called at the entry
     * of every public render method so a renderer that wraps us doesn't
     * have to know about frustum state.
     */
    private static void updateCameraForwardFromPlayer() {
        Aero_RenderDistance.updateCameraForwardFromPlayer();
    }

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
        updateCameraForwardFromPlayer();
        if (!Aero_FrustumCull.isLikelyVisible(x, y, z)) return;
        Aero_Profiler.start("aero.mesh.render");
        try {
            Tessellator tess = Tessellator.INSTANCE;
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

    public static void renderModelAtRest(Aero_MeshModel model, double x, double y, double z,
                                         float rotation, float brightness) {
        renderModelAtRest(model, x, y, z, rotation, brightness, Aero_RenderOptions.DEFAULT);
    }

    public static void renderModelAtRest(Aero_MeshModel model, double x, double y, double z,
                                         float rotation, float brightness,
                                         Aero_RenderOptions options) {
        updateCameraForwardFromPlayer();
        if (!Aero_FrustumCull.isLikelyVisible(x, y, z)) return;
        renderModelAtRestBody(model, x, y, z, rotation, brightness, options);
    }

    static void renderModelAtRestPreculled(Aero_MeshModel model, double x, double y, double z,
                                           float rotation, float brightness,
                                           Aero_RenderOptions options) {
        renderModelAtRestBody(model, x, y, z, rotation, brightness, options);
    }

    private static void renderModelAtRestBody(Aero_MeshModel model, double x, double y, double z,
                                              float rotation, float brightness,
                                              Aero_RenderOptions options) {
        Aero_Profiler.start("aero.mesh.render");
        try {
            GL11.glPushMatrix();
            try {
                GL11.glTranslated(x, y, z);
                applyRotation(rotation);
                beginMeshState(options);
                try {
                    if (!renderAtRestViaLists(model, brightness, options)) {
                        Tessellator tess = Tessellator.INSTANCE;
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
        int[] ids = ensureAtRestListIds(model);
        if (ids == null) return false;
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

    /**
     * Releases the GL display lists cached on a model. Must run on the GL
     * thread (single-threaded in Beta 1.7.3, so any block-entity tick / BER
     * call site is fine).
     *
     * <p><strong>When to actually call this.</strong> Beta has no stable
     * client-shutdown hook, and the GL driver releases every list on
     * context destruction anyway — calling this on game exit is redundant.
     * The intended call sites are:
     * <ul>
     *   <li>Resource-pack reload — bind a new texture, dispose the model,
     *       next render recompiles the lists with the new texture coords
     *       baked in.</li>
     *   <li>Model hot-swap during dev — replace the {@code Aero_MeshModel}
     *       reference with a freshly loaded one and dispose the old one
     *       before dropping it.</li>
     *   <li>Tooling / CI — disposing models between tests so the JVM can
     *       be reused without leaking list IDs.</li>
     * </ul>
     *
     * <p>Idempotent: a model that's never been rendered (or already disposed)
     * is a no-op. After dispose, the next render of the model recompiles
     * from scratch.
     */
    public static void disposeModel(Aero_MeshModel model) {
        if (model == null) return;
        Aero_BECellRenderer.disposeModel(model);
        int[] ids = model.extractAndClearAtRestListIds();
        deletePageIds(ids);
        deleteBonePageLists(model.extractAndClearBonePageLists());
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

    // -----------------------------------------------------------------------
    // Bone-page display lists for rigid animated groups
    // -----------------------------------------------------------------------

    private static boolean renderAnimatedViaBonePages(Aero_MeshModel model,
                                                       Aero_MeshModel.NamedGroup[] entries,
                                                       Aero_MeshModel.BoneRef[] refs,
                                                       Aero_BoneRenderPose[] pool,
                                                       double x, double y, double z,
                                                       float brightness,
                                                       Aero_RenderOptions options,
                                                       Aero_MorphState morphState) {
        if (!BONE_PAGES_ENABLED) return false;
        if (morphState != null && model.hasMorphTargets() && !morphState.isEmpty()) return false;

        Aero_BonePageLists pages = getOrCompileBonePageLists(model, entries);
        if (pages == null || !pages.hasAnyPages) return false;

        Aero_Profiler.start("aero.bonepages.call");
        try {
            Tessellator tess = Tessellator.INSTANCE;
            GL11.glPushMatrix();
            try {
                GL11.glTranslated(x, y, z);
                beginMeshState(options);
                try {
                    renderStaticPageOrFallback(tess, model, pages, brightness, options);
                    for (int e = 0; e < entries.length; e++) {
                        GL11.glPushMatrix();
                        try {
                            Aero_BoneRenderPose deepest = (pool != null && refs != null)
                                ? applyPoseChain(refs[e], pool)
                                : null;
                            int[] groupPages = pages.bonePages != null && e < pages.bonePages.length
                                ? pages.bonePages[e]
                                : null;
                            renderBonePageOrFallback(tess, groupPages, entries[e].tris,
                                model.invScale, brightness, options, deepest);
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
            Aero_Profiler.end("aero.bonepages.call");
        }
        return true;
    }

    static int[] ensureAtRestListIds(Aero_MeshModel model) {
        int[] ids = model.getAtRestListIds();
        if (ids != null) return ids;
        if (model.atRestListsCompileFailed()) return null;
        ids = compileAtRestLists(model);
        if (ids == null) {
            model.markAtRestListsCompileFailed();
            return null;
        }
        model.setAtRestListIds(ids);
        return ids;
    }

    private static boolean renderGraphViaBonePages(Aero_MeshModel model,
                                                    Aero_AnimationGraph graph,
                                                    Aero_AnimationBundle bundle,
                                                    Aero_MeshModel.NamedGroup[] entries,
                                                    double x, double y, double z,
                                                    float brightness, float partialTick,
                                                    Aero_RenderOptions options) {
        if (!BONE_PAGES_ENABLED) return false;
        Aero_BonePageLists pages = getOrCompileBonePageLists(model, entries);
        if (pages == null || !pages.hasAnyPages) return false;

        Aero_Profiler.start("aero.bonepages.call");
        try {
            Tessellator tess = Tessellator.INSTANCE;
            GL11.glPushMatrix();
            try {
                GL11.glTranslated(x, y, z);
                beginMeshState(options);
                try {
                    renderStaticPageOrFallback(tess, model, pages, brightness, options);
                    for (int e = 0; e < entries.length; e++) {
                        Aero_MeshModel.NamedGroup ng = entries[e];
                        SCRATCH_POSE.reset();
                        bundle.getPivotInto(ng.name, SCRATCH_PIVOT);
                        SCRATCH_POSE.setPivot(SCRATCH_PIVOT);
                        graph.samplePose(ng.name, partialTick,
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
                            renderBonePageOrFallback(tess, pageFor(pages, e), ng.tris,
                                model.invScale, brightness, options, SCRATCH_POSE);
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
            Aero_Profiler.end("aero.bonepages.call");
        }
        return true;
    }

    private static boolean renderStackViaBonePages(Aero_MeshModel model,
                                                    Aero_AnimationStack stack,
                                                    Aero_MeshModel.NamedGroup[] entries,
                                                    double x, double y, double z,
                                                    float brightness, float partialTick,
                                                    Aero_RenderOptions options,
                                                    Aero_ProceduralPose proceduralPose) {
        if (!BONE_PAGES_ENABLED) return false;
        Aero_BonePageLists pages = getOrCompileBonePageLists(model, entries);
        if (pages == null || !pages.hasAnyPages) return false;

        Aero_Profiler.start("aero.bonepages.call");
        try {
            Tessellator tess = Tessellator.INSTANCE;
            GL11.glPushMatrix();
            try {
                GL11.glTranslated(x, y, z);
                beginMeshState(options);
                try {
                    renderStaticPageOrFallback(tess, model, pages, brightness, options);
                    for (int e = 0; e < entries.length; e++) {
                        Aero_MeshModel.NamedGroup ng = entries[e];
                        Aero_AnimationPoseResolver.resolveStack(stack, ng.name, partialTick,
                            SCRATCH_PIVOT, SCRATCH_ROT, SCRATCH_POS, SCRATCH_SCL, SCRATCH_POSE);
                        if (proceduralPose != null) proceduralPose.apply(ng.name, SCRATCH_POSE);

                        GL11.glPushMatrix();
                        try {
                            applyPose(SCRATCH_POSE);
                            renderBonePageOrFallback(tess, pageFor(pages, e), ng.tris,
                                model.invScale, brightness, options, SCRATCH_POSE);
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
            Aero_Profiler.end("aero.bonepages.call");
        }
        return true;
    }

    private static Aero_BonePageLists getOrCompileBonePageLists(Aero_MeshModel model,
                                                                Aero_MeshModel.NamedGroup[] entries) {
        Aero_BonePageLists pages = model.getBonePageLists();
        if (pages != null) return pages;
        if (model.bonePageListsCompileFailed()) return null;

        Aero_Profiler.start("aero.bonepages.compile");
        try {
            pages = compileBonePageLists(model, entries);
        } finally {
            Aero_Profiler.end("aero.bonepages.compile");
        }
        if (pages == null) {
            model.markBonePageListsCompileFailed();
            return null;
        }
        model.setBonePageLists(pages);
        return pages;
    }

    private static Aero_BonePageLists compileBonePageLists(Aero_MeshModel model,
                                                           Aero_MeshModel.NamedGroup[] entries) {
        int[] staticPages = null;
        int[][] bonePages = new int[entries.length][];
        boolean any = false;

        if (eligibleForBonePage(model.groups)) {
            staticPages = compileBucketPages(model.groups, model.invScale);
            if (staticPages == null) return null;
            any |= hasPages(staticPages);
        }

        for (int e = 0; e < entries.length; e++) {
            if (!eligibleForBonePage(entries[e].tris)) continue;
            bonePages[e] = compileBucketPages(entries[e].tris, model.invScale);
            if (bonePages[e] == null) {
                deletePageIds(staticPages);
                deleteBonePageArrays(bonePages);
                return null;
            }
            any |= hasPages(bonePages[e]);
        }

        return new Aero_BonePageLists(staticPages, bonePages, any);
    }

    private static int[] compileBucketPages(float[][][] groups, float invSc) {
        int[] ids = new int[4];
        for (int g = 0; g < 4; g++) {
            float[][] tris = groups[g];
            if (tris.length == 0) continue;

            int id = GL11.glGenLists(1);
            if (id == 0) {
                deletePageIds(ids);
                return null;
            }
            GL11.glNewList(id, GL11.GL_COMPILE);
            GL11.glBegin(GL11.GL_TRIANGLES);
            emitTrisIntoList(tris, invSc);
            GL11.glEnd();
            GL11.glEndList();
            ids[g] = id;
        }
        return ids;
    }

    private static void renderStaticPageOrFallback(Tessellator tess, Aero_MeshModel model,
                                                    Aero_BonePageLists pages,
                                                    float brightness, Aero_RenderOptions options) {
        if (hasPages(pages.staticPages)) {
            callPageBuckets(pages.staticPages, brightness, options);
        } else if (hasTriangles(model.groups)) {
            drawGroups(tess, model.groups, model.invScale, brightness, options);
        }
    }

    private static void renderBonePageOrFallback(Tessellator tess, int[] ids,
                                                  float[][][] groups, float invSc,
                                                  float brightness, Aero_RenderOptions options,
                                                  Aero_BoneRenderPose pose) {
        if (hasPages(ids)) {
            boolean uv = pushUvMatrix(pose);
            try {
                callPageBuckets(ids, brightness, options);
            } finally {
                if (uv) popUvMatrix();
            }
            return;
        }

        float uOff   = pose != null ? pose.uOffset : 0f;
        float vOff   = pose != null ? pose.vOffset : 0f;
        float uScale = pose != null ? pose.uScale  : 1f;
        float vScale = pose != null ? pose.vScale  : 1f;
        drawGroups(tess, groups, invSc, brightness, options, uOff, vOff, uScale, vScale);
    }

    private static void callPageBuckets(int[] ids, float brightness, Aero_RenderOptions options) {
        for (int g = 0; g < 4; g++) {
            int id = ids[g];
            if (id == 0) continue;
            float bright = brightness * Aero_MeshModel.BRIGHTNESS_FACTORS[g];
            GL11.glColor4f(bright * options.tintR, bright * options.tintG,
                           bright * options.tintB, options.alpha);
            GL11.glCallList(id);
        }
    }

    private static boolean pushUvMatrix(Aero_BoneRenderPose pose) {
        if (pose == null || pose.uvIsIdentity()) return false;
        GL11.glMatrixMode(GL11.GL_TEXTURE);
        GL11.glPushMatrix();
        GL11.glTranslatef(pose.uOffset, pose.vOffset, 0f);
        GL11.glScalef(pose.uScale, pose.vScale, 1f);
        GL11.glMatrixMode(GL11.GL_MODELVIEW);
        return true;
    }

    private static void popUvMatrix() {
        GL11.glMatrixMode(GL11.GL_TEXTURE);
        GL11.glPopMatrix();
        GL11.glMatrixMode(GL11.GL_MODELVIEW);
    }

    private static int[] pageFor(Aero_BonePageLists pages, int index) {
        return pages.bonePages != null && index < pages.bonePages.length
            ? pages.bonePages[index]
            : null;
    }

    private static boolean eligibleForBonePage(float[][][] groups) {
        int n = triangleCount(groups);
        return n > 0 && n >= BONE_PAGES_MIN_TRIS;
    }

    private static int triangleCount(float[][][] groups) {
        int n = 0;
        for (int g = 0; g < 4; g++) n += groups[g].length;
        return n;
    }

    private static boolean hasTriangles(float[][][] groups) {
        for (int g = 0; g < 4; g++) {
            if (groups[g].length > 0) return true;
        }
        return false;
    }

    private static boolean hasPages(int[] ids) {
        if (ids == null) return false;
        for (int g = 0; g < ids.length; g++) {
            if (ids[g] != 0) return true;
        }
        return false;
    }

    private static void deleteBonePageLists(Aero_BonePageLists pages) {
        if (pages == null) return;
        deletePageIds(pages.staticPages);
        deleteBonePageArrays(pages.bonePages);
    }

    private static void deleteBonePageArrays(int[][] pages) {
        if (pages == null) return;
        for (int i = 0; i < pages.length; i++) {
            deletePageIds(pages[i]);
            pages[i] = null;
        }
    }

    private static void deletePageIds(int[] ids) {
        if (ids == null) return;
        for (int g = 0; g < ids.length; g++) {
            if (ids[g] != 0) {
                GL11.glDeleteLists(ids[g], 1);
                ids[g] = 0;
            }
        }
    }

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
            Tessellator tess = Tessellator.INSTANCE;
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
        updateCameraForwardFromPlayer();
        if (!Aero_FrustumCull.isLikelyVisible(x, y, z)) return;
        float[][][] ng = model.getNamedGroup(groupName);
        if (ng == null) return;

        Tessellator tess = Tessellator.INSTANCE;
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
     * Bundle/def/state overload with procedural pose + IK chain hooks.
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

    /** Maximal overload with morph state. */
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
        renderAnimatedInternal(model, bundle, def, state, x, y, z, brightness,
            partialTick, options, proceduralPose, ikChains, morphState, false);
    }

    static void renderAnimatedPreculled(Aero_MeshModel model,
                                        Aero_AnimationBundle bundle,
                                        Aero_AnimationDefinition def,
                                        Aero_AnimationPlayback state,
                                        double x, double y, double z,
                                        float brightness, float partialTick,
                                        Aero_RenderOptions options,
                                        Aero_ProceduralPose proceduralPose) {
        renderAnimatedInternal(model, bundle, def, state, x, y, z, brightness,
            partialTick, options, proceduralPose, null, null, true);
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
                                                Aero_MorphState morphState,
                                                boolean preculled) {
        if (!preculled
            && prepareAnimatedRender(model, x, y, z, brightness, options) != ANIMATED_RENDER_ACTIVE) return;

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
                    // Pre-resolved pivots — single bundle.resolvePivotsFor
                    // call replaces N×HashMap.get inside the loop body.
                    float[][] clipPivots = bundle.resolvePivotsFor(clip);
                    // Pass 1: pre-resolve every animated bone's pose.
                    for (int b = 0; b < clip.boneNames.length; b++) {
                        String boneName = clip.boneNames[b];
                        Aero_AnimationPoseResolver.resolveClip(b, boneName, clipPivots[b],
                            clip, state, time, partialTick,
                            SCRATCH_ROT, SCRATCH_POS, SCRATCH_SCL, pool[b]);
                        if (proceduralPose != null) proceduralPose.apply(boneName, pool[b]);
                    }

                    // Pass 1.5: run IK chains.
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

            if (renderAnimatedViaBonePages(model, entries, refs, pool, x, y, z,
                    brightness, options, morphState)) {
                return;
            }

            Tessellator tess = Tessellator.INSTANCE;
            GL11.glPushMatrix();
            try {
                GL11.glTranslated(x, y, z);
                beginMeshState(options);
                try {
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
                            // Pass 2: walk root → leaf, applying ancestors.
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
     * Renders the model with an {@link Aero_AnimationGraph} driving every
     * bone's pose. Bones are looked up by name (graph rendering is flat
     * in v0.2.0 — no hierarchy walk). The bundle provides pivot lookup.
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
        if (prepareAnimatedRender(model, x, y, z, brightness, options) != ANIMATED_RENDER_ACTIVE) return;
        Aero_Profiler.start("aero.mesh.renderAnimated");
        try {
            Aero_MeshModel.NamedGroup[] entries = model.getNamedGroupArray();
            if (renderGraphViaBonePages(model, graph, bundle, entries, x, y, z,
                    brightness, partialTick, options)) {
                return;
            }
            Tessellator tess = Tessellator.INSTANCE;
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
        if (prepareAnimatedRender(model, x, y, z, brightness, options) != ANIMATED_RENDER_ACTIVE) return;
        Aero_Profiler.start("aero.mesh.renderAnimated");
        try {
            Aero_MeshModel.NamedGroup[] entries = model.getNamedGroupArray();
            if (renderStackViaBonePages(model, stack, entries, x, y, z,
                    brightness, partialTick, options, proceduralPose)) {
                return;
            }

            Tessellator tess = Tessellator.INSTANCE;
            GL11.glPushMatrix();
            try {
                GL11.glTranslated(x, y, z);
                beginMeshState(options);
                try {
                    // Render the static (unnamed) geometry once at the BE origin.
                    drawGroups(tess, model.groups, model.invScale, brightness, options);

                    for (int e = 0; e < entries.length; e++) {
                        Aero_MeshModel.NamedGroup ng = entries[e];
                        String boneName = ng.name;

                        Aero_AnimationPoseResolver.resolveStack(stack, boneName, partialTick,
                            SCRATCH_PIVOT, SCRATCH_ROT, SCRATCH_POS, SCRATCH_SCL, SCRATCH_POSE);
                        if (proceduralPose != null) proceduralPose.apply(boneName, SCRATCH_POSE);

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
    // Batched animated render — v3.0 BE batching path
    // -----------------------------------------------------------------------
    //
    // Drains an Aero_AnimatedBatcher.Batch in a single Tessellator session
    // per bone (vs one per instance × bone in the non-batched path). Per-
    // vertex CPU matrix transforms replace the GL matrix-stack push/pop +
    // glRotate/glTranslate sequence each instance previously paid.
    //
    // Win is bounded by the GL state-setup cost saved per cycle; for the
    // stress test's 9-10 motor grid sharing one model with one named bone
    // ("rotor"), this collapses 9-10 tess cycles per frame into 1.
    //
    // Constraints (v3.0):
    //   - Flat-skeleton only: bones with ancestorBoneIdx.length > 1 fall
    //     back to per-instance rendering (composing nested ancestor poses
    //     in CPU is doable but adds complexity for marginal gain — most
    //     mass-produced static-machine BEs are flat).
    //   - No procedural pose / IK / morph batching — those route to the
    //     non-batched overload.

    public static void renderAnimatedBatch(Aero_AnimatedBatcher.Batch batch) {
        Aero_MeshModel model = batch.model;
        int count = batch.count;
        if (count == 0) return;

        // Use first instance's options; we assume all in a same-model
        // batch share render options (showcase BERs typically use DEFAULT).
        Aero_RenderOptions options = batch.options[0];
        if (options == null) options = Aero_RenderOptions.DEFAULT;

        Aero_Profiler.start("aero.mesh.renderAnimatedBatch");
        try {
            Aero_MeshModel.NamedGroup[] entries = model.getNamedGroupArray();
            BatchPlan renderPlan = batchPlanFor(model, null, batch.bundles[0], entries);
            // Per-instance, per-bone resolved poses. Lazy-resolve on first
            // need; null if instance has no named bones / nested skeleton.
            Aero_BoneRenderPose[][] perInstancePoses = ensureBatchPoseScratch(count, entries.length);
            boolean[][] perInstancePoseActive = ensureBatchPoseActiveScratch(count, entries.length);

            // Pre-resolve poses for all instances. Falls back to the
            // unbatched path (returns false) if any instance has nested
            // ancestor chain.
            boolean canBatch = resolveBatchPoses(model, batch, count, entries,
                perInstancePoses, perInstancePoseActive);
            if (!canBatch) {
                drainAsUnbatched(batch, count);
                return;
            }

            Tessellator tess = Tessellator.INSTANCE;
            // Bind the batch's texture once before any tess.draw — at this
            // point in the entity render pass, the texture state is whatever
            // the last unbatched BER bound. Our batched draws need the
            // model's own texture for the duration of all subsequent cycles.
            Aero_AnimatedBatcher.bindBatchTexture(batch);
            beginMeshState(options);
            try {
                // Static (unnamed) groups — no per-instance bone transform,
                // just translate. Single tess cycle for ALL instances.
                if (renderPlan.hasStaticGeometry) {
                    tess.start(GL11.GL_TRIANGLES);
                    // Dedup tess.color across (instance, bucket) — instances
                    // in the same chunk share lighting so consecutive bright
                    // values usually match.
                    float lastBrightStatic = Float.NaN;
                    for (int g = 0; g < 4; g++) {
                        float[][] tris = model.groups[g];
                        if (tris.length == 0) continue;
                        float bucketFactor = Aero_MeshModel.BRIGHTNESS_FACTORS[g];
                        lastBrightStatic = emitStaticInstancesBatched(tess, tris,
                            model.invScale, bucketFactor, batch, count, options,
                            lastBrightStatic);
                    }
                    tess.draw();
                }

                // Named bones — per-vertex CPU matrix transform composes
                // pose + instance translate. Single tess cycle per bone
                // for ALL instances.
                for (int d = 0; d < renderPlan.drawableEntries.length; d++) {
                    int e = renderPlan.drawableEntries[d];
                    Aero_MeshModel.NamedGroup ng = entries[e];

                    tess.start(GL11.GL_TRIANGLES);
                    // tess.color dedup — instances inside a batch share a
                    // chunk so most consecutive brightness values match.
                    // NaN sentinel forces the first iteration to set the
                    // color; downstream comparisons skip the JNI call when
                    // the bright value is identical.
                    float lastBright = Float.NaN;
                    for (int g = 0; g < 4; g++) {
                        float[][] tris = ng.tris[g];
                        if (tris.length == 0) continue;
                        float bucketFactor = Aero_MeshModel.BRIGHTNESS_FACTORS[g];
                        for (int i = 0; i < count; i++) {
                            float bright = batch.brightnesses[i] * bucketFactor;
                            if (bright != lastBright) {
                                tess.color(bright * options.tintR, bright * options.tintG,
                                           bright * options.tintB, options.alpha);
                                lastBright = bright;
                            }
                            if (!perInstancePoseActive[i][e]) {
                                // Named group with no animated bone in the
                                // clip (e.g. static body parts of a model
                                // whose only animated parts are sub-bones).
                                // Match the unbatched path: render at rest
                                // pose, no transform — just instance
                                // translate. Otherwise the static body
                                // disappears, leaving "fans floating in air".
                                emitBoneInstanceBatchedRest(tess, tris,
                                    model.invScale,
                                    batch.xs[i], batch.ys[i], batch.zs[i]);
                            } else {
                                Aero_BoneRenderPose pose = perInstancePoses[i][e];
                                emitBoneInstanceBatched(tess, tris, model.invScale, pose,
                                    batch.xs[i], batch.ys[i], batch.zs[i]);
                            }
                        }
                    }
                    tess.draw();
                }
            } finally {
                endMeshState();
            }
        } finally {
            Aero_Profiler.end("aero.mesh.renderAnimatedBatch");
        }
    }

    /**
     * Per-call scratch grown on demand. Holds {@code [instanceCount][boneCount]}
     * resolved poses for the duration of one batched render. The poses
     * themselves are pulled from {@link #POSE_POOL} (per-bone reuse) and
     * snapshotted into the matching {@code BoneRef} index.
     */
    private static Aero_BoneRenderPose[][] BATCH_POSES = new Aero_BoneRenderPose[16][];
    private static boolean[][] BATCH_POSE_ACTIVE = new boolean[16][];

    private static Aero_BoneRenderPose[][] ensureBatchPoseScratch(int instanceCount, int boneCount) {
        if (BATCH_POSES.length < instanceCount) {
            BATCH_POSES = new Aero_BoneRenderPose[Math.max(instanceCount, BATCH_POSES.length * 2)][];
        }
        for (int i = 0; i < instanceCount; i++) {
            if (BATCH_POSES[i] == null || BATCH_POSES[i].length < boneCount) {
                BATCH_POSES[i] = new Aero_BoneRenderPose[Math.max(boneCount, 4)];
            }
            for (int b = 0; b < boneCount; b++) {
                if (BATCH_POSES[i][b] == null) BATCH_POSES[i][b] = new Aero_BoneRenderPose();
                else BATCH_POSES[i][b].reset();
            }
        }
        return BATCH_POSES;
    }

    private static boolean[][] ensureBatchPoseActiveScratch(int instanceCount, int boneCount) {
        if (BATCH_POSE_ACTIVE.length < instanceCount) {
            boolean[][] grown = new boolean[Math.max(instanceCount, BATCH_POSE_ACTIVE.length * 2)][];
            System.arraycopy(BATCH_POSE_ACTIVE, 0, grown, 0, BATCH_POSE_ACTIVE.length);
            BATCH_POSE_ACTIVE = grown;
        }
        for (int i = 0; i < instanceCount; i++) {
            if (BATCH_POSE_ACTIVE[i] == null || BATCH_POSE_ACTIVE[i].length < boneCount) {
                BATCH_POSE_ACTIVE[i] = new boolean[Math.max(boneCount, 4)];
            } else {
                for (int b = 0; b < boneCount; b++) BATCH_POSE_ACTIVE[i][b] = false;
            }
        }
        return BATCH_POSE_ACTIVE;
    }

    /**
     * Resolves per-instance bone poses for the batch. Returns false if
     * any instance has a nested ancestor chain (multi-bone-deep), in
     * which case the caller must fall back to per-instance rendering.
     */
    private static boolean resolveBatchPoses(Aero_MeshModel model,
                                              Aero_AnimatedBatcher.Batch batch, int count,
                                              Aero_MeshModel.NamedGroup[] entries,
                                              Aero_BoneRenderPose[][] perInstancePoses,
                                              boolean[][] perInstancePoseActive) {
        for (int i = 0; i < count; i++) {
            Aero_AnimationPlayback state = batch.states[i];
            Aero_AnimationBundle bundle = batch.bundles[i];
            Aero_AnimationClip clip = state.getCurrentClip();
            BatchPlan plan = batchPlanFor(model, clip, bundle, entries);
            if (!plan.batchableFlat) return false;
            if (clip == null) {
                // No active clip — render named groups at rest.
                continue;
            }
            float time = state.getInterpolatedTime(batch.partialTicks[i]);
            Aero_BoneRenderPose[] pool = ensurePoolSize(clip.boneNames.length);
            float[][] clipPivots = bundle.resolvePivotsFor(clip);
            for (int b = 0; b < clip.boneNames.length; b++) {
                String boneName = clip.boneNames[b];
                Aero_AnimationPoseResolver.resolveClip(b, boneName, clipPivots[b],
                    clip, state, time, batch.partialTicks[i],
                    SCRATCH_ROT, SCRATCH_POS, SCRATCH_SCL, pool[b]);
            }
            // Snapshot deepest (== self for flat skeletons) per bone group.
            for (int e = 0; e < entries.length; e++) {
                int idx = plan.entryBoneIdx[e];
                if (idx >= 0) {
                    copyPose(pool[idx], perInstancePoses[i][e]);
                    perInstancePoseActive[i][e] = true;
                }
            }
        }
        return true;
    }

    private static void copyPose(Aero_BoneRenderPose src, Aero_BoneRenderPose dst) {
        dst.pivotX = src.pivotX; dst.pivotY = src.pivotY; dst.pivotZ = src.pivotZ;
        dst.offsetX = src.offsetX; dst.offsetY = src.offsetY; dst.offsetZ = src.offsetZ;
        dst.rotX = src.rotX; dst.rotY = src.rotY; dst.rotZ = src.rotZ;
        dst.scaleX = src.scaleX; dst.scaleY = src.scaleY; dst.scaleZ = src.scaleZ;
        dst.uOffset = src.uOffset; dst.vOffset = src.vOffset;
        dst.uScale = src.uScale; dst.vScale = src.vScale;
    }

    private static boolean hasStaticGeometry(Aero_MeshModel model) {
        for (int g = 0; g < 4; g++) {
            if (model.groups[g].length > 0) return true;
        }
        return false;
    }

    private static BatchPlan batchPlanFor(Aero_MeshModel model,
                                          Aero_AnimationClip clip,
                                          Aero_AnimationBundle bundle,
                                          Aero_MeshModel.NamedGroup[] entries) {
        BatchPlanCache cache = BATCH_PLAN_CACHE.get(model);
        if (cache == null) {
            cache = new BatchPlanCache();
            BATCH_PLAN_CACHE.put(model, cache);
        }
        return cache.get(model, clip, bundle, entries);
    }

    private static final class BatchPlanCache {
        private static final int SIZE = 8;
        private final Aero_AnimationClip[] clips = new Aero_AnimationClip[SIZE];
        private final Aero_AnimationBundle[] bundles = new Aero_AnimationBundle[SIZE];
        private final BatchPlan[] plans = new BatchPlan[SIZE];
        private int nextSlot;

        BatchPlan get(Aero_MeshModel model, Aero_AnimationClip clip,
                      Aero_AnimationBundle bundle, Aero_MeshModel.NamedGroup[] entries) {
            for (int i = 0; i < SIZE; i++) {
                BatchPlan plan = plans[i];
                if (plan != null && clips[i] == clip && bundles[i] == bundle) return plan;
            }
            BatchPlan plan = buildBatchPlan(model, clip, bundle, entries);
            int slot = nextSlot;
            clips[slot] = clip;
            bundles[slot] = bundle;
            plans[slot] = plan;
            nextSlot = (slot + 1) & (SIZE - 1);
            return plan;
        }
    }

    private static BatchPlan buildBatchPlan(Aero_MeshModel model,
                                            Aero_AnimationClip clip,
                                            Aero_AnimationBundle bundle,
                                            Aero_MeshModel.NamedGroup[] entries) {
        int[] entryBoneIdx = new int[entries.length];
        int[] drawableTmp = new int[entries.length];
        int drawableCount = 0;
        for (int e = 0; e < entries.length; e++) {
            entryBoneIdx[e] = -1;
            if (hasGeometry(entries[e])) drawableTmp[drawableCount++] = e;
        }

        boolean batchableFlat = true;
        if (bundle != null) {
            Aero_MeshModel.BoneRef[] refs = model.boneRefsFor(clip, bundle);
            for (int e = 0; e < entries.length; e++) {
                int len = refs[e].ancestorBoneIdx.length;
                if (len > 1) {
                    batchableFlat = false;
                } else if (len == 1) {
                    entryBoneIdx[e] = refs[e].ancestorBoneIdx[0];
                }
            }
        } else if (clip != null) {
            batchableFlat = false;
        }

        int[] drawableEntries = new int[drawableCount];
        System.arraycopy(drawableTmp, 0, drawableEntries, 0, drawableCount);
        return new BatchPlan(batchableFlat, entryBoneIdx, drawableEntries,
            hasStaticGeometry(model));
    }

    private static boolean hasGeometry(Aero_MeshModel.NamedGroup group) {
        for (int g = 0; g < 4; g++) {
            if (group.tris[g].length > 0) return true;
        }
        return false;
    }

    private static final class BatchPlan {
        final boolean batchableFlat;
        final int[] entryBoneIdx;
        final int[] drawableEntries;
        final boolean hasStaticGeometry;

        BatchPlan(boolean batchableFlat, int[] entryBoneIdx,
                  int[] drawableEntries, boolean hasStaticGeometry) {
            this.batchableFlat = batchableFlat;
            this.entryBoneIdx = entryBoneIdx;
            this.drawableEntries = drawableEntries;
            this.hasStaticGeometry = hasStaticGeometry;
        }
    }

    /**
     * Emits all batch instances of {@code tris} for a single brightness
     * bucket. Returns the last-set {@code bright} value so the caller can
     * carry it across buckets and avoid re-issuing identical
     * {@code tess.color(...)} calls (the dedup hits ~80% of the time when
     * many BEs in a batch share the same chunk lighting).
     */
    private static float emitStaticInstancesBatched(Tessellator tess, float[][] tris, float invSc,
                                                    float bucketFactor,
                                                    Aero_AnimatedBatcher.Batch batch, int count,
                                                    Aero_RenderOptions options,
                                                    float lastBright) {
        for (int i = 0; i < count; i++) {
            float bright = batch.brightnesses[i] * bucketFactor;
            if (bright != lastBright) {
                tess.color(bright * options.tintR, bright * options.tintG,
                           bright * options.tintB, options.alpha);
                lastBright = bright;
            }
            double instX = batch.xs[i], instY = batch.ys[i], instZ = batch.zs[i];
            for (int t = 0; t < tris.length; t++) {
                float[] tri = tris[t];
                tess.vertex(instX + tri[0]*invSc,  instY + tri[1]*invSc,  instZ + tri[2]*invSc,  tri[3],  tri[4]);
                tess.vertex(instX + tri[5]*invSc,  instY + tri[6]*invSc,  instZ + tri[7]*invSc,  tri[8],  tri[9]);
                tess.vertex(instX + tri[10]*invSc, instY + tri[11]*invSc, instZ + tri[12]*invSc, tri[13], tri[14]);
            }
        }
        return lastBright;
    }

    private static void emitBoneInstanceBatched(Tessellator tess, float[][] tris, float invSc,
                                                 Aero_BoneRenderPose pose,
                                                 double instX, double instY, double instZ) {
        // Pre-compute trig once per (instance, bone). The same matrix
        // applies to every vertex of this bone for this instance.
        final float DEG_TO_RAD = (float) (Math.PI / 180.0);
        float cosX = (float) Math.cos(pose.rotX * DEG_TO_RAD);
        float sinX = (float) Math.sin(pose.rotX * DEG_TO_RAD);
        float cosY = (float) Math.cos(pose.rotY * DEG_TO_RAD);
        float sinY = (float) Math.sin(pose.rotY * DEG_TO_RAD);
        float cosZ = (float) Math.cos(pose.rotZ * DEG_TO_RAD);
        float sinZ = (float) Math.sin(pose.rotZ * DEG_TO_RAD);
        float scaleX = pose.scaleX, scaleY = pose.scaleY, scaleZ = pose.scaleZ;
        float pivotX = pose.pivotX, pivotY = pose.pivotY, pivotZ = pose.pivotZ;
        float postX  = pose.pivotX + pose.offsetX;
        float postY  = pose.pivotY + pose.offsetY;
        float postZ  = pose.pivotZ + pose.offsetZ;

        for (int t = 0; t < tris.length; t++) {
            float[] tri = tris[t];
            // Apply pose × (vertex × invScale) for each of the 3 vertices.
            // Same transform sequence as Aero_MeshRenderer.applyPose() but
            // composed in CPU: T(-pivot) → S → Rx → Ry → Rz → T(pivot+offset).
            for (int v = 0; v < 3; v++) {
                int off = v * 5;
                float lx = tri[off]     * invSc - pivotX;
                float ly = tri[off + 1] * invSc - pivotY;
                float lz = tri[off + 2] * invSc - pivotZ;
                lx *= scaleX; ly *= scaleY; lz *= scaleZ;
                // Rx: y, z change
                float ny = ly * cosX - lz * sinX;
                float nz = ly * sinX + lz * cosX;
                ly = ny; lz = nz;
                // Ry: x, z change
                float nx = lx * cosY + lz * sinY;
                nz       = -lx * sinY + lz * cosY;
                lx = nx; lz = nz;
                // Rz: x, y change
                nx = lx * cosZ - ly * sinZ;
                ny = lx * sinZ + ly * cosZ;
                lx = nx; ly = ny;
                tess.vertex(instX + lx + postX, instY + ly + postY, instZ + lz + postZ,
                            tri[off + 3], tri[off + 4]);
            }
        }
    }

    /**
     * Like {@link #emitBoneInstanceBatched} but for the rest-pose case
     * (no animated bone — named group exists in the OBJ but has no
     * matching pivot/clip in the bundle). Mirrors the unbatched path's
     * behavior of still drawing the geometry at its model-space
     * position when {@code applyPoseChain} returns null.
     */
    private static void emitBoneInstanceBatchedRest(Tessellator tess, float[][] tris, float invSc,
                                                     double instX, double instY, double instZ) {
        for (int t = 0; t < tris.length; t++) {
            float[] tri = tris[t];
            tess.vertex(instX + tri[0]*invSc,  instY + tri[1]*invSc,  instZ + tri[2]*invSc,  tri[3],  tri[4]);
            tess.vertex(instX + tri[5]*invSc,  instY + tri[6]*invSc,  instZ + tri[7]*invSc,  tri[8],  tri[9]);
            tess.vertex(instX + tri[10]*invSc, instY + tri[11]*invSc, instZ + tri[12]*invSc, tri[13], tri[14]);
        }
    }

    /**
     * Drains a batch by rendering each instance through the unbatched
     * path. Used when the batch contains a model that can't be safely
     * batched (multi-bone skeleton, etc).
     */
    private static void drainAsUnbatched(Aero_AnimatedBatcher.Batch batch, int count) {
        for (int i = 0; i < count; i++) {
            renderAnimated(batch.model, batch.bundles[i], batch.defs[i], batch.states[i],
                batch.xs[i], batch.ys[i], batch.zs[i],
                batch.brightnesses[i], batch.partialTicks[i], batch.options[i]);
        }
    }

    // -----------------------------------------------------------------------
    // Internal helpers
    // -----------------------------------------------------------------------

    private static int prepareAnimatedRender(Aero_MeshModel model,
                                             double x, double y, double z,
                                             float brightness,
                                             Aero_RenderOptions options) {
        updateCameraForwardFromPlayer();
        if (!Aero_FrustumCull.isLikelyVisible(x, y, z)) return ANIMATED_RENDER_CULLED;

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
            return ANIMATED_RENDER_STATIC_DONE;
        }
        return ANIMATED_RENDER_ACTIVE;
    }

    /**
     * Walks the resolved ancestor chain (root → leaf) of the given BoneRef
     * and applies each ancestor's pose to the current GL matrix, so a child
     * bone's geometry inherits every animated parent's transform.
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

    /**
     * Static-geometry draw with morph-target blending. Per-vertex applies
     * {@code finalPos = base + Σ(weight × delta)} across active targets.
     */
    // Pooled scratch for drawGroupsMorph — render thread is single-threaded
    // in Beta 1.7.3 so static reuse is safe. Pre-sized to 4 since 99% of
    // morph cases have ≤4 active targets; grows on demand.
    private static Aero_MorphTarget[] SCRATCH_MORPH_TARGETS = new Aero_MorphTarget[4];
    private static float[] SCRATCH_MORPH_WEIGHTS = new float[4];

    private static void drawGroupsMorph(Tessellator tess, Aero_MeshModel model,
                                         float brightness, Aero_RenderOptions options,
                                         Aero_MorphState morphState) {
        java.util.Map weights = morphState.getWeightsView();
        int upperBound = weights.size();
        if (SCRATCH_MORPH_TARGETS.length < upperBound) {
            SCRATCH_MORPH_TARGETS = new Aero_MorphTarget[upperBound];
            SCRATCH_MORPH_WEIGHTS = new float[upperBound];
        }
        Aero_MorphTarget[] activeTargets = SCRATCH_MORPH_TARGETS;
        float[] activeWeights = SCRATCH_MORPH_WEIGHTS;
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
        tess.start(GL11.GL_TRIANGLES);
        for (int g = 0; g < 4; g++) {
            float[][] tris = model.groups[g];
            if (tris.length == 0) continue;
            float bright = brightness * Aero_MeshModel.BRIGHTNESS_FACTORS[g];
            tess.color(bright * options.tintR, bright * options.tintG,
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
                tess.vertex((t[0]  + v0dx) * invSc, (t[1]  + v0dy) * invSc, (t[2]  + v0dz) * invSc, t[3],  t[4]);
                tess.vertex((t[5]  + v1dx) * invSc, (t[6]  + v1dy) * invSc, (t[7]  + v1dz) * invSc, t[8],  t[9]);
                tess.vertex((t[10] + v2dx) * invSc, (t[11] + v2dy) * invSc, (t[12] + v2dz) * invSc, t[13], t[14]);
            }
        }
        tess.draw();
    }

    private static void drawGroups(Tessellator tess, float[][][] groups, float invSc,
                                   float brightness, Aero_RenderOptions options) {
        // Identity UV transform — fast path that emits raw u/v with no per-vertex math.
        drawGroups(tess, groups, invSc, brightness, options, 0f, 0f, 1f, 1f);
    }

    /**
     * UV-aware variant. Per-bone calls pass pose UV offset/scale so the
     * emit loop transforms each vertex's u/v before tessellation. Identity
     * UV branches to the raw path that allocates zero work per vertex —
     * any bone without uv_offset/uv_scale channels pays nothing.
     */
    private static void drawGroups(Tessellator tess, float[][][] groups, float invSc,
                                   float brightness, Aero_RenderOptions options,
                                   float uOff, float vOff, float uScale, float vScale) {
        boolean uvIdentity = uOff == 0f && vOff == 0f && uScale == 1f && vScale == 1f;
        tess.start(GL11.GL_TRIANGLES);
        for (int g = 0; g < 4; g++) {
            float[][] tris = groups[g];
            if (tris.length == 0) continue;
            float bright = brightness * Aero_MeshModel.BRIGHTNESS_FACTORS[g];
            tess.color(bright * options.tintR, bright * options.tintG, bright * options.tintB, options.alpha);
            if (uvIdentity) {
                for (int i = 0; i < tris.length; i++) {
                    float[] t = tris[i];
                    tess.vertex(t[0]*invSc,  t[1]*invSc,  t[2]*invSc,  t[3],  t[4]);
                    tess.vertex(t[5]*invSc,  t[6]*invSc,  t[7]*invSc,  t[8],  t[9]);
                    tess.vertex(t[10]*invSc, t[11]*invSc, t[12]*invSc, t[13], t[14]);
                }
            } else {
                for (int i = 0; i < tris.length; i++) {
                    float[] t = tris[i];
                    tess.vertex(t[0]*invSc,  t[1]*invSc,  t[2]*invSc,
                        t[3] *uScale + uOff,  t[4] *vScale + vOff);
                    tess.vertex(t[5]*invSc,  t[6]*invSc,  t[7]*invSc,
                        t[8] *uScale + uOff,  t[9] *vScale + vOff);
                    tess.vertex(t[10]*invSc, t[11]*invSc, t[12]*invSc,
                        t[13]*uScale + uOff, t[14]*vScale + vOff);
                }
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

    static void beginMeshState() {
        beginMeshState(Aero_RenderOptions.DEFAULT);
    }

    static void beginMeshState(Aero_RenderOptions options) {
        GL11.glPushAttrib(MESH_ATTRIB_BITS);
        if (options.cullFaces) {
            GL11.glEnable(GL11.GL_CULL_FACE);
            GL11.glCullFace(GL11.GL_BACK);
        } else {
            GL11.glDisable(GL11.GL_CULL_FACE);
        }
        GL11.glDisable(GL11.GL_LIGHTING);
        applyBlendMode(options.blend);
        if (options.alphaClip > 0f) {
            GL11.glEnable(GL11.GL_ALPHA_TEST);
            GL11.glAlphaFunc(GL11.GL_GREATER, options.alphaClip);
        } else {
            GL11.glDisable(GL11.GL_ALPHA_TEST);
        }
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

    static void endMeshState() {
        GL11.glPopAttrib();
    }

    static void applyRotation(float rotation) {
        if (rotation != 0) {
            GL11.glTranslatef(0.5f, 0.5f, 0.5f);
            GL11.glRotatef(rotation, 0.0f, 1.0f, 0.0f);
            GL11.glTranslatef(-0.5f, -0.5f, -0.5f);
        }
    }
}
