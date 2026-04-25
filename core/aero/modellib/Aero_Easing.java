package aero.modellib;

import java.util.HashMap;
import java.util.Map;

/**
 * Easing curve dispatch for Aero_AnimationClip's per-keyframe interpolation
 * mode. Each easing maps a linear alpha t ∈ [0, 1] onto a non-linear curve;
 * the clip then linearly interpolates between keyframe values using the
 * eased alpha.
 *
 * <p>Curves follow the standard <a href="https://easings.net/">easings.net</a>
 * formulas. STEP (no interpolation, snap to lo) and CATMULLROM (cubic spline
 * across 4 keyframes) are not curves over alpha — they are handled directly
 * by the clip's sampler.
 *
 * <p>Adding a new easing: bump the INTERP_* counter, dispatch in {@link #ease},
 * and register the JSON name in {@link #BY_NAME}. Aero_AnimationLoader looks
 * up the name there and stores the int constant on each keyframe.
 */
public final class Aero_Easing {

    private Aero_Easing() {}

    // -------------------------------------------------------------------
    // INTERP_* constants — must be unique and stable across versions because
    // they are written to the per-keyframe interp arrays of every clip.
    // Aero_AnimationClip mirrors LINEAR/CATMULLROM/STEP for backward compat
    // with code that references them on the clip class directly.
    // -------------------------------------------------------------------
    public static final int LINEAR             = 0;
    public static final int CATMULLROM         = 1;
    public static final int STEP               = 2;

    public static final int EASE_IN_SINE       = 3;
    public static final int EASE_OUT_SINE      = 4;
    public static final int EASE_IN_OUT_SINE   = 5;

    public static final int EASE_IN_QUAD       = 6;
    public static final int EASE_OUT_QUAD      = 7;
    public static final int EASE_IN_OUT_QUAD   = 8;

    public static final int EASE_IN_CUBIC      = 9;
    public static final int EASE_OUT_CUBIC     = 10;
    public static final int EASE_IN_OUT_CUBIC  = 11;

    public static final int EASE_IN_QUART      = 12;
    public static final int EASE_OUT_QUART     = 13;
    public static final int EASE_IN_OUT_QUART  = 14;

    public static final int EASE_IN_QUINT      = 15;
    public static final int EASE_OUT_QUINT     = 16;
    public static final int EASE_IN_OUT_QUINT  = 17;

    public static final int EASE_IN_EXPO       = 18;
    public static final int EASE_OUT_EXPO      = 19;
    public static final int EASE_IN_OUT_EXPO   = 20;

    public static final int EASE_IN_CIRC       = 21;
    public static final int EASE_OUT_CIRC      = 22;
    public static final int EASE_IN_OUT_CIRC   = 23;

    public static final int EASE_IN_BACK       = 24;
    public static final int EASE_OUT_BACK      = 25;
    public static final int EASE_IN_OUT_BACK   = 26;

    public static final int EASE_IN_ELASTIC    = 27;
    public static final int EASE_OUT_ELASTIC   = 28;
    public static final int EASE_IN_OUT_ELASTIC = 29;

    public static final int EASE_IN_BOUNCE     = 30;
    public static final int EASE_OUT_BOUNCE    = 31;
    public static final int EASE_IN_OUT_BOUNCE = 32;

    private static final Map BY_NAME = new HashMap();
    static {
        BY_NAME.put("linear",             Integer.valueOf(LINEAR));
        BY_NAME.put("catmullrom",         Integer.valueOf(CATMULLROM));
        BY_NAME.put("step",               Integer.valueOf(STEP));
        BY_NAME.put("easeInSine",         Integer.valueOf(EASE_IN_SINE));
        BY_NAME.put("easeOutSine",        Integer.valueOf(EASE_OUT_SINE));
        BY_NAME.put("easeInOutSine",      Integer.valueOf(EASE_IN_OUT_SINE));
        BY_NAME.put("easeInQuad",         Integer.valueOf(EASE_IN_QUAD));
        BY_NAME.put("easeOutQuad",        Integer.valueOf(EASE_OUT_QUAD));
        BY_NAME.put("easeInOutQuad",      Integer.valueOf(EASE_IN_OUT_QUAD));
        BY_NAME.put("easeInCubic",        Integer.valueOf(EASE_IN_CUBIC));
        BY_NAME.put("easeOutCubic",       Integer.valueOf(EASE_OUT_CUBIC));
        BY_NAME.put("easeInOutCubic",     Integer.valueOf(EASE_IN_OUT_CUBIC));
        BY_NAME.put("easeInQuart",        Integer.valueOf(EASE_IN_QUART));
        BY_NAME.put("easeOutQuart",       Integer.valueOf(EASE_OUT_QUART));
        BY_NAME.put("easeInOutQuart",     Integer.valueOf(EASE_IN_OUT_QUART));
        BY_NAME.put("easeInQuint",        Integer.valueOf(EASE_IN_QUINT));
        BY_NAME.put("easeOutQuint",       Integer.valueOf(EASE_OUT_QUINT));
        BY_NAME.put("easeInOutQuint",     Integer.valueOf(EASE_IN_OUT_QUINT));
        BY_NAME.put("easeInExpo",         Integer.valueOf(EASE_IN_EXPO));
        BY_NAME.put("easeOutExpo",        Integer.valueOf(EASE_OUT_EXPO));
        BY_NAME.put("easeInOutExpo",      Integer.valueOf(EASE_IN_OUT_EXPO));
        BY_NAME.put("easeInCirc",         Integer.valueOf(EASE_IN_CIRC));
        BY_NAME.put("easeOutCirc",        Integer.valueOf(EASE_OUT_CIRC));
        BY_NAME.put("easeInOutCirc",      Integer.valueOf(EASE_IN_OUT_CIRC));
        BY_NAME.put("easeInBack",         Integer.valueOf(EASE_IN_BACK));
        BY_NAME.put("easeOutBack",        Integer.valueOf(EASE_OUT_BACK));
        BY_NAME.put("easeInOutBack",      Integer.valueOf(EASE_IN_OUT_BACK));
        BY_NAME.put("easeInElastic",      Integer.valueOf(EASE_IN_ELASTIC));
        BY_NAME.put("easeOutElastic",     Integer.valueOf(EASE_OUT_ELASTIC));
        BY_NAME.put("easeInOutElastic",   Integer.valueOf(EASE_IN_OUT_ELASTIC));
        BY_NAME.put("easeInBounce",       Integer.valueOf(EASE_IN_BOUNCE));
        BY_NAME.put("easeOutBounce",      Integer.valueOf(EASE_OUT_BOUNCE));
        BY_NAME.put("easeInOutBounce",    Integer.valueOf(EASE_IN_OUT_BOUNCE));
    }

    /**
     * Resolves a string name from the .anim.json {@code interp} field to the
     * INTERP_* constant, or LINEAR if unknown. Unknown names degrade to
     * linear instead of throwing so a typo doesn't crash the loader.
     */
    public static int byName(String name) {
        if (name == null) return LINEAR;
        Integer v = (Integer) BY_NAME.get(name);
        return v != null ? v.intValue() : LINEAR;
    }

    /**
     * Maps linear alpha [0, 1] onto the requested curve. STEP and CATMULLROM
     * fall through to linear (the clip's sampler handles them with the raw
     * keyframe values, not via this function).
     */
    public static float ease(int mode, float t) {
        switch (mode) {
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
            default:                  return t;     // LINEAR / unknown
        }
    }

    // -------- Sine --------
    private static float easeInSine(float t)    { return 1f - (float) Math.cos(t * Math.PI * 0.5); }
    private static float easeOutSine(float t)   { return (float) Math.sin(t * Math.PI * 0.5); }
    private static float easeInOutSine(float t) { return -((float) Math.cos(Math.PI * t) - 1f) * 0.5f; }

    // -------- Expo --------
    private static float easeInOutExpo(float t) {
        if (t == 0f) return 0f;
        if (t == 1f) return 1f;
        return t < 0.5f
            ? 0.5f * (float) Math.pow(2.0, 20.0 * t - 10.0)
            : 1f - 0.5f * (float) Math.pow(2.0, -20.0 * t + 10.0);
    }

    // -------- Circ --------
    private static float easeInOutCirc(float t) {
        return t < 0.5f
            ? (1f - (float) Math.sqrt(1.0 - 4.0 * t * t)) * 0.5f
            : ((float) Math.sqrt(1.0 - sq(-2f * t + 2f)) + 1f) * 0.5f;
    }

    // -------- Back (overshoot) --------
    private static final float BACK_C1 = 1.70158f;
    private static final float BACK_C2 = BACK_C1 * 1.525f;
    private static final float BACK_C3 = BACK_C1 + 1f;

    private static float easeInBack(float t)    { return BACK_C3 * t * t * t - BACK_C1 * t * t; }
    private static float easeOutBack(float t)   {
        float u = t - 1f;
        return 1f + BACK_C3 * u * u * u + BACK_C1 * u * u;
    }
    private static float easeInOutBack(float t) {
        return t < 0.5f
            ? (sq(2f * t) * ((BACK_C2 + 1f) * 2f * t - BACK_C2)) * 0.5f
            : (sq(2f * t - 2f) * ((BACK_C2 + 1f) * (2f * t - 2f) + BACK_C2) + 2f) * 0.5f;
    }

    // -------- Elastic (springy overshoot) --------
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

    // -------- Bounce --------
    private static final float BOUNCE_N1 = 7.5625f;
    private static final float BOUNCE_D1 = 2.75f;

    private static float bounceOut(float t) {
        if (t < 1f / BOUNCE_D1)        return BOUNCE_N1 * t * t;
        if (t < 2f / BOUNCE_D1)        { t -= 1.5f / BOUNCE_D1;  return BOUNCE_N1 * t * t + 0.75f; }
        if (t < 2.5f / BOUNCE_D1)      { t -= 2.25f / BOUNCE_D1; return BOUNCE_N1 * t * t + 0.9375f; }
                                       { t -= 2.625f / BOUNCE_D1; return BOUNCE_N1 * t * t + 0.984375f; }
    }
    private static float easeInOutBounce(float t) {
        return t < 0.5f
            ? (1f - bounceOut(1f - 2f * t)) * 0.5f
            : (1f + bounceOut(2f * t - 1f)) * 0.5f;
    }

    // -------- helpers --------
    private static float sq(float x)    { return x * x; }
    private static float cube(float x)  { return x * x * x; }
    private static float quart(float x) { return x * x * x * x; }
    private static float quint(float x) { return x * x * x * x * x; }
}
