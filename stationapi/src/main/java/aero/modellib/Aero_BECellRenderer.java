package aero.modellib;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import net.minecraft.block.entity.BlockEntity;
import org.lwjgl.opengl.GL11;

/**
 * At-rest BlockEntity cell pages. Renderers can queue static/LOD-overflow
 * meshes here instead of drawing each BE immediately; the flush compiles one
 * small display-list page per visible cell/render key and replays that page
 * while preserving the existing direct-render fallback.
 */
public final class Aero_BECellRenderer {

    public static final boolean ENABLED =
        !"false".equalsIgnoreCase(System.getProperty("aero.becell.pages"));
    public static final boolean SKIP_INDIVIDUAL_RENDERERS =
        !"false".equalsIgnoreCase(System.getProperty("aero.becell.skipIndividual"));

    private static final int MIN_INSTANCES =
        clampInt(Integer.getInteger("aero.becell.minInstances", 2).intValue(), 1, 4096);
    private static final int PAGE_TTL_FRAMES =
        Aero_PerfConfig.intProperty("aero.becell.pageTtlFrames",
            600, 1800, 60, 100000);
    private static final int REBUILDS_PER_FRAME =
        Aero_PerfConfig.intProperty("aero.becell.rebuildsPerFrame",
            8, 16, -1, 100000);
    private static final int MAX_CACHED_PAGES =
        Aero_PerfConfig.intProperty("aero.becell.maxCachedPages",
            -1, 4096, -1, 1000000);
    private static final boolean PER_INSTANCE_LIGHT =
        Aero_PerfConfig.booleanProperty("aero.becell.perInstanceLight", false, false);
    private static final int LIGHT_BUCKETS =
        Aero_PerfConfig.intProperty("aero.becell.lightBuckets", 0, 0, 0, 256);
    private static final boolean STABLE_MEMBERSHIP =
        Aero_PerfConfig.booleanProperty("aero.becell.stableMembership", false, false);
    private static final boolean FLATTENED_PAGES =
        Aero_PerfConfig.booleanProperty("aero.becell.flatten", false, true);

    private static final HashMap<PageKey, QueuedPage> ACTIVE =
        new HashMap<PageKey, QueuedPage>();
    private static final ArrayList<QueuedPage> ACTIVE_PAGES =
        new ArrayList<QueuedPage>();
    private static final HashMap<PageKey, CachedPage> CACHE =
        new HashMap<PageKey, CachedPage>();
    private static final PageLookupKey LOOKUP_KEY = new PageLookupKey();

    private static int frameIndex;
    private static int queuedThisFrame;
    private static int queuedLastFrame;
    private static int pageCallsThisFrame;
    private static int pageRebuildsThisFrame;
    private static int directFallbacksThisFrame;
    private static int deletedPages;
    private static int compiledCachedPages;
    private static int expiredCachedPages;
    private static int evictedCachedPages;

    private static final Comparator<QueuedPage> BY_RENDER_STATE =
        new Comparator<QueuedPage>() {
            @Override
            public int compare(QueuedPage a, QueuedPage b) {
                String at = a.key.texturePath;
                String bt = b.key.texturePath;
                if (at != bt) {
                    if (at == null) return -1;
                    if (bt == null) return 1;
                    int texture = at.compareTo(bt);
                    if (texture != 0) return texture;
                }
                int model = System.identityHashCode(a.key.model) - System.identityHashCode(b.key.model);
                if (model != 0) return model;
                if (a.key.cellX != b.key.cellX) return a.key.cellX - b.key.cellX;
                if (a.key.cellY != b.key.cellY) return a.key.cellY - b.key.cellY;
                return a.key.cellZ - b.key.cellZ;
            }
        };

    private Aero_BECellRenderer() {}

    public static void queueAtRest(Aero_MeshModel model, String texturePath,
                                   BlockEntity be,
                                   double x, double y, double z,
                                   float rotation, float brightness,
                                   Aero_RenderOptions options) {
        if (options == null) options = Aero_RenderOptions.DEFAULT;
        if (!canQueue(model, be, options)) {
            drawDirect(model, texturePath, x, y, z, rotation, brightness, options);
            return;
        }
        if (!Aero_RenderDistance.hasCachedCamera()) {
            drawDirect(model, texturePath, x, y, z, rotation, brightness, options);
            return;
        }

        Aero_CellRenderableBE renderable = (Aero_CellRenderableBE) be;
        if (!renderable.aeroCanCellPage()) {
            drawDirect(model, texturePath, x, y, z, rotation, brightness, options);
            return;
        }

        // Renderer-provided x/y/z are camera-relative and may use vanilla's
        // interpolated camera, while Aero_RenderDistance caches the current
        // tick camera for culling. Reconstructing world coords from the cache
        // makes static overflow pages shimmer while the player walks. The BE
        // block coordinates are stable and match the normal renderer origin.
        double worldX = be.x;
        double worldY = be.y;
        double worldZ = be.z;
        if (!queueWorldAtRest(model, texturePath, be, worldX, worldY, worldZ,
                rotation, brightness, options)) {
            drawDirect(model, texturePath, x, y, z, rotation, brightness, options);
        }
    }

    /**
     * Called from {@link Aero_RenderDistanceBlockEntity#distanceFrom} before
     * vanilla dispatches the individual renderer. A true return means the BE
     * has been queued for this frame and the dispatcher can be suppressed.
     */
    public static boolean tryQueueManagedAtRest(BlockEntity be, Aero_CellPageRenderableBE renderable) {
        if (!SKIP_INDIVIDUAL_RENDERERS || renderable == null) return false;
        Aero_RenderOptions options = renderable.aeroCellRenderOptions();
        if (options == null) options = Aero_RenderOptions.DEFAULT;
        Aero_MeshModel model = renderable.aeroCellModel();
        if (!canQueue(model, be, options) || !renderable.aeroCanCellPage()) return false;
        return queueWorldAtRest(model, renderable.aeroCellTexturePath(), be,
            be.x, be.y, be.z,
            renderable.aeroCellRotation(),
            renderable.aeroCellBrightness(),
            options);
    }

    private static boolean queueWorldAtRest(Aero_MeshModel model, String texturePath,
                                            BlockEntity be,
                                            double worldX, double worldY, double worldZ,
                                            float rotation, float brightness,
                                            Aero_RenderOptions options) {
        if (options == null) options = Aero_RenderOptions.DEFAULT;
        if (!canQueue(model, be, options)) return false;

        Aero_CellRenderableBE renderable = (Aero_CellRenderableBE) be;
        if (!renderable.aeroCanCellPage()) return false;

        int cellX = Math.floorDiv(floorToInt(worldX), Aero_BECellIndex.CELL_SIZE);
        int cellY = Math.floorDiv(floorToInt(worldY), Aero_BECellIndex.CELL_SIZE);
        int cellZ = Math.floorDiv(floorToInt(worldZ), Aero_BECellIndex.CELL_SIZE);

        float keyBrightness = pageKeyBrightness(brightness);
        int stateHash = renderable.aeroRenderStateHash();
        int orientationHash = renderable.aeroOrientationHash();
        QueuedPage page = ACTIVE.get(LOOKUP_KEY.set(be.world, model, texturePath, options,
            cellX, cellY, cellZ, rotation, keyBrightness, stateHash, orientationHash));
        if (page == null) {
            PageKey key = new PageKey(be.world, model, texturePath, options,
                cellX, cellY, cellZ, rotation, keyBrightness,
                stateHash, orientationHash);
            page = new QueuedPage(key);
            ACTIVE.put(key, page);
            ACTIVE_PAGES.add(page);
        }
        page.add(be, worldX, worldY, worldZ, brightness);
        queuedThisFrame++;
        return true;
    }

    public static void flush(double cameraX, double cameraY, double cameraZ) {
        pageCallsThisFrame = 0;
        pageRebuildsThisFrame = 0;
        directFallbacksThisFrame = 0;
        if (ACTIVE_PAGES.isEmpty()) {
            queuedLastFrame = queuedThisFrame;
            queuedThisFrame = 0;
            return;
        }
        frameIndex++;
        Aero_Profiler.start("aero.becell.flush");
        try {
            Collections.sort(ACTIVE_PAGES, BY_RENDER_STATE);
            for (int i = 0; i < ACTIVE_PAGES.size(); i++) {
                flushPage(ACTIVE_PAGES.get(i), cameraX, cameraY, cameraZ);
            }
        } finally {
            Aero_Profiler.end("aero.becell.flush");
        }
        ACTIVE.clear();
        for (int i = 0; i < ACTIVE_PAGES.size(); i++) {
            ACTIVE_PAGES.get(i).clear();
        }
        ACTIVE_PAGES.clear();
        queuedLastFrame = queuedThisFrame;
        queuedThisFrame = 0;
        if ((frameIndex & 127) == 0) {
            sweepOldPages();
        }
    }

    static void flushCachedCamera() {
        if (!Aero_RenderDistance.hasCachedCamera()) return;
        flush(Aero_RenderDistance.cachedCameraX(),
              Aero_RenderDistance.cachedCameraY(),
              Aero_RenderDistance.cachedCameraZ());
    }

    static void disposeModel(Aero_MeshModel model) {
        if (model == null || CACHE.isEmpty()) return;
        Iterator<Map.Entry<PageKey, CachedPage>> it = CACHE.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<PageKey, CachedPage> entry = it.next();
            if (entry.getKey().model == model) {
                deleteIds(entry.getValue().ids);
                it.remove();
            }
        }
    }

    private static boolean canQueue(Aero_MeshModel model, BlockEntity be, Aero_RenderOptions options) {
        if (!ENABLED || model == null || be == null || be.world == null) return false;
        if (!(be instanceof Aero_CellRenderableBE)) return false;
        return options.blend == Aero_MeshBlendMode.OFF;
    }

    private static void flushPage(QueuedPage page, double cameraX, double cameraY, double cameraZ) {
        if (page.count < MIN_INSTANCES) {
            drawDirect(page, cameraX, cameraY, cameraZ);
            return;
        }
        int[] modelIds = null;
        if (!FLATTENED_PAGES) {
            modelIds = Aero_MeshRenderer.ensureAtRestListIds(page.key.model);
            if (modelIds == null) {
                drawDirect(page, cameraX, cameraY, cameraZ);
                return;
            }
        }

        CachedPage cached = CACHE.get(page.key);
        int membershipHash = page.membershipHash();
        if (cached == null
            || cached.count != page.count
            || cached.membershipHash != membershipHash) {
            if (!canRebuildAnotherPageThisFrame()) {
                drawDirect(page, cameraX, cameraY, cameraZ);
                return;
            }
            CachedPage rebuilt = compilePage(page, modelIds, membershipHash);
            if (rebuilt == null) {
                drawDirect(page, cameraX, cameraY, cameraZ);
                return;
            }
            if (cached != null) deleteIds(cached.ids);
            CACHE.put(page.key, rebuilt);
            enforceMaxCachedPages(page.key);
            cached = rebuilt;
            pageRebuildsThisFrame++;
            compiledCachedPages++;
        }

        drawCached(page.key, cached, cameraX, cameraY, cameraZ);
    }

    private static boolean canRebuildAnotherPageThisFrame() {
        return REBUILDS_PER_FRAME < 0 || pageRebuildsThisFrame < REBUILDS_PER_FRAME;
    }

    private static CachedPage compilePage(QueuedPage page, int[] modelIds, int membershipHash) {
        Aero_Profiler.start("aero.becell.compile");
        try {
            int[] ids = new int[4];
            for (int g = 0; g < 4; g++) {
                int modelList = modelIds != null ? modelIds[g] : 0;
                if (FLATTENED_PAGES) {
                    if (!hasBucketGeometry(page.key.model, g)) continue;
                } else if (modelList == 0) {
                    continue;
                }
                int id = Aero_DisplayListBudget.glGenList();
                if (id == 0) {
                    deleteIds(ids);
                    return null;
                }
                GL11.glNewList(id, GL11.GL_COMPILE);
                if (FLATTENED_PAGES) {
                    GL11.glBegin(GL11.GL_TRIANGLES);
                    for (int i = 0; i < page.count; i++) {
                        if (PER_INSTANCE_LIGHT) {
                            float bright = page.brightnesses[i] * Aero_MeshModel.BRIGHTNESS_FACTORS[g];
                            GL11.glColor4f(bright * page.key.options.tintR,
                                           bright * page.key.options.tintG,
                                           bright * page.key.options.tintB,
                                           page.key.options.alpha);
                        }
                        emitModelBucketFlattened(page.key.model, g,
                            page.worldXs[i] - page.key.originX(),
                            page.worldYs[i] - page.key.originY(),
                            page.worldZs[i] - page.key.originZ(),
                            page.key.rotation);
                    }
                    GL11.glEnd();
                } else {
                    for (int i = 0; i < page.count; i++) {
                        if (PER_INSTANCE_LIGHT) {
                            float bright = page.brightnesses[i] * Aero_MeshModel.BRIGHTNESS_FACTORS[g];
                            GL11.glColor4f(bright * page.key.options.tintR,
                                           bright * page.key.options.tintG,
                                           bright * page.key.options.tintB,
                                           page.key.options.alpha);
                        }
                        GL11.glPushMatrix();
                        GL11.glTranslated(
                            page.worldXs[i] - page.key.originX(),
                            page.worldYs[i] - page.key.originY(),
                            page.worldZs[i] - page.key.originZ());
                        Aero_MeshRenderer.applyRotation(page.key.rotation);
                        GL11.glCallList(modelList);
                        GL11.glPopMatrix();
                    }
                }
                GL11.glEndList();
                ids[g] = id;
            }
            return new CachedPage(ids, page.count, membershipHash, frameIndex);
        } finally {
            Aero_Profiler.end("aero.becell.compile");
        }
    }

    private static boolean hasBucketGeometry(Aero_MeshModel model, int bucket) {
        if (model.groups[bucket].length > 0) return true;
        Aero_MeshModel.NamedGroup[] entries = model.getNamedGroupArray();
        for (int i = 0; i < entries.length; i++) {
            if (entries[i].tris[bucket].length > 0) return true;
        }
        return false;
    }

    private static void emitModelBucketFlattened(Aero_MeshModel model, int bucket,
                                                 double ox, double oy, double oz,
                                                 float rotation) {
        float invSc = model.invScale;
        emitTrisFlattened(model.groups[bucket], invSc, ox, oy, oz, rotation);
        Aero_MeshModel.NamedGroup[] entries = model.getNamedGroupArray();
        for (int i = 0; i < entries.length; i++) {
            emitTrisFlattened(entries[i].tris[bucket], invSc, ox, oy, oz, rotation);
        }
    }

    private static void emitTrisFlattened(float[][] tris, float invSc,
                                          double ox, double oy, double oz,
                                          float rotation) {
        if (tris.length == 0) return;
        double radians = Math.toRadians(rotation);
        double sin = rotation != 0.0f ? Math.sin(radians) : 0.0d;
        double cos = rotation != 0.0f ? Math.cos(radians) : 1.0d;
        for (int i = 0; i < tris.length; i++) {
            float[] t = tris[i];
            emitVertexFlattened(t[0] * invSc, t[1] * invSc, t[2] * invSc,
                t[3], t[4], ox, oy, oz, rotation, sin, cos);
            emitVertexFlattened(t[5] * invSc, t[6] * invSc, t[7] * invSc,
                t[8], t[9], ox, oy, oz, rotation, sin, cos);
            emitVertexFlattened(t[10] * invSc, t[11] * invSc, t[12] * invSc,
                t[13], t[14], ox, oy, oz, rotation, sin, cos);
        }
    }

    private static void emitVertexFlattened(double x, double y, double z,
                                            float u, float v,
                                            double ox, double oy, double oz,
                                            float rotation, double sin, double cos) {
        double rx = x;
        double rz = z;
        if (rotation != 0.0f) {
            double dx = x - 0.5d;
            double dz = z - 0.5d;
            rx = dx * cos + dz * sin + 0.5d;
            rz = -dx * sin + dz * cos + 0.5d;
        }
        GL11.glTexCoord2f(u, v);
        GL11.glVertex3d(ox + rx, oy + y, oz + rz);
    }

    private static void drawCached(PageKey key, CachedPage cached,
                                   double cameraX, double cameraY, double cameraZ) {
        Aero_Profiler.start("aero.becell.call");
        Aero_AnimatedBatcher.bindTexturePath(key.texturePath);
        Aero_MeshRenderer.beginMeshState(key.options);
        try {
            GL11.glPushMatrix();
            try {
                GL11.glTranslated(key.originX() - cameraX,
                                  key.originY() - cameraY,
                                  key.originZ() - cameraZ);
                for (int g = 0; g < 4; g++) {
                    int id = cached.ids[g];
                    if (id == 0) continue;
                    if (!PER_INSTANCE_LIGHT) {
                        float bright = key.brightness * Aero_MeshModel.BRIGHTNESS_FACTORS[g];
                        GL11.glColor4f(bright * key.options.tintR,
                                       bright * key.options.tintG,
                                       bright * key.options.tintB,
                                       key.options.alpha);
                    }
                    GL11.glCallList(id);
                    pageCallsThisFrame++;
                }
            } finally {
                GL11.glPopMatrix();
            }
        } finally {
            Aero_MeshRenderer.endMeshState();
            Aero_Profiler.end("aero.becell.call");
        }
        cached.lastUsedFrame = frameIndex;
    }

    private static void drawDirect(QueuedPage page, double cameraX, double cameraY, double cameraZ) {
        Aero_Profiler.start("aero.becell.direct");
        Aero_AnimatedBatcher.bindTexturePath(page.key.texturePath);
        try {
            for (int i = 0; i < page.count; i++) {
                Aero_MeshRenderer.renderModelAtRestPreculled(page.key.model,
                    page.worldXs[i] - cameraX,
                    page.worldYs[i] - cameraY,
                    page.worldZs[i] - cameraZ,
                    page.key.rotation,
                    page.brightnesses[i],
                    page.key.options);
                directFallbacksThisFrame++;
            }
        } finally {
            Aero_Profiler.end("aero.becell.direct");
        }
    }

    private static void drawDirect(Aero_MeshModel model, String texturePath,
                                   double x, double y, double z,
                                   float rotation, float brightness,
                                   Aero_RenderOptions options) {
        if (model == null) return;
        Aero_Profiler.start("aero.becell.direct");
        Aero_AnimatedBatcher.bindTexturePath(texturePath);
        try {
            Aero_MeshRenderer.renderModelAtRestPreculled(model, x, y, z, rotation, brightness, options);
            directFallbacksThisFrame++;
        } finally {
            Aero_Profiler.end("aero.becell.direct");
        }
    }

    private static void sweepOldPages() {
        if (CACHE.isEmpty()) return;
        Iterator<Map.Entry<PageKey, CachedPage>> it = CACHE.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<PageKey, CachedPage> entry = it.next();
            if (frameIndex - entry.getValue().lastUsedFrame > PAGE_TTL_FRAMES) {
                deleteIds(entry.getValue().ids);
                it.remove();
                expiredCachedPages++;
            }
        }
    }

    private static void enforceMaxCachedPages(PageKey protectedKey) {
        if (MAX_CACHED_PAGES < 0) return;
        while (CACHE.size() > MAX_CACHED_PAGES) {
            PageKey oldestKey = null;
            CachedPage oldestPage = null;
            Iterator<Map.Entry<PageKey, CachedPage>> it = CACHE.entrySet().iterator();
            while (it.hasNext()) {
                Map.Entry<PageKey, CachedPage> entry = it.next();
                if (entry.getKey() == protectedKey) continue;
                CachedPage page = entry.getValue();
                if (oldestPage == null || page.lastUsedFrame < oldestPage.lastUsedFrame) {
                    oldestKey = entry.getKey();
                    oldestPage = page;
                }
            }
            if (oldestKey == null) return;
            deleteIds(oldestPage.ids);
            CACHE.remove(oldestKey);
            evictedCachedPages++;
        }
    }

    private static float pageKeyBrightness(float brightness) {
        if (PER_INSTANCE_LIGHT) return 1.0f;
        if (LIGHT_BUCKETS <= 1) return brightness;
        int max = LIGHT_BUCKETS - 1;
        int bucket = Math.round(brightness * max);
        if (bucket < 0) bucket = 0;
        if (bucket > max) bucket = max;
        return bucket / (float) max;
    }

    private static void deleteIds(int[] ids) {
        if (ids == null) return;
        for (int i = 0; i < ids.length; i++) {
            if (ids[i] != 0) {
                Aero_DisplayListBudget.glDeleteList(ids[i]);
                ids[i] = 0;
                deletedPages++;
            }
        }
    }

    private static int floorToInt(double value) {
        int i = (int) value;
        return value < i ? i - 1 : i;
    }

    private static long stableSortKey(double worldX, double worldY, double worldZ,
                                      int identityHash) {
        long x = floorToInt(worldX * 16.0d) & 0x1FFFFFL;
        long y = floorToInt(worldY * 16.0d) & 0x1FFFFFL;
        long z = floorToInt(worldZ * 16.0d) & 0x1FFFFFL;
        return (x << 43) ^ (z << 22) ^ (y << 1) ^ (identityHash & 1);
    }

    private static int clampInt(int value, int min, int max) {
        if (value < min) return min;
        if (value > max) return max;
        return value;
    }

    public static int queuedThisFrame() {
        return queuedThisFrame;
    }

    public static int queuedLastFrame() {
        return queuedLastFrame;
    }

    public static int pageCallsThisFrame() {
        return pageCallsThisFrame;
    }

    public static int pageRebuildsThisFrame() {
        return pageRebuildsThisFrame;
    }

    public static int directFallbacksThisFrame() {
        return directFallbacksThisFrame;
    }

    public static int cachedPageCount() {
        return CACHE.size();
    }

    public static int deletedPages() {
        return deletedPages;
    }

    public static int compiledCachedPages() {
        return compiledCachedPages;
    }

    public static int expiredCachedPages() {
        return expiredCachedPages;
    }

    public static int evictedCachedPages() {
        return evictedCachedPages;
    }

    private static final class QueuedPage {
        final PageKey key;
        double[] worldXs = new double[16];
        double[] worldYs = new double[16];
        double[] worldZs = new double[16];
        float[] brightnesses = new float[16];
        int[] identityHashes = new int[16];
        long[] sortKeys = new long[16];
        int count;

        QueuedPage(PageKey key) {
            this.key = key;
        }

        void add(BlockEntity be, double worldX, double worldY, double worldZ,
                 float brightness) {
            ensureCapacity();
            worldXs[count] = worldX;
            worldYs[count] = worldY;
            worldZs[count] = worldZ;
            brightnesses[count] = brightness;
            int identity = System.identityHashCode(be);
            identityHashes[count] = identity;
            sortKeys[count] = stableSortKey(worldX, worldY, worldZ, identity);
            count++;
        }

        int membershipHash() {
            if (STABLE_MEMBERSHIP && count > 1) {
                sortEntries(0, count - 1);
            }
            int hash = 1;
            for (int i = 0; i < count; i++) {
                hash = 31 * hash + identityHashes[i];
                hash = 31 * hash + floorToInt(worldXs[i] * 16.0d);
                hash = 31 * hash + floorToInt(worldYs[i] * 16.0d);
                hash = 31 * hash + floorToInt(worldZs[i] * 16.0d);
                if (PER_INSTANCE_LIGHT) {
                    hash = 31 * hash + Float.floatToIntBits(brightnesses[i]);
                }
            }
            return hash;
        }

        void clear() {
            count = 0;
        }

        private void ensureCapacity() {
            if (count < worldXs.length) return;
            int n = worldXs.length * 2;
            double[] newXs = new double[n];
            double[] newYs = new double[n];
            double[] newZs = new double[n];
            float[] newBrightnesses = new float[n];
            int[] newHashes = new int[n];
            long[] newSortKeys = new long[n];
            System.arraycopy(worldXs, 0, newXs, 0, worldXs.length);
            System.arraycopy(worldYs, 0, newYs, 0, worldYs.length);
            System.arraycopy(worldZs, 0, newZs, 0, worldZs.length);
            System.arraycopy(brightnesses, 0, newBrightnesses, 0, brightnesses.length);
            System.arraycopy(identityHashes, 0, newHashes, 0, identityHashes.length);
            System.arraycopy(sortKeys, 0, newSortKeys, 0, sortKeys.length);
            worldXs = newXs;
            worldYs = newYs;
            worldZs = newZs;
            brightnesses = newBrightnesses;
            identityHashes = newHashes;
            sortKeys = newSortKeys;
        }

        private void sortEntries(int left, int right) {
            int i = left;
            int j = right;
            long pivot = sortKeys[(left + right) >>> 1];
            while (i <= j) {
                while (sortKeys[i] < pivot) i++;
                while (sortKeys[j] > pivot) j--;
                if (i <= j) {
                    swap(i, j);
                    i++;
                    j--;
                }
            }
            if (left < j) sortEntries(left, j);
            if (i < right) sortEntries(i, right);
        }

        private void swap(int a, int b) {
            if (a == b) return;
            double dx = worldXs[a];
            double dy = worldYs[a];
            double dz = worldZs[a];
            float br = brightnesses[a];
            int ih = identityHashes[a];
            long sk = sortKeys[a];
            worldXs[a] = worldXs[b];
            worldYs[a] = worldYs[b];
            worldZs[a] = worldZs[b];
            brightnesses[a] = brightnesses[b];
            identityHashes[a] = identityHashes[b];
            sortKeys[a] = sortKeys[b];
            worldXs[b] = dx;
            worldYs[b] = dy;
            worldZs[b] = dz;
            brightnesses[b] = br;
            identityHashes[b] = ih;
            sortKeys[b] = sk;
        }
    }

    private static final class CachedPage {
        final int[] ids;
        final int count;
        final int membershipHash;
        int lastUsedFrame;

        CachedPage(int[] ids, int count, int membershipHash, int lastUsedFrame) {
            this.ids = ids;
            this.count = count;
            this.membershipHash = membershipHash;
            this.lastUsedFrame = lastUsedFrame;
        }
    }

    private static final class PageKey {
        final Object world;
        final Aero_MeshModel model;
        final String texturePath;
        final Aero_RenderOptions options;
        final int cellX;
        final int cellY;
        final int cellZ;
        final float rotation;
        final float brightness;
        final int rotationBits;
        final int brightnessBits;
        final int stateHash;
        final int orientationHash;
        final int tintRBits;
        final int tintGBits;
        final int tintBBits;
        final int alphaBits;
        final int alphaClipBits;
        final int hash;

        PageKey(Object world, Aero_MeshModel model, String texturePath,
                Aero_RenderOptions options, int cellX, int cellY, int cellZ,
                float rotation, float brightness, int stateHash, int orientationHash) {
            this.world = world;
            this.model = model;
            this.texturePath = texturePath;
            this.options = options;
            this.cellX = cellX;
            this.cellY = cellY;
            this.cellZ = cellZ;
            this.rotation = rotation;
            this.brightness = brightness;
            this.rotationBits = Float.floatToIntBits(rotation);
            this.brightnessBits = Float.floatToIntBits(brightness);
            this.stateHash = stateHash;
            this.orientationHash = orientationHash;
            this.tintRBits = Float.floatToIntBits(options.tintR);
            this.tintGBits = Float.floatToIntBits(options.tintG);
            this.tintBBits = Float.floatToIntBits(options.tintB);
            this.alphaBits = Float.floatToIntBits(options.alpha);
            this.alphaClipBits = Float.floatToIntBits(options.alphaClip);
            this.hash = computeHash();
        }

        int originX() {
            return cellX * Aero_BECellIndex.CELL_SIZE;
        }

        int originY() {
            return cellY * Aero_BECellIndex.CELL_SIZE;
        }

        int originZ() {
            return cellZ * Aero_BECellIndex.CELL_SIZE;
        }

        @Override
        public int hashCode() {
            return hash;
        }

        private int computeHash() {
            int result = System.identityHashCode(world);
            result = 31 * result + System.identityHashCode(model);
            result = 31 * result + (texturePath != null ? texturePath.hashCode() : 0);
            result = 31 * result + cellX;
            result = 31 * result + cellY;
            result = 31 * result + cellZ;
            result = 31 * result + rotationBits;
            result = 31 * result + brightnessBits;
            result = 31 * result + stateHash;
            result = 31 * result + orientationHash;
            result = 31 * result + tintRBits;
            result = 31 * result + tintGBits;
            result = 31 * result + tintBBits;
            result = 31 * result + alphaBits;
            result = 31 * result + alphaClipBits;
            result = 31 * result + options.blend.hashCode();
            result = 31 * result + (options.depthTest ? 1 : 0);
            result = 31 * result + (options.cullFaces ? 1 : 0);
            return result;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) return true;
            if (!(obj instanceof PageKey)) return false;
            PageKey other = (PageKey) obj;
            if (world != other.world || model != other.model) return false;
            if (cellX != other.cellX || cellY != other.cellY || cellZ != other.cellZ) return false;
            if (rotationBits != other.rotationBits || brightnessBits != other.brightnessBits) return false;
            if (stateHash != other.stateHash || orientationHash != other.orientationHash) return false;
            if (tintRBits != other.tintRBits || tintGBits != other.tintGBits
                || tintBBits != other.tintBBits || alphaBits != other.alphaBits
                || alphaClipBits != other.alphaClipBits) return false;
            if (options.blend != other.options.blend
                || options.depthTest != other.options.depthTest
                || options.cullFaces != other.options.cullFaces) return false;
            if (texturePath == other.texturePath) return true;
            return texturePath != null && texturePath.equals(other.texturePath);
        }
    }

    private static final class PageLookupKey {
        Object world;
        Aero_MeshModel model;
        String texturePath;
        Aero_RenderOptions options;
        int cellX;
        int cellY;
        int cellZ;
        int rotationBits;
        int brightnessBits;
        int stateHash;
        int orientationHash;
        int tintRBits;
        int tintGBits;
        int tintBBits;
        int alphaBits;
        int alphaClipBits;
        int hash;

        PageLookupKey set(Object world, Aero_MeshModel model, String texturePath,
                          Aero_RenderOptions options, int cellX, int cellY, int cellZ,
                          float rotation, float brightness,
                          int stateHash, int orientationHash) {
            this.world = world;
            this.model = model;
            this.texturePath = texturePath;
            this.options = options;
            this.cellX = cellX;
            this.cellY = cellY;
            this.cellZ = cellZ;
            this.rotationBits = Float.floatToIntBits(rotation);
            this.brightnessBits = Float.floatToIntBits(brightness);
            this.stateHash = stateHash;
            this.orientationHash = orientationHash;
            this.tintRBits = Float.floatToIntBits(options.tintR);
            this.tintGBits = Float.floatToIntBits(options.tintG);
            this.tintBBits = Float.floatToIntBits(options.tintB);
            this.alphaBits = Float.floatToIntBits(options.alpha);
            this.alphaClipBits = Float.floatToIntBits(options.alphaClip);
            this.hash = computeHash();
            return this;
        }

        @Override
        public int hashCode() {
            return hash;
        }

        private int computeHash() {
            int result = System.identityHashCode(world);
            result = 31 * result + System.identityHashCode(model);
            result = 31 * result + (texturePath != null ? texturePath.hashCode() : 0);
            result = 31 * result + cellX;
            result = 31 * result + cellY;
            result = 31 * result + cellZ;
            result = 31 * result + rotationBits;
            result = 31 * result + brightnessBits;
            result = 31 * result + stateHash;
            result = 31 * result + orientationHash;
            result = 31 * result + tintRBits;
            result = 31 * result + tintGBits;
            result = 31 * result + tintBBits;
            result = 31 * result + alphaBits;
            result = 31 * result + alphaClipBits;
            result = 31 * result + options.blend.hashCode();
            result = 31 * result + (options.depthTest ? 1 : 0);
            result = 31 * result + (options.cullFaces ? 1 : 0);
            return result;
        }

        @Override
        public boolean equals(Object obj) {
            if (!(obj instanceof PageKey)) return false;
            PageKey other = (PageKey) obj;
            if (world != other.world || model != other.model) return false;
            if (cellX != other.cellX || cellY != other.cellY || cellZ != other.cellZ) return false;
            if (rotationBits != other.rotationBits || brightnessBits != other.brightnessBits) return false;
            if (stateHash != other.stateHash || orientationHash != other.orientationHash) return false;
            if (tintRBits != other.tintRBits || tintGBits != other.tintGBits
                || tintBBits != other.tintBBits || alphaBits != other.alphaBits
                || alphaClipBits != other.alphaClipBits) return false;
            if (options.blend != other.options.blend
                || options.depthTest != other.options.depthTest
                || options.cullFaces != other.options.cullFaces) return false;
            if (texturePath == other.texturePath) return true;
            return texturePath != null && texturePath.equals(other.texturePath);
        }
    }
}
