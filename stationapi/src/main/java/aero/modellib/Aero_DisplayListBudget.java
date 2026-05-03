package aero.modellib;

import org.lwjgl.opengl.GL11;

import aero.modellib.util.Aero_PerfConfig;

/**
 * Small guardrail around OpenGL display-list allocation. Driver memory is not
 * visible in Java heap graphs, so high-memory mode still needs a hard ceiling
 * and counters when old GL drivers stop handing out IDs.
 */
public final class Aero_DisplayListBudget {

    private static final int MAX_LIVE =
        Aero_PerfConfig.intProperty("aero.displayLists.maxLive",
            -1, 32768, -1, Integer.MAX_VALUE);
    private static final int WARN_LIVE =
        Aero_PerfConfig.intProperty("aero.displayLists.warnLive",
            -1, 24576, -1, Integer.MAX_VALUE);

    private static int liveLists;
    private static int peakLiveLists;
    private static int totalAllocatedLists;
    private static int deniedAllocations;
    private static int failedAllocations;
    private static boolean warned;

    private Aero_DisplayListBudget() {}

    public static int glGenList() {
        if (MAX_LIVE >= 0 && liveLists >= MAX_LIVE) {
            deniedAllocations++;
            return 0;
        }
        int id = GL11.glGenLists(1);
        if (id == 0) {
            failedAllocations++;
            return 0;
        }
        liveLists++;
        totalAllocatedLists++;
        if (liveLists > peakLiveLists) peakLiveLists = liveLists;
        maybeWarn();
        return id;
    }

    public static void glDeleteList(int id) {
        if (id == 0) return;
        GL11.glDeleteLists(id, 1);
        if (liveLists > 0) liveLists--;
    }

    public static int liveLists() {
        return liveLists;
    }

    public static int peakLiveLists() {
        return peakLiveLists;
    }

    public static int totalAllocatedLists() {
        return totalAllocatedLists;
    }

    public static int deniedAllocations() {
        return deniedAllocations;
    }

    public static int failedAllocations() {
        return failedAllocations;
    }

    public static int maxLiveLists() {
        return MAX_LIVE;
    }

    private static void maybeWarn() {
        if (warned || WARN_LIVE < 0 || liveLists < WARN_LIVE) return;
        warned = true;
        System.out.println("[Aero_DisplayListBudget] live display lists reached "
            + liveLists + " (warn=" + WARN_LIVE + ", max=" + MAX_LIVE + ")");
    }
}
