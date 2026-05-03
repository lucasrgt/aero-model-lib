package aero.modellib.animation;

import java.util.HashMap;
import java.util.Map;

/**
 * Type-safe easing strategy for per-keyframe interpolation.
 *
 * STEP and CATMULLROM are channel-level interpolation modes handled by
 * Aero_AnimationClip. The remaining constants remap alpha in [0, 1].
 */
public enum Aero_Easing {
    LINEAR("linear"),
    CATMULLROM("catmullrom"),
    STEP("step"),

    EASE_IN_SINE("easeInSine"),
    EASE_OUT_SINE("easeOutSine"),
    EASE_IN_OUT_SINE("easeInOutSine"),

    EASE_IN_QUAD("easeInQuad"),
    EASE_OUT_QUAD("easeOutQuad"),
    EASE_IN_OUT_QUAD("easeInOutQuad"),

    EASE_IN_CUBIC("easeInCubic"),
    EASE_OUT_CUBIC("easeOutCubic"),
    EASE_IN_OUT_CUBIC("easeInOutCubic"),

    EASE_IN_QUART("easeInQuart"),
    EASE_OUT_QUART("easeOutQuart"),
    EASE_IN_OUT_QUART("easeInOutQuart"),

    EASE_IN_QUINT("easeInQuint"),
    EASE_OUT_QUINT("easeOutQuint"),
    EASE_IN_OUT_QUINT("easeInOutQuint"),

    EASE_IN_EXPO("easeInExpo"),
    EASE_OUT_EXPO("easeOutExpo"),
    EASE_IN_OUT_EXPO("easeInOutExpo"),

    EASE_IN_CIRC("easeInCirc"),
    EASE_OUT_CIRC("easeOutCirc"),
    EASE_IN_OUT_CIRC("easeInOutCirc"),

    EASE_IN_BACK("easeInBack"),
    EASE_OUT_BACK("easeOutBack"),
    EASE_IN_OUT_BACK("easeInOutBack"),

    EASE_IN_ELASTIC("easeInElastic"),
    EASE_OUT_ELASTIC("easeOutElastic"),
    EASE_IN_OUT_ELASTIC("easeInOutElastic"),

    EASE_IN_BOUNCE("easeInBounce"),
    EASE_OUT_BOUNCE("easeOutBounce"),
    EASE_IN_OUT_BOUNCE("easeInOutBounce");

    private static final Map BY_NAME = new HashMap();

    static {
        Aero_Easing[] values = values();
        for (int i = 0; i < values.length; i++) {
            BY_NAME.put(values[i].jsonName, values[i]);
        }
    }

    public final String jsonName;

    Aero_Easing(String jsonName) {
        this.jsonName = jsonName;
    }

    public static Aero_Easing fromName(String name) {
        if (name == null) throw new IllegalArgumentException("interp must be a string");
        Aero_Easing easing = (Aero_Easing) BY_NAME.get(name);
        if (easing == null) throw new IllegalArgumentException("unknown easing: " + name);
        return easing;
    }

    public float apply(float t) {
        switch (this) {
            case EASE_IN_SINE:        return easeInSine(t);
            case EASE_OUT_SINE:       return easeOutSine(t);
            case EASE_IN_OUT_SINE:    return easeInOutSine(t);
            case EASE_IN_QUAD:        return t * t;
            case EASE_OUT_QUAD:       { float u = 1f - t; return 1f - u * u; }
            case EASE_IN_OUT_QUAD:    return t < 0.5f ? 2f * t * t : 1f - 0.5f * sq(-2f * t + 2f);
            case EASE_IN_CUBIC:       return t * t * t;
            case EASE_OUT_CUBIC:      { float u = 1f - t; return 1f - u * u * u; }
            case EASE_IN_OUT_CUBIC:   return t < 0.5f ? 4f * t * t * t : 1f - 0.5f * cube(-2f * t + 2f);
            case EASE_IN_QUART:       return t * t * t * t;
            case EASE_OUT_QUART:      { float u = 1f - t; return 1f - u * u * u * u; }
            case EASE_IN_OUT_QUART:   return t < 0.5f ? 8f * t * t * t * t : 1f - 0.5f * quart(-2f * t + 2f);
            case EASE_IN_QUINT:       return t * t * t * t * t;
            case EASE_OUT_QUINT:      { float u = 1f - t; return 1f - u * u * u * u * u; }
            case EASE_IN_OUT_QUINT:   return t < 0.5f ? 16f * t * t * t * t * t : 1f - 0.5f * quint(-2f * t + 2f);
            case EASE_IN_EXPO:        return t == 0f ? 0f : (float) Math.pow(2.0, 10.0 * t - 10.0);
            case EASE_OUT_EXPO:       return t == 1f ? 1f : 1f - (float) Math.pow(2.0, -10.0 * t);
            case EASE_IN_OUT_EXPO:    return easeInOutExpo(t);
            case EASE_IN_CIRC:        return 1f - (float) Math.sqrt(1.0 - t * t);
            case EASE_OUT_CIRC:       { float u = t - 1f; return (float) Math.sqrt(1.0 - u * u); }
            case EASE_IN_OUT_CIRC:    return easeInOutCirc(t);
            case EASE_IN_BACK:        return easeInBack(t);
            case EASE_OUT_BACK:       return easeOutBack(t);
            case EASE_IN_OUT_BACK:    return easeInOutBack(t);
            case EASE_IN_ELASTIC:     return easeInElastic(t);
            case EASE_OUT_ELASTIC:    return easeOutElastic(t);
            case EASE_IN_OUT_ELASTIC: return easeInOutElastic(t);
            case EASE_IN_BOUNCE:      return 1f - bounceOut(1f - t);
            case EASE_OUT_BOUNCE:     return bounceOut(t);
            case EASE_IN_OUT_BOUNCE:  return easeInOutBounce(t);
            default:                  return t;
        }
    }

    private static float easeInSine(float t)    { return 1f - (float) Math.cos(t * Math.PI * 0.5); }
    private static float easeOutSine(float t)   { return (float) Math.sin(t * Math.PI * 0.5); }
    private static float easeInOutSine(float t) { return -((float) Math.cos(Math.PI * t) - 1f) * 0.5f; }

    private static float easeInOutExpo(float t) {
        if (t == 0f) return 0f;
        if (t == 1f) return 1f;
        return t < 0.5f
            ? 0.5f * (float) Math.pow(2.0, 20.0 * t - 10.0)
            : 1f - 0.5f * (float) Math.pow(2.0, -20.0 * t + 10.0);
    }

    private static float easeInOutCirc(float t) {
        return t < 0.5f
            ? (1f - (float) Math.sqrt(1.0 - 4.0 * t * t)) * 0.5f
            : ((float) Math.sqrt(1.0 - sq(-2f * t + 2f)) + 1f) * 0.5f;
    }

    private static final float BACK_C1 = 1.70158f;
    private static final float BACK_C2 = BACK_C1 * 1.525f;
    private static final float BACK_C3 = BACK_C1 + 1f;

    private static float easeInBack(float t) {
        return BACK_C3 * t * t * t - BACK_C1 * t * t;
    }

    private static float easeOutBack(float t) {
        float u = t - 1f;
        return 1f + BACK_C3 * u * u * u + BACK_C1 * u * u;
    }

    private static float easeInOutBack(float t) {
        return t < 0.5f
            ? (sq(2f * t) * ((BACK_C2 + 1f) * 2f * t - BACK_C2)) * 0.5f
            : (sq(2f * t - 2f) * ((BACK_C2 + 1f) * (2f * t - 2f) + BACK_C2) + 2f) * 0.5f;
    }

    private static final float ELASTIC_C4 = (float) (2.0 * Math.PI / 3.0);
    private static final float ELASTIC_C5 = (float) (2.0 * Math.PI / 4.5);

    private static float easeInElastic(float t) {
        if (t == 0f) return 0f;
        if (t == 1f) return 1f;
        return -((float) Math.pow(2.0, 10.0 * t - 10.0))
             * (float) Math.sin((t * 10.0 - 10.75) * ELASTIC_C4);
    }

    private static float easeOutElastic(float t) {
        if (t == 0f) return 0f;
        if (t == 1f) return 1f;
        return ((float) Math.pow(2.0, -10.0 * t))
             * (float) Math.sin((t * 10.0 - 0.75) * ELASTIC_C4) + 1f;
    }

    private static float easeInOutElastic(float t) {
        if (t == 0f) return 0f;
        if (t == 1f) return 1f;
        return t < 0.5f
            ? -((float) Math.pow(2.0, 20.0 * t - 10.0) * (float) Math.sin((20.0 * t - 11.125) * ELASTIC_C5)) * 0.5f
            : ((float) Math.pow(2.0, -20.0 * t + 10.0) * (float) Math.sin((20.0 * t - 11.125) * ELASTIC_C5)) * 0.5f + 1f;
    }

    private static final float BOUNCE_N1 = 7.5625f;
    private static final float BOUNCE_D1 = 2.75f;

    private static float bounceOut(float t) {
        if (t < 1f / BOUNCE_D1)   return BOUNCE_N1 * t * t;
        if (t < 2f / BOUNCE_D1)   { t -= 1.5f / BOUNCE_D1;  return BOUNCE_N1 * t * t + 0.75f; }
        if (t < 2.5f / BOUNCE_D1) { t -= 2.25f / BOUNCE_D1; return BOUNCE_N1 * t * t + 0.9375f; }
        t -= 2.625f / BOUNCE_D1;
        return BOUNCE_N1 * t * t + 0.984375f;
    }

    private static float easeInOutBounce(float t) {
        return t < 0.5f
            ? (1f - bounceOut(1f - 2f * t)) * 0.5f
            : (1f + bounceOut(2f * t - 1f)) * 0.5f;
    }

    private static float sq(float x)    { return x * x; }
    private static float cube(float x)  { return x * x * x; }
    private static float quart(float x) { return x * x * x * x; }
    private static float quint(float x) { return x * x * x * x * x; }
}
