package aero.modellib;

/**
 * Shared performance-preset helpers. Defaults stay conservative unless the
 * user opts into {@code -Daero.perf.memory=high}; explicit properties always
 * win over the preset.
 */
public final class Aero_PerfConfig {

    public static final boolean HIGH_MEMORY = isHighMemoryPreset();

    private Aero_PerfConfig() {}

    public static boolean booleanProperty(String name,
                                          boolean normalDefault,
                                          boolean highMemoryDefault) {
        String value = System.getProperty(name);
        if (value == null) return HIGH_MEMORY ? highMemoryDefault : normalDefault;
        return "true".equalsIgnoreCase(value)
            || "1".equals(value)
            || "yes".equalsIgnoreCase(value)
            || "on".equalsIgnoreCase(value);
    }

    public static int intProperty(String name,
                                  int normalDefault,
                                  int highMemoryDefault,
                                  int min,
                                  int max) {
        int value = HIGH_MEMORY ? highMemoryDefault : normalDefault;
        String raw = System.getProperty(name);
        if (raw != null) {
            try {
                value = Integer.parseInt(raw.trim());
            } catch (NumberFormatException e) {
                // Keep preset/default value.
            }
        }
        if (value < min) return min;
        if (value > max) return max;
        return value;
    }

    public static double doubleProperty(String name,
                                        double normalDefault,
                                        double highMemoryDefault,
                                        double min,
                                        double max) {
        double value = HIGH_MEMORY ? highMemoryDefault : normalDefault;
        String raw = System.getProperty(name);
        if (raw != null) {
            try {
                value = Double.parseDouble(raw.trim());
            } catch (NumberFormatException e) {
                // Keep preset/default value.
            }
        }
        if (Double.isNaN(value) || Double.isInfinite(value)) {
            value = normalDefault;
        }
        if (value < min) return min;
        if (value > max) return max;
        return value;
    }

    public static boolean isPropertyExplicit(String name) {
        return System.getProperty(name) != null;
    }

    private static boolean isHighMemoryPreset() {
        String mode = System.getProperty("aero.perf.memory");
        if (mode == null) return false;
        return "high".equalsIgnoreCase(mode)
            || "aggressive".equalsIgnoreCase(mode)
            || "ram".equalsIgnoreCase(mode)
            || "true".equalsIgnoreCase(mode);
    }
}
