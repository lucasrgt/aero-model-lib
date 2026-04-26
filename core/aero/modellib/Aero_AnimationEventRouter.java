package aero.modellib;

import java.util.HashMap;
import java.util.Map;

/**
 * Routing {@link Aero_AnimationEventListener} that dispatches keyframe events
 * by {@code (channel, name)} or {@code channel} fallback to small focused
 * handlers — replaces the giant {@code if/else} in a hand-rolled lambda.
 *
 * <pre>
 * Aero_AnimationEventListener listener = Aero_AnimationEventRouter.builder()
 *     .on("sound",    "random.click", (channel, data, locator, time) -> world.playSound(...))
 *     .on("particle", "smoke",        (channel, data, locator, time) -> world.spawnParticle(...))
 *     .onChannel("custom",            (channel, data, locator, time) -> handleCustom(data))
 *     .build();
 *
 * playback.setEventListener(listener);
 * </pre>
 *
 * <p>Lookup order: {@code (channel, name)} exact match, then channel
 * fallback, then a fallback registered via {@link Builder#otherwise}. Events
 * with no matching route are silently dropped.
 */
public final class Aero_AnimationEventRouter implements Aero_AnimationEventListener {

    private final Map exactByChannel;          // Map<channel, Map<name, listener>>
    private final Map channelFallbacks;        // Map<channel, listener>
    private final Aero_AnimationEventListener fallback;

    private Aero_AnimationEventRouter(Builder builder) {
        this.exactByChannel = new HashMap(builder.exactByChannel);
        this.channelFallbacks = new HashMap(builder.channelFallbacks);
        this.fallback = builder.fallback;
    }

    public static Builder builder() {
        return new Builder();
    }

    public void onEvent(String channel, String data, String locator, float time) {
        Map byName = (Map) exactByChannel.get(channel);
        if (byName != null) {
            Aero_AnimationEventListener exact = (Aero_AnimationEventListener) byName.get(data);
            if (exact != null) {
                exact.onEvent(channel, data, locator, time);
                return;
            }
        }
        Aero_AnimationEventListener channelFallback =
            (Aero_AnimationEventListener) channelFallbacks.get(channel);
        if (channelFallback != null) {
            channelFallback.onEvent(channel, data, locator, time);
            return;
        }
        if (fallback != null) {
            fallback.onEvent(channel, data, locator, time);
        }
    }

    public static final class Builder {
        private final Map exactByChannel = new HashMap();
        private final Map channelFallbacks = new HashMap();
        private Aero_AnimationEventListener fallback;

        private Builder() {}

        /** Routes events whose channel AND name both match exactly. */
        public Builder on(String channel, String name, Aero_AnimationEventListener handler) {
            requireNonEmpty("channel", channel);
            requireNonEmpty("name", name);
            requireNonNull("handler", handler);
            Map byName = (Map) exactByChannel.get(channel);
            if (byName == null) {
                byName = new HashMap();
                exactByChannel.put(channel, byName);
            }
            byName.put(name, handler);
            return this;
        }

        /** Routes any event on the given channel that wasn't claimed by an {@link #on} entry. */
        public Builder onChannel(String channel, Aero_AnimationEventListener handler) {
            requireNonEmpty("channel", channel);
            requireNonNull("handler", handler);
            channelFallbacks.put(channel, handler);
            return this;
        }

        /** Routes any event that didn't match an {@link #on} or {@link #onChannel} entry. */
        public Builder otherwise(Aero_AnimationEventListener handler) {
            requireNonNull("handler", handler);
            this.fallback = handler;
            return this;
        }

        public Aero_AnimationEventRouter build() {
            return new Aero_AnimationEventRouter(this);
        }

        private static void requireNonEmpty(String name, String value) {
            if (value == null || value.length() == 0) {
                throw new IllegalArgumentException(name + " must not be empty");
            }
        }

        private static void requireNonNull(String name, Object value) {
            if (value == null) throw new IllegalArgumentException(name + " must not be null");
        }
    }
}
