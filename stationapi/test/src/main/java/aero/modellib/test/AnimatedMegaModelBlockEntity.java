package aero.modellib.test;

import aero.modellib.Aero_AnimationBundle;
import aero.modellib.Aero_AnimationDefinition;
import aero.modellib.Aero_AnimationEventListener;
import aero.modellib.Aero_AnimationLoader;
import aero.modellib.Aero_AnimationState;
import aero.modellib.Aero_CellPageRenderableBE;
import aero.modellib.Aero_MeshModel;
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
 * server log when {@code -Daero.test.events.log=true}; sound / particle
 * side-effects are opt-in with {@code -Daero.test.events.sideEffects=true}.
 * Perf runs keep both disabled so the benchmark measures model rendering.
 */
public class AnimatedMegaModelBlockEntity extends Aero_RenderDistanceBlockEntity implements Aero_CellPageRenderableBE {

    public static final int STATE_IDLE = 0;
    public static final int STATE_SPIN = 1;
    private static final boolean LOG_EVENTS =
        "true".equalsIgnoreCase(System.getProperty("aero.test.events.log"));

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
    private boolean phaseSeeded;

    public AnimatedMegaModelBlockEntity() {
        // Route the clip's keyframe events into actual MC sound + particle
        // calls so testers can confirm the Tier 2.B wiring without grepping
        // the server log. When the keyframe declares a "locator" (a bone
        // name from the bundle), we resolve the bone's pivot through the
        // bundle and spawn the side-effect AT that pivot offset relative
        // to the BE — so a particle declared on the "turbine_l" bone fires
        // from the actual turbine position, not the block centre.
        //
        // Sound calls go through {@link aero.modellib.Aero_SoundCoalesce}
        // — under MEGA load (144 identical BEs per chunk all firing the
        // same sound on the same tick) raw {@code world.playSound} hits
        // a sub-frame stutter as Paul Lamb's SoundSystem churns through
        // 144 source allocations. The coalescer caps simultaneous plays
        // per-name to {@link Aero_SoundCoalesce#getMaxPerName()} (default
        // 3), keeping the closest-to-camera N. Toggle:
        // {@code -Daero.soundcoalesce=false} for raw playSound.
        animState.setEventListener(new Aero_AnimationEventListener() {
            @Override
            public void onEvent(String channel, String data, String locator, float time) {
                if (AeroTestMod.MEGA_TEST && !AeroTestMod.MEGA_SIDE_EFFECTS) {
                    return;
                }
                if (LOG_EVENTS) {
                    // The println is hot under dense BE load (hundreds of
                    // BEs × 2-3 events / cycle = stdout backpressure). Keep
                    // it opt-in for focused event-listener debugging only.
                    System.out.println("[aerotest:event] " + channel + "=" + data
                        + " @ t=" + time + "s loc=" + locator);
                }
                if (!AeroTestMod.TEST_EVENT_SIDE_EFFECTS) {
                    return;
                }
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
                    aero.modellib.Aero_SoundCoalesce.queue(cx, cy, cz, data, 0.6f, 1.0f);
                } else if ("particle".equals(channel)) {
                    // Particle cap: under MEGA load skip the 5-particle
                    // burst and emit a single particle (still visible
                    // from a distance, ~5× cheaper). Real downstream
                    // mods can scale similarly via a flag.
                    int burst = AeroTestMod.MEGA_TEST ? 1 : 5;
                    for (int i = 0; i < burst; i++) {
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
        });
    }

    @Override
    protected double getAeroRenderRadius() {
        return 4.0d;
    }

    @Override
    public Aero_MeshModel aeroCellModel() {
        return AnimatedMegaModelBlockEntityRenderer.MODEL;
    }

    @Override
    public String aeroCellTexturePath() {
        return AnimatedMegaModelBlockEntityRenderer.TEXTURE;
    }

    @Override
    public float aeroCellBrightness() {
        return AeroLight.brightnessAbove(world, x, y, z);
    }

    @Override
    public double aeroCellVisualRadius() {
        return 4.0d;
    }

    @Override
    public double aeroCellAnimatedDistance() {
        return AeroTestMod.demoAnimatedLodDistance();
    }

    @Override
    public double aeroCellMaxRenderDistance() {
        return getAeroMaxRenderDistance();
    }

    @Override
    public void tick() {
        super.tick();
        if (!shouldTickAnimation()) return;
        animState.setState(STATE_SPIN);
        if (!phaseSeeded) {
            AeroTestMod.seedMegaLoopPhase(animState, STATE_SPIN, x, y, z);
            phaseSeeded = true;
        }
        animState.tick();
    }
}
