package aero.modellib.test;

import aero.modellib.Aero_AnimationBundle;
import aero.modellib.Aero_AnimationDefinition;
import aero.modellib.Aero_AnimationLoader;
import aero.modellib.Aero_AnimationState;
import aero.modellib.Aero_MorphState;
import aero.modellib.Aero_RenderDistanceBlockEntity;

public class MorphCrystalBlockEntity extends Aero_RenderDistanceBlockEntity {

    public static final int STATE_REST = 1;

    public static final Aero_AnimationBundle BUNDLE =
        Aero_AnimationLoader.load("/models/MorphCrystal.anim.json");

    public static final Aero_AnimationDefinition ANIM_DEF =
        new Aero_AnimationDefinition().state(STATE_REST, "rest");

    public final Aero_AnimationState animState = ANIM_DEF.createState(BUNDLE);
    public final Aero_MorphState morphState = new Aero_MorphState();

    private int tick = 0;

    @Override protected double getAeroRenderRadius() { return 2.0d; }

    @Override
    public void tick() {
        super.tick();
        animState.setState(STATE_REST);
        animState.tick();
        tick++;
        // Sine pulse 0..1 over 2 seconds (40 ticks at 20 tps).
        float phase = (tick % 40) / 40f * (float) (2.0 * Math.PI);
        float w = 0.5f + 0.5f * (float) Math.sin(phase);
        morphState.set("expanded", w);
    }
}
