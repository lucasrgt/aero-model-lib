package aero.modellib.animation;

/**
 * Immutable render layer inside an {@link Aero_AnimationStack}.
 *
 * The playback is the mutable animation clock; this object only stores how
 * that playback contributes to the final pose.
 */
public final class Aero_AnimationLayer {

    private final Aero_AnimationPlayback playback;
    private final boolean additive;
    private final float weight;

    public static Builder builder(Aero_AnimationPlayback playback) {
        return new Builder(playback);
    }

    public static Aero_AnimationLayer replace(Aero_AnimationPlayback playback) {
        return builder(playback).build();
    }

    public static Aero_AnimationLayer additive(Aero_AnimationPlayback playback) {
        return builder(playback).additive(true).build();
    }

    private Aero_AnimationLayer(Builder builder) {
        this.playback = builder.playback;
        this.additive = builder.additive;
        this.weight = builder.weight;
    }

    public Aero_AnimationPlayback getPlayback() {
        return playback;
    }

    public boolean isAdditive() {
        return additive;
    }

    public float getWeight() {
        return weight;
    }

    public Builder toBuilder() {
        return builder(playback)
            .additive(additive)
            .weight(weight);
    }

    public static final class Builder {
        private final Aero_AnimationPlayback playback;
        private boolean additive;
        private float weight = 1f;

        private Builder(Aero_AnimationPlayback playback) {
            if (playback == null) throw new IllegalArgumentException("playback must not be null");
            this.playback = playback;
        }

        public Builder additive(boolean additive) {
            this.additive = additive;
            return this;
        }

        public Builder weight(float weight) {
            if (Float.isNaN(weight) || Float.isInfinite(weight)) {
                throw new IllegalArgumentException("weight must be finite");
            }
            if (weight < 0f || weight > 1f) {
                throw new IllegalArgumentException("weight must be between 0 and 1");
            }
            this.weight = weight;
            return this;
        }

        public Aero_AnimationLayer build() {
            return new Aero_AnimationLayer(this);
        }
    }
}
