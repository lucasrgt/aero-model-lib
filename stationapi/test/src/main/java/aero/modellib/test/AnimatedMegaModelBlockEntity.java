package aero.modellib.test;

import aero.modellib.Aero_AnimationBundle;
import aero.modellib.Aero_AnimationDefinition;
import aero.modellib.Aero_AnimationLoader;
import aero.modellib.Aero_AnimationState;
import net.minecraft.block.entity.BlockEntity;

/**
 * BlockEntity that exposes an {@link Aero_AnimationState} driven by tick().
 * Permanently in STATE_SPIN so the rendered mesh keeps looping the bundle's
 * "working" clip (loop=true, length=2s) without needing redstone — placing
 * the block is enough to see the keyframes play continuously.
 */
public class AnimatedMegaModelBlockEntity extends BlockEntity {

    public static final int STATE_IDLE = 0;
    public static final int STATE_SPIN = 1;

    public static final Aero_AnimationBundle BUNDLE =
        Aero_AnimationLoader.load("/models/MegaCrusher.anim.json");

    // The MegaCrusher .anim.json bundles a single clip named "working".
    // STATE_IDLE has no clip mapped (model stays at rest pose),
    // STATE_SPIN plays the working animation. Toggle in tick() exercises
    // both branches of Aero_AnimationState (no-op clip + active clip).
    public static final Aero_AnimationDefinition ANIM_DEF =
        new Aero_AnimationDefinition()
            .state(STATE_SPIN, "working");

    public final Aero_AnimationState animState = ANIM_DEF.createState(BUNDLE);

    private long tickCount = 0;
    private boolean dumpedBundle = false;

    @Override
    public void tick() {
        super.tick();
        if (!dumpedBundle) {
            dumpedBundle = true;
            StringBuilder keys = new StringBuilder();
            for (Object k : BUNDLE.clips.keySet()) {
                if (keys.length() > 0) keys.append(",");
                keys.append("'").append(k).append("'");
            }
            System.out.println("[aerotest:anim] BUNDLE.clips.keys=[" + keys + "]"
                + " def(0)=" + ANIM_DEF.getClipName(STATE_IDLE)
                + " def(1)=" + ANIM_DEF.getClipName(STATE_SPIN)
                + " getClip('working')=" + BUNDLE.getClip("working"));
        }
        animState.setState(STATE_SPIN);
        animState.tick();
        tickCount++;
        if (tickCount % 60 == 0) {
            System.out.println("[aerotest:anim] tick=" + tickCount
                + " state=" + animState.currentState
                + " clip=" + (animState.getCurrentClip() != null ? animState.getCurrentClip().name : "null")
                + " interpTime=" + animState.getInterpolatedTime(0));
        }
    }
}
