package aero.modellib;

/**
 * Declarative model contract shared by ModLoader and StationAPI integrations.
 *
 * The lower-level loaders/renderers stay available for custom code, but a
 * spec lets normal integrations keep one static description for the model,
 * texture path, animation wiring, entity transform and render options.
 */
public final class Aero_ModelSpec {

    public enum Kind {
        JSON,
        MESH
    }

    private final Kind kind;
    private final String modelPath;
    private final String texturePath;
    private final Aero_JsonModel jsonModel;
    private final Aero_MeshModel meshModel;
    private final Aero_AnimationSpec animationSpec;
    private final Aero_EntityModelTransform entityTransform;
    private final Aero_RenderOptions renderOptions;
    private final double animatedDistanceBlocks;

    public static Builder json(String modelPath) {
        return new Builder(Kind.JSON, modelPath, null, null);
    }

    public static Builder json(Aero_JsonModel model) {
        return new Builder(Kind.JSON, null, model, null);
    }

    public static Builder mesh(String modelPath) {
        return new Builder(Kind.MESH, modelPath, null, null);
    }

    public static Builder mesh(Aero_MeshModel model) {
        return new Builder(Kind.MESH, null, null, model);
    }

    private Aero_ModelSpec(Builder builder) {
        this.kind = builder.kind;
        this.modelPath = builder.modelPath;
        this.texturePath = builder.texturePath;
        this.jsonModel = builder.resolveJsonModel();
        this.meshModel = builder.resolveMeshModel();
        this.animationSpec = builder.animationSpec;
        this.entityTransform = builder.transformBuilder.build();
        this.renderOptions = builder.renderOptions;
        this.animatedDistanceBlocks = builder.animatedDistanceBlocks;

        if (animationSpec != null && kind != Kind.MESH) {
            throw new IllegalStateException("animations are supported only for mesh specs");
        }
    }

    public Kind getKind() {
        return kind;
    }

    public boolean isJson() {
        return kind == Kind.JSON;
    }

    public boolean isMesh() {
        return kind == Kind.MESH;
    }

    public boolean isAnimated() {
        return animationSpec != null;
    }

    public String getModelPath() {
        return modelPath;
    }

    public String getTexturePath() {
        return texturePath;
    }

    public Aero_JsonModel getJsonModel() {
        if (jsonModel == null) throw new IllegalStateException("spec is not a JSON model");
        return jsonModel;
    }

    public Aero_MeshModel getMeshModel() {
        if (meshModel == null) throw new IllegalStateException("spec is not a mesh model");
        return meshModel;
    }

    public Aero_AnimationSpec getAnimationSpec() {
        if (animationSpec == null) throw new IllegalStateException("spec has no animations");
        return animationSpec;
    }

    public Aero_AnimationBundle getAnimationBundle() {
        return getAnimationSpec().getBundle();
    }

    public Aero_AnimationDefinition getAnimationDefinition() {
        return getAnimationSpec().getDefinition();
    }

    public Aero_EntityModelTransform getEntityTransform() {
        return entityTransform;
    }

    public Aero_RenderOptions getRenderOptions() {
        return renderOptions;
    }

    public double getAnimatedDistanceBlocks() {
        return animatedDistanceBlocks;
    }

    public Aero_RenderLod lodRelative(double x, double y, double z, int viewDistance) {
        return Aero_RenderDistanceCulling.lodRelative(
            x, y, z, viewDistance,
            entityTransform.cullingRadius,
            animatedDistanceBlocks,
            entityTransform.maxRenderDistance
        );
    }

    public Aero_AnimationPlayback createPlayback() {
        return getAnimationSpec().createPlayback();
    }

    public Aero_AnimationState createState() {
        return getAnimationSpec().createState();
    }

    /**
     * Sets {@code playback}'s state honoring the configured default transition.
     * Equivalent to {@link Aero_AnimationSpec#applyState}; provided here so the
     * model spec can be the single declarative entry point for callers.
     */
    public void applyState(Aero_AnimationPlayback playback, int stateId) {
        getAnimationSpec().applyState(playback, stateId);
    }

    public int getDefaultTransitionTicks() {
        return isAnimated() ? animationSpec.getDefaultTransitionTicks() : 0;
    }

    public static final class Builder {
        private final Kind kind;
        private final String modelPath;
        private final Aero_JsonModel jsonModel;
        private final Aero_MeshModel meshModel;
        private String texturePath;
        private Aero_AnimationSpec animationSpec;
        private Aero_AnimationSpec.Builder animationBuilder;
        private Aero_EntityModelTransform.Builder transformBuilder =
            Aero_EntityModelTransform.builder();
        private Aero_RenderOptions renderOptions = Aero_RenderOptions.DEFAULT;
        private double animatedDistanceBlocks =
            Aero_RenderDistanceCulling.DEFAULT_SPECIAL_RENDER_RADIUS;

        private Builder(Kind kind, String modelPath,
                        Aero_JsonModel jsonModel,
                        Aero_MeshModel meshModel) {
            if (kind == null) throw new IllegalArgumentException("kind must not be null");
            if (modelPath == null && jsonModel == null && meshModel == null) {
                throw new IllegalArgumentException("modelPath or model must be provided");
            }
            this.kind = kind;
            this.modelPath = modelPath;
            this.jsonModel = jsonModel;
            this.meshModel = meshModel;
        }

        public Builder texture(String texturePath) {
            this.texturePath = texturePath;
            return this;
        }

        public Builder animations(String animationPath) {
            this.animationSpec = null;
            this.animationBuilder = Aero_AnimationSpec.builder(animationPath);
            return this;
        }

        public Builder animations(Aero_AnimationBundle bundle) {
            this.animationSpec = null;
            this.animationBuilder = Aero_AnimationSpec.builder(bundle);
            return this;
        }

        public Builder animations(Aero_AnimationSpec animationSpec) {
            if (animationSpec == null) {
                throw new IllegalArgumentException("animationSpec must not be null");
            }
            this.animationSpec = animationSpec;
            this.animationBuilder = null;
            return this;
        }

        public Builder state(int stateId, String clipName) {
            if (animationBuilder == null) {
                if (animationSpec != null) {
                    throw new IllegalStateException("state() cannot be used after animations(Aero_AnimationSpec)");
                }
                throw new IllegalStateException("state() requires animations(...) first");
            }
            animationBuilder.state(stateId, clipName);
            return this;
        }

        public Builder definition(Aero_AnimationDefinition definition) {
            if (animationBuilder == null) {
                throw new IllegalStateException("definition() requires animations(...) first");
            }
            animationBuilder.definition(definition);
            return this;
        }

        /**
         * Default crossfade applied by {@link Aero_ModelSpec#applyState}
         * when the integer state changes. {@code 0} = snap (default).
         */
        public Builder defaultTransitionTicks(int ticks) {
            if (animationBuilder == null) {
                throw new IllegalStateException("defaultTransitionTicks() requires animations(...) first");
            }
            animationBuilder.defaultTransitionTicks(ticks);
            return this;
        }

        public Builder transform(Aero_EntityModelTransform transform) {
            if (transform == null) throw new IllegalArgumentException("transform must not be null");
            this.transformBuilder = transform.toBuilder();
            return this;
        }

        public Builder offset(float x, float y, float z) {
            transformBuilder.offset(x, y, z);
            return this;
        }

        public Builder scale(float scale) {
            transformBuilder.scale(scale);
            return this;
        }

        public Builder yawOffset(float yawOffset) {
            transformBuilder.yawOffset(yawOffset);
            return this;
        }

        public Builder cullingRadius(float cullingRadius) {
            transformBuilder.cullingRadius(cullingRadius);
            return this;
        }

        public Builder maxRenderDistance(float maxRenderDistance) {
            transformBuilder.maxRenderDistance(maxRenderDistance);
            return this;
        }

        public Builder animatedDistance(double animatedDistanceBlocks) {
            requireNonNegativeFinite("animatedDistanceBlocks", animatedDistanceBlocks);
            this.animatedDistanceBlocks = animatedDistanceBlocks;
            return this;
        }

        public Builder renderOptions(Aero_RenderOptions renderOptions) {
            if (renderOptions == null) {
                throw new IllegalArgumentException("renderOptions must not be null");
            }
            this.renderOptions = renderOptions;
            return this;
        }

        public Builder tint(float r, float g, float b) {
            return renderOptions(Aero_RenderOptions.tint(r, g, b));
        }

        public Aero_ModelSpec build() {
            if (animationBuilder != null) {
                animationSpec = animationBuilder.build();
                animationBuilder = null;
            }
            return new Aero_ModelSpec(this);
        }

        private Aero_JsonModel resolveJsonModel() {
            if (kind != Kind.JSON) return null;
            return jsonModel != null ? jsonModel : Aero_JsonModelLoader.load(modelPath);
        }

        private Aero_MeshModel resolveMeshModel() {
            if (kind != Kind.MESH) return null;
            return meshModel != null ? meshModel : Aero_ObjLoader.load(modelPath);
        }

        private static void requireNonNegativeFinite(String name, double value) {
            if (Double.isNaN(value) || Double.isInfinite(value)) {
                throw new IllegalArgumentException(name + " must be finite");
            }
            if (value < 0.0d) throw new IllegalArgumentException(name + " must be >= 0");
        }
    }
}
