package aero.modellib;

import java.util.HashMap;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;

/**
 * Small hot-path texture binder for model renderers.
 *
 * <p>StationAPI's TextureManager#getTextureId(String) path is allocation-heavy
 * under mixin instrumentation. Resolving once per texture path keeps dense BE
 * scenes from feeding periodic GC just to bind the same model textures.
 */
public final class Aero_TextureBinder {
    private static final HashMap<String, Integer> IDS_BY_PATH = new HashMap<String, Integer>();
    private static Object lastTextureManager;

    private Aero_TextureBinder() {
    }

    public static void bind(String path) {
        if (path == null) return;
        Object game = FabricLoader.getInstance().getGameInstance();
        if (!(game instanceof Minecraft)) return;
        Minecraft mc = (Minecraft) game;
        if (mc.textureManager == null) return;

        if (mc.textureManager != lastTextureManager) {
            IDS_BY_PATH.clear();
            lastTextureManager = mc.textureManager;
        }

        Integer cached = IDS_BY_PATH.get(path);
        int id;
        if (cached == null) {
            id = mc.textureManager.getTextureId(path);
            IDS_BY_PATH.put(path, Integer.valueOf(id));
        } else {
            id = cached.intValue();
        }
        mc.textureManager.bindTexture(id);
    }

    public static int cachedTextureCount() {
        return IDS_BY_PATH.size();
    }

    public static void clear() {
        IDS_BY_PATH.clear();
        lastTextureManager = null;
    }
}
