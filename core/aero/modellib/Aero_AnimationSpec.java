package aero.modellib;

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

    public static Builder builder(String animationPath) {
        return new Builder(animationPath);
    }

    public static Builder builder(Aero_AnimationBundle bundle) {
        return new Builder(bundle);
    }

    private Aero_AnimationSpec(String animationPath,
                               Aero_AnimationBundle bundle,
                               Aero_AnimationDefinition definition) {
        if (bundle == null) throw new IllegalArgumentException("bundle must not be null");
        if (definition == null) throw new IllegalArgumentException("definition must not be null");
        this.animationPath = animationPath;
        this.bundle = bundle;
        this.definition = definition;
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

    public Aero_AnimationPlayback createPlayback() {
        return definition.createPlayback(bundle);
    }

    public Aero_AnimationState createState() {
        return definition.createState(bundle);
    }

    public static final class Builder {
        private final String animationPath;
        private final Aero_AnimationBundle bundle;
        private Aero_AnimationDefinition definition;
        private Aero_AnimationDefinition.Builder definitionBuilder =
            Aero_AnimationDefinition.builder();

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

        public Aero_AnimationSpec build() {
            Aero_AnimationBundle resolvedBundle =
                bundle != null ? bundle : Aero_AnimationLoader.load(animationPath);
            Aero_AnimationDefinition resolvedDefinition =
                definition != null ? definition : definitionBuilder.build();
            return new Aero_AnimationSpec(animationPath, resolvedBundle, resolvedDefinition);
        }
    }
}
