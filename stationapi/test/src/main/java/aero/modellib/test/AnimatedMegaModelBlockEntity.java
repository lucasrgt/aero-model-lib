package aero.modellib.test;

import aero.modellib.Aero_AnimationBundle;
import aero.modellib.Aero_AnimationDefinition;
import aero.modellib.Aero_AnimationEventListener;
import aero.modellib.Aero_AnimationLoader;
import aero.modellib.Aero_AnimationState;
import aero.modellib.Aero_RenderDistanceBlockEntity;

/**
 * BlockEntity that exposes an {@link Aero_AnimationState} driven by tick().
 * Permanently in STATE_SPIN so the rendered mesh keeps looping the bundle's
 * "working" clip (loop=true, length=2s) without needing redstone — placing
 * the block is enough to see the keyframes play continuously.
 *
 * <p>Doubles as the keyframe-event smoke test: the {@code working} clip
 * carries {@code sound}, {@code particle} and {@code custom} entries that
 * are routed to {@link #eventListener} on every cross. They print to the
 * server log so testers can verify the listener is firing without needing
 * sound/particle infrastructure on the consumer side.
 */
public class AnimatedMegaModelBlockEntity extends Aero_RenderDistanceBlockEntity {

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

    public AnimatedMegaModelBlockEntity() {
        // Route the clip's keyframe events into actual MC sound + particle
        // calls so testers can confirm the Tier 2.B wiring without grepping
        // the server log. When the keyframe declares a "locator" (a bone
        // name from the bundle), we resolve the bone's pivot through the
        // bundle and spawn the side-effect AT that pivot offset relative
        // to the BE — so a particle declared on the "turbine_l" bone fires
        // from the actual turbine position, not the block centre.
        animState.setEventListener(new Aero_AnimationEventListener() {
            @Override
            public void onEvent(String channel, String data, String locator, float time) {
                System.out.println("[aerotest:event] " + channel + "=" + data
                    + " @ t=" + time + "s loc=" + locator);
                if (world == null) return;

                // Resolve the locator to its CURRENT animated pivot — this
                // adds the position-channel offset on top of the bundle's
                // rest pivot, so a particle declared on "turbine_l" emits
                // from wherever the turbine actually is in the current
                // frame, not its rest position. Falls back to block centre
                // when locator is null or unknown to the bundle.
                double ox = 0.5, oy = 0.5, oz = 0.5;
                if (locator != null) {
                    float[] pivot = new float[3];
                    if (animState.getAnimatedPivot(locator, 0f, pivot)) {
                        ox = pivot[0]; oy = pivot[1]; oz = pivot[2];
                    }
                }
                double cx = x + ox, cy = y + oy, cz = z + oz;

                if ("sound".equals(channel)) {
                    world.playSound(cx, cy, cz, data, 0.6f, 1.0f);
                } else if ("particle".equals(channel)) {
                    for (int i = 0; i < 5; i++) {
                        world.addParticle(data,
                            cx + (Math.random() - 0.5) * 0.3,
                            cy,
                            cz + (Math.random() - 0.5) * 0.3,
                            0, 0.05, 0);
                    }
                }
                // "custom" is intentionally console-only — gameplay logic
                // would consume it at the entity level (e.g. mark damage
                // hitbox active) rather than inflict a visual side-effect.
            }

            // Default 3-arg overload required by the interface — never
            // called when the 4-arg form is implemented.
            public void onEvent(String channel, String data, float time) {}
        });
    }

    @Override
    protected double getAeroRenderRadius() {
        return 4.0d;
    }

    @Override
    public void tick() {
        super.tick();
        animState.setState(STATE_SPIN);
        animState.tick();
    }
}
