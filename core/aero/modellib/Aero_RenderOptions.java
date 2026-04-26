package aero.modellib;

/**
 * Explicit render styling knobs. Instances are immutable and passed to
 * render calls instead of relying on renderer-global state.
 *
 * <p>Defaults are "render normally": white tint, full alpha, depth test on,
 * blending off. Each setter returns a fresh builder; pre-build common
 * variants as static finals when you can.
 */
public final class Aero_RenderOptions {

    public static final Aero_RenderOptions DEFAULT = builder().build();

    public final float tintR;
    public final float tintG;
    public final float tintB;
    public final float alpha;
    public final boolean blend;
    public final boolean depthTest;

    public static Builder builder() {
        return new Builder();
    }

    public static Aero_RenderOptions tint(float r, float g, float b) {
        return builder().tint(r, g, b).build();
    }

    /** Translucent variant: enables blending, sets alpha, leaves tint white. */
    public static Aero_RenderOptions translucent(float alpha) {
        return builder().alpha(alpha).blend(true).build();
    }

    private Aero_RenderOptions(Builder builder) {
        this.tintR = builder.tintR;
        this.tintG = builder.tintG;
        this.tintB = builder.tintB;
        this.alpha = builder.alpha;
        this.blend = builder.blend;
        this.depthTest = builder.depthTest;
    }

    public Builder toBuilder() {
        Builder b = new Builder();
        b.tintR = tintR;
        b.tintG = tintG;
        b.tintB = tintB;
        b.alpha = alpha;
        b.blend = blend;
        b.depthTest = depthTest;
        return b;
    }

    public static final class Builder {
        private float tintR = 1f;
        private float tintG = 1f;
        private float tintB = 1f;
        private float alpha = 1f;
        private boolean blend = false;
        private boolean depthTest = true;

        private Builder() {}

        public Builder tint(float r, float g, float b) {
            validateUnit("tintR", r);
            validateUnit("tintG", g);
            validateUnit("tintB", b);
            tintR = r;
            tintG = g;
            tintB = b;
            return this;
        }

        /** Per-render alpha multiplier (0..1). Renderers ignore it unless {@link #blend} is on. */
        public Builder alpha(float value) {
            validateUnit("alpha", value);
            this.alpha = value;
            return this;
        }

        /** Toggles GL_BLEND (with the standard SRC_ALPHA / ONE_MINUS_SRC_ALPHA pair). */
        public Builder blend(boolean enabled) {
            this.blend = enabled;
            return this;
        }

        /** Disable depth testing for X-ray/overlay style renders. Default {@code true}. */
        public Builder depthTest(boolean enabled) {
            this.depthTest = enabled;
            return this;
        }

        public Aero_RenderOptions build() {
            return new Aero_RenderOptions(this);
        }

        private static void validateUnit(String name, float value) {
            if (Float.isNaN(value) || Float.isInfinite(value)) {
                throw new IllegalArgumentException(name + " must be finite");
            }
            if (value < 0f || value > 1f) {
                throw new IllegalArgumentException(name + " must be between 0 and 1");
            }
        }
    }
}
