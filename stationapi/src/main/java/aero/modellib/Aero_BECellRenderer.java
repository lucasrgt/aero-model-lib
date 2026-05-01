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

    private static final int MIN_INSTANCES =
        clampInt(Integer.getInteger("aero.becell.minInstances", 2).intValue(), 1, 4096);
    private static final int PAGE_TTL_FRAMES =
        clampInt(Integer.getInteger("aero.becell.pageTtlFrames", 600).intValue(), 60, 100000);

    private static final HashMap<PageKey, QueuedPage> ACTIVE =
        new HashMap<PageKey, QueuedPage>();
    private static final ArrayList<QueuedPage> ACTIVE_PAGES =
        new ArrayList<QueuedPage>();
    private static final HashMap<PageKey, CachedPage> CACHE =
        new HashMap<PageKey, CachedPage>();

    private static int frameIndex;
    private static int queuedThisFrame;
    private static int pageCallsThisFrame;
    private static int pageRebuildsThisFrame;
    private static int directFallbacksThisFrame;
    private static int deletedPages;

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

        double worldX = Aero_RenderDistance.cachedCameraX() + x;
        double worldY = Aero_RenderDistance.cachedCameraY() + y;
        double worldZ = Aero_RenderDistance.cachedCameraZ() + z;
        int cellX = Math.floorDiv(floorToInt(worldX), Aero_BECellIndex.CELL_SIZE);
        int cellY = Math.floorDiv(floorToInt(worldY), Aero_BECellIndex.CELL_SIZE);
        int cellZ = Math.floorDiv(floorToInt(worldZ), Aero_BECellIndex.CELL_SIZE);

        PageKey key = new PageKey(be.world, model, texturePath, options,
            cellX, cellY, cellZ, rotation, brightness,
            renderable.aeroRenderStateHash(), renderable.aeroOrientationHash());
        QueuedPage page = ACTIVE.get(key);
        if (page == null) {
            page = new QueuedPage(key);
            ACTIVE.put(key, page);
            ACTIVE_PAGES.add(page);
        }
        page.add(be, worldX, worldY, worldZ);
        queuedThisFrame++;
    }

    public static void flush(double cameraX, double cameraY, double cameraZ) {
        if (ACTIVE_PAGES.isEmpty()) return;
        frameIndex++;
        pageCallsThisFrame = 0;
        pageRebuildsThisFrame = 0;
        directFallbacksThisFrame = 0;
        Collections.sort(ACTIVE_PAGES, BY_RENDER_STATE);
        for (int i = 0; i < ACTIVE_PAGES.size(); i++) {
            flushPage(ACTIVE_PAGES.get(i), cameraX, cameraY, cameraZ);
        }
        ACTIVE.clear();
        for (int i = 0; i < ACTIVE_PAGES.size(); i++) {
            ACTIVE_PAGES.get(i).clear();
        }
        ACTIVE_PAGES.clear();
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
        int[] modelIds = Aero_MeshRenderer.ensureAtRestListIds(page.key.model);
        if (modelIds == null) {
            drawDirect(page, cameraX, cameraY, cameraZ);
            return;
        }

        CachedPage cached = CACHE.get(page.key);
        int membershipHash = page.membershipHash();
        if (cached == null
            || cached.count != page.count
            || cached.membershipHash != membershipHash) {
            CachedPage rebuilt = compilePage(page, modelIds, membershipHash);
            if (rebuilt == null) {
                drawDirect(page, cameraX, cameraY, cameraZ);
                return;
            }
            if (cached != null) deleteIds(cached.ids);
            CACHE.put(page.key, rebuilt);
            cached = rebuilt;
            pageRebuildsThisFrame++;
        }

        drawCached(page.key, cached, cameraX, cameraY, cameraZ);
    }

    private static CachedPage compilePage(QueuedPage page, int[] modelIds, int membershipHash) {
        int[] ids = new int[4];
        for (int g = 0; g < 4; g++) {
            int modelList = modelIds[g];
            if (modelList == 0) continue;
            int id = GL11.glGenLists(1);
            if (id == 0) {
                deleteIds(ids);
                return null;
            }
            GL11.glNewList(id, GL11.GL_COMPILE);
            for (int i = 0; i < page.count; i++) {
                GL11.glPushMatrix();
                GL11.glTranslated(
                    page.worldXs[i] - page.key.originX(),
                    page.worldYs[i] - page.key.originY(),
                    page.worldZs[i] - page.key.originZ());
                Aero_MeshRenderer.applyRotation(page.key.rotation);
                GL11.glCallList(modelList);
                GL11.glPopMatrix();
            }
            GL11.glEndList();
            ids[g] = id;
        }
        return new CachedPage(ids, page.count, membershipHash, frameIndex);
    }

    private static void drawCached(PageKey key, CachedPage cached,
                                   double cameraX, double cameraY, double cameraZ) {
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
                    float bright = key.brightness * Aero_MeshModel.BRIGHTNESS_FACTORS[g];
                    GL11.glColor4f(bright * key.options.tintR,
                                   bright * key.options.tintG,
                                   bright * key.options.tintB,
                                   key.options.alpha);
                    GL11.glCallList(id);
                    pageCallsThisFrame++;
                }
            } finally {
                GL11.glPopMatrix();
            }
        } finally {
            Aero_MeshRenderer.endMeshState();
        }
        cached.lastUsedFrame = frameIndex;
    }

    private static void drawDirect(QueuedPage page, double cameraX, double cameraY, double cameraZ) {
        Aero_AnimatedBatcher.bindTexturePath(page.key.texturePath);
        for (int i = 0; i < page.count; i++) {
            Aero_MeshRenderer.renderModelAtRestPreculled(page.key.model,
                page.worldXs[i] - cameraX,
                page.worldYs[i] - cameraY,
                page.worldZs[i] - cameraZ,
                page.key.rotation,
                page.key.brightness,
                page.key.options);
            directFallbacksThisFrame++;
        }
    }

    private static void drawDirect(Aero_MeshModel model, String texturePath,
                                   double x, double y, double z,
                                   float rotation, float brightness,
                                   Aero_RenderOptions options) {
        if (model == null) return;
        Aero_AnimatedBatcher.bindTexturePath(texturePath);
        Aero_MeshRenderer.renderModelAtRestPreculled(model, x, y, z, rotation, brightness, options);
        directFallbacksThisFrame++;
    }

    private static void sweepOldPages() {
        if (CACHE.isEmpty()) return;
        Iterator<Map.Entry<PageKey, CachedPage>> it = CACHE.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<PageKey, CachedPage> entry = it.next();
            if (frameIndex - entry.getValue().lastUsedFrame > PAGE_TTL_FRAMES) {
                deleteIds(entry.getValue().ids);
                it.remove();
            }
        }
    }

    private static void deleteIds(int[] ids) {
        if (ids == null) return;
        for (int i = 0; i < ids.length; i++) {
            if (ids[i] != 0) {
                GL11.glDeleteLists(ids[i], 1);
                ids[i] = 0;
                deletedPages++;
            }
        }
    }

    private static int floorToInt(double value) {
        int i = (int) value;
        return value < i ? i - 1 : i;
    }

    private static int clampInt(int value, int min, int max) {
        if (value < min) return min;
        if (value > max) return max;
        return value;
    }

    public static int queuedThisFrame() {
        return queuedThisFrame;
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

    private static final class QueuedPage {
        final PageKey key;
        double[] worldXs = new double[16];
        double[] worldYs = new double[16];
        double[] worldZs = new double[16];
        int[] identityHashes = new int[16];
        int count;
        private int membershipHash = 1;

        QueuedPage(PageKey key) {
            this.key = key;
        }

        void add(BlockEntity be, double worldX, double worldY, double worldZ) {
            ensureCapacity();
            worldXs[count] = worldX;
            worldYs[count] = worldY;
            worldZs[count] = worldZ;
            int identity = System.identityHashCode(be);
            identityHashes[count] = identity;
            membershipHash = 31 * membershipHash + identity;
            membershipHash = 31 * membershipHash + floorToInt(worldX * 16.0d);
            membershipHash = 31 * membershipHash + floorToInt(worldY * 16.0d);
            membershipHash = 31 * membershipHash + floorToInt(worldZ * 16.0d);
            count++;
        }

        int membershipHash() {
            return membershipHash;
        }

        void clear() {
            count = 0;
            membershipHash = 1;
        }

        private void ensureCapacity() {
            if (count < worldXs.length) return;
            int n = worldXs.length * 2;
            double[] newXs = new double[n];
            double[] newYs = new double[n];
            double[] newZs = new double[n];
            int[] newHashes = new int[n];
            System.arraycopy(worldXs, 0, newXs, 0, worldXs.length);
            System.arraycopy(worldYs, 0, newYs, 0, worldYs.length);
            System.arraycopy(worldZs, 0, newZs, 0, worldZs.length);
            System.arraycopy(identityHashes, 0, newHashes, 0, identityHashes.length);
            worldXs = newXs;
            worldYs = newYs;
            worldZs = newZs;
            identityHashes = newHashes;
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
}
