package aero.modellib;

import java.util.ArrayList;
import java.util.List;

/**
 * Ordered chain of (predicate → stateId) rules that picks the next state
 * for an {@link Aero_AnimationPlayback} based on runtime conditions.
 *
 * <p>Without this helper, consumers typically write a giant {@code if/else}
 * in their tick method to translate gameplay state into the int that
 * {@code playback.setState(...)} consumes. The router lets you declare
 * the rules once at construction time, in priority order, and just call
 * {@link #applyTo(Aero_AnimationPlayback)} each tick.
 *
 * <pre>
 *   Aero_AnimationStateRouter router = new Aero_AnimationStateRouter()
 *       .when(pb -&gt; entity.isDead(),       STATE_DEATH)
 *       .when(pb -&gt; entity.isAttacking(),  STATE_ATTACK)
 *       .when(pb -&gt; entity.isMoving(),     STATE_WALK)
 *       .otherwise(STATE_IDLE);
 *
 *   // each tick:
 *   router.applyTo(playback);
 * </pre>
 *
 * <p>Rules are evaluated in insertion order — the first matching predicate
 * wins. The optional {@link #otherwise(int)} state fires when none of the
 * predicates returned true; without it, no setState call is made on a
 * "no-match" tick (the playback keeps its previous state).
 */
public final class Aero_AnimationStateRouter {

    private final List rules = new ArrayList();    // List<Rule>
    private boolean    hasFallback;
    private int        fallbackState;
    private int        transitionTicks;

    /** Adds a rule to the end of the chain. */
    public Aero_AnimationStateRouter when(Aero_AnimationPredicate predicate, int stateId) {
        if (predicate == null) throw new IllegalArgumentException("predicate must not be null");
        rules.add(new Rule(predicate, stateId));
        return this;
    }

    /**
     * Sets the state to fall back to when no rule matches. Without this,
     * a tick with no matching rule leaves the playback's state untouched.
     */
    public Aero_AnimationStateRouter otherwise(int stateId) {
        this.hasFallback = true;
        this.fallbackState = stateId;
        return this;
    }

    /**
     * Configures the router to call {@link Aero_AnimationPlayback#setStateWithTransition}
     * with this tick count instead of {@link Aero_AnimationPlayback#setState},
     * so every state change fades smoothly. Pass 0 (default) for hard cuts.
     */
    public Aero_AnimationStateRouter withTransition(int ticks) {
        this.transitionTicks = ticks;
        return this;
    }

    /**
     * Evaluates the rules and applies the matching state to the playback.
     * Idempotent on a per-tick basis — calling it twice with the same
     * conditions produces a single (no-op) setState since Aero_AnimationPlayback
     * already short-circuits identical state transitions.
     */
    public void applyTo(Aero_AnimationPlayback playback) {
        for (int i = 0; i < rules.size(); i++) {
            Rule rule = (Rule) rules.get(i);
            if (rule.predicate.test(playback)) {
                set(playback, rule.stateId);
                return;
            }
        }
        if (hasFallback) {
            set(playback, fallbackState);
        }
    }

    private void set(Aero_AnimationPlayback playback, int stateId) {
        if (transitionTicks > 0) {
            playback.setStateWithTransition(stateId, transitionTicks);
        } else {
            playback.setState(stateId);
        }
    }

    private static final class Rule {
        final Aero_AnimationPredicate predicate;
        final int stateId;
        Rule(Aero_AnimationPredicate predicate, int stateId) {
            this.predicate = predicate;
            this.stateId   = stateId;
        }
    }
}
