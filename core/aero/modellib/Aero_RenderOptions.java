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
    public final Aero_MeshBlendMode blend;
    public final boolean depthTest;
    /**
     * Whether GL_CULL_FACE is enabled during the render. Default {@code false}
     * — OBJ exporters often produce inconsistent triangle winding, so
     * enabling cull globally would silently invisibilify some models. Opt-in
     * via {@link Builder#cullFaces(boolean)} for ~40% GPU triangle savings
     * once you've verified the model's winding is consistent. Decals,
     * banners, and transparent foliage should keep this {@code false}.
     */
    public final boolean cullFaces;

    public static Builder builder() {
        return new Builder();
    }

    public static Aero_RenderOptions tint(float r, float g, float b) {
        return builder().tint(r, g, b).build();
    }

    /** Translucent variant: enables alpha blending, sets alpha, leaves tint white. */
    public static Aero_RenderOptions translucent(float alpha) {
        return builder().alpha(alpha).blend(Aero_MeshBlendMode.ALPHA).build();
    }

    /**
     * Additive variant: enables additive blending so the mesh brightens
     * whatever is behind it. Tint multiplies the contribution; alpha scales
     * its intensity. Pair with a glow texture (e.g. white-on-black) for
     * energy/plasma effects.
     */
    public static Aero_RenderOptions additive(float alpha) {
        return builder().alpha(alpha).blend(Aero_MeshBlendMode.ADDITIVE).build();
    }

    private Aero_RenderOptions(Builder builder) {
        this.tintR = builder.tintR;
        this.tintG = builder.tintG;
        this.tintB = builder.tintB;
        this.alpha = builder.alpha;
        this.blend = builder.blend;
        this.depthTest = builder.depthTest;
        this.cullFaces = builder.cullFaces;
    }

    public Builder toBuilder() {
        Builder b = new Builder();
        b.tintR = tintR;
        b.tintG = tintG;
        b.tintB = tintB;
        b.alpha = alpha;
        b.blend = blend;
        b.depthTest = depthTest;
        b.cullFaces = cullFaces;
        return b;
    }

    public static final class Builder {
        private float tintR = 1f;
        private float tintG = 1f;
        private float tintB = 1f;
        private float alpha = 1f;
        private Aero_MeshBlendMode blend = Aero_MeshBlendMode.OFF;
        private boolean depthTest = true;
        // Default false (matches pre-v0.2.2 behavior). OBJ exporters often
        // produce inconsistent triangle winding — enabling cull globally
        // would silently invisibilify some models. Opt-in via cullFaces(true)
        // when you've verified the model's winding is consistent.
        private boolean cullFaces = false;

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

        /** Per-render alpha multiplier (0..1). Renderers ignore it unless blending is on. */
        public Builder alpha(float value) {
            validateUnit("alpha", value);
            this.alpha = value;
            return this;
        }

        /** Selects the blend mode (OFF/ALPHA/ADDITIVE). */
        public Builder blend(Aero_MeshBlendMode mode) {
            if (mode == null) throw new IllegalArgumentException("blend mode must not be null");
            this.blend = mode;
            return this;
        }

        /** Convenience: {@code true} = ALPHA blending, {@code false} = OFF. */
        public Builder blend(boolean enabled) {
            this.blend = enabled ? Aero_MeshBlendMode.ALPHA : Aero_MeshBlendMode.OFF;
            return this;
        }

        /** Disable depth testing for X-ray/overlay style renders. Default {@code true}. */
        public Builder depthTest(boolean enabled) {
            this.depthTest = enabled;
            return this;
        }

        /**
         * Marks the model as double-sided — disables GL_CULL_FACE so triangles
         * facing away from the camera still draw. Use for decals, banners,
         * transparent foliage, or any mesh whose winding is intentionally
         * inconsistent. Default is single-sided (back-face culled).
         */
        public Builder doubleSided() {
            this.cullFaces = false;
            return this;
        }

        /** Explicit override for back-face culling (default {@code true}). */
        public Builder cullFaces(boolean enabled) {
            this.cullFaces = enabled;
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
