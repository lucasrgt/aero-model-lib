package aero.modellib.animation;

import aero.modellib.Aero_AnimationState;

/**
 * Declarative animation wiring for one model family.
 *
 * A spec owns the loaded animation bundle plus the state-to-clip map that
 * every instance should use. Entity/tile classes can keep one static spec and
 * create their per-instance playback from it, instead of passing bundle and
 * definition through several constructors and renderer calls.
 */
public final class Aero_AnimationSpec {

    private final String animationPath;
    private final Aero_AnimationBundle bundle;
    private final Aero_AnimationDefinition definition;
    private final int defaultTransitionTicks;

    public static Builder builder(String animationPath) {
        return new Builder(animationPath);
    }

    public static Builder builder(Aero_AnimationBundle bundle) {
        return new Builder(bundle);
    }

    private Aero_AnimationSpec(String animationPath,
                               Aero_AnimationBundle bundle,
                               Aero_AnimationDefinition definition,
                               int defaultTransitionTicks) {
        if (bundle == null) throw new IllegalArgumentException("bundle must not be null");
        if (definition == null) throw new IllegalArgumentException("definition must not be null");
        if (defaultTransitionTicks < 0) {
            throw new IllegalArgumentException("defaultTransitionTicks must be >= 0");
        }
        this.animationPath = animationPath;
        this.bundle = bundle;
        this.definition = definition;
        this.defaultTransitionTicks = defaultTransitionTicks;
    }

    public String getAnimationPath() {
        return animationPath;
    }

    public Aero_AnimationBundle getBundle() {
        return bundle;
    }

    public Aero_AnimationDefinition getDefinition() {
        return definition;
    }

    public String getClipName(int stateId) {
        return definition.getClipName(stateId);
    }

    public int getDefaultTransitionTicks() {
        return defaultTransitionTicks;
    }

    public Aero_AnimationPlayback createPlayback() {
        return definition.createPlayback(bundle);
    }

    public Aero_AnimationState createState() {
        return definition.createState(bundle);
    }

    /**
     * Creates a state with a custom NBT key prefix — see
     * {@link Aero_AnimationDefinition#createState(Aero_AnimationBundle, String)}.
     */
    public Aero_AnimationState createState(String nbtKeyPrefix) {
        return definition.createState(bundle, nbtKeyPrefix);
    }

    /**
     * Sets {@code playback}'s state honoring this spec's default transition.
     * When {@code defaultTransitionTicks > 0} this is equivalent to
     * {@link Aero_AnimationPlayback#setStateWithTransition}; otherwise it
     * falls through to {@link Aero_AnimationPlayback#setState}.
     */
    public void applyState(Aero_AnimationPlayback playback, int stateId) {
        if (playback == null) throw new IllegalArgumentException("playback must not be null");
        if (defaultTransitionTicks > 0) {
            playback.setStateWithTransition(stateId, defaultTransitionTicks);
        } else {
            playback.setState(stateId);
        }
    }

    /**
     * Runs {@code router} against {@code playback}, using this spec's
     * {@code defaultTransitionTicks} when the router itself wasn't
     * configured via {@link Aero_AnimationStateRouter#withTransition}. Lets
     * the modder declare the spec once and route via predicates without
     * tracking transition counts in two places.
     */
    public void applyState(Aero_AnimationPlayback playback, Aero_AnimationStateRouter router) {
        if (playback == null) throw new IllegalArgumentException("playback must not be null");
        if (router == null) throw new IllegalArgumentException("router must not be null");
        router.applyTo(playback, defaultTransitionTicks);
    }

    public static final class Builder {
        private final String animationPath;
        private final Aero_AnimationBundle bundle;
        private Aero_AnimationDefinition definition;
        private Aero_AnimationDefinition.Builder definitionBuilder =
            Aero_AnimationDefinition.builder();
        private int defaultTransitionTicks = 0;

        private Builder(String animationPath) {
            if (animationPath == null) {
                throw new IllegalArgumentException("animationPath must not be null");
            }
            this.animationPath = animationPath;
            this.bundle = null;
        }

        private Builder(Aero_AnimationBundle bundle) {
            if (bundle == null) throw new IllegalArgumentException("bundle must not be null");
            this.animationPath = null;
            this.bundle = bundle;
        }

        public Builder state(int stateId, String clipName) {
            if (definition != null) {
                throw new IllegalStateException("state() cannot be used after definition()");
            }
            definitionBuilder.state(stateId, clipName);
            return this;
        }

        public Builder definition(Aero_AnimationDefinition definition) {
            if (definition == null) {
                throw new IllegalArgumentException("definition must not be null");
            }
            this.definition = definition;
            this.definitionBuilder = null;
            return this;
        }

        /**
         * Default crossfade applied by {@link Aero_AnimationSpec#applyState}
         * when state changes. {@code 0} (the default) snaps without blending.
         */
        public Builder defaultTransitionTicks(int ticks) {
            if (ticks < 0) {
                throw new IllegalArgumentException("defaultTransitionTicks must be >= 0");
            }
            this.defaultTransitionTicks = ticks;
            return this;
        }

        public Aero_AnimationSpec build() {
            Aero_AnimationBundle resolvedBundle =
                bundle != null ? bundle : Aero_AnimationLoader.load(animationPath);
            Aero_AnimationDefinition resolvedDefinition =
                definition != null ? definition : definitionBuilder.build();
            return new Aero_AnimationSpec(animationPath, resolvedBundle, resolvedDefinition,
                defaultTransitionTicks);
        }
    }
}
