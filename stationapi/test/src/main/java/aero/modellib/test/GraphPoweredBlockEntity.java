package aero.modellib.test;

import aero.modellib.Aero_AnimationBundle;
import aero.modellib.Aero_AnimationDefinition;
import aero.modellib.Aero_AnimationGraph;
import aero.modellib.Aero_AnimationLoader;
import aero.modellib.Aero_AnimationPlayback;
import aero.modellib.Aero_GraphBlend1DNode;
import aero.modellib.Aero_GraphClipNode;
import aero.modellib.Aero_GraphNode;
import aero.modellib.Aero_GraphParams;
import aero.modellib.Aero_RenderDistanceBlockEntity;

public class GraphPoweredBlockEntity extends Aero_RenderDistanceBlockEntity {

    private static final String SPEED_PARAM = "speed";

    public static final Aero_AnimationBundle BUNDLE =
        Aero_AnimationLoader.load("/models/GraphPowered.anim.json");

    private final Aero_AnimationDefinition slowDef =
        new Aero_AnimationDefinition().state(0, "slow");
    private final Aero_AnimationDefinition fastDef =
        new Aero_AnimationDefinition().state(0, "fast");

    private final Aero_AnimationPlayback slowPlayback = slowDef.createPlayback(BUNDLE);
    private final Aero_AnimationPlayback fastPlayback = fastDef.createPlayback(BUNDLE);

    private final Aero_GraphParams params = new Aero_GraphParams();
    public final Aero_AnimationGraph graph;

    private int tick = 0;

    public GraphPoweredBlockEntity() {
        slowPlayback.setState(0);
        fastPlayback.setState(0);
        Aero_GraphNode root = new Aero_GraphBlend1DNode(SPEED_PARAM,
            new float[]{0f, 1f},
            new Aero_GraphNode[]{
                new Aero_GraphClipNode(slowPlayback),
                new Aero_GraphClipNode(fastPlayback)
            });
        this.graph = new Aero_AnimationGraph(root, params);
    }

    @Override protected double getAeroRenderRadius() { return 2.0d; }

    @Override
    public void tick() {
        super.tick();
        slowPlayback.tick();
        fastPlayback.tick();
        // Smooth sine over ~3 seconds (60 ticks at 20 tps): speed cycles
        // 0..1 so the visible blend ramps slow → fast → slow continuously.
        tick++;
        float phase = (tick % 60) / 60f * (float) (2.0 * Math.PI);
        float speed = 0.5f + 0.5f * (float) Math.sin(phase);
        params.setFloat(SPEED_PARAM, speed);
    }
}
