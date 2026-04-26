package aero.modellib;

import net.minecraft.src.World;

/**
 * Helper for gating animation event side-effects per server/client side
 * in Beta 1.7.3 (ModLoader/Forge runtime).
 *
 * <p>{@link Aero_AnimationPlayback#tick()} fires keyframe events on every
 * tick that crosses a keyframe — and tile entities tick on BOTH sides in
 * SMP. Without a gate, sounds get played twice (server broadcasts via
 * packet, client also calls {@code playSoundEffect} locally) and any
 * server-side rendering attempt is wasted work.
 *
 * <p>The canonical pattern is:
 *
 * <pre>
 *   animState.setEventListener(new Aero_AnimationEventListener() {
 *       public void onEvent(String channel, String name, String locator, float time) {
 *           if ("sound".equals(channel)) {
 *               // Sounds: server-side only — playSoundEffect packet broadcasts
 *               // to every client, so doing this on the client too would double-play.
 *               if (Aero_AnimationSide.isServerSide(worldObj)) {
 *                   double[] p = locatorWorldPos(locator);
 *                   worldObj.playSoundEffect(p[0], p[1], p[2], name, 0.4f, 1f);
 *               }
 *           } else if ("particle".equals(channel)) {
 *               // Particles: fire unconditionally — World#spawnParticle is a
 *               // no-op on the dedicated SMP server (no RenderEngine), and on
 *               // SP/SMP-client it just renders locally.
 *               double[] p = locatorWorldPos(locator);
 *               worldObj.spawnParticle(name, p[0], p[1], p[2], 0d, 0.05d, 0d);
 *           }
 *       }
 *   });
 * </pre>
 *
 * <p>Note that this only fixes side-effect duplication. To actually drive
 * the animation on the SMP CLIENT (so the model moves, not just makes
 * sounds), the consuming mod still has to sync the integer state via
 * {@code Packet230ModLoader} or a custom packet — the lib has no way to
 * know which channel/IDs you reserved.
 */
public final class Aero_AnimationSide {

    private Aero_AnimationSide() {}

    /**
     * Returns {@code true} on the SP integrated server world AND on the
     * SMP dedicated server. Use this to gate broadcasting actions like
     * {@code World#playSoundEffect}.
     */
    public static boolean isServerSide(World world) {
        return world != null && !world.multiplayerWorld;
    }

    /**
     * Returns {@code true} on the world owned by an SMP client connected
     * to a remote server. Use this when the action only makes sense on
     * the rendering side.
     */
    public static boolean isClientSide(World world) {
        return world != null && world.multiplayerWorld;
    }
}
