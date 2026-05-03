package aero.modellib.render;

/**
 * Runtime toggles for Aero render LOD heuristics.
 */
public final class Aero_LODConfig {

    /**
     * Multiplier applied to |dy| before computing distance squared.
     * 1.0 = legacy isotropic euclidean distance. Values above 1.0 make
     * vertical distance count heavier, so tall towers LOD out faster.
     *
     * <p>Override with {@code -Daero.ybias=N}. Default 2.0 means 16 blocks
     * above/below the player count like 32 horizontal blocks.</p>
     */
    public static final double Y_BIAS = parseDouble("aero.ybias", 2.0d);

    /**
     * Master switch for Smart LOD. Set {@code -Daero.smartlod=false} to
     * restore legacy euclidean distance without changing renderer code.
     */
    public static final boolean ENABLED =
        !"false".equalsIgnoreCase(System.getProperty("aero.smartlod"));

    private Aero_LODConfig() {
    }

    private static double parseDouble(String key, double dflt) {
        String value = System.getProperty(key);
        if (value == null || value.length() == 0) return dflt;
        try {
            double parsed = Double.parseDouble(value);
            if (Double.isNaN(parsed) || Double.isInfinite(parsed)) return dflt;
            return parsed > 0.0d ? parsed : dflt;
        } catch (NumberFormatException ignored) {
            return dflt;
        }
    }
}
