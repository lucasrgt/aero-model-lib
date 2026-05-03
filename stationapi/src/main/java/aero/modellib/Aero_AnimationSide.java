package aero.modellib;

import net.minecraft.world.World;

import aero.modellib.animation.Aero_AnimationEventListener;
import aero.modellib.animation.Aero_AnimationPlayback;

/**
 * Helper for gating animation event side-effects per server/client side
 * in StationAPI/Babric (Yarn-mapped).
 *
 * <p>{@link Aero_AnimationPlayback#tick()} fires keyframe events on every
 * tick that crosses a keyframe — and {@code BlockEntity}s tick on BOTH
 * sides in SMP. Without a gate, sounds get played twice (server
 * broadcasts via packet, client also calls {@code playSound} locally) and
 * server-side particle attempts are wasted work.
 *
 * <p>The canonical pattern is:
 *
 * <pre>
 *   animState.setEventListener(new Aero_AnimationEventListener() {
 *       public void onEvent(String channel, String name, String locator, float time) {
 *           if ("sound".equals(channel)) {
 *               if (Aero_AnimationSide.isServerSide(world)) {
 *                   double[] p = locatorWorldPos(locator);
 *                   world.playSound(p[0], p[1], p[2], name, 0.4f, 1f);
 *               }
 *           } else if ("particle".equals(channel)) {
 *               // Particles: fire unconditionally — yarn World#addParticle is
 *               // client-only, so it's a silent no-op on the dedicated server.
 *               double[] p = locatorWorldPos(locator);
 *               world.addParticle(name, p[0], p[1], p[2], 0d, 0.05d, 0d);
 *           }
 *       }
 *   });
 * </pre>
 *
 * <p>Note that this only fixes side-effect duplication. To actually drive
 * the animation on the SMP CLIENT (so the model moves, not just makes
 * sounds), the consuming mod still has to sync the integer state through
 * Fabric Networking API (or a custom packet) — the lib has no way to know
 * which channel ID you reserved.
 */
public final class Aero_AnimationSide {

    private Aero_AnimationSide() {}

    /**
     * Returns {@code true} on the SP integrated server world AND on the
     * SMP dedicated server. Use this to gate broadcasting actions like
     * {@code World#playSound}.
     */
    public static boolean isServerSide(World world) {
        return world != null && !world.isRemote;
    }

    /**
     * Returns {@code true} on the world owned by an SMP client connected
     * to a remote server. Use this when the action only makes sense on
     * the rendering side.
     */
    public static boolean isClientSide(World world) {
        return world != null && world.isRemote;
    }
}
