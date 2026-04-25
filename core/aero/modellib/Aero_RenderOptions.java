package aero.modellib;

/**
 * Explicit render styling knobs. Instances are immutable and passed to
 * render calls instead of relying on renderer-global state.
 */
public final class Aero_RenderOptions {

    public static final Aero_RenderOptions DEFAULT = builder().build();

    public final float tintR;
    public final float tintG;
    public final float tintB;

    public static Builder builder() {
        return new Builder();
    }

    public static Aero_RenderOptions tint(float r, float g, float b) {
        return builder().tint(r, g, b).build();
    }

    private Aero_RenderOptions(Builder builder) {
        this.tintR = builder.tintR;
        this.tintG = builder.tintG;
        this.tintB = builder.tintB;
    }

    public static final class Builder {
        private float tintR = 1f;
        private float tintG = 1f;
        private float tintB = 1f;

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
