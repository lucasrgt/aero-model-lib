package aero.modellib;

/**
 * Callback invoked by {@link Aero_AnimationPlayback} when playback crosses a
 * non-pose keyframe declared in the {@code .anim.json} {@code keyframes}
 * section. Use it to fire sounds, spawn particles, trigger gameplay events,
 * etc. — any side-effect that must be synchronised with an animation's
 * timeline rather than continuously sampled.
 *
 * <p>The lib itself is platform-neutral, so it doesn't dispatch the event
 * to MC sound/particle systems. Each consumer mod implements this listener
 * and routes the channel + data string however it wants (call {@code
 * world.playSoundEffect}, spawn its own particles, etc.). Channel names are
 * not validated — they are pure routing strings — but the JSON schema
 * encourages {@code "sound"}, {@code "particle"}, and {@code "custom"} so
 * tooling can render a unified palette.
 *
 * <p>Events fire on the SERVER if the playback is ticking on a server side,
 * and on the CLIENT if it ticks on the client. Most mods listen on the
 * client only (sounds + particles are client-side anyway).
 */
public interface Aero_AnimationEventListener {

    /**
     * @param channel  routing string from the JSON ("sound" / "particle" /
     *                 "custom" — or any custom channel the mod adds).
     * @param data     payload string for the event. Sound name, particle
     *                 type, instruction id, etc. — interpretation is up
     *                 to the consumer.
     * @param time     keyframe time in seconds, relative to clip start.
     *                 Useful when a single channel has many events and the
     *                 listener wants to disambiguate.
     */
    void onEvent(String channel, String data, float time);

    /**
     * Optional richer overload that also reports the bone-name "locator"
     * declared on the keyframe. The locator lets a multiblock or multi-
     * bone mesh tell the listener WHERE the event should anchor (the tip
     * of a blade, the muzzle of a cannon, the chimney on a furnace) so
     * sounds, particles or projectiles can spawn at the right point in
     * the model regardless of the BE's origin.
     *
     * <p>Default impl forwards to {@link #onEvent(String, String, float)}
     * for backwards compat — existing listeners keep working without
     * changes; new listeners override this method to receive the locator.
     *
     * @param locator bone name from the JSON, or {@code null} when the
     *                keyframe used the legacy bare-string form.
     */
    default void onEvent(String channel, String data, String locator, float time) {
        onEvent(channel, data, time);
    }
}
