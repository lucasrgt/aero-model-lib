package aero.modellib;

/**
 * Single-method strategy that decides whether a given animation state
 * should be selected for the current tick. Used by
 * {@link Aero_AnimationStateRouter} to express "play X when condition Y"
 * without the consumer hard-coding state-int routing in their tick method.
 *
 * <p>The lib is Java 8-source, so this stays a regular interface (not a
 * {@code @FunctionalInterface}) — but a single-method shape lets you pass
 * a lambda where the target language allows it.
 */
public interface Aero_AnimationPredicate {

    /**
     * @param playback The playback being routed. Inspect
     *                 {@link Aero_AnimationPlayback#currentState},
     *                 {@link Aero_AnimationPlayback#isFinished()},
     *                 etc., to decide.
     * @return {@code true} if the rule's state should win this tick.
     */
    boolean test(Aero_AnimationPlayback playback);
}
