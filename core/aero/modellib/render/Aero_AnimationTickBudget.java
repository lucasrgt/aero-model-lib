package aero.modellib.render;

/**
 * Dense-scene animation tick thinning.
 *
 * <p>Distance LOD already lowers far-away animation update rates, but stress
 * scenes can put hundreds of BEs inside the "close" tier. This helper watches
 * how many BEs asked to tick last client tick and raises a shared stride on
 * the next tick when the scene exceeds the configured budget. The position
 * hash phases entities across that stride, so they do not all update on the
 * same game tick.
 */
public final class Aero_AnimationTickBudget {

    public static final boolean ENABLED =
        "true".equalsIgnoreCase(System.getProperty("aero.animTickBudget"));

    public static final int MAX_TICKED =
        intProperty("aero.maxAnimationTickBE", defaultMaxTicked(), 1, 100000);

    private static final int MAX_STRIDE =
        intProperty("aero.animTickBudget.maxStride", 8, 1, 64);
    private static final boolean HARD_CAP =
        !"false".equalsIgnoreCase(System.getProperty("aero.animTickBudget.hardCap"));

    private static boolean tickBoundarySeen;
    private static int tickIndex;
    private static int seenThisTick;
    private static int tickedThisTick;
    private static int lastSeen;
    private static int lastTicked;
    private static int denseStride = 1;

    private Aero_AnimationTickBudget() {}

    public static void beginClientTick() {
        tickBoundarySeen = true;
        lastSeen = seenThisTick;
        lastTicked = tickedThisTick;
        denseStride = computeDenseStride(lastSeen);
        seenThisTick = 0;
        tickedThisTick = 0;
        tickIndex++;
    }

    public static boolean shouldTick(int baseStride, int age, int x, int y, int z) {
        if (baseStride <= 0) return false;
        if (!ENABLED || MAX_TICKED <= 0 || !tickBoundarySeen) {
            return Aero_AnimationTickLOD.shouldTick(baseStride, age);
        }
        seenThisTick++;
        int stride = baseStride;
        if (denseStride > stride) stride = denseStride;
        if (!phaseAllows(stride, age, phaseHash(x, y, z))) return false;
        if (HARD_CAP && tickedThisTick >= MAX_TICKED) return false;
        tickedThisTick++;
        return true;
    }

    public static int denseStrideThisTick() {
        return denseStride;
    }

    public static int seenLastTick() {
        return lastSeen;
    }

    public static int tickedLastTick() {
        return lastTicked;
    }

    private static int computeDenseStride(int seen) {
        if (!ENABLED || MAX_TICKED <= 0 || seen <= MAX_TICKED) return 1;
        int needed = (seen + MAX_TICKED - 1) / MAX_TICKED;
        int stride = 1;
        while (stride < needed && stride < MAX_STRIDE) stride <<= 1;
        return stride;
    }

    private static boolean phaseAllows(int stride, int age, int phaseHash) {
        if (stride <= 1) return true;
        int phase = phaseHash & 0x7fffffff;
        if ((stride & (stride - 1)) == 0) {
            return ((age + phase) & (stride - 1)) == 0;
        }
        return positiveMod(age + phase, stride) == 0;
    }

    private static int positiveMod(int value, int mod) {
        int result = value % mod;
        return result < 0 ? result + mod : result;
    }

    private static int phaseHash(int x, int y, int z) {
        int h = x * 73428767 ^ y * 912931 ^ z * 42317861;
        h ^= (h >>> 16);
        h *= 0x7feb352d;
        h ^= (h >>> 15);
        h *= 0x846ca68b;
        h ^= (h >>> 16);
        return h;
    }

    private static int defaultMaxTicked() {
        int renderMax = Aero_AnimationRenderBudget.MAX_ANIMATED;
        if (renderMax > 0) return Math.max(96, renderMax);
        return 192;
    }

    private static int intProperty(String name, int fallback, int min, int max) {
        String raw = System.getProperty(name);
        if (raw == null) return fallback;
        try {
            int parsed = Integer.parseInt(raw.trim());
            return parsed >= min && parsed <= max ? parsed : fallback;
        } catch (NumberFormatException e) {
            return fallback;
        }
    }
}
