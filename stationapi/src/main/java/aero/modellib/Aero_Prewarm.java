package aero.modellib;

import java.util.ArrayDeque;
import java.util.IdentityHashMap;

/**
 * Opt-in render-thread prewarm queue for display-list caches. Consumers can
 * enqueue models after loading them; the queue drains gradually during render
 * frames so cache compilation does not all land on the first visible frame.
 */
public final class Aero_Prewarm {

    public static final boolean ENABLED =
        Aero_PerfConfig.booleanProperty("aero.prewarm", false, true);
    private static final int PER_FRAME =
        Aero_PerfConfig.intProperty("aero.prewarm.perFrame", 0, 4, 0, 1024);
    private static final long MAX_NANOS_PER_FRAME = (long)
        (Aero_PerfConfig.doubleProperty("aero.prewarm.maxMsPerFrame",
            0.0d, 1.0d, 0.0d, 1000.0d) * 1000000.0d);

    private static final ArrayDeque<Aero_MeshModel> MODELS =
        new ArrayDeque<Aero_MeshModel>();
    private static final IdentityHashMap<Aero_MeshModel, Boolean> MODEL_SET =
        new IdentityHashMap<Aero_MeshModel, Boolean>();

    private static int drainedThisFrame;
    private static int queuedModels;

    private Aero_Prewarm() {}

    public static void enqueueModel(Aero_MeshModel model) {
        if (!ENABLED || model == null) return;
        if (MODEL_SET.containsKey(model)) return;
        MODEL_SET.put(model, Boolean.TRUE);
        MODELS.addLast(model);
        queuedModels++;
    }

    static void drainFrame() {
        drainedThisFrame = 0;
        if (!ENABLED || PER_FRAME <= 0 || MODELS.isEmpty()) return;
        long start = System.nanoTime();
        while (drainedThisFrame < PER_FRAME && !MODELS.isEmpty()) {
            if (MAX_NANOS_PER_FRAME > 0
                && System.nanoTime() - start >= MAX_NANOS_PER_FRAME) {
                break;
            }
            Aero_MeshModel model = MODELS.removeFirst();
            MODEL_SET.remove(model);
            Aero_MeshRenderer.prewarmModel(model);
            drainedThisFrame++;
        }
    }

    public static int queuedModelCount() {
        return MODELS.size();
    }

    public static int queuedModelsTotal() {
        return queuedModels;
    }

    public static int drainedThisFrame() {
        return drainedThisFrame;
    }
}
